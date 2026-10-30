package app.investwatch.core

/**
 * Расчёты по ценам. Приложение само ничего не оценивает — считает только
 * арифметику, которую видно на графике и в карточке.
 */
object Prices {

    /** Отрезок истории под выбранный период. Данные приходят по возрастанию даты. */
    fun slice(candles: List<PriceCandle>, period: ChartPeriod, today: String): List<PriceCandle> {
        val days = period.days ?: return candles
        val from = Dates.minusDays(today, days) ?: return candles
        return candles.filter { it.date >= from }
    }

    /** Изменение за период в процентах: от первой свечи к последней. */
    fun changePct(candles: List<PriceCandle>): Double? {
        val first = candles.firstOrNull()?.close ?: return null
        val last = candles.lastOrNull()?.close ?: return null
        if (first == 0.0) return null
        return (last - first) / first * 100.0
    }

    /** Цена на дату отсечки — ближайшая свеча не позже этой даты. */
    fun priceOn(candles: List<PriceCandle>, date: String): Double? =
        candles.lastOrNull { it.date <= date }?.close

    /** Дивдоходность выплаты к цене на дату отсечки. */
    fun paymentYield(payment: DividendPayment, candles: List<PriceCandle>): Double? {
        payment.yieldPct?.let { return it }
        val price = priceOn(candles, payment.recordDate) ?: return null
        if (price == 0.0) return null
        return payment.amount / price * 100.0
    }

    /**
     * Дивдоходность за последние 12 месяцев к текущей цене.
     * Нужна карточке и фильтру «проходит порог рынка».
     */
    fun trailingYieldPct(dividends: Dividends, price: Double?, today: String): Double? {
        if (price == null || price == 0.0) return null
        val from = Dates.minusDays(today, 365) ?: return null
        val sum = dividends.payments.filter { it.recordDate in from..today }.sumOf { it.amount }
        if (sum == 0.0) return null
        return sum / price * 100.0
    }

    /** Проходит ли бумага порог дивдоходности своего рынка. */
    fun passesYieldFloor(yieldPct: Double?, market: Market?): Boolean? {
        val floor = market?.yieldFloorPct ?: return null
        val value = yieldPct ?: return null
        return value >= floor
    }
}

/** Работа с датами в формате ISO без зависимости от Android. */
object Dates {

    fun today(): String = java.time.LocalDate.now().toString()

    fun minusDays(date: String, days: Int): String? = runCatching {
        java.time.LocalDate.parse(date.take(10)).minusDays(days.toLong()).toString()
    }.getOrNull()

    fun isValid(date: String?): Boolean =
        date != null && runCatching { java.time.LocalDate.parse(date.take(10)) }.isSuccess
}
