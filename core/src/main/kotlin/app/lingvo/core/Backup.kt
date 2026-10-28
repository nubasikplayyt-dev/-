package app.lingvo.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Полная резервная копия: словари + прогресс + история + настройки.
 * В отличие от JSON-словарей содержит историю интервального повторения.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    @SerialName("format_version") val formatVersion: Int = VERSION,
    @SerialName("created_at") val createdAt: String,
    val entries: List<BackupEntry>,
    @SerialName("review_states") val reviewStates: List<BackupReviewState>,
    @SerialName("review_history") val reviewHistory: List<BackupHistory>,
    val settings: Map<String, String> = emptyMap(),
    /** С версии 2: языковые курсы. В копиях версии 1 курсы восстанавливаются из карточек (es, de). */
    val courses: List<BackupCourse> = emptyList(),
    /** С версии 2: достижения, области карты, существа, иконки. */
    val unlocks: List<BackupUnlock> = emptyList(),
    /** С версии 2: дневной прогресс для серий. */
    @SerialName("daily_progress") val dailyProgress: List<BackupDay> = emptyList(),
) {
    companion object {
        const val FORMAT = "lingvo-world-backup"
        /** Копии старой версии (Lingvo Island) тоже принимаются. */
        const val LEGACY_FORMAT = "lingvo-island-backup"
        const val VERSION = 2
    }
}

@Serializable
data class BackupCourse(
    val id: String,
    @SerialName("language_code") val languageCode: String,
    val name: String,
    val flag: String,
    @SerialName("translation_code") val translationCode: String = "ru",
    @SerialName("translation_name") val translationName: String = "Русский",
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class BackupUnlock(
    val kind: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("unlocked_at") val unlockedAt: Long,
    val level: Int = 1,
)

@Serializable
data class BackupDay(
    @SerialName("epoch_day") val epochDay: Long,
    val answers: Int,
    val remembered: Int,
    val goal: Int,
    @SerialName("completed_at") val completedAt: Long? = null,
)

@Serializable
data class BackupEntry(
    val id: String,
    val language: String,
    val term: String,
    val translation: String,
    @SerialName("example_target") val exampleTarget: String = "",
    @SerialName("example_ru") val exampleRu: String = "",
    @SerialName("part_of_speech") val partOfSpeech: String = "",
    val article: String = "",
    val plural: String = "",
    val level: String = "",
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val source: String = "",
    @SerialName("grammar_notes") val grammarNotes: String = "",
    val collocations: List<String> = emptyList(),
    @SerialName("verb_forms") val verbFormsJson: String = "",
    @SerialName("regional_usage") val regionalUsage: String = "",
    val extra: String = "",
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("locally_edited") val locallyEdited: Boolean = false,
)

@Serializable
data class BackupReviewState(
    @SerialName("entry_id") val entryId: String,
    val state: String,
    val stability: Double,
    val difficulty: Double,
    val due: Long,
    @SerialName("last_review") val lastReview: Long? = null,
    val reps: Int,
    val lapses: Int,
    val step: Int,
    @SerialName("introduced_at") val introducedAt: Long? = null,
    val suspended: Boolean = false,
)

@Serializable
data class BackupHistory(
    @SerialName("entry_id") val entryId: String,
    val language: String,
    val rating: Int,
    @SerialName("reviewed_at") val reviewedAt: Long,
    @SerialName("state_before") val stateBefore: String,
    @SerialName("elapsed_ms") val elapsedMs: Long = 0,
)

object BackupCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    /** @throws VocabularyFormatException с понятным текстом, если файл не является резервной копией. */
    fun decode(text: String): BackupFile {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text.trim().removePrefix("﻿"))
        } catch (e: SerializationException) {
            throw VocabularyFormatException("Файл не является резервной копией приложения или повреждён.")
        } catch (e: IllegalArgumentException) {
            throw VocabularyFormatException("Файл не является резервной копией приложения или повреждён.")
        }
        if (backup.format != BackupFile.FORMAT && backup.format != BackupFile.LEGACY_FORMAT) {
            throw VocabularyFormatException("Это не резервная копия Lingvo World.")
        }
        if (backup.formatVersion > BackupFile.VERSION) throw VocabularyFormatException("Резервная копия создана более новой версией приложения.")
        val ids = backup.entries.map { it.id }
        if (ids.size != ids.toSet().size) throw VocabularyFormatException("В резервной копии повторяются ID карточек.")
        val idSet = ids.toSet()
        val courseIds = coursesOf(backup).map { it.id }.toSet()
        backup.entries.forEach {
            if (it.language !in courseIds) throw VocabularyFormatException("Карточка ${it.id} относится к неизвестному курсу «${it.language}».")
            if (it.term.isBlank() || it.translation.isBlank()) throw VocabularyFormatException("Карточка ${it.id} без выражения или перевода.")
        }
        backup.reviewStates.forEach {
            if (it.entryId !in idSet) throw VocabularyFormatException("Прогресс ссылается на отсутствующую карточку ${it.entryId}.")
            runCatching { CardState.valueOf(it.state) }.getOrElse { _ -> throw VocabularyFormatException("Неизвестное состояние карточки: ${it.state}.") }
        }
        return backup
    }

    /** Курсы копии; для копий версии 1 — испанский и немецкий, если в них есть карточки этих языков. */
    fun coursesOf(backup: BackupFile): List<BackupCourse> = backup.courses.ifEmpty {
        backup.entries.map { it.language }.distinct().mapNotNull { Languages.legacyCourse(it) }.map {
            BackupCourse(it.id, it.languageCode, it.name, it.flag)
        }
    }
}
