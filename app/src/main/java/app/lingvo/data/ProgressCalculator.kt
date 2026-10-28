package app.lingvo.data

import app.lingvo.core.LearningStatus
import app.lingvo.core.ProgressSnapshot
import app.lingvo.core.StreakState

/** Единственное место, где из данных базы собираются показатели для достижений, карты и существ. */
object ProgressCalculator {
    /** Курс считается «изучаемым», если в нём дано хотя бы столько ответов. */
    const val COURSE_ACTIVE_ANSWERS = 10
    /** Занятие «без ошибок» засчитывается от этого числа ответов. */
    const val PERFECT_SESSION_MIN = 10

    fun snapshot(
        cards: List<Card>,
        logs: List<ReviewLogEntity>,
        sessions: List<LearningSessionEntity>,
        courseIds: Set<String>,
        streak: StreakState,
        listens: Int,
    ): ProgressSnapshot {
        val finished = sessions.filter { it.done > 0 && it.status != SessionStatus.ACTIVE }
        return ProgressSnapshot(
            answers = logs.size,
            remembered = logs.count { it.rating > 1 },
            sessions = finished.size,
            wordsAdded = cards.size,
            wordsLearned = cards.count { it.status == LearningStatus.WELL_KNOWN || it.status == LearningStatus.MASTERED },
            mastered = cards.count { it.status == LearningStatus.MASTERED },
            currentStreak = streak.current,
            bestStreak = streak.best,
            goalDays = streak.totalGoalDays,
            courses = logs.groupBy { it.language }.count { (lang, l) -> lang in courseIds && l.size >= COURSE_ACTIVE_ANSWERS },
            longestSession = sessions.maxOfOrNull { it.done } ?: 0,
            perfectSessions = finished.count { it.done >= PERFECT_SESSION_MIN && it.correct == it.done },
            listens = listens,
        )
    }
}
