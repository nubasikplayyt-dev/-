package app.investwatch.core

import java.text.NumberFormat
import java.util.Locale

/** Даты «30 окт 2026», числа с неразрывным пробелом между тысячами и запятой. */
object Format {

    private val RU = Locale("ru")

    private val MONTHS = listOf(
        "янв", "фев", "мар", "апр", "мая", "июн",
        "июл", "авг", "сен", "окт", "ноя", "дек",
    )

    private val ISO = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")

    /** Непонятную строку возвращаем как есть, пустую — прочерком. */
    fun date(value: String?): String {
        if (value.isNullOrBlank()) return "—"
        val match = ISO.find(value.trim()) ?: return value
        val month = MONTHS.getOrNull(match.groupValues[2].toInt() - 1) ?: return value
        return "${match.groupValues[3].toInt()} $month ${match.groupValues[1]}"
    }

    /** Год из даты — для группировки дивидендов. */
    fun year(value: String?): Int? =
        ISO.find(value?.trim().orEmpty())?.groupValues?.get(1)?.toIntOrNull()

    fun number(value: Double, maxFractionDigits: Int = 2): String {
        val format = NumberFormat.getNumberInstance(RU)
        format.maximumFractionDigits = maxFractionDigits
        // В русской локали разделитель тысяч — неразрывный пробел; оставляем его.
        return format.format(value)
    }

    fun money(value: Double, currency: String?): String {
        val amount = number(value, if (kotlin.math.abs(value) < 100) 2 else 0)
        val sign = currencySign(currency)
        return if (sign == null) amount else "$amount $sign"
    }

    fun percent(value: Double, withSign: Boolean = false): String {
        val text = number(value, 1)
        return if (withSign && value > 0) "+$text%" else "$text%"
    }

    fun currencySign(currency: String?): String? = when (currency?.uppercase()) {
        "RUB", "RUR" -> "₽"
        "USD" -> "$"
        "EUR" -> "€"
        "HKD" -> "HK$"
        "CNY", "CNH" -> "¥"
        null, "" -> null
        else -> currency
    }
}

/**
 * Флаг страны по коду ISO-3166 alpha-2 — запасной вариант, если у рынка
 * в watchlist.json не указан свой `flag`.
 */
object Flags {
    private const val BASE = 0x1F1E6 - 'A'.code

    fun of(code: String): String {
        val upper = code.trim().uppercase()
        if (!Regex("^[A-Z]{2}$").matches(upper)) return "🌐"
        return upper.map { Character.toChars(it.code + BASE).concatToString() }.joinToString("")
    }
}
