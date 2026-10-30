package app.investwatch.data.net

import app.investwatch.core.Asset
import app.investwatch.core.DividendPayment
import app.investwatch.core.PriceCandle

/**
 * Источник цен. Для России это API самой Мосбиржи (официальный, без ключа),
 * для США и Китая — EODHD по ключу из настроек. Интерфейс один, чтобы
 * сменить провайдера можно было правкой одного класса.
 */
interface PriceSource {

    /** Умеет ли источник работать с этой бумагой. */
    fun supports(asset: Asset): Boolean

    /** Нужен ли для неё ключ, которого может не быть. */
    fun needsKey(asset: Asset): Boolean

    /** Дневная история, по возрастанию даты. Пустой список — данных нет. */
    suspend fun history(asset: Asset, fromDate: String): List<PriceCandle>

    /** Последняя цена и изменение за день. */
    suspend fun quote(asset: Asset): QuoteResult?

    /**
     * Дивиденды источника. Используются, только если в репозитории пусто:
     * приоритет всегда у данных агента.
     */
    suspend fun dividends(asset: Asset, fromDate: String): List<DividendPayment> = emptyList()
}

data class QuoteResult(
    val price: Double,
    val changePct: Double?,
    val currency: String?,
)

/** Источник не настроен: нет ключа или бумага ему незнакома. */
class PriceSourceUnavailable(message: String) : Exception(message)
