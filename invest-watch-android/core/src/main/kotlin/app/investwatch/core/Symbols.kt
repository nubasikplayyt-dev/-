package app.investwatch.core

/**
 * Как назвать бумагу в источнике цен.
 *
 * Россия — Мосбиржа (ISS), там идентификатор это `secid`: для акций тикер,
 * для ЗПИФ — ISIN. США и Китай — EODHD, там символ это `ТИКЕР.БИРЖА`.
 */
object Symbols {

    /** Идентификатор бумаги на Мосбирже. */
    fun moex(asset: Asset): String = asset.ticker.trim().uppercase()

    /**
     * Символ EODHD. Биржу берём из поля `exchange`, если агент его заполнил,
     * иначе выводим из рынка. `null` — источник для этой бумаги неизвестен.
     */
    fun eodhd(asset: Asset): String? {
        val suffix = exchangeSuffix(asset) ?: return null
        return "${asset.ticker.trim().uppercase()}.$suffix"
    }

    private fun exchangeSuffix(asset: Asset): String? {
        asset.exchange?.trim()?.uppercase()?.let { exchange ->
            return when (exchange) {
                "US", "NYSE", "NASDAQ", "NYSE ARCA", "AMEX", "BATS" -> "US"
                "HK", "HKEX", "XHKG" -> "HK"
                "SSE", "SHG", "XSHG", "SHANGHAI" -> "SHG"
                "SZSE", "SHE", "XSHE", "SHENZHEN" -> "SHE"
                "MOEX", "MCX", "XMOS" -> null
                else -> exchange
            }
        }

        return when (asset.marketId.uppercase()) {
            "US" -> "US"
            // Без явной биржи китайскую бумагу различаем по номеру: шестизначный
            // код — материковый Китай, иначе Гонконг.
            "CN" -> chineseSuffix(asset.ticker)
            else -> null
        }
    }

    private fun chineseSuffix(ticker: String): String {
        val digits = ticker.trim().filter { it.isDigit() }
        if (digits.length != 6) return "HK"
        // Шанхай: 6xxxxx и 5xxxxx (фонды). Шэньчжэнь: 0xxxxx, 3xxxxx, 1xxxxx.
        return when (digits.first()) {
            '6', '5', '9' -> "SHG"
            else -> "SHE"
        }
    }

    /** Нужен ли для этой бумаги ключ платного источника. */
    fun needsPriceApiKey(asset: Asset): Boolean = asset.marketId.uppercase() != "RU"
}
