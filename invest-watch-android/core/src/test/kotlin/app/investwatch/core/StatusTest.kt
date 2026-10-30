package app.investwatch.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusTest {

    @Test
    fun `читает вложенные status_verdict и status_updated`() {
        val status = Parse.status(Fixtures.repo("data/SBER/status.json"))

        assertEquals(Verdict.BUY_ZONE, status.verdict)
        assertEquals("2026-10-30", status.updated)
        assertEquals("SBER", status.assetId)
    }

    @Test
    fun `читает watch массивом строк`() {
        assertEquals(
            listOf(
                "Резервы и стоимость риска во 2П 2026 и 2027",
                "Рекомендация дивидендов за 2026 г. (весна 2027)",
            ),
            Parse.status(Fixtures.repo("data/SBER/status.json")).watch,
        )
    }

    @Test
    fun `читает критерии объектом, в том числе без note`() {
        val status = Parse.status(Fixtures.repo("data/SBER/status.json"))

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
        val status = Parse.status(Fixtures.repo("data/PARUS-SBL/status.json"))

        assertEquals(Verdict.DO_NOT_BUY, status.verdict)
        assertEquals(listOf("C1", "C2", "C3", "C6", "P1", "P3"), status.criteria.map { it.code })
        assertEquals(CriterionState.FAIL, status.criteria.single { it.code == "P3" }.state)
    }

    @Test
    fun `поля metrics в репозитории ещё нет, блок будет скрыт`() {
        for (assetId in Fixtures.repoAssetIds) {
            assertEquals(emptyList(), Parse.status(Fixtures.repo("data/$assetId/status.json")).metrics, assetId)
        }
    }

    @Test
    fun `метрики из моков читаются вместе с датой`() {
        val status = Parse.status(Fixtures.mock("data/NBU/status.json"))

        assertEquals("2026-10-29", status.metricsDate)
        assertEquals(
            listOf("price_usd" to "61,4", "yield_pct" to "3,8", "payout_pct" to "62",
                "net_debt_to_ebitda" to "3,1"),
            status.metrics.map { it.name to it.value },
        )
    }

    @Test
    fun `null в метриках отбрасывается`() {
        val status = Parse.status(Fixtures.mock("data/2899/status.json"))

        assertFalse(status.metrics.any { it.name == "nav_rub" })
    }

    @Test
    fun `статусы всех бумаг укладываются в шкалу CRITERIA_md`() {
        for (assetId in Fixtures.repoAssetIds) {
            val status = Parse.status(Fixtures.repo("data/$assetId/status.json"))
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
            listOf("C1" to CriterionState.WARN, "C2" to CriterionState.NOT_APPLICABLE,
                "P1" to CriterionState.PASS),
            status.criteria.map { it.code to it.state },
        )
    }

    @Test
    fun `неизвестный вердикт не роняет экран`() {
        assertEquals(Verdict.WATCH, Parse.status("""{"status":{"verdict":"новое"}}""").verdict)
    }
}

class OverridesTest {

    private val overrides = Parse.overrides(Fixtures.repo("config/overrides.json"))

    @Test
    fun `читает решения владельца из репозитория`() {
        assertEquals(setOf("PHOR"), overrides.keys)
        val phor = overrides.getValue("PHOR").single()
        assertEquals("C6", phor.code)
        assertEquals(CriterionState.WARN, phor.state)
        assertTrue(phor.fromOverride)
        assertTrue(phor.note.contains("прощена владельцем"))
    }

    // В AGENT.md сказано прямо: решения владельца сильнее оценки агента.
    @Test
    fun `решение владельца перекрывает оценку агента`() {
        val status = Parse.status(Fixtures.repo("data/PHOR/status.json"))
        val before = status.criteria.single { it.code == "C6" }

        val after = Overrides.apply(status, overrides.getValue("PHOR"))
            .criteria.single { it.code == "C6" }

        assertFalse(before.fromOverride)
        assertTrue(after.fromOverride)
        assertTrue(after.note.contains("прощена владельцем"))
    }

    @Test
    fun `порядок критериев после наложения сохраняется`() {
        val status = Parse.status(Fixtures.repo("data/PHOR/status.json"))

        val codes = Overrides.apply(status, overrides.getValue("PHOR")).criteria.map { it.code }

        assertEquals(codes.sortedWith(compareBy({ it.first() }, { it.drop(1).toInt() })), codes)
    }

    @Test
    fun `решение по критерию, которого у агента не было, добавляется`() {
        val result = Overrides.apply(
            emptyList(),
            listOf(Criterion("P3", CriterionState.FAIL, "решение владельца", fromOverride = true)),
        )

        assertEquals(1, result.size)
        assertEquals("P3", result.single().code)
    }

    @Test
    fun `пустая пометка владельца не стирает пояснение агента`() {
        val result = Overrides.apply(
            listOf(Criterion("C6", CriterionState.PASS, "пояснение агента")),
            listOf(Criterion("C6", CriterionState.FAIL, "", fromOverride = true)),
        )

        assertEquals(CriterionState.FAIL, result.single().state)
        assertEquals("пояснение агента", result.single().note)
    }

    @Test
    fun `без overrides критерии не меняются`() {
        val criteria = listOf(Criterion("C1", CriterionState.PASS, "как есть"))

        assertEquals(criteria, Overrides.apply(criteria, emptyList()))
    }
}

class CriteriaMarkdownTest {

    private val help = Criteria.parse(Fixtures.repo("CRITERIA.md"))

    // В репозитории критерии лежат таблицами, а не заголовками.
    @Test
    fun `достаёт все одиннадцать кодов из CRITERIA_md`() {
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
        assertFalse(help.getValue("P1").title.contains("**"))
    }

    @Test
    fun `понимает и разметку заголовками`() {
        val parsed = Criteria.parse("### C1. Про прибыль\nтело\n\n### **P2** — Ликвидность\nдругое")

        assertEquals("Про прибыль", parsed.getValue("C1").title)
        assertEquals("Ликвидность", parsed.getValue("P2").title)
        assertEquals("другое", parsed.getValue("P2").text)
    }

    @Test
    fun `строка заголовка таблицы критерием не считается`() {
        assertEquals(emptyMap(), Criteria.parse("| Код | Критерий | Что проверять |\n|---|---|---|"))
    }

    @Test
    fun `у каждого критерия из status есть расшифровка`() {
        for (assetId in Fixtures.repoAssetIds) {
            for (criterion in Parse.status(Fixtures.repo("data/$assetId/status.json")).criteria) {
                assertContains(help.keys, criterion.code, "$assetId / ${criterion.code}")
            }
        }
    }
}
