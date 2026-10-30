package app.portfolio.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusAndCriteriaTest {

    @Test
    fun `читает вложенные status_verdict и status_updated`() {
        val status = Parse.status(Fixtures.read("data/SBER/status.json"))

        assertEquals(Verdict.BUY_ZONE, status.verdict)
        assertEquals("2026-10-30", status.updated)
        assertEquals("SBER", status.assetId)
    }

    @Test
    fun `читает watch массивом строк`() {
        val status = Parse.status(Fixtures.read("data/SBER/status.json"))

        assertEquals(
            listOf(
                "Резервы и стоимость риска во 2П 2026 и 2027",
                "Рекомендация дивидендов за 2026 г. (весна 2027)",
            ),
            status.watch,
        )
    }

    @Test
    fun `читает критерии объектом, в том числе без note`() {
        val status = Parse.status(Fixtures.read("data/SBER/status.json"))

        assertEquals(
            listOf("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "P1", "P2"),
            status.criteria.map { it.code },
        )
        assertEquals(
            Criterion("C7", CriterionState.NOT_APPLICABLE, ""),
            status.criteria.single { it.code == "C7" },
        )
    }

    @Test
    fun `неполный набор критериев читается как есть`() {
        val status = Parse.status(Fixtures.read("data/PARUS-SBL/status.json"))

        assertEquals(Verdict.DO_NOT_BUY, status.verdict)
        assertEquals(listOf("C1", "C2", "C3", "C6", "P1", "P3"), status.criteria.map { it.code })
        assertEquals(CriterionState.FAIL, status.criteria.single { it.code == "P3" }.state)
    }

    @Test
    fun `статусы всех бумаг укладываются в шкалу CRITERIA_md`() {
        for (assetId in Fixtures.assetIds) {
            val status = Parse.status(Fixtures.read("data/$assetId/status.json"))
            assertTrue(status.criteria.isNotEmpty(), "у $assetId нет критериев")
            for (criterion in status.criteria) {
                assertContains(CriterionState.entries, criterion.state, "$assetId / ${criterion.code}")
            }
        }
    }

    @Test
    fun `плоская раскладка без вложенного status тоже читается`() {
        val status = Parse.status(
            """
            {"verdict":"sell_candidate","updated_at":"2026-10-30",
             "watch":"Одна строка вместо массива",
             "criteria":[{"code":"P1","status":"OK","note":"норма"},
                         {"code":"C2","state":"N/A"},
                         {"code":"C1","result":"warning","comment":"дорого"}]}
            """.trimIndent(),
        )

        assertEquals(Verdict.SELL_CANDIDATE, status.verdict)
        assertEquals(listOf("Одна строка вместо массива"), status.watch)
        assertEquals(
            listOf(
                "C1" to CriterionState.WARN,
                "C2" to CriterionState.NOT_APPLICABLE,
                "P1" to CriterionState.PASS,
            ),
            status.criteria.map { it.code to it.state },
        )
        assertEquals("дорого", status.criteria.first().note)
    }

    @Test
    fun `C-критерии идут перед P-критериями, внутри группы по номеру`() {
        val codes = listOf("P2", "C10", "C2", "P1", "C1")
            .map { Criterion(it, CriterionState.PASS) }
            .sortedWith(Parse.CRITERIA_ORDER)

        assertEquals(listOf("C1", "C2", "C10", "P1", "P2"), codes.map { it.code })
    }

    @Test
    fun `неизвестный вердикт не роняет экран`() {
        assertEquals(Verdict.WATCH, Parse.status("""{"status":{"verdict":"что-то новое"}}""").verdict)
    }
}

class CriteriaMarkdownTest {

    private val help = Criteria.parse(Fixtures.read("CRITERIA.md"))

    // В репозитории данных критерии лежат таблицами, а не заголовками.
    @Test
    fun `достаёт все одиннадцать кодов из CRITERIA_md репозитория`() {
        assertEquals(
            setOf("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "P1", "P2", "P3"),
            help.keys,
        )
    }

    @Test
    fun `трёхколоночная строка даёт название и пояснение`() {
        assertEquals("Вечная тема", help.getValue("C1").title)
        assertTrue(help.getValue("C1").text.contains("нужный людям и 500 лет назад"))
        assertEquals("Экспортёр (плюс)", help.getValue("C8").title)
    }

    @Test
    fun `двухколоночная строка даёт правило целиком`() {
        assertTrue(help.getValue("P1").title.contains("дивдоходность ниже порога"))
        assertTrue(help.getValue("P3").title.contains("критерий Штернлихта"))
    }

    @Test
    fun `жирный из таблицы убирается`() {
        assertTrue(!help.getValue("P1").title.contains("**"))
    }

    @Test
    fun `понимает и разметку заголовками`() {
        val parsed = Criteria.parse(
            listOf("### C1. Про прибыль", "тело", "", "### **P2** — Ликвидность", "другое тело")
                .joinToString("\n"),
        )

        assertEquals("Про прибыль", parsed.getValue("C1").title)
        assertEquals("Ликвидность", parsed.getValue("P2").title)
        assertEquals("другое тело", parsed.getValue("P2").text)
    }

    @Test
    fun `заголовок важнее строки таблицы для того же кода`() {
        val parsed = Criteria.parse(
            listOf("| C1 | Коротко | из таблицы |", "", "## C1 — Подробно", "из заголовка")
                .joinToString("\n"),
        )

        assertEquals("Подробно", parsed.getValue("C1").title)
        assertEquals("из заголовка", parsed.getValue("C1").text)
    }

    @Test
    fun `строка заголовка таблицы критерием не считается`() {
        assertEquals(emptyMap(), Criteria.parse("| Код | Критерий | Что проверять |\n|---|---|---|"))
    }

    @Test
    fun `без CRITERIA_md словарь просто пустой`() {
        assertEquals(emptyMap(), Criteria.parse(""))
    }

    @Test
    fun `у каждого критерия из status есть расшифровка`() {
        for (assetId in Fixtures.assetIds) {
            for (criterion in Parse.status(Fixtures.read("data/$assetId/status.json")).criteria) {
                assertContains(help.keys, criterion.code, "$assetId / ${criterion.code}")
            }
        }
    }
}

class MetricsAndDatesTest {

    @Test
    fun `переводит технические ключи агента и добавляет единицы`() {
        assertEquals(
            Metrics.Display("Дивиденд", "37,64 ₽"),
            Metrics.describe(Metric("dividend_rub", "37,64")),
        )
        assertEquals(
            Metrics.Display("Доходность", "12,7%"),
            Metrics.describe(Metric("yield_pct", "12,7")),
        )
        assertEquals(
            Metrics.Display("Чистый долг", "325 млрд ₽"),
            Metrics.describe(Metric("net_debt_bn_rub", "325")),
        )
        assertEquals(
            Metrics.Display("ЧД / EBITDA", "2,1"),
            Metrics.describe(Metric("net_debt_to_ebitda", "2,1")),
        )
    }

    @Test
    fun `незнакомый ключ не теряется, а становится читаемым`() {
        assertEquals(
            Metrics.Display("Some new metric", "5%"),
            Metrics.describe(Metric("some_new_metric_pct", "5")),
        )
    }

    @Test
    fun `даты в формате 30 окт 2026`() {
        assertEquals("30 окт 2026", Dates.format("2026-10-30"))
        assertEquals("5 янв 2026", Dates.format("2026-01-05T12:00:00Z"))
    }

    @Test
    fun `непонятная дата возвращается как есть, пустая прочерком`() {
        assertEquals("когда-нибудь", Dates.format("когда-нибудь"))
        assertEquals("—", Dates.format(null))
    }

    @Test
    fun `адрес файла в репозитории данных собирается правильно`() {
        val config = RepoConfig()

        assertEquals(
            "https://raw.githubusercontent.com/nubasikplayyt-dev/invest-watch/main/config/watchlist.json",
            config.url(Paths.WATCHLIST),
        )
        assertEquals(
            "https://raw.githubusercontent.com/nubasikplayyt-dev/invest-watch/main/data/PARUS-SBL/events.jsonl",
            config.url(Paths.events("PARUS-SBL")),
        )
    }
}
