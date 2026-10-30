package app.investwatch.core

/** Бумага со всем, что нужно карточке на экране. */
data class AssetCard(
    val asset: Asset,
    val market: Market?,
    val kind: Kind?,
    val status: Status?,
    val quote: Quote?,
    val yieldPct: Double?,
    val isFavorite: Boolean = false,
    val hasUnread: Boolean = false,
) {
    val verdict: Verdict? get() = status?.verdict

    /** Проходит ли порог дивдоходности своего рынка; null — нечего сравнивать. */
    val passesYieldFloor: Boolean? get() = Prices.passesYieldFloor(yieldPct, market)

    /** Бумаги без status.json показываются с пометкой «ещё не проверена». */
    val notChecked: Boolean get() = status == null
}

/** Отбор и сортировка списка бумаг. Логика общая для всех вкладок рынков. */
object AssetList {

    fun filter(cards: List<AssetCard>, filter: AssetFilter): List<AssetCard> =
        cards.filter { card ->
            matchesQuery(card, filter.query) &&
                (filter.verdicts.isEmpty() || card.verdict in filter.verdicts) &&
                (!filter.passesYieldFloorOnly || card.passesYieldFloor == true) &&
                matchesCommodity(card, filter.commodity) &&
                (!filter.inUniverseOnly || card.asset.inUniverse == true)
        }

    private fun matchesQuery(card: AssetCard, query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return card.asset.displayTicker.lowercase().contains(needle) ||
            card.asset.ticker.lowercase().contains(needle) ||
            card.asset.name.lowercase().contains(needle)
    }

    private fun matchesCommodity(card: AssetCard, mode: CommodityFilter): Boolean = when (mode) {
        CommodityFilter.ANY -> true
        CommodityFilter.ONLY -> card.asset.isCommodity
        CommodityFilter.EXCLUDE -> !card.asset.isCommodity
    }

    fun sort(cards: List<AssetCard>, sort: AssetSort): List<AssetCard> = when (sort) {
        // Бумаги без доходности уходят вниз, а не наверх с нулём.
        AssetSort.YIELD -> cards.sortedWith(
            compareByDescending<AssetCard> { it.yieldPct ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.asset.name.lowercase() },
        )
        AssetSort.VERDICT -> cards.sortedWith(
            compareBy<AssetCard> { it.verdict?.ordinal ?: Int.MAX_VALUE }
                .thenBy { it.asset.name.lowercase() },
        )
        AssetSort.NAME -> cards.sortedBy { it.asset.name.lowercase() }
        AssetSort.UPDATED -> cards.sortedWith(
            compareByDescending<AssetCard> { it.status?.updated ?: "" }
                .thenBy { it.asset.name.lowercase() },
        )
    }

    fun apply(cards: List<AssetCard>, filter: AssetFilter, sort: AssetSort): List<AssetCard> =
        sort(filter(cards, filter), sort)
}
