package app.portfolio.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParseEventsTest {

    private val schema =
        Json.parseToJsonElement(Fixtures.read("schema/event.schema.json")).jsonObject

    private fun enumOf(property: String): Set<String> =
        schema["properties"]!!.jsonObject[property]!!.jsonObject["enum"]!!.jsonArray
            .map { it.jsonPrimitive.content }.toSet()

    // ─────────────────── сверка с schema/event.schema.json ───────────────────

    @Test
    fun `типы событий совпадают со схемой репозитория`() {
        assertEquals(enumOf("type"), EventTypes.all)
    }

    @Test
    fun `уровни важности совпадают со схемой репозитория`() {
        assertEquals(enumOf("importance"), Importance.entries.map { it.raw }.toSet())
    }

    @Test
    fun `вердикты совпадают со схемой репозитория`() {
        assertEquals(enumOf("verdict"), Verdict.entries.map { it.raw }.toSet())
    }

    @Test
    fun `все обязательные поля схемы читаются разбором`() {
        val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(
            listOf("id", "asset_id", "date", "type", "importance", "title", "summary",
                "criteria", "verdict", "sources", "notify"),
            required,
        )

        val line = """
            {"id":"SBER-20261028-x","asset_id":"SBER","date":"2026-10-28",
             "type":"financial_report","importance":"high","title":"Отчёт","summary":"Сводка.",
             "criteria":{"C6":{"status":"pass","note":"норма"}},"verdict":"buy_zone",
             "sources":[{"title":"Источник","url":"https://example.com"}],"notify":true}
        """.trimIndent().replace("\n", "")

        val event = Parse.eventsJsonl(line).events.single()

        assertEquals("SBER-20261028-x", event.id)
        assertEquals(Importance.HIGH, event.importance)
        assertEquals(Verdict.BUY_ZONE, event.verdict)
        assertEquals(listOf(Criterion("C6", CriterionState.PASS, "норма")), event.criteria)
        assertEquals(listOf(Source("Источник", "https://example.com")), event.sources)
        assertTrue(event.notify)
    }

    // ───────────────────────────── events.jsonl ──────────────────────────────

    @Test
    fun `читает events_jsonl и пропускает строки-комментарии`() {
        val parsed = Parse.eventsJsonl(Fixtures.read("data/SBER/events.jsonl"), "SBER")

        assertEquals(emptyList(), parsed.skippedLines)
        assertEquals(3, parsed.events.size)
        assertTrue(parsed.events.all { it.assetId == "SBER" })
    }

    @Test
    fun `события отдаются новыми сверху`() {
        val parsed = Parse.eventsJsonl(Fixtures.read("data/SBER/events.jsonl"), "SBER")

        assertEquals(listOf("2026-10-28", "2026-09-12", "2026-08-04"), parsed.events.map { it.date })
    }

    @Test
    fun `числа в metrics форматируются по-русски, null отбрасывается`() {
        val line = """{"id":"e1","asset_id":"SBER","date":"2026-10-28","type":"dividend",
            "importance":"critical","title":"Дивиденд",
            "metrics":{"dividend_rub":37.64,"yield_pct":12.7,"net_debt_bn_rub":null}}"""
            .replace("\n", "")

        assertEquals(
            listOf(Metric("dividend_rub", "37,64"), Metric("yield_pct", "12,7")),
            Parse.eventsJsonl(line).events.single().metrics,
        )
    }

    @Test
    fun `criteria события сортируются C перед P`() {
        val line = """{"id":"e2","asset_id":"SIBN","date":"2026-09-30","type":"dividend",
            "importance":"critical","title":"Дивиденд",
            "criteria":{"P2":{"status":"warn","note":"фаза цикла"},"C6":{"status":"pass"},
            "P1":{"status":"pass","note":"8,4%"}}}""".replace("\n", "")

        assertEquals(
            listOf(
                Criterion("C6", CriterionState.PASS, ""),
                Criterion("P1", CriterionState.PASS, "8,4%"),
                Criterion("P2", CriterionState.WARN, "фаза цикла"),
            ),
            Parse.eventsJsonl(line).events.single().criteria,
        )
    }

    @Test
    fun `sources принимаются строками и объектами, битые ссылки отбрасываются`() {
        val line = """{"id":"e3","asset_id":"SBER","date":"2026-10-28","type":"other",
            "importance":"medium","title":"Новость",
            "sources":["https://www.e-disclosure.ru/a",{"url":"https://b.example/c","title":"Б"},
            "не ссылка"]}""".replace("\n", "")

        assertEquals(
            listOf(
                Source("e-disclosure.ru", "https://www.e-disclosure.ru/a"),
                Source("Б", "https://b.example/c"),
            ),
            Parse.eventsJsonl(line).events.single().sources,
        )
    }

    @Test
    fun `битая строка пропускается, остальные читаются`() {
        val text = listOf(
            "{ это не json",
            """{"id":"ok","asset_id":"SBER","date":"2026-10-01","type":"debt",
               "importance":"medium","title":"Живое событие"}""".replace("\n", ""),
            """{"id":"нет-заголовка","asset_id":"SBER","date":"2026-10-02","type":"debt",
               "importance":"medium"}""".replace("\n", ""),
        ).joinToString("\n")

        val parsed = Parse.eventsJsonl(text)

        assertEquals(listOf("ok"), parsed.events.map { it.id })
        assertEquals(listOf(1, 3), parsed.skippedLines)
    }

    @Test
    fun `пустой events_jsonl — это не ошибка`() {
        assertEquals(emptyList(), Parse.eventsJsonl("").events)
        assertEquals(emptyList(), Parse.eventsJsonl("\n\n").skippedLines)
    }

    @Test
    fun `поле detected_at читается, неизвестные поля не мешают`() {
        val line = """{"id":"e4","asset_id":"SBER","date":"2026-10-28","type":"financial_report",
            "importance":"high","title":"Отчёт","detected_at":"2026-10-28T19:05:00+03:00",
            "custom_field":{"foo":"bar"}}""".replace("\n", "")

        assertEquals("2026-10-28T19:05:00+03:00", Parse.eventsJsonl(line).events.single().detectedAt)
    }

    @Test
    fun `low вне схемы показывается как medium`() {
        val line = """{"id":"e5","asset_id":"SBER","date":"2026-10-28","type":"other",
            "importance":"low","title":"Мелочь"}""".replace("\n", "")

        assertEquals(Importance.MEDIUM, Parse.eventsJsonl(line).events.single().importance)
    }

    @Test
    fun `событие без вердикта читается, вердикт остаётся пустым`() {
        val line = """{"id":"e6","asset_id":"SBER","date":"2026-10-28","type":"other",
            "importance":"medium","title":"Без вердикта"}""".replace("\n", "")

        assertNull(Parse.eventsJsonl(line).events.single().verdict)
    }

    // ───────────────────────────────── feed.json ─────────────────────────────

    @Test
    fun `читает ленту из поля items`() {
        val parsed = Parse.feed(Fixtures.read("feed/feed.json"))

        assertEquals(emptyList(), parsed.skippedLines)
        assertEquals(9, parsed.events.size)
        assertEquals("2026-10-30", parsed.events.first().date)
    }

    @Test
    fun `пустая лента репозитория читается без ошибок`() {
        val parsed = Parse.feed("""{"generated_at":"2026-10-30T13:30:00+03:00","items":[]}""")

        assertEquals(emptyList(), parsed.events)
    }

    @Test
    fun `лента, записанная массивом, тоже читается`() {
        val items = (Json.parseToJsonElement(Fixtures.read("feed/feed.json")) as JsonObject)["items"]
            as JsonArray

        assertEquals(9, Parse.feed(items.toString()).events.size)
    }

    @Test
    fun `все типы событий в ленте известны приложению`() {
        for (event in Parse.feed(Fixtures.read("feed/feed.json")).events) {
            assertContains(EventTypes.all, event.type, "тип ${event.type}")
        }
    }
}
