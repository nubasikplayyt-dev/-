package app.lingvo.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoursesTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `any language course parses and serializes with generic keys`() {
        val italian = Course(id = "it", languageCode = "it", name = "Итальянский", flag = "🇮🇹").spec
        assertEquals("term", italian.termKey)
        assertEquals("russian", italian.translationKey)
        val text = """{"language":"it","entries":[{"term":"la finestra","russian":"окно","article":"la","example_it":"Apri la finestra."}]}"""
        val parsed = VocabularyJson.parse(text, italian, now)
        val e = parsed.entries.single().entry
        assertEquals("it", e.language)
        assertEquals("la", e.article)
        assertEquals("Apri la finestra.", e.exampleTarget)
        val out = VocabularyJson.serialize(italian, listOf(e.copy(id = "it-1")), now)
        assertTrue(out.contains("\"language\": \"it\"") && out.contains("\"term\": \"la finestra\""))
        assertEquals(e.copy(id = "it-1"), VocabularyJson.parse(out, italian, now).entries.single().entry)
    }

    @Test
    fun `custom language pair with non russian translation`() {
        val course = Course(id = "ja-en", languageCode = "ja", name = "Японский (англ.)", flag = "🇯🇵", translationCode = "en", translationName = "Английский")
        val spec = course.spec
        assertEquals("translation", spec.translationKey)
        assertEquals("ja-en_vocabulary.json", spec.fileName)
        val out = VocabularyJson.serialize(spec, listOf(VocabEntry("ja-1", "ja-en", "水", "water", exampleRu = "Water, please.", createdAt = 1, updatedAt = 1)), now)
        assertTrue(out.contains("\"translation_language\": \"en\"") && out.contains("\"example_translation\""))
        val back = VocabularyJson.parse(out, spec, now).entries.single().entry
        assertEquals("water", back.translation)
        assertEquals("Water, please.", back.exampleRu)
        // Тот же файл нельзя загрузить в курс с переводом на русский.
        assertFailsWith<VocabularyFormatException> { VocabularyJson.parse(out, LanguageSpec.forLanguage("ja"), now) }
    }

    @Test
    fun `legacy spanish and german dictionaries stay compatible with course ids`() {
        val legacy = """{"schema_version":1,"language":"de","entries":[{"id":"de-1","german":"die Bank","russian":"скамья","article":"die"}]}"""
        val course = Course(id = "de", languageCode = "de", name = "Немецкий", flag = "🇩🇪").spec
        assertEquals("german", course.termKey)
        assertEquals("german_vocabulary.json", course.fileName)
        assertEquals("die Bank", VocabularyJson.parse(legacy, course, now).entries.single().entry.term)
        // Второй немецкий курс с другим ID тоже принимает немецкий файл.
        val second = Course(id = "de-work", languageCode = "de", name = "Немецкий для работы", flag = "🇩🇪").spec
        assertEquals("de-work", VocabularyJson.parse(legacy, second, now).entries.single().entry.language)
        assertEquals("de", VocabularyJson.peekLanguage(legacy))
        assertEquals("es", VocabularyJson.peekLanguage("""[{"spanish":"hola","russian":"привет"}]"""))
        assertFailsWith<VocabularyFormatException> { VocabularyJson.parse(legacy, LanguageSpec.forLanguage("fr"), now) }
    }

    @Test
    fun `catalog does not force spanish or german and supports custom languages`() {
        val custom = LanguageCatalog.infoOrCustom("tlh", "Клингонский")
        assertEquals("Клингонский", custom.nameRu)
        assertEquals("🌐", custom.flag)
        assertTrue(LanguageCatalog.all.size >= 30)
        assertNull(Languages.legacyCourse("fr"))
        assertEquals("es", Languages.legacyCourse("es")?.id)
    }

    @Test
    fun `backup v1 restores legacy courses, v2 keeps custom ones`() {
        val v1 = BackupFile(
            format = BackupFile.LEGACY_FORMAT, formatVersion = 1, createdAt = "2026-10-28T12:00:00Z",
            entries = listOf(BackupEntry(id = "es-1", language = "es", term = "hola", translation = "привет", createdAt = 1, updatedAt = 1)),
            reviewStates = emptyList(), reviewHistory = emptyList(),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(v1))
        assertEquals(listOf("es"), BackupCodec.coursesOf(decoded).map { it.id })
        val v2 = v1.copy(
            format = BackupFile.FORMAT, formatVersion = 2,
            entries = v1.entries + BackupEntry(id = "it-1", language = "it-x", term = "ciao", translation = "привет", createdAt = 1, updatedAt = 1),
            courses = listOf(BackupCourse("es", "es", "Испанский", "🇪🇸"), BackupCourse("it-x", "it", "Итальянский", "🇮🇹")),
            unlocks = listOf(BackupUnlock("ACHIEVEMENT", "first_step", 5)),
            dailyProgress = listOf(BackupDay(20000, 12, 10, 10, 7)),
        )
        assertEquals(v2, BackupCodec.decode(BackupCodec.encode(v2)))
        val orphan = v2.copy(courses = v2.courses.take(1))
        assertFailsWith<VocabularyFormatException> { BackupCodec.decode(BackupCodec.encode(orphan)) }
    }
}

