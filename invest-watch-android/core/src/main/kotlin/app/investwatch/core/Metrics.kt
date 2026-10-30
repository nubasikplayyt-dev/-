package app.investwatch.core

/**
 * Подписи ключевых чисел. Агент пишет их техническими ключами
 * (`net_debt_to_ebitda`, `yield_pct`), показывать надо по-русски.
 * Незнакомый ключ не теряется: из него делается читаемая подпись,
 * а суффикс единицы измерения переносится к значению.
 */
object Metrics {

    private val LABELS = mapOf(
        "dividend_rub" to "Дивиденд",
        "dividend_usd" to "Дивиденд",
        "payout_rub_month" to "Выплата в месяц",
        "payout_pct" to "Payout",
        "yield_pct" to "Дивдоходность",
        "price_rub" to "Цена",
        "price_usd" to "Цена",
        "price_hkd" to "Цена",
        "nav_rub" to "СЧА пая",
        "premium_pct" to "Премия к СЧА",
        "discount_pct" to "Дисконт к СЧА",
        "net_debt_bn_rub" to "Чистый долг",
        "net_debt_to_ebitda" to "ЧД / EBITDA",
        "fcf_bn_rub" to "FCF",
        "net_profit_bn_rub" to "Чистая прибыль",
        "revenue_bn_rub" to "Выручка",
        "ebitda_bn_rub" to "EBITDA",
        "capex_bn_rub" to "Капекс",
        "roe_pct" to "ROE",
        "cost_of_risk_pct" to "Стоимость риска",
        "coupon_pct" to "Купон",
        "volume_bn_rub" to "Объём",
        "brent_usd" to "Brent",
        "urals_discount_usd" to "Дисконт Urals",
        "sulphur_usd_t" to "Сера",
        "dap_usd_t" to "DAP",
        "usd_rub" to "USD/RUB",
    )

    /** Суффикс ключа → единица измерения рядом со значением. */
    private val UNITS = listOf(
        Regex("_bn_rub$") to "млрд ₽",
        Regex("_rub_month$") to "₽/мес",
        Regex("_rub$") to "₽",
        Regex("_usd_t$") to "$/т",
        Regex("_hkd$") to "HK$",
        Regex("_cny$") to "¥",
        Regex("_usd$") to "$",
        Regex("_pct$") to "%",
        Regex("_x$") to "x",
    )

    data class Display(val label: String, val value: String)

    fun describe(name: String, value: String): Display {
        val unit = UNITS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(name) }?.second
        // Процент пишется вплотную к числу, остальные единицы — через пробел.
        val shown = when {
            unit == null -> value
            unit == "%" || unit == "x" -> value + unit
            else -> "$value $unit"
        }
        return Display(LABELS[name] ?: humanize(name), shown)
    }

    fun describe(metric: EventMetric): Display = describe(metric.name, metric.value)

    fun describe(metric: StatusMetric): Display = describe(metric.name, metric.value)

    /** `net_debt_to_ebitda` → «Net debt to ebitda»: лучше, чем сырой ключ. */
    private fun humanize(name: String): String {
        val words = name
            .replace(Regex("_(bn|mn)?_?(rub|usd|hkd|cny|pct|t|month|x)$", RegexOption.IGNORE_CASE), "")
            .split("_")
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return name
        return words.joinToString(" ").replaceFirstChar { it.uppercase() }
    }
}
