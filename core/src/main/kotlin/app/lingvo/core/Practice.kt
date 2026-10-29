package app.lingvo.core

import kotlin.random.Random

/**
 * Тест по уже пройденным словам курса: все слова вперемешку, в любой момент, сколько угодно раз.
 *
 * Это проверка себя, а не повторение по расписанию: ответы **не меняют** состояние FSRS,
 * не влияют на интервалы, дневную цель и серию. Иначе каждый тест сдвигал бы расписание
 * и ломал интервальное повторение. Слова, в которых пользователь ошибся, можно отправить
 * в обычное повторение отдельной кнопкой.
 */
data class PracticeState(
    val queue: List<String>,
    val index: Int = 0,
    val correct: Int = 0,
    /** Слова с ошибками — в порядке прохождения, без повторов. */
    val wrong: List<String> = emptyList(),
) {
    val total: Int get() = queue.size
    val finished: Boolean get() = index >= queue.size
    val currentId: String? get() = queue.getOrNull(index)
    val answered: Int get() = index
    /** Доля пройденного, 0..1 — для полосы прогресса. */
    val progress: Float get() = if (queue.isEmpty()) 1f else index.toFloat() / queue.size
    /** Процент правильных ответов среди отвеченных. */
    val accuracy: Int get() = if (index == 0) 0 else correct * 100 / index
}

object Practice {
    /** Минимум слов, при котором тест имеет смысл. */
    const val MIN_WORDS = 4

    fun start(ids: List<String>, random: Random = Random.Default): PracticeState =
        PracticeState(ids.distinct().shuffled(random))

    fun answer(state: PracticeState, correct: Boolean): PracticeState {
        val id = state.currentId ?: return state
        return state.copy(
            index = state.index + 1,
            correct = state.correct + if (correct) 1 else 0,
            wrong = if (correct || id in state.wrong) state.wrong else state.wrong + id,
        )
    }

    /** Пройти заново только те слова, в которых была ошибка. */
    fun retryWrong(state: PracticeState, random: Random = Random.Default): PracticeState =
        start(state.wrong, random)

    /** Короткий итог теста для показа пользователю. */
    fun summaryRu(state: PracticeState): String = when {
        state.total == 0 -> "Нет слов для теста"
        state.wrong.isEmpty() -> "Все ${state.total} ${SessionText.plural(state.total, "слово", "слова", "слов")} — верно!"
        else -> "Верно ${state.correct} из ${state.total} · ${state.accuracy}%"
    }
}
