package app.lingvo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsTest {
    private val fsrs = Fsrs()
    private val t0 = 1_800_000_000_000L
    private val day = Fsrs.DAY_MS
    private val min = Fsrs.MIN_MS

    @Test
    fun `new card again goes to first learning step`() {
        val s = fsrs.review(SrsState(), Rating.AGAIN, t0)
        assertEquals(CardState.LEARNING, s.state)
        assertEquals(t0 + 1 * min, s.due)
        assertEquals(1, s.reps)
        assertEquals(FsrsParams.DEFAULT_W[0], s.stability, 1e-9)
    }

    @Test
    fun `new card good goes to second step then graduates`() {
        val s1 = fsrs.review(SrsState(), Rating.GOOD, t0)
        assertEquals(CardState.LEARNING, s1.state)
        assertEquals(t0 + 10 * min, s1.due)
        val s2 = fsrs.review(s1, Rating.GOOD, s1.due)
        assertEquals(CardState.REVIEW, s2.state)
        assertTrue(s2.due - s1.due >= day, "after graduation interval is at least 1 day")
    }

    @Test
    fun `new card easy graduates immediately with initial easy stability`() {
        val s = fsrs.review(SrsState(), Rating.EASY, t0)
        assertEquals(CardState.REVIEW, s.state)
        val expectedDays = fsrs.nextIntervalDays(FsrsParams.DEFAULT_W[3])
        assertEquals(t0 + expectedDays * day, s.due)
        assertEquals(16L, expectedDays) // S0(Easy)=15.69 → 16 дней при retention 0.9
    }

    @Test
    fun `interval equals stability at 90 percent retention`() {
        assertEquals(10L, fsrs.nextIntervalDays(10.0))
        assertEquals(1L, fsrs.nextIntervalDays(0.2))
        assertEquals(36500L, fsrs.nextIntervalDays(1e9))
    }

    @Test
    fun `initial difficulty decreases with better rating and is clamped`() {
        val d = Rating.entries.map { fsrs.review(SrsState(), it, t0).difficulty }
        assertTrue(d[0] > d[1] && d[1] > d[2] && d[2] > d[3])
        d.forEach { assertTrue(it in 1.0..10.0) }
    }

    private fun graduated(): SrsState {
        var s = fsrs.review(SrsState(), Rating.GOOD, t0)
        s = fsrs.review(s, Rating.GOOD, s.due)
        return s
    }

    @Test
    fun `successful review grows interval, ordering hard lt good lt easy`() {
        val card = graduated()
        val at = card.due
        val hard = fsrs.review(card, Rating.HARD, at)
        val good = fsrs.review(card, Rating.GOOD, at)
        val easy = fsrs.review(card, Rating.EASY, at)
        assertTrue(hard.stability < good.stability && good.stability < easy.stability)
        assertTrue(good.stability > card.stability, "успешный ответ увеличивает стабильность")
        assertTrue(hard.due <= good.due && good.due < easy.due)
        assertEquals(CardState.REVIEW, good.state)
    }

    @Test
    fun `lapse lowers stability, counts error and enters relearning`() {
        val card = graduated()
        val failed = fsrs.review(card, Rating.AGAIN, card.due)
        assertEquals(CardState.RELEARNING, failed.state)
        assertEquals(card.lapses + 1, failed.lapses)
        assertTrue(failed.stability < card.stability)
        assertTrue(failed.difficulty > card.difficulty, "ошибка повышает сложность")
        assertEquals(card.due + 10 * min, failed.due)
        val back = fsrs.review(failed, Rating.GOOD, failed.due)
        assertEquals(CardState.REVIEW, back.state)
    }

    @Test
    fun `repeated success makes reviews rarer`() {
        var s = graduated()
        var lastInterval = 0L
        repeat(5) {
            val next = fsrs.review(s, Rating.GOOD, s.due)
            val interval = next.due - s.due
            assertTrue(interval > lastInterval, "интервал растёт: $interval > $lastInterval")
            lastInterval = interval
            s = next
        }
    }

    @Test
    fun `retrievability decays and equals retention at interval`() {
        val card = graduated()
        val r = fsrs.retrievability(card, card.lastReview!! + (card.stability * day).toLong())
        assertEquals(0.9, r, 0.01)
        assertTrue(fsrs.retrievability(card, card.lastReview!! + 100 * day) < r)
    }

    @Test
    fun `hard on first learning step uses average of first two steps`() {
        val s = fsrs.review(SrsState(), Rating.HARD, t0)
        assertEquals(t0 + (5.5 * min).toLong(), s.due)
    }
}
