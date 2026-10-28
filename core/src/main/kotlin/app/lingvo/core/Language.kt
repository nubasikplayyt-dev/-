package app.lingvo.core

/**
 * Описание изучаемого языка. Новый язык добавляется новой записью в [Languages.all]:
 * алгоритм обучения, база данных и импорт работают с любым [LanguageSpec].
 */
data class LanguageSpec(
    /** ISO 639-1 код, он же ключ языка в базе данных и поле "language" в JSON. */
    val code: String,
    val nameRu: String,
    val flag: String,
    /** Имя поля с изучаемым выражением в JSON ("spanish", "german"). */
    val termKey: String,
    /** Имя поля с примером на изучаемом языке ("example_es", "example_de"). */
    val exampleKey: String,
    /** Канонический файл словаря. */
    val fileName: String,
    /** Артикли, которые язык использует перед существительными (для нормализации). */
    val articles: Set<String> = emptySet(),
    /** BCP 47 тег для TextToSpeech. */
    val ttsTag: String,
    /** Префикс для автоматически созданных ID. */
    val idPrefix: String = code,
)

object Languages {
    val SPANISH = LanguageSpec(
        code = "es",
        nameRu = "Испанский",
        flag = "🇪🇸",
        termKey = "spanish",
        exampleKey = "example_es",
        fileName = "spanish_vocabulary.json",
        articles = setOf("el", "la", "los", "las"),
        ttsTag = "es-ES",
    )

    val GERMAN = LanguageSpec(
        code = "de",
        nameRu = "Немецкий",
        flag = "🇩🇪",
        termKey = "german",
        exampleKey = "example_de",
        fileName = "german_vocabulary.json",
        articles = setOf("der", "die", "das"),
        ttsTag = "de-DE",
    )

    val all: List<LanguageSpec> = listOf(SPANISH, GERMAN)

    fun byCode(code: String): LanguageSpec? = all.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }

    fun require(code: String): LanguageSpec =
        byCode(code) ?: throw IllegalArgumentException("Неизвестный язык: $code")
}
