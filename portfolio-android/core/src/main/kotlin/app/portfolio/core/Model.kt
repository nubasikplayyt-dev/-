package app.portfolio.core

/**
 * Модели данных репозитория мониторинга (nubasikplayyt-dev/invest-watch).
 * Формат описан там в schema/event.schema.json, AGENT.md и CRITERIA.md.
 */

/** Вердикт по бумаге. Значения описаны в AGENT.md репозитория данных. */
enum class Verdict(val raw: String, val label: String) {
    BUY_ZONE("buy_zone", "Зона покупки"),
    HOLD("hold", "Держать"),
    WATCH("watch", "Наблюдать"),
    DO_NOT_BUY("do_not_buy", "Не покупать"),
    SELL_CANDIDATE("sell_candidate", "Кандидат на продажу");

    companion object {
        /** Незнакомый вердикт не роняет экран, а показывается как «Наблюдать». */
        fun from(value: String?): Verdict =
            entries.firstOrNull { it.raw == value?.trim()?.lowercase() } ?: WATCH
    }
}

/** Шкала оценки критерия из CRITERIA.md. */
enum class CriterionState(val raw: String, val sign: String) {
    PASS("pass", "✓"),
    WARN("warn", "!"),
    FAIL("fail", "✕"),
    NOT_APPLICABLE("n/a", "–");

    companion object {
        fun from(value: String?): CriterionState {
            val normalized = value?.trim()?.lowercase()?.replace(Regex("[\\s_-]"), "") ?: return NOT_APPLICABLE
            return when (normalized) {
                "pass", "ok", "yes" -> PASS
                "warn", "warning" -> WARN
                "fail", "no" -> FAIL
                else -> NOT_APPLICABLE
            }
        }
    }
}

/** Важность события. Схема допускает только эти три уровня. */
enum class Importance(val raw: String, val mark: String) {
    CRITICAL("critical", "🔴"),
    HIGH("high", "🟠"),
    MEDIUM("medium", "🟡");

    companion object {
        /** «low» схемой не предусмотрен; если появится — показываем как medium. */
        fun from(value: String?): Importance =
            entries.firstOrNull { it.raw == value?.trim()?.lowercase() } ?: MEDIUM
    }
}

data class Asset(
    val id: String,
    val ticker: String,
    val name: String,
    val isin: String? = null,
    val type: String? = null,
    val logo: String? = null,
    val isCommodity: Boolean = false,
    val commodity: String? = null,
    val qualifiedOnly: Boolean = false,
    val irUrl: String? = null,
    val disclosureUrl: String? = null,
) {
    /**
     * Как подписывать бумагу крупно. У ЗПИФ в `ticker` лежит ISIN —
     * показывать его бессмысленно, поэтому берём `id`.
     */
    val displayTicker: String get() = if (isin != null && ticker == isin) id else ticker

    /** Первые две буквы для заглушки логотипа. */
    val initials: String get() =
        displayTicker.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { "??" }
}

data class Section(
    val id: String,
    val title: String,
    val country: String,
    val currency: String? = null,
    val yieldFloorPct: Double? = null,
    val assets: List<Asset> = emptyList(),
)

data class Watchlist(val sections: List<Section>) {
    val assets: List<Pair<Section, Asset>>
        get() = sections.flatMap { section -> section.assets.map { section to it } }

    fun find(assetId: String): Pair<Section, Asset>? =
        assets.firstOrNull { (_, asset) -> asset.id == assetId }
}

data class Criterion(val code: String, val state: CriterionState, val note: String = "")

data class Status(
    val assetId: String? = null,
    val verdict: Verdict = Verdict.WATCH,
    val updated: String? = null,
    val watch: List<String> = emptyList(),
    val criteria: List<Criterion> = emptyList(),
)

data class Source(val title: String, val url: String)

data class Metric(val name: String, val value: String)

data class MonitorEvent(
    val id: String,
    val assetId: String,
    val date: String,
    val type: String,
    val importance: Importance,
    val title: String,
    val summary: String = "",
    val verdict: Verdict? = null,
    val notify: Boolean = false,
    val metrics: List<Metric> = emptyList(),
    val criteria: List<Criterion> = emptyList(),
    val sources: List<Source> = emptyList(),
    val detectedAt: String? = null,
)

/** Типы событий из schema/event.schema.json и их русские подписи. */
object EventTypes {
    private val LABELS = mapOf(
        "financial_report" to "Отчёт",
        "dividend" to "Дивиденды",
        "debt" to "Долг",
        "management" to "Менеджмент",
        "shareholder" to "Акционеры",
        "sanctions" to "Санкции",
        "regulation" to "Регулирование",
        "commodity_cycle" to "Сырьевой цикл",
        "nav_rent" to "СЧА и аренда",
        "price_filter" to "Цена и доходность",
        "other" to "Прочее",
    )

    val all: Set<String> get() = LABELS.keys

    /** Незнакомый тип показываем как есть, а не прячем. */
    fun label(type: String): String = LABELS[type] ?: type
}

/** Сырьё из поля `commodity` в watchlist.json. */
object Commodities {
    private val LABELS = mapOf(
        "oil" to "нефть",
        "gas" to "газ",
        "fertilizers" to "удобрения",
        "metals" to "металлы",
        "gold" to "золото",
        "coal" to "уголь",
    )

    fun label(commodity: String): String = LABELS[commodity] ?: commodity
}
