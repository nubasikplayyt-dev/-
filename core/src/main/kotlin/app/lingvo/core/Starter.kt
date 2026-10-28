package app.lingvo.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Тематический блок стартового словаря. */
data class StarterBlock(val number: Int, val title: String, val entryIds: List<String>) {
    val size: Int get() = entryIds.size
}

/**
 * Стартовые словари для новичков: файлы `starters/<язык>-<перевод>.json` в APK
 * (генерируются `tools/make_starters.py`). Слова идут блоками от простого к сложному:
 * сначала первая порция ([FIRST_BLOCKS], около 28 слов), дальше — по одному блоку (5–10 слов).
 */
object Starter {
    /** Первая порция при создании курса: приветствия, местоимения и вопросы, числа. */
    const val FIRST_BLOCKS = 3

    /** Языки и направления, для которых есть стартовый набор. */
    val available: Set<String> = setOf(
        "en-ru", "de-ru", "es-ru", "fr-ru", "it-ru", "pt-ru", "nl-ru", "pl-ru", "tr-ru", "uk-ru",
        "zh-ru", "ja-ru", "ko-ru", "ar-ru", "ru-en",
    )

    fun key(languageCode: String, translationCode: String) = "${languageCode.lowercase()}-${translationCode.lowercase()}"
    fun assetPath(languageCode: String, translationCode: String) = "starters/${key(languageCode, translationCode)}.json"
    fun has(languageCode: String, translationCode: String) = key(languageCode, translationCode) in available

    /** ID стартовой карточки в конкретном курсе (курсов одного языка может быть несколько). */
    fun entryId(courseId: String, sourceId: String): String =
        "start-$courseId-" + sourceId.substringAfterLast('-')

    private fun entriesOf(text: String): List<JsonObject> {
        val root = VocabularyJson.json.parseToJsonElement(text.trim().removePrefix("﻿")) as? JsonObject
            ?: throw VocabularyFormatException("Стартовый словарь повреждён.")
        return (root["entries"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }

    fun blocks(text: String, courseId: String): List<StarterBlock> =
        entriesOf(text).groupBy { it["block"]?.jsonPrimitive?.intOrNull ?: 1 }.toSortedMap().map { (n, list) ->
            StarterBlock(
                number = n,
                title = (list.first()["block_title"] as? JsonPrimitive)?.content.orEmpty(),
                entryIds = list.mapNotNull { (it["id"] as? JsonPrimitive)?.content?.let { id -> entryId(courseId, id) } },
            )
        }

    /**
     * Следующие блоки: те, из которых в курсе ещё нет ни одной карточки.
     * Удалённые пользователем слова не возвращаются, если из блока осталось хоть что-то.
     */
    fun pendingBlocks(blocks: List<StarterBlock>, existingIds: Set<String>): List<StarterBlock> =
        blocks.filter { b -> b.entryIds.none { it in existingIds } }

    /**
     * JSON со словами выбранных блоков в формате курса: ID с префиксом курса, ключи полей курса,
     * служебные поля блоков убраны (тема остаётся в тегах).
     */
    fun select(text: String, numbers: Set<Int>, spec: LanguageSpec): String {
        val picked = entriesOf(text).filter { (it["block"]?.jsonPrimitive?.intOrNull ?: 1) in numbers }
        val rename = mapOf(
            "term" to spec.termKey, "spanish" to spec.termKey, "german" to spec.termKey,
            "example_${spec.languageCode}" to spec.exampleKey,
        )
        val entries = picked.map { obj ->
            buildJsonObject {
                obj.forEach { (k, v) ->
                    when (k) {
                        "block", "block_title" -> Unit
                        "id" -> put("id", JsonPrimitive(entryId(spec.code, v.jsonPrimitive.content)))
                        else -> put(rename[k] ?: k, v)
                    }
                }
            }
        }
        val doc = buildJsonObject {
            put("schema_version", JsonPrimitive(VOCAB_SCHEMA_VERSION))
            put("language", JsonPrimitive(spec.languageCode))
            if (spec.translationCode != "ru") put("translation_language", JsonPrimitive(spec.translationCode))
            put("entries", JsonArray(entries))
        }
        return VocabularyJson.json.encodeToString(JsonObject.serializer(), doc)
    }
}
