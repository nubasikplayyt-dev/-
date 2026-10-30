package app.investwatch.core

/**
 * Модели данных репозитория мониторинга nubasikplayyt-dev/invest-watch.
 * Формат описан там же: README.md, AGENT.md, CRITERIA.md и schema/event.schema.json.
 *
 * Приложение только отображает факты и оценки из репозитория. Никаких
 * собственных оценок, рейтингов, целевых цен и прогнозов здесь нет.
 */

/** Вердикт по бумаге. Значения описаны в AGENT.md. */
enum class Verdict(val raw: String, val label: String) {
    BUY_ZONE("buy_zone", "Зона покупки"),
    HOLD("hold", "Держать"),
    WATCH("watch", "Наблюдать"),
    DO_NOT_BUY("do_not_buy", "Не покупать"),
    SELL_CANDIDATE("sell_candidate", "Кандидат на продажу");

    companion object {
        fun fromOrNull(value: String?): Verdict? =
            entries.firstOrNull { it.raw == value?.trim()?.lowercase() }

        /** Незнакомый вердикт не роняет экран. */
        fun from(value: String?): Verdict = fromOrNull(value) ?: WATCH
    }
}

/** Шкала оценки критерия из CRITERIA.md. */
enum class CriterionState(val raw: String, val sign: String) {
    PASS("pass", "✓"),
    WARN("warn", "!"),
    FAIL("fail", "✕"),
    NOT_APPLICABLE("n/a", "—");

    companion object {
        fun from(value: String?): CriterionState {
            val normalized = value?.trim()?.lowercase()?.replace(Regex("[\\s_-]"), "")
                ?: return NOT_APPLICABLE
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
enum class Importance(val raw: String, val mark: String, val label: String) {
    CRITICAL("critical", "🔴", "Критично"),
    HIGH("high", "🟠", "Важно"),
    MEDIUM("medium", "🟡", "Контекст");

    companion object {
        /** «low» схемой не предусмотрен; если появится — показываем как medium. */
        fun from(value: String?): Importance =
            entries.firstOrNull { it.raw == value?.trim()?.lowercase() } ?: MEDIUM
    }
}

/** Рынок из watchlist.json → markets[]. Список не зашит в код. */
data class Market(
    val id: String,
    val title: String,
    val flag: String,
    val currency: String? = null,
    val yieldFloorPct: Double? = null,
    val yieldFloorNote: String? = null,
)

/** Тип бумаги из watchlist.json → kinds[]. */
data class Kind(val id: String, val title: String)

data class Asset(
    val id: String,
    val ticker: String,
    val name: String,
    val marketId: String,
    val kindId: String,
    val isin: String? = null,
    val type: String? = null,
    val exchange: String? = null,
    val currency: String? = null,
    val domain: String? = null,
    val logo: String? = null,
    val isCommodity: Boolean = false,
    val commodity: String? = null,
    val qualifiedOnly: Boolean = false,
    val inUniverse: Boolean? = null,
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

data class Watchlist(
    val markets: List<Market>,
    val kinds: List<Kind>,
    val assets: List<Asset>,
) {
    fun market(id: String): Market? = markets.firstOrNull { it.id == id }

    fun kind(id: String): Kind? = kinds.firstOrNull { it.id == id }

    fun asset(id: String): Asset? = assets.firstOrNull { it.id == id }

    fun assetsOf(marketId: String, kindId: String? = null): List<Asset> =
        assets.filter { it.marketId == marketId && (kindId == null || it.kindId == kindId) }

    /** Фильтр «только в составе индекса» имеет смысл, только если поле заполнено. */
    val hasUniverseFlag: Boolean get() = assets.any { it.inUniverse != null }
}

data class Criterion(
    val code: String,
    val state: CriterionState,
    val note: String = "",
    /** Решение владельца из overrides.json — всегда сильнее оценки агента. */
    val fromOverride: Boolean = false,
)

/** Число из status.json → metrics. Поля ещё может не быть — блок тогда скрыт. */
data class StatusMetric(val name: String, val value: String)

data class Status(
    val assetId: String? = null,
    val verdict: Verdict = Verdict.WATCH,
    val updated: String? = null,
    val watch: List<String> = emptyList(),
    val criteria: List<Criterion> = emptyList(),
    val metrics: List<StatusMetric> = emptyList(),
    val metricsDate: String? = null,
)

data class Source(val title: String, val url: String)

data class EventMetric(val name: String, val value: String)

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
    val metrics: List<EventMetric> = emptyList(),
    val criteria: List<Criterion> = emptyList(),
    val sources: List<Source> = emptyList(),
    val detectedAt: String? = null,
)

/** dividends.json → by_year[] */
data class DividendYear(val year: Int, val total: Double)

/** dividends.json → payments[] */
data class DividendPayment(
    val recordDate: String,
    val amount: Double,
    /** Доходность к цене на дату отсечки — считается, только если цена известна. */
    val yieldPct: Double? = null,
)

data class Dividends(
    val assetId: String,
    val currency: String? = null,
    val byYear: List<DividendYear> = emptyList(),
    val payments: List<DividendPayment> = emptyList(),
    /** Откуда взяты данные: репозиторий или биржа. */
    val origin: DividendOrigin = DividendOrigin.REPO,
) {
    val isEmpty: Boolean get() = byYear.isEmpty() && payments.isEmpty()
}

enum class DividendOrigin(val label: String) {
    REPO("из репозитория"),
    MOEX("по данным Мосбиржи"),
    PRICE_SOURCE("по данным источника цен"),
}

/** Дневная свеча для линейного графика (свечей в интерфейсе нет). */
data class PriceCandle(val date: String, val close: Double)

/** Последняя цена и изменение за день. */
data class Quote(
    val assetId: String,
    val price: Double,
    val changePct: Double?,
    val currency: String?,
    val at: Long,
)

/** Периоды графика. */
enum class ChartPeriod(val label: String, val days: Int?) {
    MONTH("1М", 31),
    HALF_YEAR("6М", 183),
    YEAR("1Г", 366),
    FIVE_YEARS("5Л", 1827),
    ALL("Всё", null),
}

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

    fun label(type: String): String = LABELS[type] ?: type
}

/** Сырьё из поля `commodity`. */
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
