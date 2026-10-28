package app.lingvo.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearningTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun `status is not mastered just from views`() {
        val introducedOnly = SrsState(state = CardState.LEARNING)
        assertEquals(LearningStatus.LEARNING, StatusClassifier.classify(introducedOnly, true, false, emptyList()))
        assertEquals(LearningStatus.NEW, StatusClassifier.classify(SrsState(), false, false, emptyList()))
        assertEquals(LearningStatus.SUSPENDED, StatusClassifier.classify(SrsState(), true, true, emptyList()))
    }

    @Test
    fun `mastered requires high stability and recent successes`() {
        val strong = SrsState(state = CardState.REVIEW, stability = 45.0, reps = 6)
        val good3 = listOf(Rating.GOOD, Rating.GOOD, Rating.EASY)
        assertEquals(LearningStatus.MASTERED, StatusClassifier.classify(strong, true, false, good3))
        assertEquals(LearningStatus.REVIEW, StatusClassifier.classify(strong, true, false, listOf(Rating.GOOD, Rating.AGAIN)))
        assertEquals(LearningStatus.WELL_KNOWN, StatusClassifier.classify(strong.copy(stability = 12.0), true, false, good3))
        assertEquals(LearningStatus.REVIEW, StatusClassifier.classify(strong.copy(stability = 3.0), true, false, good3))
    }

    @Test
    fun `session requeues failed card and resumes from stored queue`() {
        val now = 1_000_000L
        var p = SessionProgress.start(listOf("a", "b", "c"))
        p = p.answer("a", nextDue = now + 60_000, now = now)
        assertEquals(listOf("b", "c", "a"), p.queue)
        assertEquals(4, p.total)
        p = p.answer("b", nextDue = now + 5 * Fsrs.DAY_MS, now = now)
        val restored = SessionProgress(SessionProgress.decodeQueue(SessionProgress.encodeQueue(p.queue)), p.done, p.total)
        assertEquals(p, restored)
        assertEquals("🇪🇸 Испанский · 2/4", SessionText.progress(Languages.SPANISH, restored.done, restored.total))
        val pruned = restored.retainOnly(setOf("a"))
        assertEquals(listOf("a"), pruned.queue)
        assertEquals(3, pruned.total)
    }

    @Test
    fun `streak counts consecutive days including yesterday`() {
        val today = LocalDate.of(2026, 10, 28)
        val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(2, Streaks.current(days, today))
        assertEquals(3, Streaks.current(days + today, today))
        assertEquals(0, Streaks.current(setOf(today.minusDays(3)), today))
        assertEquals(3, Streaks.longest(days + today))
    }

    @Test
    fun `reminder texts use russian plurals`() {
        val one = listOf(Languages.SPANISH to 12, Languages.GERMAN to 0)
        assertEquals("Время повторить испанский 🇪🇸", SessionText.reminderTitle(one))
        assertEquals("Сегодня тебя ждут 12 слов. Удели им 4 минуты.", SessionText.reminderBody(one))
        val both = listOf(Languages.SPANISH to 8, Languages.GERMAN to 21)
        assertEquals("Время повторить языки", SessionText.reminderTitle(both))
        assertEquals("🇪🇸 8 слов · 🇩🇪 21 слово", SessionText.reminderBody(both))
        assertEquals("слова", SessionText.words(3))
    }

    @Test
    fun `next reminder today, tomorrow or next allowed weekday`() {
        val cfg = ReminderConfig()
        val morning = ZonedDateTime.of(2026, 10, 28, 9, 0, 0, 0, zone)
        assertEquals(morning.withHour(19), ReminderPlanner.nextTrigger(cfg, morning))
        val evening = morning.withHour(20)
        assertEquals(morning.plusDays(1).withHour(19), ReminderPlanner.nextTrigger(cfg, evening))
        // 28.10.2026 — среда; только понедельник → 2 ноября.
        val mondays = cfg.copy(days = setOf(DayOfWeek.MONDAY))
        assertEquals(ZonedDateTime.of(2026, 11, 2, 19, 0, 0, 0, zone), ReminderPlanner.nextTrigger(mondays, morning))
        assertNull(ReminderPlanner.nextTrigger(cfg.copy(enabled = false), morning))
    }

    @Test
    fun `several reminders a day and quiet hours across midnight`() {
        val cfg = ReminderConfig(
            times = listOf(LocalTime.of(23, 0), LocalTime.of(8, 30), LocalTime.of(19, 0)),
            quietHoursEnabled = true, quietStart = LocalTime.of(22, 0), quietEnd = LocalTime.of(8, 0),
        )
        val t = ZonedDateTime.of(2026, 10, 28, 7, 0, 0, 0, zone)
        assertEquals(t.withHour(8).withMinute(30), ReminderPlanner.nextTrigger(cfg, t))
        assertEquals(t.withHour(19), ReminderPlanner.nextTrigger(cfg, t.withHour(9)))
        // 23:00 попадает в тихие часы и пропускается.
        assertEquals(t.plusDays(1).withHour(8).withMinute(30), ReminderPlanner.nextTrigger(cfg, t.withHour(20)))
        assertTrue(ReminderPlanner.isQuiet(cfg, LocalTime.of(2, 0)))
        assertFalse(ReminderPlanner.isQuiet(cfg, LocalTime.of(12, 0)))
    }

    @Test
    fun `reminder recomputed after timezone change keeps local 19 00`() {
        val cfg = ReminderConfig()
        val berlin = ZonedDateTime.of(2026, 10, 28, 9, 0, 0, 0, zone)
        val tokyo = berlin.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
        val next = ReminderPlanner.nextTrigger(cfg, tokyo)!!
        assertEquals(19, next.hour)
        assertEquals(ZoneId.of("Asia/Tokyo"), next.zone)
    }

    @Test
    fun `reminder decision skips when nothing due, recently studied or no permission`() {
        val cfg = ReminderConfig()
        val now = ZonedDateTime.of(2026, 10, 28, 19, 0, 0, 0, zone)
        val ms = now.toInstant().toEpochMilli()
        assertTrue(ReminderPlanner.decide(cfg, now, 5, null, true).notify)
        assertFalse(ReminderPlanner.decide(cfg, now, 0, null, true).notify)
        assertFalse(ReminderPlanner.decide(cfg, now, 5, ms - 10 * 60_000, true).notify)
        assertTrue(ReminderPlanner.decide(cfg, now, 5, ms - 3 * 3_600_000, true).notify)
        assertFalse(ReminderPlanner.decide(cfg, now, 5, null, false).notify)
    }

    @Test
    fun `backup round trip and validation`() {
        val backup = BackupFile(
            createdAt = "2026-10-28T12:00:00Z",
            entries = listOf(BackupEntry(id = "de-1", language = "de", term = "die Voraussetzung", translation = "условие", article = "die", plural = "Voraussetzungen", createdAt = 1, updatedAt = 2)),
            reviewStates = listOf(BackupReviewState("de-1", "REVIEW", 12.0, 4.0, 100, 50, 5, 1, 0, 10)),
            reviewHistory = listOf(BackupHistory("de-1", "de", 3, 50, "REVIEW", 1000)),
            settings = mapOf("new_per_day_de" to "10"),
        )
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
        val broken = backup.copy(reviewStates = listOf(BackupReviewState("missing", "REVIEW", 1.0, 1.0, 0, null, 0, 0, 0)))
        kotlin.test.assertFailsWith<VocabularyFormatException> { BackupCodec.decode(BackupCodec.encode(broken)) }
        kotlin.test.assertFailsWith<VocabularyFormatException> { BackupCodec.decode("{\"entries\":1}") }
    }
}
