package app.investwatch.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Разбор файлов репозитория данных.
 *
 * Разбор терпимый: неизвестные поля не мешают, у полей разобраны синонимы,
 * а битая запись пропускается и не роняет экран — её номер возвращается
 * отдельно. Всё, что зависит от формата репозитория, живёт в этом файле.
 */
object Parse {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun element(text: String): JsonElement? =
        runCatching { json.parseToJsonElement(text) }.getOrNull()

    // ─────────────────────────── config/watchlist.json ───────────────────────

    fun watchlist(text: String): Watchlist {
        val root = element(text) as? JsonObject ?: return Watchlist(emptyList(), emptyList(), emptyList())

        val markets = (root["markets"] as? JsonArray).orEmpty().mapNotNull { raw ->
            val record = raw as? JsonObject ?: return@mapNotNull null
            val id = record.string("id") ?: return@mapNotNull null
            Market(
                id = id,
                title = record.string("title") ?: id,
                flag = record.string("flag") ?: Flags.of(id),
                currency = record.string("currency"),
                yieldFloorPct = record.double("yield_floor_pct"),
                yieldFloorNote = record.string("yield_floor_note"),
            )
        }

        val kinds = (root["kinds"] as? JsonArray).orEmpty().mapNotNull { raw ->
            val record = raw as? JsonObject ?: return@mapNotNull null
            val id = record.string("id") ?: return@mapNotNull null
            Kind(id = id, title = record.string("title") ?: id)
        }

        val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { asset(it) }

        return Watchlist(markets = markets, kinds = kinds, assets = assets)
    }

    private fun asset(raw: JsonElement): Asset? {
        val record = raw as? JsonObject ?: return null
        val ticker = record.firstString("ticker", "symbol", "secid")
        val id = record.firstString("id", "asset_id") ?: ticker ?: return null
        val market = record.firstString("market", "market_id") ?: return null

        return Asset(
            id = id,
            ticker = ticker ?: id,
            name = record.firstString("name", "title") ?: ticker ?: id,
            marketId = market,
            kindId = record.firstString("kind", "kind_id") ?: "stock",
            isin = record.string("isin"),
            type = record.string("type"),
            exchange = record.string("exchange"),
            currency = record.string("currency"),
            domain = record.string("domain"),
            logo = record.string("logo"),
            isCommodity = record.boolean("is_commodity") ?: false,
            commodity = record.string("commodity"),
            qualifiedOnly = record.boolean("qualified_only") ?: false,
            inUniverse = record.boolean("in_universe"),
            irUrl = record.string("ir_url"),
            disclosureUrl = record.string("disclosure_url"),
        )
    }

    // ───────────────────────────── data/<id>/status.json ─────────────────────

    /**
     * Вердикт и дата лежат во вложенном объекте `status`; плоская раскладка
     * тоже принимается. `watch` — массив строк. Набор критериев бывает
     * неполным. Поля `metrics` у бумаг может ещё не быть.
     */
    fun status(text: String): Status {
        val record = element(text) as? JsonObject ?: return Status()
        val nested = record["status"] as? JsonObject
        val metricsRaw = record["metrics"]

        return Status(
            assetId = record.string("asset_id"),
            verdict = Verdict.from(
                nested?.string("verdict") ?: record.firstString("verdict", "decision"),
            ),
            updated = nested?.firstString("updated", "updated_at")
                ?: record.firstString("updated_at", "updated", "date", "as_of"),
            watch = strings(record["watch"] ?: record["watching"] ?: record["watch_items"]),
            criteria = criteria(record["criteria"]),
            metrics = statusMetrics(metricsRaw),
            metricsDate = (metricsRaw as? JsonObject)?.firstString("date", "as_of", "updated"),
        )
    }

    /** Метрики — объект вида {"price_rub": 296, "date": "2026-10-26"}. */
    private fun statusMetrics(raw: JsonElement?): List<StatusMetric> {
        val record = raw as? JsonObject ?: return emptyList()
        return record.entries
            .filter { it.key !in setOf("date", "as_of", "updated") }
            .mapNotNull { (name, value) -> number(value)?.let { StatusMetric(name, it) } }
    }

    /** Критерии принимаются объектом {"C1": {...}} и массивом [{code, status}]. */
    fun criteria(raw: JsonElement?): List<Criterion> {
        val parsed = when (raw) {
            is JsonObject -> raw.map { (code, value) -> criterion(code, value) }
            is JsonArray -> raw.mapNotNull { item ->
                val record = item as? JsonObject ?: return@mapNotNull null
                val code = record.firstString("code", "id", "criterion") ?: return@mapNotNull null
                criterion(code, record)
            }
            else -> emptyList()
        }
        return parsed.sortedWith(CRITERIA_ORDER)
    }

