package app.lingvo.core

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** Оценка ответа: «Не вспомнил», «С трудом», «Хорошо», «Легко». */
enum class Rating(val value: Int, val labelRu: String) {
    AGAIN(1, "Не вспомнил"),
    HARD(2, "С трудом"),
    GOOD(3, "Хорошо"),
    EASY(4, "Легко");

    companion object {
        fun of(value: Int): Rating = entries.first { it.value == value }
    }
}

enum class CardState { NEW, LEARNING, REVIEW, RELEARNING }

/** Состояние карточки в алгоритме. Время — миллисекунды Unix. */
data class SrsState(
    val state: CardState = CardState.NEW,
    /** Стабильность памяти в днях (FSRS). 0 — ещё не оценивалась. */
    val stability: Double = 0.0,
    /** Сложность 1..10 (FSRS). */
    val difficulty: Double = 0.0,
    val due: Long = 0L,
    val lastReview: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    /** Текущий шаг изучения/переизучения. */
    val step: Int = 0,
)

data class FsrsParams(
    val w: DoubleArray = DEFAULT_W,
    val desiredRetention: Double = 0.9,
    val maximumIntervalDays: Int = 36500,
    val learningStepsMinutes: List<Int> = listOf(1, 10),
    val relearningStepsMinutes: List<Int> = listOf(10),
) {
    init {
        require(w.size == 19) { "FSRS-5 требует 19 параметров" }
        require(desiredRetention in 0.7..0.99) { "Целевая вероятность вспоминания должна быть 0.70–0.99" }
    }

    companion object {
        /** Параметры FSRS-5 по умолчанию (open-spaced-repetition). */
        val DEFAULT_W = doubleArrayOf(
            0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
            1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is FsrsParams && w.contentEquals(other.w) && desiredRetention == other.desiredRetention &&
            maximumIntervalDays == other.maximumIntervalDays && learningStepsMinutes == other.learningStepsMinutes &&
            relearningStepsMinutes == other.relearningStepsMinutes

    override fun hashCode(): Int = w.contentHashCode() * 31 + desiredRetention.hashCode()
}

/**
 * Реализация FSRS-5 (Free Spaced Repetition Scheduler) с короткими шагами изучения.
 * Формулы: https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm
 * Интервалы детерминированы (без случайного «fuzz»), чтобы поведение было проверяемым.
 */
class Fsrs(val params: FsrsParams = FsrsParams()) {
    private val w = params.w

    fun retrievability(state: SrsState, now: Long): Double {
        if (state.stability <= 0.0 || state.lastReview == null) return 0.0
        val days = max(0.0, (now - state.lastReview).toDouble() / DAY_MS)
        return (1.0 + FACTOR * days / state.stability).pow(DECAY)
    }

    /** Интервал в днях для заданной стабильности при целевой вероятности вспоминания. */
    fun nextIntervalDays(stability: Double): Long {
        val raw = stability / FACTOR * (params.desiredRetention.pow(1.0 / DECAY) - 1.0)
        return raw.roundToLong().coerceIn(1L, params.maximumIntervalDays.toLong())
    }

    fun review(card: SrsState, rating: Rating, now: Long): SrsState {
        val g = rating.value
        val elapsedDays = card.lastReview?.let { max(0.0, (now - it).toDouble() / DAY_MS) } ?: 0.0
        var stability: Double
        var difficulty: Double

        if (card.state == CardState.NEW || card.stability <= 0.0) {
            stability = initialStability(g)
            difficulty = initialDifficulty(g)
        } else {
            difficulty = nextDifficulty(card.difficulty, g)
            stability = when {
                elapsedDays < 1.0 -> shortTermStability(card.stability, g)
                rating == Rating.AGAIN -> forgetStability(card.difficulty, card.stability, retrievabilityAt(card.stability, elapsedDays))
                else -> recallStability(card.difficulty, card.stability, retrievabilityAt(card.stability, elapsedDays), rating)
            }
        }
        stability = max(stability, 0.01)

        val reps = card.reps + 1
        val base = card.copy(stability = stability, difficulty = difficulty, lastReview = now, reps = reps)

        return when (card.state) {
            CardState.NEW, CardState.LEARNING -> stepOrGraduate(base, rating, params.learningStepsMinutes, CardState.LEARNING, now)
            CardState.RELEARNING -> stepOrGraduate(base, rating, params.relearningStepsMinutes, CardState.RELEARNING, now)
            CardState.REVIEW -> if (rating == Rating.AGAIN) {
                val lapsed = base.copy(lapses = card.lapses + 1)
                if (params.relearningStepsMinutes.isEmpty()) graduate(lapsed, now)
                else lapsed.copy(state = CardState.RELEARNING, step = 0, due = now + params.relearningStepsMinutes[0] * MIN_MS)
            } else graduate(base, now)
        }
    }

    private fun stepOrGraduate(card: SrsState, rating: Rating, steps: List<Int>, learningState: CardState, now: Long): SrsState {
        if (steps.isEmpty()) return graduate(card, now)
        val step = if (card.state == CardState.NEW) 0 else card.step.coerceIn(0, steps.lastIndex)
        return when (rating) {
            Rating.AGAIN -> card.copy(state = learningState, step = 0, due = now + steps[0] * MIN_MS)
            Rating.HARD -> {
                val minutes = when {
                    step == 0 && steps.size == 1 -> steps[0] * 1.5
                    step == 0 -> (steps[0] + steps[1]) / 2.0
                    else -> steps[step].toDouble()
                }
                card.copy(state = learningState, step = step, due = now + (minutes * MIN_MS).roundToLong())
            }
            Rating.GOOD -> if (step + 1 >= steps.size) graduate(card, now)
            else card.copy(state = learningState, step = step + 1, due = now + steps[step + 1] * MIN_MS)
            Rating.EASY -> graduate(card, now)
        }
    }

    private fun graduate(card: SrsState, now: Long): SrsState =
        card.copy(state = CardState.REVIEW, step = 0, due = now + nextIntervalDays(card.stability) * DAY_MS)

    private fun retrievabilityAt(stability: Double, days: Double) = (1.0 + FACTOR * days / stability).pow(DECAY)

    private fun initialStability(g: Int) = max(w[g - 1], 0.1)

    private fun initialDifficulty(g: Int) = clampD(w[4] - exp(w[5] * (g - 1)) + 1.0)

    private fun nextDifficulty(d: Double, g: Int): Double {
        val delta = -w[6] * (g - 3)
        val damped = d + delta * (10.0 - d) / 9.0
        val reverted = w[7] * initialDifficulty(Rating.EASY.value) + (1 - w[7]) * damped
        return clampD(reverted)
    }

    private fun shortTermStability(s: Double, g: Int) = s * exp(w[17] * (g - 3 + w[18]))

    private fun recallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) w[16] else 1.0
        return s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp(w[10] * (1 - r)) - 1) * hardPenalty * easyBonus)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double {
        val long = w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp(w[14] * (1 - r))
        val shortCap = s / exp(w[17] * w[18])
        return min(long, shortCap)
    }

    private fun clampD(d: Double) = d.coerceIn(1.0, 10.0)

    companion object {
        const val DECAY = -0.5
        const val FACTOR = 19.0 / 81.0
        const val MIN_MS = 60_000L
        const val DAY_MS = 86_400_000L
    }
}
