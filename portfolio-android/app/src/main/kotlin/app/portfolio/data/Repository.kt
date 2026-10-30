package app.portfolio.data

import app.portfolio.core.Asset
import app.portfolio.core.Criteria
import app.portfolio.core.CriterionHelp
import app.portfolio.core.MonitorEvent
import app.portfolio.core.Parse
import app.portfolio.core.Paths
import app.portfolio.core.RepoConfig
import app.portfolio.core.Section
import app.portfolio.core.Status
import app.portfolio.core.Watchlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Всё, что приложение знает о портфеле на данный момент. */
data class PortfolioData(
    val watchlist: Watchlist,
    val statuses: Map<String, Status>,
    val events: Map<String, List<MonitorEvent>>,
    val feed: List<MonitorEvent>,
    val criteria: Map<String, CriterionHelp>,
) {
    val assets: List<Pair<Section, Asset>> get() = watchlist.assets

    fun eventsOf(assetId: String): List<MonitorEvent> = events[assetId].orEmpty()

    /** Все события по всем бумагам — нужны фоновой проверке. */
    val allEvents: List<MonitorEvent> get() = events.values.flatten()
}

/**
 * Читает репозиторий данных по HTTP. Ничего туда не пишет.
 *
 * Ответы кэшируются на диске средствами OkHttp, поэтому повторное открытие
 * экрана не ходит в сеть, а обновление по кнопке читает заново.
 */
class Repository(cacheDir: java.io.File) {

    private val client = OkHttpClient.Builder()
        .cache(okhttp3.Cache(java.io.File(cacheDir, "http"), CACHE_BYTES))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** @param force читать заново, минуя кэш (кнопка «Обновить»). */
    suspend fun load(config: RepoConfig, force: Boolean): PortfolioData = coroutineScope {
        val watchlistText = fetch(config.url(Paths.WATCHLIST), force)
            ?: throw IOException("Не удалось прочитать ${Paths.WATCHLIST}")
        val watchlist = Parse.watchlist(watchlistText)

        val criteriaText = async { fetch(config.url(Paths.CRITERIA), force) }
        val feedText = async { fetch(config.url(Paths.FEED), force) }

        val perAsset = watchlist.assets.map { (_, asset) ->
            async {
                val status = fetch(config.url(Paths.status(asset.id)), force)
                    ?.let { runCatching { Parse.status(it) }.getOrNull() }
                val events = fetch(config.url(Paths.events(asset.id)), force)
                    ?.let { Parse.eventsJsonl(it, asset.id).events }
                    .orEmpty()
                Triple(asset.id, status, events)
            }
        }.map { it.await() }

        PortfolioData(
            watchlist = watchlist,
            statuses = perAsset.mapNotNull { (id, status, _) -> status?.let { id to it } }.toMap(),
            events = perAsset.associate { (id, _, events) -> id to events },
            feed = feedText.await()?.let { Parse.feed(it).events }.orEmpty(),
            criteria = criteriaText.await()?.let { Criteria.parse(it) }.orEmpty(),
        )
    }

    /** Возвращает null, если файла нет (404) — это не ошибка. */
    private suspend fun fetch(url: String, force: Boolean): String? = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        if (force) builder.cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
        client.newCall(builder.build()).execute().use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> throw IOException("$url — HTTP ${response.code}")
                else -> response.body?.string()
            }
        }
    }

    private companion object {
        const val CACHE_BYTES = 8L * 1024 * 1024
    }
}
