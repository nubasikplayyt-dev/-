package app.lingvo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.lingvo.core.CardState
import app.lingvo.core.Normalizer
import app.lingvo.core.SrsState
import app.lingvo.core.VocabEntry

/** Лексические данные карточки. Прогресс хранится отдельно в [ReviewStateEntity]. */
@Entity(
    tableName = "vocabulary_entry",
    indices = [Index("language"), Index(value = ["language", "term_key"])],
)
data class VocabularyEntryEntity(
    @PrimaryKey val id: String,
    val language: String,
    val term: String,
    @ColumnInfo(name = "term_key") val termKey: String,
    val translation: String,
    @ColumnInfo(name = "example_target") val exampleTarget: String,
    @ColumnInfo(name = "example_ru") val exampleRu: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String,
    val article: String,
    val plural: String,
    val level: String,
    /** Теги, разделённые переводом строки. */
    val tags: String,
    val notes: String,
    val source: String,
    @ColumnInfo(name = "grammar_notes") val grammarNotes: String,
    val collocations: String,
    @ColumnInfo(name = "verb_forms") val verbForms: String,
    @ColumnInfo(name = "regional_usage") val regionalUsage: String,
    val extra: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Пользователь менял карточку в приложении — импорт не перезапишет её молча. */
    @ColumnInfo(name = "locally_edited") val locallyEdited: Boolean,
) {
    fun toDomain() = VocabEntry(
        id = id, language = language, term = term, translation = translation,
        exampleTarget = exampleTarget, exampleRu = exampleRu, partOfSpeech = partOfSpeech,
        article = article, plural = plural, level = level, tags = splitList(tags), notes = notes,
        source = source, grammarNotes = grammarNotes, collocations = splitList(collocations),
        verbFormsJson = verbForms, regionalUsage = regionalUsage, extraJson = extra,
        createdAt = createdAt, updatedAt = updatedAt,
    )

    companion object {
        fun from(e: VocabEntry, locallyEdited: Boolean) = VocabularyEntryEntity(
            id = e.id, language = e.language, term = e.term,
            termKey = Normalizer.entryKey(e.term, e.article),
            translation = e.translation, exampleTarget = e.exampleTarget, exampleRu = e.exampleRu,
            partOfSpeech = e.partOfSpeech, article = e.article, plural = e.plural, level = e.level,
            tags = joinList(e.tags), notes = e.notes, source = e.source, grammarNotes = e.grammarNotes,
            collocations = joinList(e.collocations), verbForms = e.verbFormsJson, regionalUsage = e.regionalUsage,
            extra = e.extraJson, createdAt = e.createdAt, updatedAt = e.updatedAt, locallyEdited = locallyEdited,
        )

        fun splitList(s: String): List<String> = s.split('\n').filter { it.isNotBlank() }
        fun joinList(l: List<String>): String = l.joinToString("\n") { it.replace('\n', ' ') }
    }
}

@Entity(
    tableName = "review_state",
    foreignKeys = [ForeignKey(VocabularyEntryEntity::class, ["id"], ["entry_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["language", "due"])],
)
data class ReviewStateEntity(
    @PrimaryKey @ColumnInfo(name = "entry_id") val entryId: String,
    val language: String,
    val state: String,
    val stability: Double,
    val difficulty: Double,
    val due: Long,
    @ColumnInfo(name = "last_review") val lastReview: Long?,
    val reps: Int,
    val lapses: Int,
    val step: Int,
    /** Когда пользователь впервые познакомился с карточкой; null — карточка новая. */
    @ColumnInfo(name = "introduced_at") val introducedAt: Long?,
    val suspended: Boolean,
) {
    fun toSrs() = SrsState(
        state = runCatching { CardState.valueOf(state) }.getOrDefault(CardState.NEW),
        stability = stability, difficulty = difficulty, due = due, lastReview = lastReview,
        reps = reps, lapses = lapses, step = step,
    )

    fun withSrs(s: SrsState) = copy(
        state = s.state.name, stability = s.stability, difficulty = s.difficulty, due = s.due,
        lastReview = s.lastReview, reps = s.reps, lapses = s.lapses, step = s.step,
    )

    companion object {
        fun fresh(entryId: String, language: String) = ReviewStateEntity(
            entryId = entryId, language = language, state = CardState.NEW.name, stability = 0.0, difficulty = 0.0,
            due = 0L, lastReview = null, reps = 0, lapses = 0, step = 0, introducedAt = null, suspended = false,
        )
    }
}

/** История оценок. Не удаляется вместе с карточкой, чтобы статистика оставалась честной. */
@Entity(tableName = "review_log", indices = [Index("entry_id"), Index(value = ["language", "reviewed_at"])])
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: String,
    val language: String,
    val rating: Int,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long,
    @ColumnInfo(name = "state_before") val stateBefore: String,
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
)

object SessionStatus {
    const val ACTIVE = "ACTIVE"
    const val FINISHED = "FINISHED"
    const val ENDED = "ENDED"
}

@Entity(tableName = "learning_session", indices = [Index(value = ["language", "status"])])
data class LearningSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val language: String,
    /** Очередь оставшихся карточек (ID через перевод строки). */
    val queue: String,
    val done: Int,
    val total: Int,
    val correct: Int,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    val status: String,
)

@Entity(tableName = "import_history")
data class VocabularyImportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val language: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "source_name") val sourceName: String,
    val added: Int,
    val updated: Int,
    val unchanged: Int,
    val skipped: Int,
    val errors: Int,
)

/** Карточка вместе с прогрессом. */
data class EntryWithState(
    @androidx.room.Embedded val entry: VocabularyEntryEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "entry_id")
    val state: ReviewStateEntity?,
)
