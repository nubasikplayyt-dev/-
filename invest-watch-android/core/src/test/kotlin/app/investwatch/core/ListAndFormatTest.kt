package app.investwatch.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetListTest {

    private val watchlist = Fixtures.mockWatchlist

    private fun card(
        assetId: String,
        verdict: Verdict? = null,
        yieldPct: Double? = null,
    ): AssetCard {
        val asset = watchlist.asset(assetId)!!
        return AssetCard(
            asset = asset,
            market = watchlist.market(asset.marketId),
            kind = watchlist.kind(asset.kindId),
            status = verdict?.let { Status(assetId = assetId, verdict = it, updated = "2026-10-30") },
            quote = null,
            yieldPct = yieldPct,
        )
    }

    private val cards = listOf(
        card("SBER", Verdict.BUY_ZONE, 12.7),
        card("SIBN", Verdict.HOLD, 8.4),
        card("PHOR", Verdict.WATCH, 0.0),
        card("PARUS-SBL", Verdict.DO_NOT_BUY, 10.7),
        card("NBU", Verdict.BUY_ZONE, 3.8),
    )

    @Test
    fun `поиск по тикеру и названию`() {
        assertEquals(
            listOf("SBER"),
            AssetList.filter(cards, AssetFilter(query = "сбер")).map { it.asset.id },
        )
        assertEquals(
            listOf("SIBN"),
            AssetList.filter(cards, AssetFilter(query = "SIBN")).map { it.asset.id },
        )
        assertEquals(cards.size, AssetList.filter(cards, AssetFilter(query = "  ")).size)
    }

    @Test
    fun `поиск находит ЗПИФ по его id, а не только по ISIN`() {
        assertEquals(
            listOf("PARUS-SBL"),
            AssetList.filter(cards, AssetFilter(query = "parus")).map { it.asset.id },
        )
    }

    @Test
    fun `фильтр по вердикту`() {
        val filtered = AssetList.filter(cards, AssetFilter(verdicts = setOf(Verdict.BUY_ZONE)))

        assertEquals(listOf("SBER", "NBU"), filtered.map { it.asset.id })
    }

    @Test
    fun `фильтр по порогу дивдоходности рынка`() {
        // Порог России 5%, США 1%: SBER 12,7 и PARUS 10,7 проходят, PHOR 0 — нет.
        val filtered = AssetList.filter(cards, AssetFilter(passesYieldFloorOnly = true))

        assertEquals(listOf("SBER", "SIBN", "PARUS-SBL", "NBU"), filtered.map { it.asset.id })
    }

    @Test
    fun `порог без известной доходности не проходит и не ломается`() {
        val unknown = card("SBER", Verdict.BUY_ZONE, yieldPct = null)

        assertNull(unknown.passesYieldFloor)
        assertEquals(emptyList(), AssetList.filter(listOf(unknown), AssetFilter(passesYieldFloorOnly = true)))
    }

    @Test
    fun `фильтр по сырьевым`() {
        assertEquals(
            listOf("SIBN", "PHOR"),
            AssetList.filter(cards, AssetFilter(commodity = CommodityFilter.ONLY)).map { it.asset.id },
        )
        assertEquals(
            listOf("SBER", "PARUS-SBL", "NBU"),
            AssetList.filter(cards, AssetFilter(commodity = CommodityFilter.EXCLUDE)).map { it.asset.id },
        )
    }

    @Test
    fun `фильтр по составу индекса`() {
        val filtered = AssetList.filter(cards, AssetFilter(inUniverseOnly = true))

        // in_universe пока проставлен только у моковых бумаг.
        assertEquals(listOf("NBU"), filtered.map { it.asset.id })
    }

    @Test
    fun `сортировка по дивдоходности, бумаги без неё внизу`() {
        val withUnknown = cards + card("HRF", Verdict.DO_NOT_BUY, null)

        val sorted = AssetList.sort(withUnknown, AssetSort.YIELD).map { it.asset.id }

        assertEquals(listOf("SBER", "PARUS-SBL", "SIBN", "NBU", "PHOR", "HRF"), sorted)
    }

    @Test
    fun `сортировка по вердикту — зона покупки первой`() {
        val sorted = AssetList.sort(cards, AssetSort.VERDICT).map { it.verdict }

        assertEquals(
            listOf(Verdict.BUY_ZONE, Verdict.BUY_ZONE, Verdict.HOLD, Verdict.WATCH, Verdict.DO_NOT_BUY),
            sorted,
        )
    }

    @Test
    fun `сортировка по названию и по дате оценки`() {
        assertEquals(
            cards.map { it.asset.name }.sortedBy { it.lowercase() },
            AssetList.sort(cards, AssetSort.NAME).map { it.asset.name },
        )

        val stale = card("HRF", Verdict.HOLD).copy(
            status = Status(assetId = "HRF", verdict = Verdict.HOLD, updated = "2020-01-01"),
        )
        val sorted = AssetList.sort(listOf(stale) + cards, AssetSort.UPDATED)

        assertEquals("HRF", sorted.last().asset.id)
    }

    @Test
    fun `бумага без status помечается как ещё не проверенная`() {
        assertTrue(card("HRF").notChecked)
        assertFalse(card("HRF", Verdict.HOLD).notChecked)
    }

    @Test
    fun `пустой фильтр ничего не отбрасывает`() {
        assertTrue(AssetFilter().isEmpty)
        assertEquals(cards.size, AssetList.apply(cards, AssetFilter(), AssetSort.NAME).size)
    }
}

