package app.lingvo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class LanguageCounts(val total: Int, val newCount: Int, val dueCount: Int, val suspended: Int)

@Dao
interface EntryDao {
    @Transaction
    @Query("SELECT * FROM vocabulary_entry WHERE language = :language ORDER BY created_at DESC, id")
    fun observeWithState(language: String): Flow<List<EntryWithState>>

    @Transaction
    @Query("SELECT * FROM vocabulary_entry ORDER BY created_at DESC, id")
    fun observeAllWithState(): Flow<List<EntryWithState>>

    @Query("SELECT * FROM vocabulary_entry WHERE language = :language")
    suspend fun byLanguage(language: String): List<VocabularyEntryEntity>

    @Query("SELECT * FROM vocabulary_entry")
    suspend fun all(): List<VocabularyEntryEntity>

    @Query("SELECT id FROM vocabulary_entry WHERE language != :language")
    suspend fun idsOutside(language: String): List<String>

    @Query("SELECT id FROM vocabulary_entry")
    suspend fun allIds(): List<String>

    @Transaction
    @Query("SELECT * FROM vocabulary_entry WHERE id = :id")
    fun observe(id: String): Flow<EntryWithState?>

    @Query("SELECT * FROM vocabulary_entry WHERE id = :id")
    suspend fun get(id: String): VocabularyEntryEntity?

    @Query("SELECT * FROM vocabulary_entry WHERE language = :language AND term_key = :key")
    suspend fun byKey(language: String, key: String): List<VocabularyEntryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entries: List<VocabularyEntryEntity>)

    @Update
    suspend fun update(entries: List<VocabularyEntryEntity>)

    @Upsert
    suspend fun upsert(entries: List<VocabularyEntryEntity>)

    @Query("DELETE FROM vocabulary_entry WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM vocabulary_entry WHERE language = :language")
    suspend fun deleteByLanguage(language: String)

    @Transaction
    @Query("SELECT * FROM vocabulary_entry")
    suspend fun allWithState(): List<EntryWithState>

    @Query("DELETE FROM vocabulary_entry")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM vocabulary_entry")
    fun observeTotal(): Flow<Int>
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_state WHERE entry_id = :id")
    suspend fun get(id: String): ReviewStateEntity?

    @Query("SELECT * FROM review_state WHERE entry_id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<ReviewStateEntity>

    @Query("SELECT * FROM review_state")
    suspend fun all(): List<ReviewStateEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(states: List<ReviewStateEntity>)

    @Upsert
    suspend fun upsert(state: ReviewStateEntity)

    @Upsert
    suspend fun upsertAll(states: List<ReviewStateEntity>)

    @Query(
        """SELECT COUNT(*) AS total,
           COALESCE(SUM(CASE WHEN s.introduced_at IS NULL AND s.suspended = 0 THEN 1 ELSE 0 END), 0) AS newCount,
           COALESCE(SUM(CASE WHEN s.introduced_at IS NOT NULL AND s.suspended = 0 AND s.due <= :now THEN 1 ELSE 0 END), 0) AS dueCount,
           COALESCE(SUM(CASE WHEN s.suspended = 1 THEN 1 ELSE 0 END), 0) AS suspended
           FROM vocabulary_entry e JOIN review_state s ON s.entry_id = e.id WHERE e.language = :language"""
    )
    suspend fun counts(language: String, now: Long): LanguageCounts

    @Query(
        """SELECT COUNT(*) FROM review_state
           WHERE language = :language AND introduced_at IS NOT NULL AND suspended = 0 AND due <= :now"""
    )
    suspend fun dueCount(language: String, now: Long): Int

    @Transaction
    @Query(
        """SELECT e.* FROM vocabulary_entry e JOIN review_state s ON s.entry_id = e.id
           WHERE e.language = :language AND s.introduced_at IS NOT NULL AND s.suspended = 0 AND s.due <= :now
           ORDER BY s.due ASC, s.lapses DESC LIMIT :limit"""
    )
    suspend fun dueEntries(language: String, now: Long, limit: Int): List<EntryWithState>

    @Transaction
    @Query(
        """SELECT e.* FROM vocabulary_entry e JOIN review_state s ON s.entry_id = e.id
           WHERE e.language = :language AND s.introduced_at IS NULL AND s.suspended = 0
           ORDER BY e.created_at ASC, e.id ASC LIMIT :limit"""
    )
    suspend fun newEntries(language: String, limit: Int): List<EntryWithState>

    @Query("SELECT COUNT(*) FROM review_state WHERE language = :language AND introduced_at >= :since")
    suspend fun introducedSince(language: String, since: Long): Int

    @Query("SELECT MIN(due) FROM review_state WHERE language = :language AND introduced_at IS NOT NULL AND suspended = 0 AND due > :now")
    suspend fun nextDue(language: String, now: Long): Long?

    @Query("DELETE FROM review_state")
    suspend fun deleteAll()
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: ReviewLogEntity)

    @Insert
    suspend fun insertAll(logs: List<ReviewLogEntity>)

    @Query("SELECT * FROM review_log WHERE reviewed_at >= :since ORDER BY reviewed_at")
    fun observeSince(since: Long): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM review_log ORDER BY reviewed_at")
    fun observeAll(): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM review_log ORDER BY reviewed_at")
    suspend fun all(): List<ReviewLogEntity>

    @Query("SELECT * FROM review_log WHERE entry_id = :entryId ORDER BY reviewed_at")
    fun observeForEntry(entryId: String): Flow<List<ReviewLogEntity>>

    @Query("SELECT MAX(reviewed_at) FROM review_log")
    suspend fun lastActivity(): Long?

    @Query("DELETE FROM review_log")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM learning_session WHERE language = :language AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    suspend fun active(language: String): LearningSessionEntity?

    @Query("SELECT * FROM learning_session WHERE status = 'ACTIVE' ORDER BY updated_at DESC")
    fun observeActive(): Flow<List<LearningSessionEntity>>

    @Query("SELECT * FROM learning_session WHERE status = 'ACTIVE' ORDER BY updated_at DESC")
    suspend fun allActive(): List<LearningSessionEntity>

    @Query("SELECT * FROM learning_session WHERE id = :id")
    suspend fun get(id: Long): LearningSessionEntity?

    @Query("SELECT * FROM learning_session WHERE id = :id")
    fun observe(id: Long): Flow<LearningSessionEntity?>

    @Query("SELECT * FROM learning_session WHERE done > 0 ORDER BY started_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LearningSessionEntity>>

    @Query("SELECT * FROM learning_session")
    suspend fun all(): List<LearningSessionEntity>

    @Query("SELECT * FROM learning_session")
    fun observeAll(): Flow<List<LearningSessionEntity>>

    @Query("DELETE FROM learning_session WHERE language = :language")
    suspend fun deleteByLanguage(language: String)

    @Insert
    suspend fun insert(session: LearningSessionEntity): Long

    @Update
    suspend fun update(session: LearningSessionEntity)

    @Query("DELETE FROM learning_session")
    suspend fun deleteAll()
}

