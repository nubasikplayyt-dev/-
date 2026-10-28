package app.lingvo.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocialTest {
    private fun day(d: Long, answers: Int, remembered: Int, done: Boolean) =
        DayRecord(d, answers, remembered, 10, if (done) 1L else null)

    @Test
    fun `code is readable and tolerates dashes, case and lookalikes`() {
        val code = ShareCodes.generate(Random(7))
        assertEquals(ShareCodes.LENGTH, code.length)
        assertTrue(code.none { it in "01IBO" }, code)
        assertEquals(code, ShareCodes.normalize(ShareCodes.pretty(code).lowercase()))
        assertEquals("LLKKMMNN", ShareCodes.normalize("1i-kk mm nn"))
        assertNull(ShareCodes.normalize("short"))
        assertNull(ShareCodes.normalize("ABCD-EF0O"))
        assertEquals(code, ShareCodes.fromLink(ShareCodes.link(code)))
    }

    @Test
    fun `points reward answers, recall and a finished day`() {
        assertEquals(0, Competition.points(day(1, 0, 0, false)))
        assertEquals(15, Competition.points(day(1, 10, 5, false)))
        assertEquals(35, Competition.points(day(1, 10, 5, true)))
    }

    @Test
    fun `summary counts today and the last seven days and respects privacy`() {
        val today = 20_000L
        val days = listOf(day(today, 10, 6, true), day(today - 3, 8, 4, false), day(today - 9, 50, 50, true))
        val snap = ProgressSnapshot(wordsLearned = 42)
        val full = Competition.summarize("Я", days, today, snap, streak = 5, courses = listOf("🇩🇪 Немецкий"),
            privacy = SharePrivacy(stats = true, streak = true, courses = true), now = 100L)
        assertEquals(6, full.todayWords)
        assertEquals(36, full.todayPoints)
        assertEquals(10, full.weekWords, "слова за 7 дней, без дня девятидневной давности")
        assertEquals(48, full.weekPoints)
        assertEquals(42, full.totalWords)
        assertEquals(5, full.streak)
        assertEquals(listOf("🇩🇪 Немецкий"), full.courses)

        val private = Competition.summarize("Я", days, today, snap, 5, listOf("🇩🇪 Немецкий"),
            SharePrivacy(stats = false, streak = false, courses = false), 100L)
        assertEquals(0, private.weekPoints)
        assertEquals(0, private.streak)
        assertTrue(private.courses.isEmpty())
        assertEquals("Я", private.nick, "ник виден всегда")
    }

    @Test
    fun `leaderboard sorts by points then words`() {
        val me = SharedProgress("Я", 0, weekPoints = 50, weekWords = 10)
        val friends = mapOf(
            "AAAA1111" to SharedProgress("Друг", 0, weekPoints = 50, weekWords = 20),
            "BBBB2222" to SharedProgress("Соня", 0, weekPoints = 70, weekWords = 1),
        )
        val board = Competition.leaderboard(me, friends, LeaderboardPeriod.WEEK)
        assertEquals(listOf("Соня", "Друг", "Я"), board.map { it.nick })
        assertTrue(board.single { it.isMe }.nick == "Я")
    }

    @Test
    fun `week key changes on monday and overtaking is reported once`() {
        // epochDay 20_003 — понедельник: именно там начинается новая неделя.
        assertEquals(Competition.weekKey(20_000), Competition.weekKey(20_002))
        assertEquals(Competition.weekKey(20_000) + 1, Competition.weekKey(20_003))
        val wk = Competition.weekKey(20_000)
        val friends = mapOf(
            "A" to SharedProgress("Быстрый", 0, weekPoints = 99, weekKey = wk),
            "B" to SharedProgress("Медленный", 0, weekPoints = 5, weekKey = wk),
            "C" to SharedProgress("Прошлая неделя", 0, weekPoints = 999, weekKey = wk - 1),
        )
        assertEquals(listOf("Быстрый"), Competition.overtookMe(50, friends, emptySet(), wk).map { it.nick })
        assertTrue(Competition.overtookMe(50, friends, setOf("Быстрый"), wk).isEmpty())
    }

    @Test
    fun `shared progress survives a round trip through the map`() {
        val p = SharedProgress("Ник", 123, 1, 2, 3, 4, 5, 6, 7, 8, listOf("🇫🇷 Французский", "🇩🇪 Немецкий"))
        assertEquals(p, SharedProgress.fromMap(p.toMap()))
        assertNull(SharedProgress.fromMap(mapOf("updatedAt" to 1)))
    }
}
