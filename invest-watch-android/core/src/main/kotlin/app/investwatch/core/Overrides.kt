package app.investwatch.core

/**
 * Решения владельца из config/overrides.json всегда сильнее оценки агента
 * (так написано в AGENT.md). Накладываем их поверх критериев из status.json
 * и помечаем, чтобы интерфейс мог показать «решение владельца».
 */
object Overrides {

    fun apply(criteria: List<Criterion>, overrides: List<Criterion>): List<Criterion> {
        if (overrides.isEmpty()) return criteria

        val byCode = criteria.associateBy { it.code }.toMutableMap()
        for (override in overrides) {
            val existing = byCode[override.code]
            byCode[override.code] = override.copy(
                // Пустую пометку владельца не считаем поводом стереть пояснение агента.
                note = override.note.ifBlank { existing?.note.orEmpty() },
                fromOverride = true,
            )
        }
        return byCode.values.sortedWith(Parse.CRITERIA_ORDER)
    }

    fun apply(status: Status, overrides: List<Criterion>): Status =
        status.copy(criteria = apply(status.criteria, overrides))
}
