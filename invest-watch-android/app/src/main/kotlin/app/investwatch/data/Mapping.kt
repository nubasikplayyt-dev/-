package app.investwatch.data

import app.investwatch.core.Asset
import app.investwatch.core.Criterion
import app.investwatch.core.DividendOrigin
import app.investwatch.core.Dividends
import app.investwatch.core.Importance
import app.investwatch.core.Kind
import app.investwatch.core.Market
import app.investwatch.core.MonitorEvent
import app.investwatch.core.PriceCandle
import app.investwatch.core.Quote
import app.investwatch.core.Status
import app.investwatch.core.Verdict
import app.investwatch.core.Watchlist
import app.investwatch.data.db.AssetEntity
import app.investwatch.data.db.CandleEntity
import app.investwatch.data.db.Converters
import app.investwatch.data.db.DividendsEntity
import app.investwatch.data.db.EventEntity
import app.investwatch.data.db.KindEntity
import app.investwatch.data.db.MarketEntity
import app.investwatch.data.db.OverrideEntity
import app.investwatch.data.db.QuoteEntity
import app.investwatch.data.db.StatusEntity

/** Перекладывание между моделями ядра и записями базы. */

fun Market.toEntity(position: Int) = MarketEntity(
    id = id, title = title, flag = flag, currency = currency,
    yieldFloorPct = yieldFloorPct, yieldFloorNote = yieldFloorNote, position = position,
)

fun MarketEntity.toModel() = Market(
    id = id, title = title, flag = flag, currency = currency,
    yieldFloorPct = yieldFloorPct, yieldFloorNote = yieldFloorNote,
)

fun Kind.toEntity(position: Int) = KindEntity(id = id, title = title, position = position)

fun KindEntity.toModel() = Kind(id = id, title = title)

fun Asset.toEntity(position: Int) = AssetEntity(
    id = id, ticker = ticker, name = name, marketId = marketId, kindId = kindId,
    isin = isin, type = type, exchange = exchange, currency = currency, domain = domain,
    logo = logo, isCommodity = isCommodity, commodity = commodity,
    qualifiedOnly = qualifiedOnly, inUniverse = inUniverse, irUrl = irUrl,
    disclosureUrl = disclosureUrl, position = position,
)

fun AssetEntity.toModel() = Asset(
    id = id, ticker = ticker, name = name, marketId = marketId, kindId = kindId,
    isin = isin, type = type, exchange = exchange, currency = currency, domain = domain,
    logo = logo, isCommodity = isCommodity, commodity = commodity,
    qualifiedOnly = qualifiedOnly, inUniverse = inUniverse, irUrl = irUrl,
    disclosureUrl = disclosureUrl,
)

fun watchlistOf(
    markets: List<MarketEntity>,
    kinds: List<KindEntity>,
    assets: List<AssetEntity>,
) = Watchlist(
    markets = markets.map { it.toModel() },
    kinds = kinds.map { it.toModel() },
    assets = assets.map { it.toModel() },
)

fun Status.toEntity(assetId: String) = StatusEntity(
    assetId = assetId,
    verdict = verdict.raw,
    updated = updated,
    watchJson = Converters.stringsToJson(watch),
    criteriaJson = Converters.criteriaToJson(criteria),
    metricsJson = Converters.statusMetricsToJson(metrics),
    metricsDate = metricsDate,
)

fun StatusEntity.toModel() = Status(
    assetId = assetId,
    verdict = Verdict.from(verdict),
    updated = updated,
    watch = Converters.stringsFromJson(watchJson),
    criteria = Converters.criteriaFromJson(criteriaJson),
    metrics = Converters.statusMetricsFromJson(metricsJson),
    metricsDate = metricsDate,
)

fun overrideEntity(assetId: String, criteria: List<Criterion>) =
    OverrideEntity(assetId = assetId, criteriaJson = Converters.criteriaToJson(criteria))

fun OverrideEntity.toModel(): List<Criterion> = Converters.criteriaFromJson(criteriaJson)

fun MonitorEvent.toEntity() = EventEntity(
    id = id, assetId = assetId, date = date, type = type, importance = importance.raw,
    title = title, summary = summary, verdict = verdict?.raw, notify = notify,
    metricsJson = Converters.eventMetricsToJson(metrics),
    criteriaJson = Converters.criteriaToJson(criteria),
    sourcesJson = Converters.sourcesToJson(sources),
    detectedAt = detectedAt,
)

fun EventEntity.toModel() = MonitorEvent(
    id = id, assetId = assetId, date = date, type = type,
    importance = Importance.from(importance), title = title, summary = summary,
    verdict = Verdict.fromOrNull(verdict), notify = notify,
    metrics = Converters.eventMetricsFromJson(metricsJson),
    criteria = Converters.criteriaFromJson(criteriaJson),
    sources = Converters.sourcesFromJson(sourcesJson),
    detectedAt = detectedAt,
)

fun Dividends.toEntity() = DividendsEntity(
    assetId = assetId,
    currency = currency,
    byYearJson = Converters.yearsToJson(byYear),
    paymentsJson = Converters.paymentsToJson(payments),
    origin = origin.name,
)

fun DividendsEntity.toModel() = Dividends(
    assetId = assetId,
    currency = currency,
    byYear = Converters.yearsFromJson(byYearJson),
    payments = Converters.paymentsFromJson(paymentsJson),
    origin = runCatching { DividendOrigin.valueOf(origin) }.getOrDefault(DividendOrigin.REPO),
)

fun CandleEntity.toModel() = PriceCandle(date = date, close = close)

fun PriceCandle.toEntity(assetId: String) = CandleEntity(assetId, date, close)

fun QuoteEntity.toModel() = Quote(assetId, price, changePct, currency, at)
