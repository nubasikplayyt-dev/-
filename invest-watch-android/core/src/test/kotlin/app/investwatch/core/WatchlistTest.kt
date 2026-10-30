package app.investwatch.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchlistTest {

    private val watchlist = Fixtures.repoWatchlist

    @Test
    fun `читает рынки репозитория с флагами и порогами`() {
        assertEquals(listOf("RU", "US", "CN"), watchlist.markets.map { it.id })

        val ru = watchlist.market("RU")!!
        assertEquals("Россия", ru.title)
        assertEquals("🇷🇺", ru.flag)
        assertEquals("RUB", ru.currency)
        assertEquals(5.0, ru.yieldFloorPct)

        assertEquals(1.0, watchlist.market("US")!!.yieldFloorPct)
        assertEquals(3.0, watchlist.market("CN")!!.yieldFloorPct)
    }

    @Test
    fun `читает типы бумаг`() {
        assertEquals(
            listOf("stock" to "Акции", "real_estate" to "Фонды недвижимости"),
            watchlist.kinds.map { it.id to it.title },
        )
    }

    @Test
    fun `читает бумаги репозитория со всеми нужными полями`() {
        assertEquals(Fixtures.repoAssetIds, watchlist.assets.map { it.id })

        val sibn = watchlist.asset("SIBN")!!
        assertEquals("Газпром нефть", sibn.name)
        assertEquals("RU", sibn.marketId)
        assertEquals("stock", sibn.kindId)
        assertEquals("gazprom-neft.ru", sibn.domain)
        assertTrue(sibn.isCommodity)
        assertEquals("oil", sibn.commodity)

        val parus = watchlist.asset("PARUS-SBL")!!
        assertEquals("real_estate", parus.kindId)
        assertTrue(parus.qualifiedOnly)
        assertEquals("parus-am.ru", parus.domain)
        assertNull(parus.irUrl)
    }

    // У ЗПИФ в ticker лежит ISIN — показывать его крупно бессмысленно.
    @Test
    fun `крупно показывается id, когда тикер совпадает с ISIN`() {
        assertEquals("SBER", watchlist.asset("SBER")!!.displayTicker)
        assertEquals("PARUS-SBL", watchlist.asset("PARUS-SBL")!!.displayTicker)
        assertEquals("PA", watchlist.asset("PARUS-SBL")!!.initials)
    }

    @Test
    fun `поля in_universe в репозитории ещё нет, фильтр по нему прячется`() {
        assertFalse(watchlist.hasUniverseFlag)
        assertTrue(watchlist.assets.all { it.inUniverse == null })
    }

    @Test
    fun `в моках in_universe есть, значит фильтр показывается`() {
        assertTrue(Fixtures.mockWatchlist.hasUniverseFlag)
    }

    // Обещание README репозитория: новый рынок = новая вкладка, без правок кода.
    @Test
    fun `новый рынок в markets даёт новую вкладку`() {
        val extra = """
            {
              "id": "JP",
              "title": "Япония",
              "flag": "🇯🇵",
              "currency": "JPY",
              "yield_floor_pct": 2.0
            },
        """.trimIndent()
        // Дописываем рынок первым элементом массива — файл остаётся настоящим.
        val text = Fixtures.repo("config/watchlist.json")
            .replaceFirst("\"markets\": [", "\"markets\": [\n$extra")

        val updated = Parse.watchlist(text)

        assertEquals(listOf("JP", "RU", "US", "CN"), updated.markets.map { it.id })
        assertEquals("🇯🇵", updated.market("JP")!!.flag)
        assertEquals(2.0, updated.market("JP")!!.yieldFloorPct)
    }

    @Test
    fun `рынок без своего флага получает флаг по коду страны`() {
        val updated = Parse.watchlist(
            """{"markets":[{"id":"DE","title":"Германия"}],"kinds":[],"assets":[]}""",
        )

        assertEquals("🇩🇪", updated.market("DE")!!.flag)
    }

    @Test
    fun `новая бумага в assets появляется сама`() {
        assertEquals(7, Fixtures.mockWatchlist.assets.size)
        assertEquals("Northbay Utilities", Fixtures.mockWatchlist.asset("NBU")!!.name)
        assertEquals("US", Fixtures.mockWatchlist.asset("NBU")!!.marketId)
        assertEquals("real_estate", Fixtures.mockWatchlist.asset("2899")!!.kindId)
    }

    @Test
    fun `новый тип бумаги не требует правок кода`() {
        val updated = Parse.watchlist(
            """
            {"markets":[{"id":"RU","title":"Россия","flag":"🇷🇺"}],
             "kinds":[{"id":"bond","title":"Облигации"}],
             "assets":[{"id":"OFZ","ticker":"OFZ","name":"ОФЗ","market":"RU","kind":"bond"}]}
            """.trimIndent(),
        )

        assertEquals("Облигации", updated.kind("bond")!!.title)
        assertEquals(listOf("OFZ"), updated.assetsOf("RU", "bond").map { it.id })
    }

    @Test
    fun `бумага без market пропускается, остальные остаются`() {
        val updated = Parse.watchlist(
            """
            {"markets":[{"id":"RU","title":"Россия"}],"kinds":[],
             "assets":[{"id":"X","ticker":"X","name":"Без рынка"},
                       {"id":"SBER","ticker":"SBER","name":"Сбербанк","market":"RU","kind":"stock"}]}
            """.trimIndent(),
        )

        assertEquals(listOf("SBER"), updated.assets.map { it.id })
    }

    @Test
    fun `разбивка по рынку и типу`() {
        assertEquals(
            listOf("SBER", "SIBN", "PHOR"),
            Fixtures.mockWatchlist.assetsOf("RU", "stock").map { it.id },
        )
        assertEquals(
            listOf("PARUS-SBL"),
            Fixtures.mockWatchlist.assetsOf("RU", "real_estate").map { it.id },
        )
        assertEquals(listOf("2899"), Fixtures.mockWatchlist.assetsOf("CN").map { it.id })
    }
}