class TwoButtonReviewTest {
    private val fsrs = Fsrs()
    private val t0 = 1_800_000_000_000L

    @Test
    fun `remember maps to good and forgot maps to again`() {
        assertEquals(Rating.GOOD, Answer.REMEMBERED.rating)
        assertEquals(Rating.AGAIN, Answer.FORGOT.rating)
        assertEquals(fsrs.review(SrsState(), Rating.GOOD, t0), fsrs.review(SrsState(), Answer.REMEMBERED, t0))
        assertEquals(Answer.REMEMBERED, Answer.of(Rating.EASY))
        assertEquals(Answer.REMEMBERED, Answer.of(Rating.HARD))
        assertEquals(Answer.FORGOT, Answer.of(Rating.AGAIN))
    }

    @Test
    fun `remembering repeatedly grows intervals, forgetting resets to short step`() {
        var s = fsrs.review(SrsState(), Answer.REMEMBERED, t0)
        s = fsrs.review(s, Answer.REMEMBERED, s.due)
        assertEquals(CardState.REVIEW, s.state)
        var last = 0L
        repeat(4) {
            val n = fsrs.review(s, Answer.REMEMBERED, s.due)
            assertTrue(n.due - s.due > last)
            last = n.due - s.due
            s = n
        }
        val failed = fsrs.review(s, Answer.FORGOT, s.due)
        assertEquals(CardState.RELEARNING, failed.state)
        assertEquals(s.lapses + 1, failed.lapses)
        assertEquals(s.due + 10 * Fsrs.MIN_MS, failed.due)
    }

    @Test
    fun `history with legacy four-level ratings continues to schedule`() {
        var s = fsrs.review(SrsState(), Rating.EASY, t0) // старая оценка «Легко»
        s = fsrs.review(s, Answer.REMEMBERED, s.due)
        assertEquals(CardState.REVIEW, s.state)
        assertTrue(s.stability > FsrsParams.DEFAULT_W[3])
    }
}

class DailyStreakTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    private fun answers(map: MutableMap<Long, DayRecord>, epochDay: Long, n: Int, goal: Int = 3) {
        repeat(n) { map[epochDay] = DailyStreak.record(map[epochDay], epochDay, goal, remembered = true, now = epochDay * 86_400_000L + it) }
    }

    @Test
    fun `goal completion counts once per day`() {
        val days = mutableMapOf<Long, DayRecord>()
        val d = day(2026, 10, 28)
        answers(days, d, 2)
        assertFalse(days.getValue(d).completed)
        answers(days, d, 10)
        val rec = days.getValue(d)
        assertTrue(rec.completed)
        assertEquals(12, rec.answers)
        assertEquals(1, DailyStreak.state(days.values, d, 3).current)
    }

    @Test
    fun `crossing midnight splits answers into two days`() {
        val days = mutableMapOf<Long, DayRecord>()
        val before = ZonedDateTime.of(2026, 10, 28, 23, 59, 0, 0, zone).toLocalDate().toEpochDay()
        val after = ZonedDateTime.of(2026, 10, 29, 0, 1, 0, 0, zone).toLocalDate().toEpochDay()
        answers(days, before, 3)
        answers(days, after, 1)
        assertEquals(2, days.size)
        val s = DailyStreak.state(days.values, after, 3)
        assertEquals(1, s.current) // 29-е ещё не выполнено, серия держится с 28-го
        assertTrue(s.atRisk)
        assertEquals(1, s.todayAnswers)
        answers(days, after, 2)
        assertEquals(2, DailyStreak.state(days.values, after, 3).current)
    }

    @Test
    fun `timezone change does not rewrite recorded days`() {
        val days = mutableMapOf<Long, DayRecord>()
        val berlin = ZonedDateTime.of(2026, 10, 28, 22, 30, 0, 0, zone)
        val d1 = berlin.toLocalDate().toEpochDay()
        answers(days, d1, 3)
        // Перелёт в Токио: там уже 29-е. День 28-го остаётся выполненным, сегодня — 29-е.
        val tokyoToday = berlin.withZoneSameInstant(ZoneId.of("Asia/Tokyo")).toLocalDate().toEpochDay()
        assertEquals(d1 + 1, tokyoToday)
        val s = DailyStreak.state(days.values, tokyoToday, 3)
        assertEquals(1, s.current)
        assertFalse(s.todayCompleted)
    }

    @Test
    fun `missed day resets streak but keeps record`() {
        val days = mutableMapOf<Long, DayRecord>()
        val start = day(2026, 10, 1)
        for (i in 0 until 5) answers(days, start + i, 3)
        assertEquals(5, DailyStreak.state(days.values, start + 4, 3).current)
        // 6-й день пропущен, 7-й выполнен.
        answers(days, start + 6, 3)
        val s = DailyStreak.state(days.values, start + 6, 3)
        assertEquals(1, s.current)
        assertEquals(5, s.best)
        // Сегодня (8-й) ещё ничего: серия 1 держится со вчера.
        assertEquals(1, DailyStreak.state(days.values, start + 7, 3).current)
        // Если и 8-й прошёл без занятий, на 9-й серия 0.
        assertEquals(0, DailyStreak.state(days.values, start + 8, 3).current)
    }

    @Test
    fun `reopening the app without answers changes nothing`() {
        val days = mutableMapOf<Long, DayRecord>()
        val d = day(2026, 10, 28)
        answers(days, d, 3)
        val first = DailyStreak.state(days.values, d, 3)
        repeat(5) { assertEquals(first, DailyStreak.state(days.values, d, 3)) }
        assertEquals(1, days.size)
    }

    @Test
    fun `raising goal later does not revoke completed day, lowering completes it`() {
        val days = mutableMapOf<Long, DayRecord>()
        val d = day(2026, 10, 28)
        answers(days, d, 3, goal = 3)
        assertTrue(DailyStreak.state(days.values, d, 50).todayCompleted)
        val other = d + 1
        answers(days, other, 4, goal = 10)
        assertFalse(days.getValue(other).completed)
        days[other] = DailyStreak.applyGoal(days[other], 4, 99)!!
        assertTrue(days.getValue(other).completed)
    }

    @Test
    fun `milestones and progress`() {
        val days = mutableMapOf<Long, DayRecord>()
        val d = day(2026, 10, 28)
        answers(days, d, 1, goal = 4)
        val s = DailyStreak.state(days.values, d, 4)
        assertEquals(0.25f, s.todayProgress)
        assertEquals(3, s.nextMilestone)
        assertEquals(3, s.daysToMilestone)
    }
}

class GameEngineTest {
    private fun owned(e: Evaluation, prev: Map<UnlockKey, Int> = emptyMap()) =
        prev + e.unlocked.associate { it.key to it.level }

    @Test
    fun `fresh user gets start region and free icons only`() {
        val e = GameEngine.evaluate(ProgressSnapshot(), emptyMap())
        val keys = e.unlocked.map { it.key }.toSet()
        assertTrue(UnlockKey(UnlockKind.REGION, "meadow") in keys)
        assertTrue(UnlockKey(UnlockKind.ICON, AppIcons.DEFAULT) in keys)
        assertTrue(UnlockKey(UnlockKind.ICON, "classic") in keys)
        assertTrue(keys.none { it.kind == UnlockKind.ACHIEVEMENT || it.kind == UnlockKind.CREATURE })
    }

    @Test
    fun `first session unlocks first step, lingvik and the reward icon`() {
        val base = GameEngine.evaluate(ProgressSnapshot(), emptyMap())
        val e = GameEngine.evaluate(ProgressSnapshot(sessions = 1, answers = 10, remembered = 8), owned(base))
        val keys = e.unlocked.map { it.key }
        assertTrue(UnlockKey(UnlockKind.ACHIEVEMENT, "first_step") in keys)
        assertTrue(UnlockKey(UnlockKind.CREATURE, "lingvik") in keys)
        assertTrue(UnlockKey(UnlockKind.ICON, "open_book") in keys)
    }

    @Test
    fun `regions open in order and never skip`() {
        // Достаточно слов для «Города», но «Мост привычки» не открыт — город ждёт.
        val s = ProgressSnapshot(remembered = 30, wordsAdded = 500, goalDays = 0)
        val e = GameEngine.evaluate(s, emptyMap())
        val regions = e.unlocked.filter { it.key.kind == UnlockKind.REGION }.map { it.key.id }
        assertEquals(listOf("meadow", "grove"), regions)
        assertEquals("bridge", GameEngine.nextRegion(owned(e).keys)?.id)
        assertEquals("grove", GameEngine.currentRegion(owned(e).keys).id)
    }

