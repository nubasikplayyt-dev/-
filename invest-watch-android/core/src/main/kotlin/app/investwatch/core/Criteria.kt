package app.investwatch.core

data class CriterionHelp(val title: String, val text: String)

/**
 * Расшифровки критериев из CRITERIA.md.
 *
 * В репозитории они лежат таблицами:
 *   | C1 | Вечная тема | Бизнес, нужный людям и 500 лет назад… |
 *   | P1 | Не покупать, если доходность ниже порога… |
 * Трёхколоночная строка даёт название и пояснение, двухколоночная — правило.
 * На случай другой разметки понимаются и заголовки вида «## C1 — …».
 */
object Criteria {

    private val CODE = Regex("^\\*{0,2}([CP]\\d{1,2})\\*{0,2}$")
    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val HEADING_CODE = Regex("^\\*{0,2}([CP]\\d{1,2})\\*{0,2}\\s*[—–\\-.:)]?\\s*(.*)$")

    fun parse(markdown: String): Map<String, CriterionHelp> =
        fromTables(markdown) + fromHeadings(markdown)

    private fun fromTables(markdown: String): Map<String, CriterionHelp> {
        val result = linkedMapOf<String, CriterionHelp>()

        for (line in markdown.lineSequence()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("|")) continue

            val cells = trimmed.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
            if (cells.size < 2) continue

            val code = CODE.find(cells[0])?.groupValues?.get(1) ?: continue
            val title = strip(cells[1])
            val text = if (cells.size >= 3) strip(cells.drop(2).joinToString(" ")) else ""

            result[code.uppercase()] = CriterionHelp(title.ifEmpty { code }, text)
        }

        return result
    }

    private fun fromHeadings(markdown: String): Map<String, CriterionHelp> {
        val result = linkedMapOf<String, CriterionHelp>()
        var code: String? = null
        var level = 0
        var title = ""
        val body = mutableListOf<String>()

        fun flush() {
            val current = code ?: return
            result[current] = CriterionHelp(title, body.joinToString("\n").trim())
            code = null
            body.clear()
        }

        for (line in markdown.lineSequence()) {
            val heading = HEADING.find(line)
            if (heading != null) {
                val headingLevel = heading.groupValues[1].length
                val match = HEADING_CODE.find(heading.groupValues[2].trim())
                if (match != null) {
                    flush()
                    code = match.groupValues[1].uppercase()
                    level = headingLevel
                    title = match.groupValues[2].trim().ifEmpty { match.groupValues[1] }
                } else if (code != null && headingLevel <= level) {
                    flush()
                }
                continue
            }
            if (code != null) body += line
        }
        flush()

        return result
    }

    /** Убирает жирный, курсив и обратные кавычки — в списке они мешают. */
    private fun strip(value: String): String = value
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("`(.+?)`"), "$1")
        .replace(Regex("\\*(.+?)\\*"), "$1")
        .trim()
}
