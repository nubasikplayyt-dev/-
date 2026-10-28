package app.lingvo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VocabularyEntryEntity::class,
        ReviewStateEntity::class,
        ReviewLogEntity::class,
        LearningSessionEntity::class,
        VocabularyImportHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun reviews(): ReviewDao
    abstract fun logs(): LogDao
    abstract fun sessions(): SessionDao
    abstract fun imports(): ImportHistoryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lingvo.db")
                // Схема версионируется (app/schemas). Будущие изменения — только через миграции,
                // без fallbackToDestructiveMigration: прогресс пользователя нельзя терять.
                .build()
    }
}
