package app.lingvo.core

/**
 * Прогресс одного календарного дня. [epochDay] — локальная дата устройства **в момент ответа**
 * (LocalDate.now(zone).toEpochDay()), поэтому смена часового пояса не переписывает прошлые дни.
 */
data class DayRecord(
    val epochDay: Long,
    val answers: Int,
    val remembered: Int,
    /** Цель, действовавшая в этот день. */
    val goal: Int,
    /** Когда цель дня выполнена; null — ещё не выполнена. Выполненный день остаётся выполненным. */
    val completedAt: Long?,
) {
    val completed: Boolean get() = completedAt != null
}

data class StreakState(
    /** Текущая серия. Если сегодня цель ещё не выполнена, серия держится со вчерашнего дня. */
    val current: Int,
    val best: Int,
    val todayAnswers: Int,
    val todayGoal: Int,
    val todayCompleted: Boolean,
    /** Серия есть, но сегодня цель ещё не выполнена — её можно потерять. */
    val atRisk: Boolean,
    val nextMilestone: Int?,
    val completedDays: Set<Long>,
) {
    val todayProgress: Float get() = if (todayGoal <= 0) 1f else (todayAnswers.toFloat() / todayGoal).coerceIn(0f, 1f)
    val totalGoalDays: Int get() = completedDays.size
    /** Сколько ещё выполненных дней до ближайшей отметки, считая сегодняшний, если он не выполнен. */
    val daysToMilestone: Int? get() = nextMilestone?.let { it - current }
}

/**
 * Правила серии:
 *  - день выполнен, когда число ответов за этот день достигло дневной цели;
 *  - серия растёт не чаще раза в календарный день (день либо выполнен, либо нет);
 *  - открытие приложения ничего не записывает — серия меняется только от ответов;
 *  - пропущенный (невыполненный) день обнуляет серию; сегодняшний день не считается пропущенным, пока не закончился;
 *  - рекорд — самая длинная последовательность выполненных дней в истории.
 */
object DailyStreak {
    val MILESTONES = listOf(3, 7, 14, 30, 50, 100, 180, 365)
    val GOAL_OPTIONS = listOf(5, 10, 20, 30, 50)
    const val DEFAULT_GOAL = 10

    /** Учитывает один ответ. Возвращает обновлённую запись дня. */
    fun record(existing: DayRecord?, epochDay: Long, goal: Int, remembered: Boolean, now: Long): DayRecord {
        val base = existing?.takeIf { it.epochDay == epochDay } ?: DayRecord(epochDay, 0, 0, goal, null)
        val answers = base.answers + 1
        val dayGoal = if (base.completed) base.goal else goal
        return base.copy(
            answers = answers,
            remembered = base.remembered + if (remembered) 1 else 0,
            goal = dayGoal,
            completedAt = base.completedAt ?: if (answers >= dayGoal) now else null,
        )
    }

    /** Если цель снизили и сегодняшний ответов уже достаточно — день засчитывается. */
    fun applyGoal(today: DayRecord?, goal: Int, now: Long): DayRecord? {
        if (today == null || today.completed) return today
        return today.copy(goal = goal, completedAt = if (today.answers >= goal) now else null)
    }

    fun state(days: Collection<DayRecord>, today: Long, goal: Int): StreakState {
        val completed = days.filter { it.completed }.map { it.epochDay }.toSet()
        val todayRecord = days.firstOrNull { it.epochDay == today }
        val todayDone = today in completed
        var cursor = if (todayDone) today else today - 1
        var current = 0
        while (cursor in completed) {
            current++
            cursor--
        }
        val best = maxOf(current, longestRun(completed))
        val next = MILESTONES.firstOrNull { it > current }
        return StreakState(
            current = current,
            best = best,
            todayAnswers = todayRecord?.answers ?: 0,
            todayGoal = if (todayDone) todayRecord!!.goal else goal,
            todayCompleted = todayDone,
            atRisk = current > 0 && !todayDone,
            nextMilestone = next,
            completedDays = completed,
        )
    }

    fun longestRun(days: Set<Long>): Int {
        var best = 0
        for (d in days) {
            if (d - 1 in days) continue
            var len = 0
            var c = d
            while (c in days) { len++; c++ }
            best = maxOf(best, len)
        }
        return best
    }
}