class DividendsTest {

    @Test
    fun `в репозитории дивиденды пока пустые`() {
        for (assetId in Fixtures.repoAssetIds) {
            val dividends = Parse.dividends(Fixtures.repo("data/$assetId/dividends.json"), assetId)
            assertEquals(assetId, dividends.assetId)
            assertTrue(dividends.isEmpty, assetId)
        }
    }

    @Test
    fun `читает суммы по годам и выплаты`() {
        val dividends = Parse.dividends(Fixtures.mock("data/NBU/dividends.json"), "NBU")

        assertEquals("USD", dividends.currency)
        assertEquals(10, dividends.byYear.size)
        assertEquals(2017, dividends.byYear.first().year)
        assertEquals(2.33, dividends.byYear.last().total)
        assertEquals("2026-08-14", dividends.payments.first().recordDate)
    }

    @Test
    fun `годы идут по возрастанию, выплаты — новыми сверху`() {
        val dividends = Parse.dividends(Fixtures.mock("data/NBU/dividends.json"), "NBU")

        assertEquals(dividends.byYear.map { it.year }.sorted(), dividends.byYear.map { it.year })
        assertEquals(
            dividends.payments.map { it.recordDate }.sortedDescending(),
            dividends.payments.map { it.recordDate },
        )
    }

    @Test
    fun `битая запись выплаты пропускается`() {
        val dividends = Parse.dividends(
            """{"asset_id":"X","payments":[{"record_date":"2026-01-01"},
               {"record_date":"2026-02-01","amount":1.5}]}""".replace("\n", ""),
        )

        assertEquals(1, dividends.payments.size)
        assertEquals(1.5, dividends.payments.single().amount)
    }

    @Test
    fun `доходность выплаты считается по цене на дату отсечки`() {
        val candles = listOf(
            PriceCandle("2026-08-12", 60.0),
            PriceCandle("2026-08-14", 59.0),
            PriceCandle("2026-08-20", 61.0),
        )
        val payment = DividendPayment("2026-08-14", 0.59)

        val result = Prices.paymentYield(payment, candles)!!

        assertEquals(1.0, result, 0.01)
    }

    @Test
    fun `без цены доходность выплаты не выдумывается`() {
        assertNull(Prices.paymentYield(DividendPayment("2026-08-14", 0.59), emptyList()))
    }

    @Test
    fun `дивдоходность за 12 месяцев считается от выплат и цены`() {
        val dividends = Parse.dividends(Fixtures.mock("data/NBU/dividends.json"), "NBU")

        val result = Prices.trailingYieldPct(dividends, price = 61.40, today = "2026-10-30")!!

        // 0,59 + 0,58 + 0,58 + 0,56 = 2,31 к цене 61,40 — это 3,76%.
        assertEquals(3.76, result, 0.02)
    }

    @Test
    fun `без цены дивдоходность не считается`() {
        val dividends = Parse.dividends(Fixtures.mock("data/NBU/dividends.json"), "NBU")

        assertNull(Prices.trailingYieldPct(dividends, price = null, today = "2026-10-30"))
    }
}

class PricesAndFormatTest {

    private val candles = (1..40).map {
        PriceCandle(Dates.minusDays("2026-10-30", 40 - it)!!, 100.0 + it)
    }

    @Test
    fun `период обрезает историю`() {
        // 40 свечей подряд по 30 октября; в окно 31 день попадают 32 из них.
        assertEquals(32, Prices.slice(candles, ChartPeriod.MONTH, "2026-10-30").size)
        assertEquals(candles.size, Prices.slice(candles, ChartPeriod.ALL, "2026-10-30").size)
    }

    @Test
    fun `изменение за период считается от первой свечи к последней`() {
        val result = Prices.changePct(listOf(PriceCandle("2026-01-01", 100.0), PriceCandle("2026-02-01", 110.0)))!!

        assertEquals(10.0, result, 0.001)
    }