    @Test
    fun `chained unlocks resolve - regions feed creatures and achievements`() {
        val s = ProgressSnapshot(sessions = 5, answers = 500, remembered = 400, wordsAdded = 150, goalDays = 4, courses = 2, bestStreak = 4)
        val e = GameEngine.evaluate(s, emptyMap())
        val keys = e.unlocked.map { it.key }.toSet()
        assertEquals(5, keys.count { it.kind == UnlockKind.REGION }) // meadow..archipelago
        assertTrue(UnlockKey(UnlockKind.CREATURE, "globi") in keys) // требует 3 области
        assertTrue(UnlockKey(UnlockKind.ACHIEVEMENT, "pathfinder") in keys)
        assertTrue(UnlockKey(UnlockKind.ACHIEVEMENT, "horizons") in keys)
        assertEquals(e.snapshot.regions, 5)
    }

    @Test
    fun `already owned items are never revoked or re-reported`() {
        val first = GameEngine.evaluate(ProgressSnapshot(sessions = 1), emptyMap())
        val again = GameEngine.evaluate(ProgressSnapshot(), owned(first))
        assertTrue(again.unlocked.isEmpty())
        assertEquals(owned(first).keys.count { it.kind == UnlockKind.ACHIEVEMENT }, again.snapshot.achievements)
    }

    @Test
    fun `creatures evolve as metrics grow`() {
        val lingvik = Creatures.byId("lingvik")!!
        assertEquals(1, lingvik.level(ProgressSnapshot(answers = 10)))
        assertEquals(2, lingvik.level(ProgressSnapshot(answers = 200)))
        assertEquals(3, lingvik.level(ProgressSnapshot(answers = 5000)))
        val start = owned(GameEngine.evaluate(ProgressSnapshot(sessions = 1, answers = 10), emptyMap()))
        val later = GameEngine.evaluate(ProgressSnapshot(sessions = 3, answers = 250), start)
        assertEquals(2, later.levelUps["lingvik"])
        assertEquals("Знаток", lingvik.stageName(2))
    }

    @Test
    fun `nearest achievements are sorted by progress`() {
        val s = ProgressSnapshot(answers = 90, sessions = 1)
        val near = GameEngine.nearestAchievements(s, setOf(UnlockKey(UnlockKind.ACHIEVEMENT, "first_step")), 2)
        assertEquals("hundred", near.first().id)
    }

    @Test
    fun `definitions are consistent`() {
        assertEquals(Achievements.all.size, Achievements.all.map { it.id }.toSet().size)
        assertEquals(WorldMap.regions.size, Achievements.byId("world_lord")!!.requirement.target)
        Creatures.all.forEach { c ->
            assertTrue(WorldMap.byId(c.homeRegion) != null, c.id)
            assertEquals(c.maxLevel, c.stageNames.size, c.id)
            assertEquals(c.evolutionThresholds.sorted(), c.evolutionThresholds, c.id)
        }
        AppIcons.all.forEach { i -> i.achievementId?.let { assertTrue(Achievements.byId(it) != null) } }
        assertEquals(AppIcons.all.size, AppIcons.all.map { it.id }.toSet().size)
        assertEquals(19, AppIcons.all.size)
        // Новичок быстро получает первую награду: самые лёгкие условия — 1 занятие.
        assertEquals(1, Achievements.all.minOf { it.requirement.target })
    }
}

class GoalRemindersTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun `no reminder after the daily goal is reached or when nothing to learn`() {
        val cfg = ReminderConfig()
        val now = ZonedDateTime.of(2026, 10, 28, 19, 0, 0, 0, zone)
        assertTrue(ReminderPlanner.decide(cfg, now, 5, null, true).notify)
        assertFalse(ReminderPlanner.decide(cfg, now, 5, null, true, goalCompleted = true).notify)
        assertFalse(ReminderPlanner.decide(cfg, now, 0, null, true, newAvailable = 0).notify)
        assertTrue(ReminderPlanner.decide(cfg, now, 0, null, true, newAvailable = 7).notify)
    }

    @Test
    fun `reminder texts vary and mention streak at risk`() {
        val ctx = ReminderTexts.Context(listOf(Languages.SPANISH to 8), streak = 5, atRisk = true, todayAnswers = 2, goal = 10, creatureName = "Фларо")
        val texts = (0L until 10L).map { ReminderTexts.pick(ctx, it) }.toSet()
        assertTrue(texts.size >= 4)
        assertTrue(texts.any { it.title.contains("5") && it.body.contains("8 ответов") })
        assertTrue(texts.any { it.title.contains("Фларо") })
    }
}
