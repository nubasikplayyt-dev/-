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
    /** Транскрипция / чтение (с версии БД 3). */
    val transcription: String? = null,
    /** Допустимые варианты ответа для письменного режима, через перевод строки (с версии БД 3). */
    val alternatives: String? = null,
) {
    fun toDomain() = VocabEntry(
        id = id, language = language, term = term, translation = translation,
        exampleTarget = exampleTarget, exampleRu = exampleRu, partOfSpeech = partOfSpeech,
        article = article, plural = plural, level = level, tags = splitList(tags), notes = notes,
        source = source, grammarNotes = grammarNotes, collocations = splitList(collocations),
        verbFormsJson = verbForms, regionalUsage = regionalUsage, extraJson = extra,
        transcription = transcription.orEmpty(), alternatives = splitList(alternatives.orEmpty()),
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
            transcription = e.transcription, alternatives = joinList(e.alternatives),
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
    /** Тип задания: BUTTONS или TYPING (с версии БД 3; null — старые записи, это кнопки). */
    @ColumnInfo(name = "task_type") val taskType: String? = null,
    /** Что ввёл пользователь в письменном режиме. */
    @ColumnInfo(name = "typed_answer") val typedAnswer: String? = null,
    /** Результат автоматической проверки ([app.lingvo.core.AutoResult]). */
    @ColumnInfo(name = "auto_result") val autoResult: String? = null,
    /** Ручное подтверждение: 1 — «Ответ правильный», 0 — «Ответ неправильный». */
    val confirmed: Int? = null,
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

/** Языковой курс. `id` совпадает со значением `language` у карточек (для старых данных — "es"/"de"). */
@Entity(tableName = "course")
data class CourseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "language_code") val languageCode: String,
    val name: String,
    val flag: String,
    @ColumnInfo(name = "translation_code") val translationCode: String,
    @ColumnInfo(name = "translation_name") val translationName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
) {
    fun toDomain() = app.lingvo.core.Course(id, languageCode, name, flag, translationCode, translationName, createdAt, sortOrder)

    companion object {
        fun from(c: app.lingvo.core.Course) = CourseEntity(
            c.id, c.languageCode, c.name, c.flag, c.translationCode, c.translationName, c.createdAt, c.sortOrder,
        )
    }
}

/** Открытое: достижение, область карты, существо или иконка. Не удаляется при обновлениях. */
@Entity(tableName = "unlock", primaryKeys = ["kind", "item_id"])
data class UnlockEntity(
    val kind: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "unlocked_at") val unlockedAt: Long,
    /** Уровень существа (для остальных — 1). */
    val level: Int,
    /** Пользователь уже видел анимацию получения. */
    val seen: Boolean,
)

/** Прогресс календарного дня для серий. `epoch_day` — локальная дата в момент ответа. */
@Entity(tableName = "daily_progress")
data class DailyProgressEntity(
    @PrimaryKey @ColumnInfo(name = "epoch_day") val epochDay: Long,
    val answers: Int,
    val remembered: Int,
    val goal: Int,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
) {
    fun toDomain() = app.lingvo.core.DayRecord(epochDay, answers, remembered, goal, completedAt)

    companion object {
        fun from(d: app.lingvo.core.DayRecord) = DailyProgressEntity(d.epochDay, d.answers, d.remembered, d.goal, d.completedAt)
    }
}
