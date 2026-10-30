package app.investwatch.core

/** Тема приложения. Переключается на лету, выбор хранится в DataStore. */
enum class ThemeMode(val raw: String, val label: String) {
    SYSTEM("system", "Системная"),
    LIGHT("light", "Светлая"),
    DARK("dark", "Тёмная");

    companion object {
        fun from(value: String?): ThemeMode =
            entries.firstOrNull { it.raw == value?.trim()?.lowercase() } ?: SYSTEM
    }
}

/** Тихие часы: в этот промежуток уведомления не показываются. */
data class QuietHours(
    val enabled: Boolean = false,
    val fromMinutes: Int = 23 * 60,
    val toMinutes: Int = 8 * 60,
) {
    fun covers(nowMinutes: Int): Boolean =
        enabled && Notify.inQuietHours(nowMinutes, fromMinutes, toMinutes)

    companion object {
        fun format(minutes: Int): String {
            val safe = ((minutes % 1440) + 1440) % 1440
            return "%02d:%02d".format(safe / 60, safe % 60)
        }
    }
}

/** Сортировка списка бумаг на вкладке рынка. */
enum class AssetSort(val label: String) {
    YIELD("По дивдоходности"),
    VERDICT("По вердикту"),
    NAME("По названию"),
    UPDATED("По дате оценки"),
}

/** Фильтры на вкладке рынка. */
data class AssetFilter(
    val query: String = "",
    val verdicts: Set<Verdict> = emptySet(),
    val passesYieldFloorOnly: Boolean = false,
    val commodity: CommodityFilter = CommodityFilter.ANY,
    val inUniverseOnly: Boolean = false,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && verdicts.isEmpty() && !passesYieldFloorOnly &&
            commodity == CommodityFilter.ANY && !inUniverseOnly
}

enum class CommodityFilter(val label: String) {
    ANY("Любые"),
    ONLY("Только сырьевые"),
    EXCLUDE("Без сырьевых"),
}
