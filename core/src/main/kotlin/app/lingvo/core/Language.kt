package app.lingvo.core

/** Справочные данные о языке. Каталог — только подсказка: пользователь может добавить любой язык. */
data class LanguageInfo(
    /** ISO 639-1 (или свой код для языка вне каталога). */
    val code: String,
    val nameRu: String,
    val flag: String,
    /** Артикли перед существительными — для нормализации и подсказок. */
    val articles: Set<String> = emptySet(),
    /** BCP 47 тег для TextToSpeech. */
    val ttsTag: String = code,
    /** Ключ выражения в старых словарях ("spanish", "german"). */
    val legacyTermKey: String? = null,
)

object LanguageCatalog {
    val all: List<LanguageInfo> = listOf(
        LanguageInfo("en", "Английский", "🇬🇧", setOf("the"), "en-GB"),
        LanguageInfo("es", "Испанский", "🇪🇸", setOf("el", "la", "los", "las"), "es-ES", "spanish"),
        LanguageInfo("de", "Немецкий", "🇩🇪", setOf("der", "die", "das"), "de-DE", "german"),
        LanguageInfo("fr", "Французский", "🇫🇷", setOf("le", "la", "les", "l'"), "fr-FR"),
        LanguageInfo("it", "Итальянский", "🇮🇹", setOf("il", "lo", "la", "i", "gli", "le", "l'"), "it-IT"),
        LanguageInfo("pt", "Португальский", "🇵🇹", setOf("o", "a", "os", "as"), "pt-PT"),
        LanguageInfo("nl", "Нидерландский", "🇳🇱", setOf("de", "het"), "nl-NL"),
        LanguageInfo("sv", "Шведский", "🇸🇪", emptySet(), "sv-SE"),
        LanguageInfo("no", "Норвежский", "🇳🇴", emptySet(), "nb-NO"),
        LanguageInfo("da", "Датский", "🇩🇰", emptySet(), "da-DK"),
        LanguageInfo("fi", "Финский", "🇫🇮", emptySet(), "fi-FI"),
        LanguageInfo("pl", "Польский", "🇵🇱", emptySet(), "pl-PL"),
        LanguageInfo("cs", "Чешский", "🇨🇿", emptySet(), "cs-CZ"),
        LanguageInfo("sr", "Сербский", "🇷🇸", emptySet(), "sr-RS"),
        LanguageInfo("el", "Греческий", "🇬🇷", setOf("ο", "η", "το", "οι", "τα"), "el-GR"),
        LanguageInfo("tr", "Турецкий", "🇹🇷", emptySet(), "tr-TR"),
        LanguageInfo("he", "Иврит", "🇮🇱", emptySet(), "he-IL"),
        LanguageInfo("ar", "Арабский", "🇸🇦", emptySet(), "ar"),
        LanguageInfo("fa", "Персидский", "🇮🇷", emptySet(), "fa-IR"),
        LanguageInfo("hi", "Хинди", "🇮🇳", emptySet(), "hi-IN"),
        LanguageInfo("ja", "Японский", "🇯🇵", emptySet(), "ja-JP"),
        LanguageInfo("zh", "Китайский", "🇨🇳", emptySet(), "zh-CN"),
        LanguageInfo("ko", "Корейский", "🇰🇷", emptySet(), "ko-KR"),
        LanguageInfo("ka", "Грузинский", "🇬🇪", emptySet(), "ka-GE"),
        LanguageInfo("hy", "Армянский", "🇦🇲", emptySet(), "hy-AM"),
        LanguageInfo("uk", "Украинский", "🇺🇦", emptySet(), "uk-UA"),
        LanguageInfo("be", "Белорусский", "🇧🇾", emptySet(), "be-BY"),
        LanguageInfo("kk", "Казахский", "🇰🇿", emptySet(), "kk-KZ"),
        LanguageInfo("uz", "Узбекский", "🇺🇿", emptySet(), "uz-UZ"),
        LanguageInfo("la", "Латынь", "🏛️", emptySet(), "la"),
        LanguageInfo("eo", "Эсперанто", "🌍", emptySet(), "eo"),
        LanguageInfo("ru", "Русский", "🇷🇺", emptySet(), "ru-RU"),
    )

