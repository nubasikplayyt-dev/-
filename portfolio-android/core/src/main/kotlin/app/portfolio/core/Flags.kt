package app.portfolio.core

/**
 * Флаг страны по коду ISO-3166 alpha-2. Буквы переводятся в regional
 * indicator symbols, поэтому новая страна в watchlist.json начинает
 * работать без правок кода.
 */
object Flags {
    private const val BASE = 0x1F1E6 - 'A'.code

    fun of(country: String): String {
        val code = country.trim().uppercase()
        if (!Regex("^[A-Z]{2}$").matches(code)) return "🌐"
        return code.map { Character.toChars(it.code + BASE).concatToString() }.joinToString("")
    }
}
