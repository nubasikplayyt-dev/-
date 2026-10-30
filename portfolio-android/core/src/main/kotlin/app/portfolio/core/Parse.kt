package app.portfolio.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.text.NumberFormat
import java.util.Locale

/**
 * Разбор файлов репозитория данных.
 *
 * Разбор намеренно терпимый: неизвестные поля не мешают, а у полей, которые
 * агент может записать иначе, разобраны синонимы. Битая запись пропускается
 * и не роняет экран — её номер возвращается отдельно.
 */
object Parse {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ─────────────────────────── config/watchlist.json ───────────────────────

    /**
     * Поддержаны две раскладки: assets внутри каждого раздела (так пишет агент)
     * и общий массив assets верхнего уровня с полем section у бумаги.
     */
    fun watchlist(text: String): Watchlist {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return Watchlist(emptyList())
        val loose = (root["assets"] as? JsonArray).orEmpty()

        val sections = (root["sections"] as? JsonArray).orEmpty().mapNotNull { rawSection ->
            val record = rawSection as? JsonObject ?: return@mapNotNull null
            val id = record.string("id") ?: return@mapNotNull null
            val title = record.string("title") ?: return@mapNotNull null
            val country = record.string("country") ?: return@mapNotNull null

            val own = (record["assets"] as? JsonArray).orEmpty()
            val inherited = loose.filter { item ->
                val asset = item as? JsonObject ?: return@filter false
                asset.string("section") == id || asset.string("section_id") == id
            }

            Section(
                id = id,
                title = title,
                country = country,
                currency = record.string("currency"),
                yieldFloorPct = record.double("yield_floor_pct"),
                assets = (own + inherited).mapNotNull { asset(it) },
            )
        }

        return Watchlist(sections)
    }

    private fun asset(raw: JsonElement): Asset? {
        val record = raw as? JsonObject ?: return null
        val ticker = record.firstString("ticker", "symbol", "secid")
        val id = record.firstString("id", "asset_id") ?: ticker ?: return null
        val name = record.firstString("name", "title", "full_name") ?: ticker ?: id

        return Asset(
            id = id,
            ticker = ticker ?: id,
            name = name,
            isin = record.string("isin"),
            type = record.string("type"),
            logo = record.string("logo"),
            isCommodity = record.boolean("is_commodity") ?: false,
            commodity = record.string("commodity"),
            qualifiedOnly = record.boolean("qualified_only") ?: false,
            irUrl = record.string("ir_url"),
            disclosureUrl = record.string("disclosure_url"),
        )
    }

    // ───────────────────────────── data/<id>/status.json ─────────────────────

    /**
     * Вердикт и дата лежат во вложенном объекте `status` — так пишет агент;
     * плоская раскладка тоже принимается. `watch` — массив строк, одиночная
     * строка понимается тоже. Набор критериев бывает неполным.
     */
    fun status(text: String): Status {
        val record = json.parseToJsonElement(text) as? JsonObject ?: return Status()
        val nested = record["status"] as? JsonObject

        return Status(
            assetId = record.string("asset_id"),
            verdict = Verdict.from(
                nested?.string("verdict") ?: record.firstString("verdict", "decision"),
            ),
            updated = nested?.firstString("updated", "updated_at")
                ?: record.firstString("updated_at", "updated", "date", "as_of"),
            watch = watchList(record["watch"] ?: record["watching"] ?: record["watch_items"]),
            criteria = criteria(record["criteria"]),
        )
    }

    private fun watchList(raw: JsonElement?): List<String> = when (raw) {
        is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe()?.trim() }
            .filter { it.isNotEmpty() }
        is JsonPrimitive -> listOfNotNull(raw.contentOrNullSafe()?.trim()).filter { it.isNotEmpty() }
        else -> emptyList()
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
        val state = when {
            record != null -> record.firstString("status", "state", "result")
            else -> (value as? JsonPrimitive)?.contentOrNullSafe()
        }
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

    // ───────────────────────────── data/<id>/events.jsonl ────────────────────

    data class ParsedEvents(val events: List<MonitorEvent>, val skippedLines: List<Int>)

