package app.lingvo.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

const val VOCAB_SCHEMA_VERSION = 1

/** Запись, прочитанная из файла. [entry].id пуст, если в файле ID не было. */
data class ParsedEntry(
    val index: Int,
    val entry: VocabEntry,
    val hasId: Boolean,
    val hasCreatedAt: Boolean,
)

data class EntryProblem(val index: Int, val label: String, val message: String)

data class ParsedVocabulary(
    val language: LanguageSpec,
    val schemaVersion: Int,
    val updatedAt: Long?,
    val entries: List<ParsedEntry>,
    /** Записи, которые не удалось прочитать (отсутствуют обязательные поля и т. п.). */
    val problems: List<EntryProblem>,
)

class VocabularyFormatException(message: String) : Exception(message)

object VocabularyJson {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        isLenient = false
    }

    private fun knownKeys(spec: LanguageSpec) = setOf(
        "id", spec.termKey, "term", "word", "russian", "translation", spec.exampleKey, "example", "example_ru",
        "part_of_speech", "article", "plural", "level", "tags", "notes", "source", "grammar_notes",
        "collocations", "verb_forms", "regional_usage", "created_at", "updated_at",
    )

    /**
     * Разбирает файл словаря.
     * @param expected язык, выбранный пользователем. Если файл объявляет другой язык — ошибка.
     * @throws VocabularyFormatException если файл повреждён, пуст или относится к другому языку.
     */
    fun parse(text: String, expected: LanguageSpec?, now: Long): ParsedVocabulary {
        val trimmed = text.trim().removePrefix("﻿")
        if (trimmed.isEmpty()) throw VocabularyFormatException("Файл пуст.")
        val root: JsonElement = try {
            json.parseToJsonElement(trimmed)
        } catch (e: SerializationException) {
            throw VocabularyFormatException("Файл повреждён: это не корректный JSON. ${e.message?.lineSequence()?.firstOrNull().orEmpty()}")
        } catch (e: IllegalArgumentException) {
            throw VocabularyFormatException("Файл повреждён: это не корректный JSON.")
        }

        val entriesElement: JsonArray
        var declaredLang: String? = null
        var schema = VOCAB_SCHEMA_VERSION
        var updatedAt: Long? = null
        when (root) {
            is JsonArray -> entriesElement = root
            is JsonObject -> {
                val e = root["entries"] ?: throw VocabularyFormatException("В файле нет массива \"entries\".")
                entriesElement = e as? JsonArray ?: throw VocabularyFormatException("Поле \"entries\" должно быть массивом.")
                declaredLang = (root["language"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                schema = (root["schema_version"] as? JsonPrimitive)?.intOrNull ?: VOCAB_SCHEMA_VERSION
                updatedAt = IsoTime.parseOrNull((root["updated_at"] as? JsonPrimitive)?.contentOrEmpty())
            }
            else -> throw VocabularyFormatException("Ожидался JSON-объект словаря или массив карточек.")
        }
        if (schema > VOCAB_SCHEMA_VERSION) {
            throw VocabularyFormatException("Версия формата $schema новее поддерживаемой ($VOCAB_SCHEMA_VERSION). Обновите приложение.")
        }

        val declaredSpec = declaredLang?.let {
            Languages.byCode(it) ?: throw VocabularyFormatException("Неизвестный язык словаря: \"$it\".")
        }
        val inferredSpec = declaredSpec ?: inferLanguage(entriesElement)
        val spec = when {
            expected == null -> inferredSpec
                ?: throw VocabularyFormatException("Не удалось определить язык словаря. Выберите язык вручную.")
            inferredSpec != null && inferredSpec.code != expected.code -> throw VocabularyFormatException(
                "Файл содержит словарь «${inferredSpec.nameRu}», а для импорта выбран «${expected.nameRu}». " +
                    "Выберите правильный язык."
            )
            else -> expected
        }

        val parsed = ArrayList<ParsedEntry>()
        val problems = ArrayList<EntryProblem>()
        entriesElement.forEachIndexed { index, el ->
            val obj = el as? JsonObject
            if (obj == null) {
                problems += EntryProblem(index, "#${index + 1}", "Запись должна быть JSON-объектом.")
                return@forEachIndexed
            }
            try {
                parsed += parseEntry(index, obj, spec, now)
            } catch (e: VocabularyFormatException) {
                val label = obj.str(spec.termKey).ifEmpty { obj.str("term") }.ifEmpty { "#${index + 1}" }
                problems += EntryProblem(index, label, e.message ?: "Ошибка в записи.")
            }
        }
        if (parsed.isEmpty() && problems.isEmpty()) {
            throw VocabularyFormatException("Словарь не содержит ни одной карточки.")
        }
        return ParsedVocabulary(spec, schema, updatedAt, parsed, problems)
    }

    private fun inferLanguage(entries: JsonArray): LanguageSpec? {
        for (el in entries) {
            val obj = el as? JsonObject ?: continue
            Languages.all.firstOrNull { obj.containsKey(it.termKey) }?.let { return it }
        }
        return null
    }

    private fun parseEntry(index: Int, obj: JsonObject, spec: LanguageSpec, now: Long): ParsedEntry {
        // Чужой язык внутри файла (например, поле "german" в испанском словаре).
        Languages.all.filter { it.code != spec.code }.forEach { other ->
            if (obj.containsKey(other.termKey) && !obj.containsKey(spec.termKey)) {
                throw VocabularyFormatException("Запись относится к языку «${other.nameRu}».")
            }
        }
        val rawTerm = Normalizer.clean(obj.str(spec.termKey).ifEmpty { obj.str("term") }.ifEmpty { obj.str("word") })
        val translation = Normalizer.clean(obj.str("russian").ifEmpty { obj.str("translation") })
        if (rawTerm.isEmpty()) throw VocabularyFormatException("Нет обязательного поля \"${spec.termKey}\".")
        if (translation.isEmpty()) throw VocabularyFormatException("Нет обязательного поля \"russian\" (перевод).")

        var article = Normalizer.clean(obj.str("article")).lowercase()
        if (article.isNotEmpty() && spec.articles.isNotEmpty() && article !in spec.articles) {
            throw VocabularyFormatException("Недопустимый артикль \"$article\" для языка «${spec.nameRu}».")
        }
        val pos = PartsOfSpeech.canonical(obj.str("part_of_speech"))
        if (article.isEmpty() && spec.code == Languages.GERMAN.code && pos == PartsOfSpeech.NOUN) {
            article = Normalizer.detectArticle(rawTerm, spec)
        }
        val id = Normalizer.clean(obj.str("id"))
        val createdAt = IsoTime.parseOrNull(obj.str("created_at"))
        val updatedAt = IsoTime.parseOrNull(obj.str("updated_at"))

        val extra = JsonObject(obj.filterKeys { it !in knownKeys(spec) })
        val verbForms = obj["verb_forms"]?.takeIf { it !is JsonNull && !(it is JsonPrimitive && it.content.isBlank()) }

        val entry = VocabEntry(
            id = id,
            language = spec.code,
            term = rawTerm,
            translation = translation,
            exampleTarget = Normalizer.clean(obj.str(spec.exampleKey).ifEmpty { obj.str("example") }),
            exampleRu = Normalizer.clean(obj.str("example_ru")),
            partOfSpeech = pos,
            article = article,
            plural = Normalizer.clean(obj.str("plural")),
            level = Normalizer.clean(obj.str("level")).uppercase(),
            tags = Normalizer.tags(obj.strList("tags")),
            notes = obj.str("notes").trim(),
            source = Normalizer.clean(obj.str("source")),
            grammarNotes = obj.str("grammar_notes").trim(),
            collocations = obj.strList("collocations").map(Normalizer::clean).filter { it.isNotEmpty() },
            verbFormsJson = verbForms?.let { json.encodeToString(JsonElement.serializer(), it) }.orEmpty(),
            regionalUsage = Normalizer.clean(obj.str("regional_usage")),
            extraJson = if (extra.isEmpty()) "" else json.encodeToString(JsonObject.serializer(), extra),
            createdAt = createdAt ?: updatedAt ?: now,
            updatedAt = updatedAt ?: createdAt ?: now,
        )
        return ParsedEntry(index, entry, hasId = id.isNotEmpty(), hasCreatedAt = createdAt != null)
    }

    /** Сериализует словарь в канонический формат (UTF-8, schema_version 1). */
    fun serialize(spec: LanguageSpec, entries: List<VocabEntry>, updatedAt: Long): String {
        val root = buildJsonObject {
            put("schema_version", VOCAB_SCHEMA_VERSION)
            put("language", spec.code)
            put("updated_at", IsoTime.format(updatedAt))
            putJsonArray("entries") {
                entries.sortedWith(compareBy<VocabEntry> { it.createdAt }.thenBy { it.id }).forEach { add(entryToJson(spec, it)) }
            }
        }
        return json.encodeToString(JsonObject.serializer(), root) + "\n"
    }

    fun entryToJson(spec: LanguageSpec, e: VocabEntry): JsonObject = buildJsonObject {
        put("id", e.id)
        put(spec.termKey, e.term)
        put("russian", e.translation)
        if (spec.articles.isNotEmpty()) put("article", e.article)
        if (spec.code == Languages.GERMAN.code || e.plural.isNotEmpty()) put("plural", e.plural)
        put("part_of_speech", e.partOfSpeech)
        put(spec.exampleKey, e.exampleTarget)
        put("example_ru", e.exampleRu)
        put("grammar_notes", e.grammarNotes)
        putJsonArray("collocations") { e.collocations.forEach { add(JsonPrimitive(it)) } }
        if (e.verbFormsJson.isNotEmpty()) {
            put("verb_forms", runCatching { json.parseToJsonElement(e.verbFormsJson) }.getOrElse { JsonPrimitive(e.verbFormsJson) })
        }
        if (e.regionalUsage.isNotEmpty()) put("regional_usage", e.regionalUsage)
        put("level", e.level)
        putJsonArray("tags") { e.tags.forEach { add(JsonPrimitive(it)) } }
        put("notes", e.notes)
        put("source", e.source)
        put("created_at", IsoTime.format(e.createdAt))
        put("updated_at", IsoTime.format(e.updatedAt))
        if (e.extraJson.isNotEmpty()) {
            val extra = runCatching { json.parseToJsonElement(e.extraJson) as? JsonObject }.getOrNull()
            extra?.forEach { (k, v) -> put(k, v) }
        }
    }

    private fun JsonPrimitive.contentOrEmpty(): String = if (this is JsonNull) "" else content

    private fun JsonObject.str(key: String): String = when (val v = this[key]) {
        null, is JsonNull -> ""
        is JsonPrimitive -> v.content
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }.joinToString("; ")
        is JsonObject -> json.encodeToString(JsonObject.serializer(), v)
    }

    private fun JsonObject.strList(key: String): List<String> = when (val v = this[key]) {
        null, is JsonNull -> emptyList()
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
        is JsonPrimitive -> v.content.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        is JsonObject -> emptyList()
    }
}
