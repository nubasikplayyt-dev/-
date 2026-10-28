package app.lingvo.core

import java.text.Normalizer as JNormalizer
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

object Normalizer {
    private val spaces = Regex("\\s+")
    private val edgePunctuation = Regex("^[¿¡\"'«»„“”.,;:!?\\s]+|[\"'«»„“”.,;:!?\\s]+$")

    /** Unicode NFC + схлопывание пробелов + обрезка. Регистр и диакритика сохраняются. */
    fun clean(text: String): String =
        JNormalizer.normalize(text, JNormalizer.Form.NFC)
            .replace(' ', ' ')
            .replace(spaces, " ")
            .trim()

    /**
     * Ключ для поиска дубликатов: NFC, без учёта регистра, лишних пробелов и
     * обрамляющей пунктуации (¿Qué tal? == qué tal). Диакритика значима: año ≠ ano.
     */
    fun key(text: String): String =
        clean(text).lowercase(Locale.ROOT).replace(edgePunctuation, "").replace(spaces, " ").trim()

    private val combiningMarks = Regex("\\p{Mn}+")

    /** Ключ для поиска: дополнительно без диакритики (que tal → ¿Qué tal?, ё → е). Не для дубликатов. */
    fun searchKey(text: String): String =
        JNormalizer.normalize(key(text), JNormalizer.Form.NFD).replace(combiningMarks, "")

    /** Ключ дубликата карточки с учётом артикля: "Voraussetzung"+"die" == "die Voraussetzung". */
    fun entryKey(term: String, article: String): String = key(withArticle(term, article))

    /** Добавляет артикль к термину, если он ещё не указан в начале. */
    fun withArticle(term: String, article: String): String {
        val t = clean(term)
        val a = clean(article)
        if (a.isEmpty() || t.isEmpty()) return t
        // Артикль с апострофом (l', l’) пишется слитно: l'ami.
        val sep = if (a.endsWith("'") || a.endsWith("’")) "" else " "
        return if (t.lowercase(Locale.ROOT).startsWith(a.lowercase(Locale.ROOT) + sep)) t else "$a$sep$t"
    }

    /** Отделяет ведущий артикль ("die Voraussetzung" → "die"). */
    fun detectArticle(term: String, spec: LanguageSpec): String {
        val first = clean(term).substringBefore(' ', "").lowercase(Locale.ROOT)
        return if (first in spec.articles && clean(term).contains(' ')) first else ""
    }

    fun tags(raw: List<String>): List<String> {
        val seen = LinkedHashMap<String, String>()
        for (t in raw) {
            val c = clean(t)
            if (c.isNotEmpty()) seen.putIfAbsent(c.lowercase(Locale.ROOT), c)
        }
        return seen.values.toList()
    }
}

object IsoTime {
    fun format(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    /** Возвращает null, если строка пуста или не является датой ISO-8601. */
    fun parseOrNull(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return try {
            Instant.parse(text.trim()).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                java.time.LocalDate.parse(text.trim()).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
