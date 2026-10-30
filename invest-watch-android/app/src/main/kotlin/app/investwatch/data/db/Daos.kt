package app.investwatch.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM markets ORDER BY position")
    fun markets(): Flow<List<MarketEntity>>

    @Query("SELECT * FROM kinds ORDER BY position")
    fun kinds(): Flow<List<KindEntity>>

    @Query("SELECT * FROM assets ORDER BY position")
    fun assets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :assetId")
    fun asset(assetId: String): Flow<AssetEntity?>

    @Query("SELECT * FROM assets ORDER BY position")
    suspend fun assetsNow(): List<AssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMarkets(items: List<MarketEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKinds(items: List<KindEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssets(items: List<AssetEntity>)

    @Query("DELETE FROM markets WHERE id NOT IN (:keep)")
    suspend fun pruneMarkets(keep: List<String>)

    @Query("DELETE FROM kinds WHERE id NOT IN (:keep)")
    suspend fun pruneKinds(keep: List<String>)

    @Query("DELETE FROM assets WHERE id NOT IN (:keep)")
    suspend fun pruneAssets(keep: List<String>)

    /**
     * Watchlist заменяется целиком в одной транзакции: иначе на середине
     * обновления экран показал бы половину старых, половину новых бумаг.
     */
    @Transaction
    suspend fun replaceWatchlist(
        markets: List<MarketEntity>,
        kinds: List<KindEntity>,
        assets: List<AssetEntity>,
    ) {
        upsertMarkets(markets)
        upsertKinds(kinds)
        upsertAssets(assets)
        pruneMarkets(markets.map { it.id })
        pruneKinds(kinds.map { it.id })
        pruneAssets(assets.map { it.id })
    }
}

@Dao
interface StatusDao {

    @Query("SELECT * FROM statuses")
    fun all(): Flow<List<StatusEntity>>

    @Query("SELECT * FROM statuses WHERE assetId = :assetId")
    fun byAsset(assetId: String): Flow<StatusEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: StatusEntity)

    @Query("SELECT * FROM overrides")
    fun overrides(): Flow<List<OverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverrides(items: List<OverrideEntity>)

    @Query("DELETE FROM overrides")
    suspend fun clearOverrides()

    @Transaction
    suspend fun replaceOverrides(items: List<OverrideEntity>) {
        clearOverrides()
        upsertOverrides(items)
    }
}

@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY date DESC, id")
    fun all(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE assetId = :assetId ORDER BY date DESC, id")
    fun byAsset(assetId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY date DESC, id")
    suspend fun allNow(): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<EventEntity>)

    @Query("DELETE FROM events WHERE assetId = :assetId")
    suspend fun deleteOf(assetId: String)

    /** Лента бумаги заменяется целиком: файл в репозитории — источник правды. */
    @Transaction
    suspend fun replaceOf(assetId: String, items: List<EventEntity>) {
        deleteOf(assetId)
        upsert(items)
    }
}

@Dao
interface DividendsDao {

    @Query("SELECT * FROM dividends WHERE assetId = :assetId")
    fun byAsset(assetId: String): Flow<DividendsEntity?>

    @Query("SELECT * FROM dividends")
    fun all(): Flow<List<DividendsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DividendsEntity)
}

@Dao
interface PriceDao {

    @Query("SELECT * FROM candles WHERE assetId = :assetId ORDER BY date")
    fun candles(assetId: String): Flow<List<CandleEntity>>

    @Query("SELECT * FROM candles WHERE assetId = :assetId ORDER BY date")
    suspend fun candlesNow(assetId: String): List<CandleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandles(items: List<CandleEntity>)

    @Query("SELECT * FROM quotes")
    fun quotes(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE assetId = :assetId")
    fun quote(assetId: String): Flow<QuoteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuote(item: QuoteEntity)

    @Query("SELECT * FROM price_sync WHERE assetId = :assetId")
    suspend fun priceSync(assetId: String): PriceSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPriceSync(item: PriceSyncEntity)
}

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY position")
    fun all(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY position")
    suspend fun allNow(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<FavoriteEntity>)

    @Query("DELETE FROM favorites")
    suspend fun clear()

    @Transaction
    suspend fun replace(items: List<FavoriteEntity>) {
        clear()
        upsert(items)
    }
}

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE path = :path")
    suspend fun get(path: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    suspend fun clear()
}
