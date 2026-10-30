package app.portfolio.core

/**
 * Что показывать в уведомлениях после очередной проверки.
 *
 * Новым считается событие с `notify: true`, которого ещё не видели и чья
 * бумага не заглушена. Это единственное место, где решается, о чём звонить,
 * — поэтому логика лежит в ядре и покрыта тестами.
 */
object NewEvents {

    data class Result(
        /** О чём уведомить, новые сверху. */
        val toNotify: List<MonitorEvent>,
        /** Что запомнить как виденное — все notify-события, включая заглушённые. */
        val seen: Set<String>,
    )

    fun select(
        events: List<MonitorEvent>,
        alreadySeen: Set<String>,
        mutedAssets: Set<String> = emptySet(),
        firstRun: Boolean = false,
    ): Result {
        val notifiable = events.filter { it.notify }
        val seen = alreadySeen + notifiable.map { it.id }

        // Первый запуск: всё, что уже лежит в репозитории, считается прочитанным,
        // иначе установка приложения обернулась бы пачкой уведомлений разом.
        if (firstRun) return Result(emptyList(), seen)

        val fresh = notifiable
            .filter { it.id !in alreadySeen }
            .filter { it.assetId !in mutedAssets }
            .sortedByDescending { it.date }

        return Result(fresh, seen)
    }

    /** Непрочитанные уведомления по бумаге — для точки на карточке. */
    fun hasUnread(events: List<MonitorEvent>, alreadySeen: Set<String>): Boolean =
        events.any { it.notify && it.id !in alreadySeen }
}
