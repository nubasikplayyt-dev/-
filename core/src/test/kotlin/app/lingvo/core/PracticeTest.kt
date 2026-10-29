package app.lingvo.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeTest {
    private val words = listOf("a", "b", "c", "d", "e")

    @Test
    fun `test shuffles all words once`() {
        val s = Practice.start(words + "a", Random(1))
        assertEquals(words.size, s.total, "повторы убираются")
        assertEquals(words.toSet(), s.queue.toSet())
        assertFalse(s.finished)
        assertEquals(0f, s.progress)
    }

    @Test
    fun `answers count and mistakes are remembered`() {
        var s = Practice.start(words, Random(2))
        val first = s.currentId!!
        s = Practice.answer(s, correct = false)
        s = Practice.answer(s, correct = true)
        assertEquals(2, s.answered)
        assertEquals(1, s.correct)
        assertEquals(listOf(first), s.wrong)
        assertEquals(50, s.accuracy)
        while (!s.finished) s = Practice.answer(s, correct = true)
        assertEquals(words.size, s.answered)
        assertEquals(1f, s.progress)
        assertEquals(null, s.currentId)
        // Ответ после конца ничего не ломает.
        assertEquals(s, Practice.answer(s, correct = true))
    }

    @Test
    fun `mistakes can be retried`() {
        var s = Practice.start(words, Random(3))
        s = Practice.answer(s, false)
        s = Practice.answer(s, false)
        while (!s.finished) s = Practice.answer(s, true)
        val again = Practice.retryWrong(s, Random(4))
        assertEquals(2, again.total)
        assertEquals(s.wrong.toSet(), again.queue.toSet())
        assertEquals(0, again.correct)
    }

    @Test
    fun `summary tells the result`() {
        var perfect = Practice.start(listOf("a", "b"), Random(5))
        repeat(2) { perfect = Practice.answer(perfect, true) }
        assertEquals("Все 2 слова — верно!", Practice.summaryRu(perfect))

        var mixed = Practice.start(listOf("a", "b"), Random(5))
        mixed = Practice.answer(mixed, true)
        mixed = Practice.answer(mixed, false)
        assertEquals("Верно 1 из 2 · 50%", Practice.summaryRu(mixed))
        assertTrue(Practice.summaryRu(Practice.start(emptyList())).contains("Нет слов"))
    }
}
