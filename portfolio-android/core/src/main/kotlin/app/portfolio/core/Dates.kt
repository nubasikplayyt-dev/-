package app.portfolio.core

/** Даты в интерфейсе: «30 окт 2026». */
object Dates {

    private val MONTHS = listOf(
        "янв", "фев", "мар", "апр", "мая", "июн",
        "июл", "авг", "сен", "окт", "ноя", "дек",
    )

    private val ISO = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")

    /** Непонятную строку возвращаем как есть, пустую — прочерком. */
    fun format(value: String?): String {
        if (value.isNullOrBlank()) return "—"
        val match = ISO.find(value.trim()) ?: return value
        val month = MONTHS.getOrNull(match.groupValues[2].toInt() - 1) ?: return value
        return "${match.groupValues[3].toInt()} $month ${match.groupValues[1]}"
    }
}