    private fun criterion(code: String, value: JsonElement): Criterion {
        val record = value as? JsonObject
        val state = record?.firstString("status", "state", "result")
            ?: (value as? JsonPrimitive)?.contentOrNull()
        return Criterion(
            code = code.trim().uppercase(),
            state = CriterionState.from(state),
            note = record?.firstString("note", "comment", "reason", "text").orEmpty(),
        )
    }

    /** C1…C8 идут перед P1…P3, внутри группы — по номеру. */
    val CRITERIA_ORDER: Comparator<Criterion> = Comparator { left, right ->
        val a = splitCode(left.code)
        val b = splitCode(right.code)
        if (a.first != b.first) a.first.compareTo(b.first) else a.second.compareTo(b.second)
    }

    private fun splitCode(code: String): Pair<String, Int> {
        val match = Regex("^([A-Za-z]+)(\\d+)$").find(code.trim())
        return if (match == null) code to 0
        else match.groupValues[1].uppercase() to match.groupValues[2].toInt()
    }

    // ───────────────────────── config/overrides.json ─────────────────────────

    /** {asset_id: {criterion: {status, note}}} — решения владельца. */
    fun overrides(text: String): Map<String, List<Criterion>> {
        val root = element(text) as? JsonObject ?: return emptyMap()
        val table = (root["overrides"] as? JsonObject) ?: root
        return table.entries.mapNotNull { (assetId, value) ->
            if (assetId == "note") return@mapNotNull null
            val list = criteria(value).map { it.copy(fromOverride = true) }
            if (list.isEmpty()) null else assetId to list
        }.toMap()
    }

    // ───────────────────────── config/favorites.json ─────────────────────────

