package app.investwatch.data.net

import app.investwatch.core.Asset
import app.investwatch.core.DividendPayment
import app.investwatch.core.PriceCandle
import app.investwatch.core.Symbols
import app.investwatch.data.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Цены бумаг США и Китая — EODHD.
 *
 * Бесплатного официального API сразу на Нью-Йорк, Гонконг, Шанхай и Шэньчжэнь
 * нет, поэтому здесь платный провайдер с документированным покрытием. Ключ
 * вводится в настройках и лежит в SecureStore; без ключа источник просто
 * молчит, а экраны показывают, что цены недоступны.
 */
@Singleton
class EodhdPriceSource @Inject constructor(
    private val client: OkHttpClient,
    private val secureStore: SecureStore,
) : PriceSource {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun supports(asset: Asset): Boolean = Symbols.eodhd(asset) != null

    override fun needsKey(asset: Asset): Boolean = true

    private fun token(): String? = secureStore.read(SecureStore.PRICE_API_KEY)?.takeIf { it.isNotBlank() }

    override suspend fun history(asset: Asset, fromDate: String): List<PriceCandle> {
        val symbol = Symbols.eodhd(asset) ?: return emptyList()
        val token = token() ?: throw PriceSourceUnavailable("Не задан ключ источника цен")

        val body = get("$API/eod/$symbol?api_token=$token&fmt=json&period=d&from=$fromDate")
            ?: return emptyList()
        val rows = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
            ?: return emptyList()

        return rows.mapNotNull { row ->
            val record = row as? JsonObject ?: return@mapNotNull null
            val date = record.string("date") ?: return@mapNotNull null
            val close = record.number("adjusted_close") ?: record.number("close")
                ?: return@mapNotNull null
            PriceCandle(date, close)
        }.sortedBy { it.date }
    }

    override suspend fun quote(asset: Asset): QuoteResult? {
        val symbol = Symbols.eodhd(asset) ?: return null
        val token = token() ?: throw PriceSourceUnavailable("Не задан ключ источника цен")

        val body = get("$API/real-time/$symbol?api_token=$token&fmt=json") ?: return null
        val record = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return null
        val price = record.number("close") ?: record.number("previousClose") ?: return null

        return QuoteResult(
            price = price,
            changePct = record.number("change_p"),
            currency = asset.currency,
        )
    }

    override suspend fun dividends(asset: Asset, fromDate: String): List<DividendPayment> {
        val symbol = Symbols.eodhd(asset) ?: return emptyList()
        val token = token() ?: return emptyList()

        val body = get("$API/div/$symbol?api_token=$token&fmt=json&from=$fromDate")
            ?: return emptyList()
        val rows = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
            ?: return emptyList()

        return rows.mapNotNull { row ->
            val record = row as? JsonObject ?: return@mapNotNull null
            val date = record.string("recordDate") ?: record.string("date")
                ?: return@mapNotNull null
            val value = record.number("value") ?: return@mapNotNull null
            DividendPayment(date, value)
        }.sortedByDescending { it.recordDate }
    }

    /** Проверка ключа кнопкой в настройках: понятный текст вместо кода ошибки. */
    suspend fun checkKey(token: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API/eod/AAPL.US?api_token=$token&fmt=json&period=d&from=2026-01-01")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> null
                    401, 403 -> "Ключ не принят источником"
                    402 -> "Тариф не покрывает этот запрос"
                    429 -> "Исчерпан дневной лимит запросов"
                    else -> "Источник ответил ${response.code}"
                }
            }
        }.getOrElse { "Не удалось связаться с источником: ${it.message}" }
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.code == 401 || response.code == 403 ->
                    throw PriceSourceUnavailable("Источник цен не принял ключ")
                response.code == 429 ->
                    throw PriceSourceUnavailable("Исчерпан лимит запросов к источнику цен")
                !response.isSuccessful -> throw IOException("Источник цен ответил ${response.code}")
                else -> response.body?.string()
            }
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private companion object {
        const val API = "https://eodhd.com/api"
    }
}

/** Выбирает подходящий источник для бумаги. */
@Singleton
class PriceSources @Inject constructor(
    private val moex: MoexPriceSource,
    private val eodhd: EodhdPriceSource,
) {
    fun forAsset(asset: Asset): PriceSource? = when {
        moex.supports(asset) -> moex
        eodhd.supports(asset) -> eodhd
        else -> null
    }

    val eodhdSource: EodhdPriceSource get() = eodhd
}
