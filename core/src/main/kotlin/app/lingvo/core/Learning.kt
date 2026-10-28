package app.lingvo.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class LearningStatus(val labelRu: String) {
    NEW("Новое"),
    LEARNING("Изучается"),
    REVIEW("На повторении"),
    WELL_KNOWN("Хорошо запоминается"),
    MASTERED("Устойчиво усвоено"),
    SUSPENDED("Приостановлено");
}

object StatusClassifier {
    /** Стабильность (дни), начиная с которой слово «хорошо запоминается». */
    const val WELL_KNOWN_STABILITY = 10.0
    /** Стабильность, начиная с которой слово «устойчиво усвоено». */
    const val MASTERED_STABILITY = 30.0

    /**
     * Статус определяется состоянием FSRS и историей ответов, а не числом просмотров.
     * @param introduced пользователь уже познакомился с карточкой.
     * @param recentRatings последние оценки, от старых к новым.
     */
    fun classify(state: SrsState, introduced: Boolean, suspended: Boolean, recentRatings: List<Rating>): LearningStatus {
        if (suspended) return LearningStatus.SUSPENDED
        if (!introduced) return LearningStatus.NEW
        if (state.reps == 0 || state.state == CardState.NEW || state.state == CardState.LEARNING || state.state == CardState.RELEARNING) {
            return LearningStatus.LEARNING
        }
        val lastThree = recentRatings.takeLast(3)
        val recentFailure = Rating.AGAIN in recentRatings.takeLast(2)
        return when {
            !recentFailure && state.stability >= MASTERED_STABILITY && lastThree.size == 3 && lastThree.none { it == Rating.AGAIN } ->
                LearningStatus.MASTERED
            !recentFailure && state.stability >= WELL_KNOWN_STABILITY -> LearningStatus.WELL_KNOWN
            else -> LearningStatus.REVIEW
        }
    }

    fun explain(status: LearningStatus, state: SrsState, retrievability: Double): String = when (status) {
        LearningStatus.NEW -> "Вы ещё не знакомились с этой карточкой. Откройте «Учить новые слова»."
        LearningStatus.LEARNING -> "Слово в начальной фазе: оно вернётся через несколько минут, пока вы уверенно его не вспомните."
        LearningStatus.REVIEW -> "Слово закрепляется. Память о нём держится около ${days(state.stability)}; повторения будут реже с каждым успешным ответом."
        LearningStatus.WELL_KNOWN -> "Вы уверенно вспоминаете слово: память держится около ${days(state.stability)}. Вероятность вспомнить сейчас — ${(retrievability * 100).toInt()}%."
        LearningStatus.MASTERED -> "Устойчиво усвоено: три последних ответа успешны, память держится больше месяца (${days(state.stability)})."
        LearningStatus.SUSPENDED -> "Карточка приостановлена и не попадает в повторения."
    }

    private fun days(stability: Double): String {
        val d = stability.toLong().coerceAtLeast(1)
        val word = when {
            d % 10 == 1L && d % 100 != 11L -> "дня"
            else -> "дней"
        }
        return "$d $word"
    }
}

/** Порядок карточек в очереди повторения: сначала самые просроченные, при равенстве — чаще забываемые. */
fun <T> List<T>.orderForReview(due: (T) -> Long, lapses: (T) -> Int): List<T> =
    sortedWith(compareBy<T> { due(it) }.thenByDescending { lapses(it) })

/** Подсчёт серии дней обучения по датам занятий. */
object Streaks {
    fun days(activityTimes: Collection<Long>, zone: ZoneId): Set<LocalDate> =
        activityTimes.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()

    /** Серия засчитывается, если занятие было сегодня или вчера (сегодня ещё не закончился). */
    fun current(activeDays: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in activeDays) today else today.minusDays(1)
        var count = 0
        while (day in activeDays) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun longest(activeDays: Set<LocalDate>): Int {
        var best = 0
        for (d in activeDays) {
            if (d.minusDays(1) in activeDays) continue
            var len = 0
            var cur = d
            while (cur in activeDays) { len++; cur = cur.plusDays(1) }
            best = maxOf(best, len)
        }
        return best
    }
}

/** Короткие тексты для компактного интерфейса (уведомление, капсула). */
object SessionText {
    fun progress(spec: LanguageSpec, done: Int, total: Int): String = "${spec.flag} ${spec.nameRu} · $done/$total"

    fun dueSummary(counts: List<Pair<LanguageSpec, Int>>): String =
        counts.filter { it.second > 0 }.joinToString(" · ") { (spec, n) -> "${spec.flag} $n ${words(n)}" }

    fun reminderTitle(counts: List<Pair<LanguageSpec, Int>>): String {
        val nonZero = counts.filter { it.second > 0 }
        return if (nonZero.size == 1) "Время повторить ${nonZero[0].first.nameRu.lowercase()} ${nonZero[0].first.flag}"
        else "Время повторить языки"
    }

    fun reminderBody(counts: List<Pair<LanguageSpec, Int>>): String {
        val nonZero = counts.filter { it.second > 0 }
        if (nonZero.size == 1) {
            val n = nonZero[0].second
            val minutes = estimateMinutes(n)
            return "Сегодня тебя ждут $n ${words(n)}. Удели им $minutes ${minutesWord(minutes)}."
        }
        return dueSummary(nonZero)
    }

    /** Примерно 20 секунд на карточку, не меньше 2 минут. */
    fun estimateMinutes(cards: Int): Int = maxOf(2, (cards * 20 + 59) / 60)

    fun words(n: Int): String = plural(n, "слово", "слова", "слов")

    fun minutesWord(n: Int): String = plural(n, "минута", "минуты", "минут")

    fun plural(n: Int, one: String, few: String, many: String): String {
        val n100 = n % 100
        val n10 = n % 10
        return when {
            n100 in 11..14 -> many
            n10 == 1 -> one
            n10 in 2..4 -> few
            else -> many
        }
    }
}
