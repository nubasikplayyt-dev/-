package app.lingvo.ui

import app.lingvo.core.LanguageSpec
import app.lingvo.core.LearningStatus
import app.lingvo.core.Rating
import app.lingvo.core.Streaks
import app.lingvo.data.Card
import app.lingvo.data.ReviewLogEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayCount(val date: LocalDate, val reviews: Int, val correct: Int)

data class LanguageOverview(
    val spec: LanguageSpec,
    val total: Int,
    val newAvailable: Int,
    val due: Int,
    /** Карточки со статусом «Хорошо запоминается» и «Устойчиво усвоено». */
    val learned: Int,
    val mastered: Int,
    val learning: Int,
    val suspended: Int,
    val reviewsToday: Int,
    val correctToday: Int,
    val introducedToday: Int,
    val newLimit: Int,
    val totalReviews: Int,
    val totalCorrect: Int,
    /** Доля «сложных» карточек: ≥3 ошибок или высокая сложность FSRS. */
    val hardShare: Float,
    val week: List<DayCount>,
    val month: List<DayCount>,
    val streak: Int,
    val longestStreak: Int,
    val overdueMs: Long,
) {
    val successRate: Float get() = if (totalReviews == 0) 0f else totalCorrect.toFloat() / totalReviews
    val newLeftToday: Int get() = minOf(newAvailable, (newLimit - introducedToday).coerceAtLeast(0))
}

object OverviewCalculator {
    fun compute(
        spec: LanguageSpec,
        cards: List<Card>,
        logs: List<ReviewLogEntity>,
        newLimit: Int,
        now: Long,
        zone: ZoneId,
    ): LanguageOverview {
        val own = cards.filter { it.entry.language == spec.code }
        val ownLogs = logs.filter { it.language == spec.code }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val active = own.filter { !it.state.suspended }
        val dueCards = active.filter { it.state.introducedAt != null && it.state.due <= now }
        val reviewed = active.filter { it.state.reps > 0 }
        val hard = reviewed.count { it.state.lapses >= 3 || it.state.difficulty >= 8.0 }
        val byDay = ownLogs.groupBy { Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() }
        fun days(n: Int) = (n - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val l = byDay[d].orEmpty()
            DayCount(d, l.size, l.count { it.rating != Rating.AGAIN.value })
        }
        val todayLogs = ownLogs.filter { it.reviewedAt >= todayStart }
        val activeDays = Streaks.days(ownLogs.map { it.reviewedAt }, zone)
        return LanguageOverview(
            spec = spec,
            total = own.size,
            newAvailable = active.count { it.state.introducedAt == null },
            due = dueCards.size,
            learned = own.count { it.status == LearningStatus.WELL_KNOWN || it.status == LearningStatus.MASTERED },
            mastered = own.count { it.status == LearningStatus.MASTERED },
            learning = own.count { it.status == LearningStatus.LEARNING || it.status == LearningStatus.REVIEW },
            suspended = own.count { it.state.suspended },
            reviewsToday = todayLogs.size,
            correctToday = todayLogs.count { it.rating != Rating.AGAIN.value },
            introducedToday = own.count { (it.state.introducedAt ?: 0L) >= todayStart },
            newLimit = newLimit,
            totalReviews = ownLogs.size,
            totalCorrect = ownLogs.count { it.rating != Rating.AGAIN.value },
            hardShare = if (reviewed.isEmpty()) 0f else hard.toFloat() / reviewed.size,
            week = days(7),
            month = days(30),
            streak = Streaks.current(activeDays, today),
            longestStreak = Streaks.longest(activeDays),
            overdueMs = dueCards.minOfOrNull { now - it.state.due } ?: 0L,
        )
    }

    /** Язык, которому сейчас нужно внимание: больше всего карточек к повторению. */
    fun needsAttention(list: List<LanguageOverview>): LanguageOverview? =
        list.filter { it.due > 0 }.maxWithOrNull(compareBy<LanguageOverview> { it.due }.thenBy { it.overdueMs })

    /** Карта активности: количество повторений по дням за последние [days] дней. */
    fun activity(logs: List<ReviewLogEntity>, zone: ZoneId, today: LocalDate, days: Int): List<DayCount> {
        val byDay = logs.groupBy { Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val l = byDay[d].orEmpty()
            DayCount(d, l.size, l.count { it.rating != Rating.AGAIN.value })
        }
    }
}