    @Test
    fun `изменение не считается на пустой или нулевой истории`() {
        assertNull(Prices.changePct(emptyList()))
        assertNull(Prices.changePct(listOf(PriceCandle("2026-01-01", 0.0), PriceCandle("2026-02-01", 5.0))))
    }

    @Test
    fun `цена на дату берёт ближайшую свечу не позже неё`() {
        val series = listOf(PriceCandle("2026-01-05", 10.0), PriceCandle("2026-01-09", 12.0))

        assertEquals(10.0, Prices.priceOn(series, "2026-01-07"))
        assertEquals(12.0, Prices.priceOn(series, "2026-01-09"))
        assertNull(Prices.priceOn(series, "2026-01-01"))
    }

    @Test
    fun `даты в формате 30 окт 2026`() {
        assertEquals("30 окт 2026", Format.date("2026-10-30"))
        assertEquals("5 янв 2026", Format.date("2026-01-05T12:00:00Z"))
        assertEquals("когда-нибудь", Format.date("когда-нибудь"))
        assertEquals("—", Format.date(null))
    }

    @Test
    fun `числа по-русски с запятой`() {
        assertEquals("37,64", Format.number(37.64))
        assertEquals("1 234 567", Format.number(1234567.0, 0).replace(' ', ' ').replace(' ', ' '))
        assertEquals("12,7%", Format.percent(12.7))
        assertEquals("+3,8%", Format.percent(3.8, withSign = true))
    }

    @Test
    fun `знак валюты подставляется по коду`() {
        assertEquals("₽", Format.currencySign("RUB"))
        assertEquals("HK$", Format.currencySign("HKD"))
        assertNull(Format.currencySign(null))
    }

    @Test
    fun `подписи метрик переводятся, единицы приписываются`() {
        assertEquals(Metrics.Display("Дивиденд", "37,64 ₽"), Metrics.describe("dividend_rub", "37,64"))
        assertEquals(Metrics.Display("Дивдоходность", "12,7%"), Metrics.describe("yield_pct", "12,7"))
        assertEquals(Metrics.Display("ЧД / EBITDA", "2,1"), Metrics.describe("net_debt_to_ebitda", "2,1"))
        assertEquals(Metrics.Display("Some new metric", "5%"), Metrics.describe("some_new_metric_pct", "5"))
    }

    @Test
    fun `тема читается из настроек и переключается`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("что-то не то"))
        assertEquals(ThemeMode.DARK, ThemeMode.from("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.from(ThemeMode.LIGHT.raw))
        assertEquals(
            listOf("Системная", "Светлая", "Тёмная"),
            listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK).map { it.label },
        )
    }
}

class SymbolsTest {

    private val watchlist = Fixtures.mockWatchlist

    @Test
    fun `российские бумаги адресуются по secid Мосбиржи`() {
        assertEquals("SBER", Symbols.moex(watchlist.asset("SBER")!!))
        // У ЗПИФ secid — это ISIN, он же лежит в ticker.
        assertEquals("RU000A104172", Symbols.moex(watchlist.asset("PARUS-SBL")!!))
    }

    @Test
    fun `российским бумагам ключ источника цен не нужен`() {
        assertFalse(Symbols.needsPriceApiKey(watchlist.asset("SBER")!!))
        assertTrue(Symbols.needsPriceApiKey(watchlist.asset("NBU")!!))
    }

    @Test
    fun `символ EODHD собирается из тикера и биржи`() {
        assertEquals("NBU.US", Symbols.eodhd(watchlist.asset("NBU")!!))
        assertEquals("HRF.US", Symbols.eodhd(watchlist.asset("HRF")!!))
        assertEquals("2899.HK", Symbols.eodhd(watchlist.asset("2899")!!))
    }

    @Test
    fun `у российской бумаги символа EODHD нет`() {
        assertNull(Symbols.eodhd(watchlist.asset("SBER")!!))
    }

    @Test
    fun `без поля exchange китайская бумага различается по номеру`() {
        fun asset(ticker: String) = Asset(
            id = ticker, ticker = ticker, name = ticker, marketId = "CN", kindId = "stock",
        )

        // Нечисловой тикер на рынке CN считаем гонконгским.
        assertEquals("TCEHY.HK", Symbols.eodhd(asset("TCEHY")))
        assertEquals("600519.SHG", Symbols.eodhd(asset("600519")))
        assertEquals("000001.SHE", Symbols.eodhd(asset("000001")))
        assertEquals("300750.SHE", Symbols.eodhd(asset("300750")))
        assertEquals("0700.HK", Symbols.eodhd(asset("0700")))
    }
}
