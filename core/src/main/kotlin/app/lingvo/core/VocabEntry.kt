package app.lingvo.core

/**
 * Карточка словаря (только лексические данные, без прогресса обучения).
 * Прогресс хранится отдельно в базе данных приложения и не попадает в JSON-словарь.
 */
data class VocabEntry(
    val id: String,
    val language: String,
    /** Изучаемое слово или выражение. Для немецких существительных — с артиклем ("die Voraussetzung"). */
    val term: String,
    val translation: String,
    val exampleTarget: String = "",
    val exampleRu: String = "",
    val partOfSpeech: String = "",
    val article: String = "",
    val plural: String = "",
    val level: String = "",
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val source: String = "",
    val grammarNotes: String = "",
    val collocations: List<String> = emptyList(),
    /** Формы глагола в исходном JSON-виде (объект или строка), сохраняются без потерь. */
    val verbFormsJson: String = "",
    val regionalUsage: String = "",
    /** Произношение / транскрипция / чтение (пиньинь, ромадзи, латиница) — если полезно. */
    val transcription: String = "",
    /** Допустимые варианты написания для письменного режима (синонимы, другая запись, латиница). */
    val alternatives: List<String> = emptyList(),
    /** Неизвестные поля из файла (JSON-объект), чтобы экспорт ничего не терял. */
    val extraJson: String = "",
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Термин для показа: немецкое существительное всегда с артиклем. */
    val displayTerm: String get() = Normalizer.withArticle(term, article)
}

/** Части речи, которые приложение понимает. В JSON допускаются и любые другие значения. */
object PartsOfSpeech {
    const val NOUN = "noun"
    const val VERB = "verb"
    const val ADJECTIVE = "adjective"
    const val ADVERB = "adverb"
    const val PHRASE = "phrase"
    const val PRONOUN = "pronoun"
    const val NUMERAL = "numeral"
    const val OTHER = "other"

    val labelsRu = linkedMapOf(
        NOUN to "Существительное",
        VERB to "Глагол",
        ADJECTIVE to "Прилагательное",
        ADVERB to "Наречие",
        PHRASE to "Выражение",
        PRONOUN to "Местоимение",
        NUMERAL to "Числительное",
        OTHER to "Другое",
    )

    private val aliases = mapOf(
        "n" to NOUN, "noun" to NOUN, "substantive" to NOUN, "существительное" to NOUN, "сущ" to NOUN,
        "v" to VERB, "verb" to VERB, "глагол" to VERB, "гл" to VERB,
        "adj" to ADJECTIVE, "adjective" to ADJECTIVE, "прилагательное" to ADJECTIVE, "прил" to ADJECTIVE,
        "adv" to ADVERB, "adverb" to ADVERB, "наречие" to ADVERB, "нар" to ADVERB,
        "phrase" to PHRASE, "pronoun" to PRONOUN, "местоимение" to PRONOUN, "numeral" to NUMERAL, "number" to NUMERAL,
        "числительное" to NUMERAL, "expression" to PHRASE, "idiom" to PHRASE, "collocation" to PHRASE,
        "выражение" to PHRASE, "словосочетание" to PHRASE, "устойчивое выражение" to PHRASE,
    )

    /** Приводит синонимы к каноническому коду, неизвестное значение сохраняет как есть. */
    fun canonical(raw: String): String {
        val key = raw.trim().lowercase()
        if (key.isEmpty()) return ""
        return aliases[key] ?: key
    }

    fun labelRu(code: String): String = labelsRu[code] ?: code
}

/** Предустановленные категории (теги). Пользователь может добавлять любые свои. */
object Categories {
    val defaults = listOf(
        "разговорная лексика",
        "академическая лексика",
        "деловая лексика",
        "медицинская лексика",
        "политика и экономика",
        "устойчивые выражения",
    )
}

val CEFR_LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")
