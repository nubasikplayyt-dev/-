package app.portfolio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParseWatchlistTest {

    private val watchlist = Parse.watchlist(Fixtures.read("config/watchlist.json"))

    @Test
    fun `читает watchlist репозитория данных`() {
        assertEquals(1, watchlist.sections.size)
        val section = watchlist.sections.single()
        assertEquals("ru", section.id)
        assertEquals("Российские акции", section.title)
        assertEquals("RU", section.country)
        assertEquals("RUB", section.currency)
        assertEquals(5.0, section.yieldFloorPct)
        assertEquals(Fixtures.assetIds, section.assets.map { it.id })
    }

    @Test
    fun `сохраняет поля бумаги, которые нужны экрану`() {
        val sibn = watchlist.find("SIBN")!!.second
        assertEquals("Газпром нефть", sibn.name)
        assertTrue(sibn.isCommodity)
        assertEquals("oil", sibn.commodity)
        assertEquals("logos/SIBN.svg", sibn.logo)

        val parus = watchlist.find("PARUS-SBL")!!.second
        assertEquals("real_estate_fund", parus.type)
        assertTrue(parus.qualifiedOnly)
        assertEquals("https://investfunds.ru/funds/7279/", parus.disclosureUrl)
    }

    @Test
    fun `ir_url со значением null не ломает разбор`() {
        assertNull(watchlist.find("SBER")!!.second.irUrl)
    }

    @Test
    fun `у ЗПИФ крупно показывается id, а не ISIN из поля ticker`() {
        assertEquals("SBER", watchlist.find("SBER")!!.second.displayTicker)
        assertEquals("PARUS-SBL", watchlist.find("PARUS-SBL")!!.second.displayTicker)
        assertEquals("PA", watchlist.find("PARUS-SBL")!!.second.initials)
    }

    // Главное обещание README: новая бумага подхватывается без правок кода.
    @Test
    fun `новая бумага в assets появляется сама`() {
        val text = Fixtures.read("config/watchlist.json").replace(
            """        {
          "id": "SBER",""",
            """        {
          "id": "MOEX",
          "ticker": "MOEX",
          "name": "Московская биржа",
          "type": "stock",
          "isin": "RU000A0JR4A1",
          "is_commodity": false,
          "logo": "logos/MOEX.svg",
          "ir_url": null,
          "disclosure_url": null
        },
        {
          "id": "SBER",""",
        )

        val updated = Parse.watchlist(text)

        assertEquals(5, updated.sections.single().assets.size)
        assertEquals("Московская биржа", updated.find("MOEX")!!.second.name)
    }

    // Второе обещание: новая страна — это только новый объект в sections.
    @Test
    fun `новый раздел другой страны появляется сам`() {
        val updated = Parse.watchlist(
            """
            {
              "sections": [
                {"id": "ru", "title": "Российские акции", "country": "RU", "assets": []},
                {"id": "us", "title": "Акции США", "country": "US", "currency": "USD",
                 "yield_floor_pct": 1.0,
                 "assets": [{"id": "KO", "ticker": "KO", "name": "Coca-Cola"}]}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("ru", "us"), updated.sections.map { it.id })
        assertEquals("🇺🇸", Flags.of(updated.sections[1].country))
        assertEquals("🇷🇺", Flags.of(updated.sections[0].country))
    }

    @Test
    fun `бумага по минимальному шаблону из README репозитория читается`() {
        val updated = Parse.watchlist(
            """{"sections":[{"id":"ru","title":"Российские акции","country":"RU",
               "assets":[{"id":"ROSN","ticker":"ROSN","name":"Роснефть"}]}]}""",
        )

        val asset = updated.find("ROSN")!!.second
        assertNull(asset.logo)
        assertEquals(false, asset.isCommodity)
    }

    @Test
    fun `id и name достраиваются из тикера`() {
        val updated = Parse.watchlist(
            """{"sections":[{"id":"ru","title":"Акции","country":"RU","assets":[{"ticker":"TATN"}]}]}""",
        )

        val asset = updated.sections.single().assets.single()
        assertEquals("TATN", asset.id)
        assertEquals("TATN", asset.name)
    }

    @Test
    fun `битая бумага пропускается, остальные в разделе остаются`() {
        val text = Fixtures.read("config/watchlist.json").replace(
            """      "assets": [
        {
          "id": "SBER",""",
            """      "assets": [
        {"name": "Без тикера и без id"},
        {
          "id": "SBER",""",
        )

        assertEquals(Fixtures.assetIds, Parse.watchlist(text).sections.single().assets.map { it.id })
    }

    @Test
    fun `поддержана раскладка с общим массивом assets верхнего уровня`() {
        val updated = Parse.watchlist(
            """
            {
              "sections": [
                {"id": "ru", "title": "Российские акции", "country": "RU"},
                {"id": "us", "title": "Акции США", "country": "US"}
              ],
              "assets": [
                {"id": "SBER", "ticker": "SBER", "name": "Сбербанк", "section": "ru"},
                {"id": "KO", "ticker": "KO", "name": "Coca-Cola", "section_id": "us"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("SBER"), updated.sections[0].assets.map { it.id })
        assertEquals(listOf("KO"), updated.sections[1].assets.map { it.id })
    }

    @Test
    fun `неизвестный код страны даёт нейтральный значок`() {
        assertEquals("🌐", Flags.of("ZZZZ"))
        assertEquals("🌐", Flags.of(""))
    }
}