    fun byCode(code: String): LanguageInfo? = all.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }

    /** Язык из каталога или пользовательский язык с нейтральным флагом. */
    fun infoOrCustom(code: String, name: String = code): LanguageInfo =
        byCode(code) ?: LanguageInfo(code.trim().lowercase(), name.ifBlank { code }, "🌐", emptySet(), code.trim().lowercase())

    val legacyKeys: Map<String, String> = all.mapNotNull { info -> info.legacyTermKey?.let { it to info.code } }.toMap()
}

/**
 * Языковой курс: что изучаем и на какой язык переводим. Курсов может быть сколько угодно,
 * в том числе несколько с одним изучаемым языком (например, «Испанский: путешествия» и «Испанский: работа»).
 */
data class Course(
    /** Стабильный ID курса. Он же значение `language` у карточек в базе. Для старых данных — "es" и "de". */
    val id: String,
    val languageCode: String,
    val name: String,
    val flag: String,
    val translationCode: String = "ru",
    val translationName: String = "Русский",
    val createdAt: Long = 0L,
    val sortOrder: Int = 0,
) {
    val spec: LanguageSpec get() = LanguageSpec.of(this)
}

/**
 * Всё, что нужно логике о курсе. Создаётся из [Course] — бизнес-логика не знает конкретных языков.
 */
data class LanguageSpec(
    /** ID курса (ключ в базе данных). */
    val code: String,
    /** ISO-код изучаемого языка — поле "language" в JSON. */
    val languageCode: String,
    val nameRu: String,
    val flag: String,
    /** Поле выражения в JSON: "spanish"/"german" для совместимости, иначе "term". */
    val termKey: String,
    /** Поле примера в JSON: "example_<код>". */
    val exampleKey: String,
    /** Поле перевода: "russian" при переводе на русский (совместимость), иначе "translation". */
    val translationKey: String,
    val translationCode: String,
    val fileName: String,
    val articles: Set<String> = emptySet(),
    val ttsTag: String,
    val idPrefix: String = code,
) {
    companion object {
        fun of(course: Course): LanguageSpec {
            val info = LanguageCatalog.byCode(course.languageCode)
            val lang = course.languageCode.lowercase()
            return LanguageSpec(
                code = course.id,
                languageCode = lang,
                nameRu = course.name,
                flag = course.flag,
                termKey = info?.legacyTermKey ?: "term",
                exampleKey = "example_$lang",
                translationKey = if (course.translationCode == "ru") "russian" else "translation",
                translationCode = course.translationCode,
                fileName = when {
                    info?.legacyTermKey != null && course.id == lang -> "${info.legacyTermKey}_vocabulary.json"
                    course.id == lang -> "${lang}_vocabulary.json"
                    else -> "${course.id}_vocabulary.json"
                },
                articles = info?.articles.orEmpty(),
                ttsTag = info?.ttsTag ?: lang,
            )
        }

        /** Курс по умолчанию для языка из каталога (ID = код языка). */
        fun forLanguage(code: String): LanguageSpec {
            val info = LanguageCatalog.infoOrCustom(code)
            return of(Course(id = info.code, languageCode = info.code, name = info.nameRu, flag = info.flag))
        }
    }
}

/**
 * Курсы, совместимые со старыми версиями приложения (испанский и немецкий).
 * Не создаются автоматически: используются при миграции старых данных, импорте старых словарей и в тестах.
 */
object Languages {
    val SPANISH: LanguageSpec = LanguageSpec.forLanguage("es")
    val GERMAN: LanguageSpec = LanguageSpec.forLanguage("de")
    val legacy: List<LanguageSpec> = listOf(SPANISH, GERMAN)

    fun legacyCourse(code: String): Course? = LanguageCatalog.byCode(code)?.takeIf { it.legacyTermKey != null }?.let {
        Course(id = it.code, languageCode = it.code, name = it.nameRu, flag = it.flag)
    }
}
