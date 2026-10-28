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
) {
    companion object {
        const val FORMAT = "lingvo-island-backup"
        const val VERSION = 1
    }
}

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
        if (backup.format != BackupFile.FORMAT) throw VocabularyFormatException("Это не резервная копия LingvoIsland.")
        if (backup.formatVersion > BackupFile.VERSION) throw VocabularyFormatException("Резервная копия создана более новой версией приложения.")
        val ids = backup.entries.map { it.id }
        if (ids.size != ids.toSet().size) throw VocabularyFormatException("В резервной копии повторяются ID карточек.")
        val idSet = ids.toSet()
        backup.entries.forEach {
            if (Languages.byCode(it.language) == null) throw VocabularyFormatException("Неизвестный язык «${it.language}» в резервной копии.")
            if (it.term.isBlank() || it.translation.isBlank()) throw VocabularyFormatException("Карточка ${it.id} без выражения или перевода.")
        }
        backup.reviewStates.forEach {
            if (it.entryId !in idSet) throw VocabularyFormatException("Прогресс ссылается на отсутствующую карточку ${it.entryId}.")
            runCatching { CardState.valueOf(it.state) }.getOrElse { _ -> throw VocabularyFormatException("Неизвестное состояние карточки: ${it.state}.") }
        }
        return backup
    }
}