@Dao
interface ImportHistoryDao {
    @Insert
    suspend fun insert(item: VocabularyImportHistoryEntity)

    @Query("SELECT * FROM import_history ORDER BY imported_at DESC LIMIT 20")
    fun observeRecent(): Flow<List<VocabularyImportHistoryEntity>>
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM course ORDER BY sort_order, created_at, id")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM course ORDER BY sort_order, created_at, id")
    suspend fun all(): List<CourseEntity>

    @Query("SELECT * FROM course WHERE id = :id")
    suspend fun get(id: String): CourseEntity?

    @Upsert
    suspend fun upsert(course: CourseEntity)

    @Upsert
    suspend fun upsertAll(courses: List<CourseEntity>)

    @Query("DELETE FROM course WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM course")
    suspend fun deleteAll()
}

@Dao
interface UnlockDao {
    @Query("SELECT * FROM unlock ORDER BY unlocked_at, kind, item_id")
    fun observeAll(): Flow<List<UnlockEntity>>

    @Query("SELECT * FROM unlock")
    suspend fun all(): List<UnlockEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<UnlockEntity>)

    @Upsert
    suspend fun upsertAll(items: List<UnlockEntity>)

    @Query("UPDATE unlock SET level = :level, seen = 0 WHERE kind = 'CREATURE' AND item_id = :id")
    suspend fun setCreatureLevel(id: String, level: Int)

    @Query("UPDATE unlock SET seen = 1 WHERE kind = :kind AND item_id = :id")
    suspend fun markSeen(kind: String, id: String)

    @Query("UPDATE unlock SET seen = 1")
    suspend fun markAllSeen()

    @Query("DELETE FROM unlock")
    suspend fun deleteAll()
}

@Dao
interface DailyDao {
    @Query("SELECT * FROM daily_progress ORDER BY epoch_day")
    fun observeAll(): Flow<List<DailyProgressEntity>>

    @Query("SELECT * FROM daily_progress ORDER BY epoch_day")
    suspend fun all(): List<DailyProgressEntity>

    @Query("SELECT * FROM daily_progress WHERE epoch_day = :day")
    suspend fun get(day: Long): DailyProgressEntity?

    @Upsert
    suspend fun upsert(day: DailyProgressEntity)

    @Upsert
    suspend fun upsertAll(days: List<DailyProgressEntity>)

    @Query("DELETE FROM daily_progress")
    suspend fun deleteAll()
}