    fun favorites(text: String): List<String> {
        val root = element(text)
        val list = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["favorites"] ?: root["items"]) as? JsonArray
            else -> null
        } ?: return emptyList()
        return list.mapNotNull { (it as? JsonPrimitive)?.contentOrNull() }
    }

    // ──────────────────────── data/<id>/dividends.json ───────────────────────

    fun dividends(text: String, fallbackAssetId: String? = null): Dividends {
        val root = element(text) as? JsonObject
            ?: return Dividends(assetId = fallbackAssetId.orEmpty())

        val byYear = (root["by_year"] as? JsonArray).orEmpty().mapNotNull { raw ->
            val record = raw as? JsonObject ?: return@mapNotNull null
            val year = record.int("year") ?: return@mapNotNull null
            val total = record.double("total") ?: record.double("amount") ?: return@mapNotNull null
            DividendYear(year, total)
        }.sortedBy { it.year }

        val payments = (root["payments"] as? JsonArray).orEmpty().mapNotNull { raw ->
            val record = raw as? JsonObject ?: return@mapNotNull null
            val date = record.firstString("record_date", "date", "ex_date")
                ?: return@mapNotNull null
            val amount = record.double("amount") ?: record.double("value")
                ?: return@mapNotNull null
            DividendPayment(date, amount, record.double("yield_pct"))
        }.sortedByDescending { it.recordDate }

        return Dividends(
            assetId = root.string("asset_id") ?: fallbackAssetId.orEmpty(),
            currency = root.string("currency"),
            byYear = byYear,
            payments = payments,
        )
    }

    // ───────────────────────────── data/<id>/events.jsonl ────────────────────

    data class ParsedEvents(val events: List<MonitorEvent>, val skippedLines: List<Int>)

    /**
     * Читает events.jsonl построчно. Битая строка пропускается, её номер
     * попадает в [ParsedEvents.skippedLines]. Пустые строки и комментарии
     * (`#` или `//`) игнорируются молча. События — новыми сверху.
     */
    fun eventsJsonl(text: String, fallbackAssetId: String? = null): ParsedEvents {
        val events = mutableListOf<MonitorEvent>()
        val skipped = mutableListOf<Int>()

        text.lineSequence().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                return@forEachIndexed
            }
            val parsed = element(trimmed)?.let { event(it, fallbackAssetId) }
            if (parsed == null) skipped += index + 1 else events += parsed
        }

        return ParsedEvents(events.sortedByDescending { it.date }, skipped)
    }

    /** feed/feed.json — объект с полем items (так пишет агент) или массив. */
    fun feed(text: String): ParsedEvents {
        val root = element(text)
        val list = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["items"] ?: root["events"]) as? JsonArray
            else -> null
        } ?: return ParsedEvents(emptyList(), emptyList())

        val events = mutableListOf<MonitorEvent>()
        val skipped = mutableListOf<Int>()
        list.forEachIndexed { index, item ->
            val parsed = event(item, null)
            if (parsed == null) skipped += index + 1 else events += parsed
        }

        return ParsedEvents(events.sortedByDescending { it.date }, skipped)
    }

    /**
     * Одно событие по schema/event.schema.json. Обязательные поля схемы —
     * id, asset_id, date, type, importance, title, summary, criteria,
     * verdict, sources, notify. Отсутствие ключевых из них делает запись
     * непригодной: такую строку пропускаем.
     */
    fun event(raw: JsonElement, fallbackAssetId: String?): MonitorEvent? {
        val record = raw as? JsonObject ?: return null
        val date = record.firstString("date", "published_at", "timestamp", "ts", "created_at")
            ?: return null
        val title = record.firstString("title", "headline", "name") ?: return null
        val assetId = record.firstString("asset_id", "asset", "ticker") ?: fallbackAssetId
            ?: return null
        val type = record.firstString("type", "event_type", "kind") ?: "other"

        return MonitorEvent(
            id = record.firstString("id", "event_id", "uid") ?: "$assetId:$date:$title",
            assetId = assetId,
            date = date,
            type = type,
            importance = Importance.from(record.firstString("importance", "severity", "priority")),
            title = title,
            summary = record.firstString("summary", "description", "text", "body").orEmpty(),
            verdict = Verdict.fromOrNull(record.string("verdict")),
            notify = record.boolean("notify") ?: record.boolean("should_notify") ?: false,
            metrics = eventMetrics(record["metrics"]),
            criteria = criteria(record["criteria"]),
            sources = sources(record["sources"] ?: record["links"] ?: record["refs"]),
            detectedAt = record.string("detected_at"),
        )
    }

    /** Схема допускает number, string и null. null означает «числа нет». */
    private fun eventMetrics(raw: JsonElement?): List<EventMetric> = when (raw) {
        is JsonObject -> raw.mapNotNull { (name, value) ->
            number(value)?.let { EventMetric(name, it) }
        }
        is JsonArray -> raw.mapNotNull { item ->
            val record = item as? JsonObject ?: return@mapNotNull null
            val name = record.firstString("name", "label", "key") ?: return@mapNotNull null
            number(record["value"] ?: record["val"])?.let { EventMetric(name, it) }
        }
        else -> emptyList()
    }

    private fun sources(raw: JsonElement?): List<Source> {
        val array = raw as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull()?.takeIf { it.looksLikeUrl() }
                    ?.let { Source(host(it), it) }
                is JsonObject -> {
                    val url = item.firstString("url", "href", "link")
                        ?.takeIf { it.looksLikeUrl() } ?: return@mapNotNull null
                    Source(item.firstString("title", "name", "source") ?: host(url), url)
                }
                else -> null
            }
        }
    }

    private fun String.looksLikeUrl(): Boolean = startsWith("http://") || startsWith("https://")

    private fun host(url: String): String = runCatching {
        java.net.URI(url).host?.removePrefix("www.") ?: "источник"
    }.getOrDefault("источник")

    // ───────────────────────────────── утилиты ───────────────────────────────

    private fun strings(raw: JsonElement?): List<String> = when (raw) {
        is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull()?.trim() }
            .filter { it.isNotEmpty() }
        is JsonPrimitive -> listOfNotNull(raw.contentOrNull()?.trim()).filter { it.isNotEmpty() }
        else -> emptyList()
    }

    private fun number(value: JsonElement?): String? {
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        primitive.booleanOrNull?.let { return if (it) "да" else "нет" }
        if (!primitive.isString) {
            val number = primitive.doubleOrNull ?: return primitive.content.ifEmpty { null }
            return Format.number(number)
        }
        return primitive.content.ifEmpty { null }
    }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    private fun JsonPrimitive.contentOrNull(): String? =
        if (this is JsonNull) null else content.ifEmpty { null }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull()

    private fun JsonObject.firstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { string(it) }

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolean(key: String): Boolean? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        primitive.booleanOrNull?.let { return it }
        val text = primitive.contentOrNull()?.lowercase() ?: return null
        return when (text) {
            "true", "1", "yes", "да" -> true
            "false", "0", "no", "нет" -> false
            else -> null
        }
    }
}
