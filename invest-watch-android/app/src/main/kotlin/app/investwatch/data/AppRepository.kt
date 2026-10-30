package app.investwatch.data

import app.investwatch.core.AssetCard
import app.investwatch.core.ChartPeriod
import app.investwatch.core.Criteria
import app.investwatch.core.CriterionHelp
import app.investwatch.core.Dates
import app.investwatch.core.Dividends
import app.investwatch.core.Favorites
import app.investwatch.core.MonitorEvent
import app.investwatch.core.Notify
import app.investwatch.core.Overrides
import app.investwatch.core.PriceCandle
import app.investwatch.core.Prices
import app.investwatch.core.Quote
import app.investwatch.core.Status
import app.investwatch.core.Symbols
import app.investwatch.core.Watchlist
import app.investwatch.data.db.AppDatabase
import app.investwatch.data.db.PriceSyncEntity
import app.investwatch.data.db.QuoteEntity
import app.investwatch.data.net.PriceSourceUnavailable
import app.investwatch.data.net.PriceSources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Всё, что видно на экранах, собирается здесь из базы. */
@Singleton
class AppRepository @Inject constructor(
    private val database: AppDatabase,
    private val sync: RepoSync,
    private val settings: SettingsStore,
    private val priceSources: PriceSources,
) {

    val watchlist: Flow<Watchlist> = combine(
        database.watchlistDao().markets(),
        database.watchlistDao().kinds(),
        database.watchlistDao().assets(),
    ) { markets, kinds, assets -> watchlistOf(markets, kinds, assets) }

    val favorites: Flow<List<String>> =
        database.favoriteDao().all().map { items -> items.map { it.assetId } }

    val statuses: Flow<Map<String, Status>> = combine(
        database.statusDao().all(),
        database.statusDao().overrides(),
    ) { statuses, overrides ->
        val byAsset = overrides.associate { it.assetId to it.toModel() }
        statuses.associate { entity ->
            val status = entity.toModel()
            entity.assetId to Overrides.apply(status, byAsset[entity.assetId].orEmpty())
        }
    }

    val events: Flow<List<MonitorEvent>> =
        database.eventDao().all().map { items -> items.map { it.toModel() } }

    val quotes: Flow<Map<String, Quote>> =
        database.priceDao().quotes().map { items -> items.associate { it.assetId to it.toModel() } }

    val dividends: Flow<Map<String, Dividends>> =
        database.dividendsDao().all().map { items -> items.associate { it.assetId to it.toModel() } }

    val lastSync: Flow<Long> = settings.lastSync

    fun eventsOf(assetId: String): Flow<List<MonitorEvent>> =
        database.eventDao().byAsset(assetId).map { items -> items.map { it.toModel() } }

    fun candles(assetId: String): Flow<List<PriceCandle>> =
        database.priceDao().candles(assetId).map { items -> items.map { it.toModel() } }

    suspend fun criteriaHelp(): Map<String, CriterionHelp> = Criteria.parse(sync.criteriaMarkdown())

    /** Карточки бумаг — то, что рисуется и в избранном, и на вкладках рынков. */
    val cards: Flow<List<AssetCard>> = combine(
        watchlist, statuses, quotes, dividends, favorites,
    ) { watchlist, statuses, quotes, dividends, favorites ->
        val today = Dates.today()
        watchlist.assets.map { asset ->
            val quote = quotes[asset.id]
            AssetCard(
                asset = asset,
                market = watchlist.market(asset.marketId),
                kind = watchlist.kind(asset.kindId),
                status = statuses[asset.id],
                quote = quote,
                yieldPct = dividends[asset.id]
                    ?.let { Prices.trailingYieldPct(it, quote?.price, today) },
                isFavorite = asset.id in favorites,
            )
        }
    }

    /** Карточки с отметкой непрочитанного — для списков, где видна точка. */
    val cardsWithUnread: Flow<List<AssetCard>> = combine(
        cards, events, settings.seenEvents,
    ) { cards, events, seen ->
        val byAsset = events.groupBy { it.assetId }
        cards.map { card ->
            card.copy(hasUnread = Notify.hasUnread(byAsset[card.asset.id].orEmpty(), seen))
        }
    }

    // ───────────────────────────── синхронизация ─────────────────────────────

    suspend fun sync(force: Boolean = false) {
        sync.sync(force)
        sync.pushFavorites()
    }

    suspend fun pushFavorites(): Boolean = sync.pushFavorites()

    // ─────────────────────────────── избранное ───────────────────────────────

    suspend fun toggleFavorite(assetId: String) {
        val current = database.favoriteDao().allNow().map { it.assetId }
        val isFavorite = assetId in current
        val pending = settings.pending.first().let {
            if (isFavorite) it.remove(assetId) else it.add(assetId)
        }
        val order = if (isFavorite) current - assetId else current + assetId

        settings.setPending(pending)
        settings.setFavoritesDirty(true)
        settings.setFavoritesOrder(order)
        database.favoriteDao().replace(
            order.mapIndexed { index, id -> app.investwatch.data.db.FavoriteEntity(id, index) },
        )
    }

    /** Перетаскивание карточек в избранном. */
    suspend fun reorderFavorites(order: List<String>) {
        settings.setFavoritesOrder(order)
        database.favoriteDao().replace(
            order.mapIndexed { index, id -> app.investwatch.data.db.FavoriteEntity(id, index) },
        )
        // Порядок тоже уезжает в репозиторий: файл пишется целиком.
        settings.setFavoritesDirty(true)
    }

    suspend fun markAssetSeen(assetId: String) {
        val ids = database.eventDao().byAsset(assetId).first()
            .filter { it.notify }.map { it.id }.toSet()
        settings.rememberSeen(ids)
    }

    // ──────────────────────────────── цены ───────────────────────────────────

    /**
     * Обновляет цены бумаги. История — не чаще раза в 6 часов, последняя
     * цена — не чаще раза в 15 минут, как и договаривались.
     */
    suspend fun refreshPrices(assetId: String, force: Boolean = false): String? {
        val asset = database.watchlistDao().assetsNow().firstOrNull { it.id == assetId }?.toModel()
            ?: return null
        val source = priceSources.forAsset(asset) ?: return null

        val now = System.currentTimeMillis()
        val state = database.priceDao().priceSync(assetId)
        val needHistory = force || state == null || now - state.historyAt > HISTORY_INTERVAL
        val needQuote = force || state == null || now - state.quoteAt > QUOTE_INTERVAL
        if (!needHistory && !needQuote) return null

        return try {
            if (needHistory) {
                val from = Dates.minusDays(Dates.today(), HISTORY_DAYS) ?: return null
                val candles = source.history(asset, from)
                if (candles.isNotEmpty()) {
                    database.priceDao().upsertCandles(candles.map { it.toEntity(assetId) })
                }
                refreshDividendsFallback(assetId, asset, source, from)
            }
            if (needQuote) {
                source.quote(asset)?.let { quote ->
                    database.priceDao().upsertQuote(
                        QuoteEntity(assetId, quote.price, quote.changePct, quote.currency, now),
                    )
                }
            }
            database.priceDao().upsertPriceSync(
                PriceSyncEntity(
                    assetId = assetId,
                    historyAt = if (needHistory) now else state?.historyAt ?: now,
                    quoteAt = if (needQuote) now else state?.quoteAt ?: now,
                ),
            )
            null
        } catch (error: PriceSourceUnavailable) {
            error.message
        } catch (error: Exception) {
            "Цены недоступны: ${error.message}"
        }
    }

    /** Если в репозитории дивидендов нет, берём их у источника цен с пометкой. */
    private suspend fun refreshDividendsFallback(
        assetId: String,
        asset: app.investwatch.core.Asset,
        source: app.investwatch.data.net.PriceSource,
        fromDate: String,
    ) {
        val existing = database.dividendsDao().byAsset(assetId).first()?.toModel()
        if (existing != null && !existing.isEmpty) return

        val payments = runCatching { source.dividends(asset, fromDate) }.getOrDefault(emptyList())
        if (payments.isEmpty()) return

        val byYear = payments.groupBy { app.investwatch.core.Format.year(it.recordDate) }
            .mapNotNull { (year, items) ->
                year?.let { app.investwatch.core.DividendYear(it, items.sumOf { p -> p.amount }) }
            }
            .sortedBy { it.year }

        database.dividendsDao().upsert(
            Dividends(
                assetId = assetId,
                currency = asset.currency,
                byYear = byYear,
                payments = payments,
                origin = if (Symbols.needsPriceApiKey(asset)) {
                    app.investwatch.core.DividendOrigin.PRICE_SOURCE
                } else {
                    app.investwatch.core.DividendOrigin.MOEX
                },
            ).toEntity(),
        )
    }

    fun chartSlice(candles: List<PriceCandle>, period: ChartPeriod): List<PriceCandle> =
        Prices.slice(candles, period, Dates.today())

    private companion object {
        const val HISTORY_INTERVAL = 6 * 60 * 60 * 1000L
        const val QUOTE_INTERVAL = 15 * 60 * 1000L
        const val HISTORY_DAYS = 365 * 10
    }
}
