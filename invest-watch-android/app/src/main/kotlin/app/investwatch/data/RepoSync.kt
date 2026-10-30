package app.investwatch.data

import android.content.Context
import android.util.Base64
import app.investwatch.core.Dates
import app.investwatch.core.Favorites
import app.investwatch.core.Parse
import app.investwatch.data.db.AppDatabase
import app.investwatch.data.db.FavoriteEntity
import app.investwatch.data.db.SyncStateEntity
import app.investwatch.data.net.GitHubApi
import app.investwatch.data.net.PutFileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Что пошло не так при синхронизации — показываем понятным текстом. */
class SyncError(message: String, val needsToken: Boolean = false) : Exception(message)

/**
 * Забирает репозиторий данных в локальную базу и пишет обратно единственный
 * файл — config/favorites.json.
 *
 * Сначала одним запросом берём дерево репозитория с sha каждого файла.
 * Файл скачивается, только если его sha изменился: на сотне бумаг это
 * разница между сотней запросов и несколькими.
 */
@Singleton
class RepoSync @Inject constructor(
    private val context: Context,
    private val api: GitHubApi,
    private val database: AppDatabase,
    private val settings: SettingsStore,
    private val secureStore: SecureStore,
) {

    data class Config(
        val owner: String = DEFAULT_OWNER,
        val repo: String = DEFAULT_REPO,
        val branch: String = DEFAULT_BRANCH,
    )

    private val config = Config()

    suspend fun sync(force: Boolean = false) {
        if (settings.demoMode.firstValue()) {
            syncFromAssets()
            return
        }
        if (!secureStore.has(SecureStore.GITHUB_TOKEN)) {
            throw SyncError("Не задан токен GitHub", needsToken = true)
        }

        val tree = loadTree(force) ?: return
        val paths = tree.associate { it.path to it.sha.orEmpty() }

        read(Paths.WATCHLIST, paths, force)?.let { storeWatchlist(it) }
        read(Paths.CRITERIA, paths, force)?.let { storeCriteria(it) }
        read(Paths.OVERRIDES, paths, force)?.let { storeOverrides(it) }
        read(Paths.FEED, paths, force)?.let { storeFeed(it) }
        read(Paths.FAVORITES, paths, force)?.let { storeFavorites(it) }

        for (asset in database.watchlistDao().assetsNow()) {
            read(Paths.status(asset.id), paths, force)?.let { storeStatus(asset.id, it) }
            read(Paths.events(asset.id), paths, force)?.let { storeEvents(asset.id, it) }
            read(Paths.dividends(asset.id), paths, force)?.let { storeDividends(asset.id, it) }
        }

        settings.markSynced()
    }

    // ───────────────────────────── чтение файлов ─────────────────────────────

    private suspend fun loadTree(force: Boolean): List<app.investwatch.data.net.TreeEntryDto>? {
        val state = database.syncStateDao().get(TREE_KEY)
        val response = call {
            api.tree(config.owner, config.repo, config.branch, etag = if (force) null else state?.etag)
        }
        if (response.code() == 304) return emptyList()
        val body = response.body() ?: throw SyncError("Пустой ответ GitHub на дерево репозитория")

        database.syncStateDao().upsert(
            SyncStateEntity(TREE_KEY, response.headers()["ETag"], body.sha, System.currentTimeMillis()),
        )
        return body.tree.filter { it.type == "blob" }
    }

    /** Возвращает содержимое файла или null, если оно не менялось либо файла нет. */
    private suspend fun read(path: String, paths: Map<String, String>, force: Boolean): String? {
        val remoteSha = paths[path] ?: return null
        val state = database.syncStateDao().get(path)
        if (!force && state?.sha == remoteSha) return null

        val response = call { api.raw(config.owner, config.repo, path, config.branch) }
        if (response.code() == 404) return null
        val text = response.body()?.string() ?: return null

        database.syncStateDao().upsert(
            SyncStateEntity(path, response.headers()["ETag"], remoteSha, System.currentTimeMillis()),
        )
        return text
    }

    private suspend fun <T> call(block: suspend () -> retrofit2.Response<T>): retrofit2.Response<T> {
        val response = try {
            block()
        } catch (error: IOException) {
            throw SyncError("Нет связи с GitHub: ${error.message}")
        }
        when (response.code()) {
            401 -> throw SyncError("GitHub не принял токен", needsToken = true)
            403 -> throw SyncError("GitHub отказал: нет доступа или исчерпан лимит")
            404 -> return response
            in 500..599 -> throw SyncError("GitHub ответил ${response.code()}")
        }
        return response
    }

    // ───────────────────────────── запись в базу ─────────────────────────────

    private suspend fun storeWatchlist(text: String) {
        val watchlist = Parse.watchlist(text)
        if (watchlist.assets.isEmpty() && watchlist.markets.isEmpty()) return
        database.watchlistDao().replaceWatchlist(
            markets = watchlist.markets.mapIndexed { index, item -> item.toEntity(index) },
            kinds = watchlist.kinds.mapIndexed { index, item -> item.toEntity(index) },
            assets = watchlist.assets.mapIndexed { index, item -> item.toEntity(index) },
        )
    }

    private suspend fun storeCriteria(text: String) = withContext(Dispatchers.IO) {
        criteriaText = text
        context.openFileOutput(CRITERIA_FILE, Context.MODE_PRIVATE).use {
            it.write(text.toByteArray())
        }
    }

    private suspend fun storeOverrides(text: String) {
        val overrides = Parse.overrides(text)
        database.statusDao().replaceOverrides(
            overrides.map { (assetId, criteria) -> overrideEntity(assetId, criteria) },
        )
    }

    private suspend fun storeStatus(assetId: String, text: String) {
        database.statusDao().upsert(Parse.status(text).toEntity(assetId))
    }

    private suspend fun storeEvents(assetId: String, text: String) {
        val parsed = Parse.eventsJsonl(text, assetId)
        database.eventDao().replaceOf(assetId, parsed.events.map { it.toEntity() })
    }

    private suspend fun storeDividends(assetId: String, text: String) {
        database.dividendsDao().upsert(Parse.dividends(text, assetId).toEntity())
    }

    private suspend fun storeFeed(text: String) {
        // Лента — срез тех же событий; храним её как события, чтобы экран
        // «Новости» работал и до того, как скачаны все events.jsonl.
        val parsed = Parse.feed(text)
        if (parsed.events.isEmpty()) return
        database.eventDao().upsert(parsed.events.map { it.toEntity() })
    }

    private suspend fun storeFavorites(text: String) {
        val remote = Parse.favorites(text)
        val merged = Favorites.merge(
            remote = remote,
            localOrder = settings.favoritesOrder.firstValue(),
            pending = settings.pending.firstValue(),
        )
        applyFavorites(merged)
    }

    private suspend fun applyFavorites(order: List<String>) {
        database.favoriteDao().replace(
            order.mapIndexed { index, assetId -> FavoriteEntity(assetId, index) },
        )
        settings.setFavoritesOrder(order)
    }

    // ───────────────────────── запись избранного в GitHub ────────────────────

    /**
     * Отправляет накопленные правки избранного. При конфликте sha перечитывает
     * свежий файл, накладывает правки заново и повторяет — до трёх раз.
     */
    suspend fun pushFavorites(): Boolean {
        if (settings.demoMode.firstValue()) return true
        val pending = settings.pending.firstValue()
        // Перетаскивание меняет только порядок, звёздочек в pending нет —
        // поэтому отдельный флаг, иначе новый порядок не уехал бы никогда.
        if (pending.isEmpty && !settings.favoritesDirty.firstValue()) return true
        if (!secureStore.has(SecureStore.GITHUB_TOKEN)) return false

        repeat(ATTEMPTS) {
            val metadata = call {
                api.metadata(config.owner, config.repo, Paths.FAVORITES, config.branch)
            }
            val sha = metadata.body()?.sha
            val remote = metadata.body()?.content
                ?.let { runCatching { String(Base64.decode(it, Base64.DEFAULT)) }.getOrNull() }
                ?.let { Parse.favorites(it) }
                .orEmpty()

            val merged = Favorites.merge(remote, settings.favoritesOrder.firstValue(), pending)
            val body = PutFileDto(
                message = "favorites: обновлено из приложения",
                content = Base64.encodeToString(
                    Favorites.render(merged).toByteArray(), Base64.NO_WRAP,
                ),
                sha = sha,
                branch = config.branch,
            )

            val response = call { api.put(config.owner, config.repo, Paths.FAVORITES, body) }
            if (response.isSuccessful) {
                applyFavorites(merged)
                settings.setPending(Favorites.Pending())
                settings.setFavoritesDirty(false)
                return true
            }
            // 409 и 422 — файл изменили, пока мы собирались: читаем заново.
            if (response.code() != 409 && response.code() != 422) return false
        }
        return false
    }

    // ───────────────────────────── демонстрация ──────────────────────────────

    /** Моковые данные из assets: экраны можно посмотреть без токена и сети. */
    private suspend fun syncFromAssets() = withContext(Dispatchers.IO) {
        fun read(path: String): String? =
            runCatching { context.assets.open("mock/$path").bufferedReader().readText() }.getOrNull()

        read(Paths.WATCHLIST)?.let { storeWatchlist(it) }
        read(Paths.CRITERIA)?.let { storeCriteria(it) }
        read(Paths.OVERRIDES)?.let { storeOverrides(it) }
        read(Paths.FEED)?.let { storeFeed(it) }
        read(Paths.FAVORITES)?.let { storeFavorites(it) }

        for (asset in database.watchlistDao().assetsNow()) {
            read(Paths.status(asset.id))?.let { storeStatus(asset.id, it) }
            read(Paths.events(asset.id))?.let { storeEvents(asset.id, it) }
            read(Paths.dividends(asset.id))?.let { storeDividends(asset.id, it) }
        }
        settings.markSynced()
    }

    // ────────────────────────────── CRITERIA.md ──────────────────────────────

    private var criteriaText: String? = null

    /** Текст CRITERIA.md: в базе ему не место, держим файлом. */
    suspend fun criteriaMarkdown(): String = withContext(Dispatchers.IO) {
        criteriaText?.let { return@withContext it }
        val fromFile = runCatching {
            context.openFileInput(CRITERIA_FILE).bufferedReader().readText()
        }.getOrNull()
        val fromAssets = runCatching {
            context.assets.open("mock/${Paths.CRITERIA}").bufferedReader().readText()
        }.getOrNull()
        (fromFile ?: fromAssets).orEmpty().also { criteriaText = it }
    }

    object Paths {
        const val WATCHLIST = "config/watchlist.json"
        const val FAVORITES = "config/favorites.json"
        const val OVERRIDES = "config/overrides.json"
        const val CRITERIA = "CRITERIA.md"
        const val FEED = "feed/feed.json"

        fun status(assetId: String) = "data/$assetId/status.json"
        fun events(assetId: String) = "data/$assetId/events.jsonl"
        fun dividends(assetId: String) = "data/$assetId/dividends.json"
    }

    companion object {
        const val DEFAULT_OWNER = "nubasikplayyt-dev"
        const val DEFAULT_REPO = "invest-watch"
        const val DEFAULT_BRANCH = "main"

        private const val TREE_KEY = "__tree__"
        private const val CRITERIA_FILE = "criteria.md"
        private const val ATTEMPTS = 3

        /** Сегодняшняя дата — вынесена, чтобы не тянуть java.time по месту. */
        fun today(): String = Dates.today()
    }
}

private suspend fun <T> Flow<T>.firstValue(): T = first()
