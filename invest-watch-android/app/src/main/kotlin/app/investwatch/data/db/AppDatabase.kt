package app.investwatch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MarketEntity::class,
        KindEntity::class,
        AssetEntity::class,
        StatusEntity::class,
        OverrideEntity::class,
        EventEntity::class,
        DividendsEntity::class,
        CandleEntity::class,
        QuoteEntity::class,
        FavoriteEntity::class,
        SyncStateEntity::class,
        PriceSyncEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchlistDao(): WatchlistDao
    abstract fun statusDao(): StatusDao
    abstract fun eventDao(): EventDao
    abstract fun dividendsDao(): DividendsDao
    abstract fun priceDao(): PriceDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        /**
         * Схема версионируется. Разрушающего отката нет: база — это копия
         * репозитория и локальные настройки избранного, терять их незачем.
         */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "invest-watch.db").build()
    }
}
