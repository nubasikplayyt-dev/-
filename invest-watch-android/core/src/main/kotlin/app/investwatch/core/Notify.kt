package app.investwatch.core

/**
 * Что показывать в уведомлениях после очередной фоновой проверки.
 *
 * Уведомляем только о новых событиях с `notify: true` по **избранным**
 * бумагам, которые не заглушены и не попали в тихие часы. Это единственное
 * место, где принимается такое решение, поэтому оно в ядре и под тестами.
 */
object Notify {

    data class Result(
        /** О чём уведомить, новые сверху. */
        val toNotify: List<MonitorEvent>,
        /** Что запомнить как показанное — все notify-события, даже пропущенные. */
        val seen: Set<String>,
    )

    data class Rules(
        val favorites: Set<String>,
        val muted: Set<String> = emptySet(),
        val enabled: Boolean = true,
        /** Первый запуск: всё, что уже лежит в репозитории, считается прочитанным. */
        val firstRun: Boolean = false,
    )

    fun select(events: List<MonitorEvent>, seen: Set<String>, rules: Rules): Result {
        val notifiable = events.filter { it.notify }
        val allSeen = seen + notifiable.map { it.id }

        if (rules.firstRun || !rules.enabled) return Result(emptyList(), allSeen)

        val fresh = notifiable
            .filter { it.id !in seen }
            .filter { it.assetId in rules.favorites }
            .filter { it.assetId !in rules.muted }
            .sortedByDescending { it.date }

        return Result(fresh, allSeen)
    }

    /**
     * Тихие часы. Интервал задаётся минутами от полуночи и может
     * переходить через полночь (например с 23:00 до 8:00).
     */
    fun inQuietHours(nowMinutes: Int, fromMinutes: Int, toMinutes: Int): Boolean {
        if (fromMinutes == toMinutes) return false
        return if (fromMinutes < toMinutes) {
            nowMinutes >= fromMinutes && nowMinutes < toMinutes
        } else {
            nowMinutes >= fromMinutes || nowMinutes < toMinutes
        }
    }

    /** Непрочитанные уведомления по бумаге — для точки на карточке. */
    fun hasUnread(events: List<MonitorEvent>, seen: Set<String>): Boolean =
        events.any { it.notify && it.id !in seen }
}
