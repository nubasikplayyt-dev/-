package app.lingvo.core

/**
 * Разбор массового ввода: одна карточка на строку.
 *   выражение — перевод
 *   выражение — перевод | пример | перевод примера
 * Разделитель между выражением и переводом: " — ", " – ", " - ", "=", табуляция или ";".
 */
object BulkParser {
    data class Line(val lineNumber: Int, val term: String, val translation: String, val example: String, val exampleRu: String)
    data class Result(val lines: List<Line>, val errors: List<String>)

    private val separators = listOf("\t", " — ", " – ", " - ", "=", ";")

    fun parse(text: String): Result {
        val lines = ArrayList<Line>()
        val errors = ArrayList<String>()
        text.lines().forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val parts = line.split("|").map { Normalizer.clean(it) }
            val head = parts[0]
            val sep = separators.firstOrNull { head.contains(it) }
            if (sep == null) {
                errors += "Строка ${i + 1}: нет разделителя между выражением и переводом («—»)."
                return@forEachIndexed
            }
            val term = Normalizer.clean(head.substringBefore(sep))
            val translation = Normalizer.clean(head.substringAfter(sep))
            if (term.isEmpty() || translation.isEmpty()) {
                errors += "Строка ${i + 1}: пустое выражение или перевод."
                return@forEachIndexed
            }
            lines += Line(i + 1, term, translation, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
        }
        return Result(lines, errors)
    }
}
