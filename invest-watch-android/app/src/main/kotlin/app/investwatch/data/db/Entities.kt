package app.investwatch.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Локальная копия репозитория данных. Приложение полностью работает офлайн
 * на последней загруженной копии и показывает дату обновления.
 *
 * Вложенные списки (критерии, источники, метрики) лежат в колонках как JSON:
 * они всегда читаются целиком вместе со своей записью, отдельные таблицы
 * под них ничего бы не дали.
 */

@Entity(tableName = "markets")
data class MarketEntity(
    @PrimaryKey val id: String,
    val title: String,
    val flag: String,
    val currency: String?,
    val yieldFloorPct: Double?,
    val yieldFloorNote: String?,
    val position: Int,
)

@Entity(tableName = "kinds")
data class KindEntity(
    @PrimaryKey val id: String,
    val title: String,
    val position: Int,
)

@Entity(tableName = "assets", indices = [Index("marketId"), Index("kindId")])
data class AssetEntity(
    @PrimaryKey val id: String,
    val ticker: String,
    val name: String,
    val marketId: String,
    val kindId: String,
    val isin: String?,
    val type: String?,
    val exchange: String?,
    val currency: String?,
    val domain: String?,
    val logo: String?,
    val isCommodity: Boolean,
    val commodity: String?,
    val qualifiedOnly: Boolean,
    val inUniverse: Boolean?,
    val irUrl: String?,
    val disclosureUrl: String?,
    val position: Int,
)

@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey val assetId: String,
    val verdict: String,
    val updated: String?,
    val watchJson: String,
    val criteriaJson: String,
    val metricsJson: String,
    val metricsDate: String?,
)

/** Решения владельца из overrides.json — отдельно, чтобы не смешивать с оценкой агента. */
@Entity(tableName = "overrides")
data class OverrideEntity(
    @PrimaryKey val assetId: String,
    val criteriaJson: String,
)

@Entity(tableName = "events", indices = [Index("assetId"), Index("date")])
data class EventEntity(
    @PrimaryKey val id: String,
    val assetId: String,
    val date: String,
    val type: String,
    val importance: String,
    val title: String,
    val summary: String,
    val verdict: String?,
    val notify: Boolean,
    val metricsJson: String,
    val criteriaJson: String,
    val sourcesJson: String,
    val detectedAt: String?,
)

@Entity(tableName = "dividends")
data class DividendsEntity(
    @PrimaryKey val assetId: String,
    val currency: String?,
    val byYearJson: String,
    val paymentsJson: String,
    val origin: String,
)

@Entity(tableName = "candles", primaryKeys = ["assetId", "date"], indices = [Index("assetId")])
data class CandleEntity(
    val assetId: String,
    val date: String,
    val close: Double,
)

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey val assetId: String,
    val price: Double,
    val changePct: Double?,
    val currency: String?,
    val at: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val assetId: String,
    val position: Int,
)

/** ETag и sha по каждому файлу: лишние запросы к GitHub не тратим. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val path: String,
    val etag: String?,
    val sha: String?,
    val updatedAt: Long,
)

/** Когда последний раз ходили за ценами — чтобы не частить. */
@Entity(tableName = "price_sync")
data class PriceSyncEntity(
    @PrimaryKey val assetId: String,
    val historyAt: Long,
    val quoteAt: Long,
)
