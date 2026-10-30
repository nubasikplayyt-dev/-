package app.investwatch.data.net

import app.investwatch.core.Asset
import app.investwatch.core.DividendPayment
import app.investwatch.core.PriceCandle
import app.investwatch.core.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Цены и дивиденды российских бумаг — ISS Мосбиржи. Официальный API биржи,
 * без ключа и без регистрации.
 *
 * ISS отдаёт данные таблицами: отдельно список колонок, отдельно строки.
 * Поэтому разбираем вручную, по именам колонок, а не по позициям.
 */
@Singleton
class MoexPriceSource @Inject constructor(
    private val client: OkHttpClient,
) : PriceSource {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun supports(asset: Asset): Boolean = asset.marketId.equals("RU", ignoreCase = true)

    override fun needsKey(asset: Asset): Boolean = false

    override suspend fun history(asset: Asset, fromDate: String): List<PriceCandle> {
        val secid = Symbols.moex(asset)
        val candles = mutableListOf<PriceCandle>()
        var start = 0

        // ISS отдаёт историю страницами по 100 строк; идём, пока они не кончатся.
        while (start < MAX_ROWS) {
            val url = "$ISS/history/engines/stock/markets/shares/securities/$secid.json" +
                "?iss.meta=off&from=$fromDate&start=$start" +
                "&history.columns=TRADEDATE,CLOSE,LEGALCLOSEPRICE"
            val table = fetchTable(url, "history") ?: break
            if (table.rows.isEmpty()) break

            for (row in table.rows) {
                val date = table.string(row, "TRADEDATE") ?: continue
                val close = table.number(row, "CLOSE")
                    ?: table.number(row, "LEGALCLOSEPRICE")
                    ?: continue
                candles += PriceCandle(date, close)
            }
            start += table.rows.size
            if (table.rows.size < PAGE) break
        }

        // На одну дату приходит по строке на режим торгов — берём последнюю.
        return candles.groupBy { it.date }.map { (_, group) -> group.last() }.sortedBy { it.date }
    }

    override suspend fun quote(asset: Asset): QuoteResult? {
        val secid = Symbols.moex(asset)
        val url = "$ISS/engines/stock/markets/shares/securities/$secid.json" +
            "?iss.meta=off&iss.only=marketdata,securities" +
            "&marketdata.columns=SECID,LAST,LASTTOPREVPRICE,MARKETPRICE" +
            "&securities.columns=SECID,CURRENCYID,PREVPRICE"

        val marketData = fetchTable(url, "marketdata")
        val securities = fetchTable(url, "securities")

        // Строка с последней ценой; если торгов не было — первая доступная.
        val row = marketData?.let { table ->
            table.rows.firstOrNull { table.string(it, "LAST") != null } ?: table.rows.firstOrNull()
        }

        val fromMarket = if (marketData != null && row != null) {
            marketData.number(row, "LAST") ?: marketData.number(row, "MARKETPRICE")
        } else {
            null
        }
        val fromSecurities = securities?.let { table ->
            table.rows.firstNotNullOfOrNull { table.number(it, "PREVPRICE") }
        }
        val price = fromMarket ?: fromSecurities ?: return null

        return QuoteResult(
            price = price,
            changePct = if (marketData != null && row != null) {
                marketData.number(row, "LASTTOPREVPRICE")
            } else {
                null
            },
            currency = securities?.let { table ->
                table.rows.firstNotNullOfOrNull { table.string(it, "CURRENCYID") }
            } ?: asset.currency ?: "RUB",
        )
    }

    override suspend fun dividends(asset: Asset, fromDate: String): List<DividendPayment> {
        val secid = Symbols.moex(asset)
        val table = fetchTable("$ISS/securities/$secid/dividends.json?iss.meta=off", "dividends")
            ?: return emptyList()

        return table.rows.mapNotNull { row ->
            val date = table.string(row, "registryclosedate") ?: return@mapNotNull null
            val value = table.number(row, "value") ?: return@mapNotNull null
            if (date < fromDate) null else DividendPayment(date, value)
        }.sortedByDescending { it.recordDate }
    }

    // ────────────────────────────── разбор ISS ───────────────────────────────

    private class Table(val columns: List<String>, val rows: List<JsonArray>) {
        private val index = columns.withIndex().associate { (position, name) -> name to position }

        private fun cell(row: JsonArray, column: String): JsonPrimitive? {
            val position = index[column] ?: return null
            return row.getOrNull(position) as? JsonPrimitive
        }

        fun string(row: JsonArray, column: String): String? =
            cell(row, column)?.takeIf { it.isString || it.intOrNull != null }
                ?.content?.takeIf { it.isNotBlank() && it != "null" }

        fun number(row: JsonArray, column: String): Double? = cell(row, column)?.doubleOrNull
    }

    private suspend fun fetchTable(url: String, name: String): Table? = withContext(Dispatchers.IO) {
        val body = request(url) ?: return@withContext null
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return@withContext null
        val block = root[name] as? JsonObject ?: return@withContext null
        val columns = (block["columns"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: return@withContext null
        val rows = (block["data"] as? JsonArray)?.filterIsInstance<JsonArray>().orEmpty()
        Table(columns, rows)
    }

    private fun request(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("Мосбиржа ответила ${response.code}")
            return response.body?.string()
        }
    }

    private companion object {
        const val ISS = "https://iss.moex.com/iss"
        const val PAGE = 100
        const val MAX_ROWS = 4000
        const val USER_AGENT = "InvestWatch Android"
    }
}
