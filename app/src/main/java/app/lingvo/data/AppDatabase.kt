package app.lingvo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VocabularyEntryEntity::class,
        ReviewStateEntity::class,
        ReviewLogEntity::class,
        LearningSessionEntity::class,
        VocabularyImportHistoryEntity::class,
        CourseEntity::class,
        UnlockEntity::class,
        DailyProgressEntity::class,
        FriendEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun reviews(): ReviewDao
    abstract fun logs(): LogDao
    abstract fun sessions(): SessionDao
    abstract fun imports(): ImportHistoryDao
    abstract fun courses(): CourseDao
    abstract fun unlocks(): UnlockDao
    abstract fun daily(): DailyDao
    abstract fun friends(): FriendDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lingvo.db")
                // Только явные миграции, без destructive fallback: прогресс пользователя нельзя терять.
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /**
         * 1 → 2 (Lingvo Island → Lingvo World):
         *  - курсы: для языков, у которых уже есть карточки (es, de), создаются курсы с теми же ID —
         *    карточки, прогресс и история остаются на месте;
         *  - таблицы разблокировок и дневного прогресса;
         *  - дневной прогресс восстанавливается из истории ответов (локальная дата устройства, цель 10).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `course` (`id` TEXT NOT NULL, `language_code` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `flag` TEXT NOT NULL, `translation_code` TEXT NOT NULL, " +
                        "`translation_name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `unlock` (`kind` TEXT NOT NULL, `item_id` TEXT NOT NULL, " +
                        "`unlocked_at` INTEGER NOT NULL, `level` INTEGER NOT NULL, `seen` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`kind`, `item_id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_progress` (`epoch_day` INTEGER NOT NULL, `answers` INTEGER NOT NULL, " +
                        "`remembered` INTEGER NOT NULL, `goal` INTEGER NOT NULL, `completed_at` INTEGER, PRIMARY KEY(`epoch_day`))"
                )
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT OR IGNORE INTO course (id, language_code, name, flag, translation_code, translation_name, created_at, sort_order) " +
                        "SELECT DISTINCT language, language, " +
                        "CASE language WHEN 'es' THEN 'Испанский' WHEN 'de' THEN 'Немецкий' ELSE language END, " +
                        "CASE language WHEN 'es' THEN '🇪🇸' WHEN 'de' THEN '🇩🇪' ELSE '🌐' END, " +
                        "'ru', 'Русский', $now, CASE language WHEN 'es' THEN 0 WHEN 'de' THEN 1 ELSE 2 END " +
                        "FROM vocabulary_entry"
                )
                // Локальная дата устройства через модификатор 'localtime' SQLite.
                db.execSQL(
                    "INSERT OR IGNORE INTO daily_progress (epoch_day, answers, remembered, goal, completed_at) " +
                        "SELECT d, COUNT(*), SUM(CASE WHEN rating > 1 THEN 1 ELSE 0 END), 10, " +
                        "CASE WHEN COUNT(*) >= 10 THEN MAX(reviewed_at) ELSE NULL END FROM (" +
                        "SELECT CAST(julianday(date(reviewed_at / 1000, 'unixepoch', 'localtime')) - 2440587.5 AS INTEGER) AS d, " +
                        "rating, reviewed_at FROM review_log) GROUP BY d"
                )
            }
        }
    
        /**
         * 2 → 3: письменный режим и транскрипция. Только добавление nullable-столбцов —
         * существующие карточки, прогресс и история не меняются.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vocabulary_entry` ADD COLUMN `transcription` TEXT")
                db.execSQL("ALTER TABLE `vocabulary_entry` ADD COLUMN `alternatives` TEXT")
                db.execSQL("ALTER TABLE `review_log` ADD COLUMN `task_type` TEXT")
                db.execSQL("ALTER TABLE `review_log` ADD COLUMN `typed_answer` TEXT")
                db.execSQL("ALTER TABLE `review_log` ADD COLUMN `auto_result` TEXT")
                db.execSQL("ALTER TABLE `review_log` ADD COLUMN `confirmed` INTEGER")
            }
        }
    
        /**
         * 3 → 4: список друзей для обмена прогрессом. Только новая таблица —
         * слова, прогресс и история не меняются.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `friend` (`code` TEXT NOT NULL, `nick` TEXT NOT NULL, " +
                        "`added_at` INTEGER NOT NULL, `fetched_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                        "`today_words` INTEGER NOT NULL, `today_points` INTEGER NOT NULL, `week_words` INTEGER NOT NULL, " +
                        "`week_points` INTEGER NOT NULL, `total_words` INTEGER NOT NULL, `total_points` INTEGER NOT NULL, " +
                        "`streak` INTEGER NOT NULL, `week_key` INTEGER NOT NULL, `courses` TEXT NOT NULL, PRIMARY KEY(`code`))"
                )
            }
        }
    }
}