    /**
     * Читает events.jsonl построчно. Битая строка пропускается, её номер
     * попадает в [ParsedEvents.skippedLines]. Пустые строки и комментарии
     * (`#` или `//`) игнорируются молча. События возвращаются новыми сверху.
     */
    fun eventsJsonl(text: String, fallbackAssetId: String? = null): ParsedEvents {
        val events = mutableListOf<MonitorEvent>()
        val skipped = mutableListOf<Int>()

        text.lineSequence().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) return@forEachIndexed
            val parsed = runCatching { event(json.parseToJsonElement(trimmed), fallbackAssetId) }.getOrNull()
            if (parsed == null) skipped += index + 1 else events += parsed
        }

        return ParsedEvents(events.sortedByDescending { it.date }, skipped)
    }

    /** feed/feed.json — объект с полем items (так пишет агент) или массив. */
    fun feed(text: String): ParsedEvents {
        val root = json.parseToJsonElement(text)
        val list = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["items"] ?: root["events"]) as? JsonArray
            else -> null
        } ?: return ParsedEvents(emptyList(), emptyList())

        val events = mutableListOf<MonitorEvent>()
        val skipped = mutableListOf<Int>()
        list.forEachIndexed { index, item ->
            val parsed = runCatching { event(item, null) }.getOrNull()
            if (parsed == null) skipped += index + 1 else events += parsed
        }

        return ParsedEvents(events.sortedByDescending { it.date }, skipped)
    }

    private fun event(raw: JsonElement, fallbackAssetId: String?): MonitorEvent? {
        val record = raw as? JsonObject ?: return null
        val date = record.firstString("date", "published_at", "timestamp", "ts", "created_at")
            ?: return null
        val title = record.firstString("title", "headline", "name") ?: return null
        val assetId = record.firstString("asset_id", "asset", "ticker") ?: fallbackAssetId ?: return null

        return MonitorEvent(
            id = record.firstString("id", "event_id", "uid") ?: "$date:$title",
            assetId = assetId,
            date = date,
            type = record.firstString("type", "event_type", "kind") ?: "other",
            importance = Importance.from(record.firstString("importance", "severity", "priority")),
            title = title,
            summary = record.firstString("summary", "description", "text", "body").orEmpty(),
            verdict = record.string("verdict")?.let { Verdict.from(it) },
            notify = record.boolean("notify") ?: record.boolean("should_notify") ?: false,
            metrics = metrics(record["metrics"]),
            criteria = criteria(record["criteria"]),
            sources = sources(record["sources"] ?: record["links"] ?: record["refs"]),
            detectedAt = record.string("detected_at"),
        )
    }

    /** Схема допускает number, string и null. null означает «числа нет». */
    private fun metrics(raw: JsonElement?): List<Metric> = when (raw) {
        is JsonObject -> raw.mapNotNull { (name, value) ->
            formatNumber(value)?.let { Metric(name, it) }
        }
        is JsonArray -> raw.mapNotNull { item ->
            val record = item as? JsonObject ?: return@mapNotNull null
            val name = record.firstString("name", "label", "key") ?: return@mapNotNull null
            formatNumber(record["value"] ?: record["val"])?.let { Metric(name, it) }
        }
        else -> emptyList()
    }

    private fun formatNumber(value: JsonElement?): String? {
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        primitive.booleanOrNull?.let { return if (it) "да" else "нет" }
        if (!primitive.isString) {
            val number = primitive.doubleOrNull ?: return primitive.content.ifEmpty { null }
            val format = NumberFormat.getNumberInstance(Locale("ru"))
            format.maximumFractionDigits = 2
            return format.format(number)
        }
        return primitive.content.ifEmpty { null }
    }

    private fun sources(raw: JsonElement?): List<Source> {
        val array = raw as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNullSafe()?.takeIf { it.looksLikeUrl() }
                    ?.let { Source(host(it), it) }
                is JsonObject -> {
                    val url = item.firstString("url", "href", "link")?.takeIf { it.looksLikeUrl() }
                        ?: return@mapNotNull null
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

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this is JsonNull) null else content.ifEmpty { null }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNullSafe()

    private fun JsonObject.firstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { string(it) }

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.boolean(key: String): Boolean? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        primitive.booleanOrNull?.let { return it }
        val text = primitive.contentOrNullSafe()?.lowercase() ?: return null
        return text in setOf("true", "1", "yes", "да")
    }
}
