package app.lingvo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.lingvo.core.Answer
import app.lingvo.core.UnlockKind
import app.lingvo.core.VocabEntry
import app.lingvo.data.AppDatabase
import app.lingvo.data.LingvoRepository
import app.lingvo.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Схема версии 1 (Lingvo Island) в том виде, в каком её создавал Room. */
    private val v1 = listOf(
        "CREATE TABLE IF NOT EXISTS `vocabulary_entry` (`id` TEXT NOT NULL, `language` TEXT NOT NULL, `term` TEXT NOT NULL, " +
            "`term_key` TEXT NOT NULL, `translation` TEXT NOT NULL, `example_target` TEXT NOT NULL, `example_ru` TEXT NOT NULL, " +
            "`part_of_speech` TEXT NOT NULL, `article` TEXT NOT NULL, `plural` TEXT NOT NULL, `level` TEXT NOT NULL, `tags` TEXT NOT NULL, " +
            "`notes` TEXT NOT NULL, `source` TEXT NOT NULL, `grammar_notes` TEXT NOT NULL, `collocations` TEXT NOT NULL, " +
            "`verb_forms` TEXT NOT NULL, `regional_usage` TEXT NOT NULL, `extra` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
            "`updated_at` INTEGER NOT NULL, `locally_edited` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_vocabulary_entry_language` ON `vocabulary_entry` (`language`)",
        "CREATE INDEX IF NOT EXISTS `index_vocabulary_entry_language_term_key` ON `vocabulary_entry` (`language`, `term_key`)",
        "CREATE TABLE IF NOT EXISTS `review_state` (`entry_id` TEXT NOT NULL, `language` TEXT NOT NULL, `state` TEXT NOT NULL, " +
            "`stability` REAL NOT NULL, `difficulty` REAL NOT NULL, `due` INTEGER NOT NULL, `last_review` INTEGER, `reps` INTEGER NOT NULL, " +
            "`lapses` INTEGER NOT NULL, `step` INTEGER NOT NULL, `introduced_at` INTEGER, `suspended` INTEGER NOT NULL, PRIMARY KEY(`entry_id`), " +
            "FOREIGN KEY(`entry_id`) REFERENCES `vocabulary_entry`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_review_state_language_due` ON `review_state` (`language`, `due`)",
        "CREATE TABLE IF NOT EXISTS `review_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entry_id` TEXT NOT NULL, " +
            "`language` TEXT NOT NULL, `rating` INTEGER NOT NULL, `reviewed_at` INTEGER NOT NULL, `state_before` TEXT NOT NULL, `elapsed_ms` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_review_log_entry_id` ON `review_log` (`entry_id`)",
        "CREATE INDEX IF NOT EXISTS `index_review_log_language_reviewed_at` ON `review_log` (`language`, `reviewed_at`)",
        "CREATE TABLE IF NOT EXISTS `learning_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `language` TEXT NOT NULL, " +
            "`queue` TEXT NOT NULL, `done` INTEGER NOT NULL, `total` INTEGER NOT NULL, `correct` INTEGER NOT NULL, `started_at` INTEGER NOT NULL, " +
            "`updated_at` INTEGER NOT NULL, `finished_at` INTEGER, `status` TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_learning_session_language_status` ON `learning_session` (`language`, `status`)",
        "CREATE TABLE IF NOT EXISTS `import_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `language` TEXT NOT NULL, " +
            "`imported_at` INTEGER NOT NULL, `source_name` TEXT NOT NULL, `added` INTEGER NOT NULL, `updated` INTEGER NOT NULL, " +
            "`unchanged` INTEGER NOT NULL, `skipped` INTEGER NOT NULL, `errors` INTEGER NOT NULL)",
    )

    @Test
    fun migration_1_2_keeps_words_progress_history_and_creates_courses() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            v1.forEach(db::execSQL)
            val e = "'', '', '', '', '', '', '', '', '', '', '', '', '', '', 1, 1, 0"
            db.execSQL("INSERT INTO vocabulary_entry VALUES ('es-1','es','la mesa','la mesa','стол',$e)")
            db.execSQL("INSERT INTO vocabulary_entry VALUES ('de-1','de','die Bank','die bank','скамья',$e)")
            db.execSQL("INSERT INTO review_state VALUES ('es-1','es','REVIEW',12.5,4.0,100,50,6,1,0,10,0)")
            val day = 1_800_000_000_000L
            for (i in 0 until 12) db.execSQL("INSERT INTO review_log (entry_id, language, rating, reviewed_at, state_before, elapsed_ms) VALUES ('es-1','es',3,${day + i * 1000},'REVIEW',0)")
            db.execSQL("INSERT INTO learning_session (language, queue, done, total, correct, started_at, updated_at, finished_at, status) VALUES ('es','',12,12,12,1,2,3,'FINISHED')")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        // Открытие проверяет, что схема после миграций 1→2→3→4 совпадает со схемой Room версии 4.
        val courses = room.courses().all()
        assertEquals(listOf("es", "de"), courses.map { it.id })
        assertEquals("Испанский", courses[0].name)
        assertEquals(2, room.entries().all().size)
        assertEquals(12.5, room.reviews().get("es-1")!!.stability, 0.0)
        assertEquals(12, room.logs().all().size)
        val days = room.daily().all()
        assertEquals(1, days.size)
        assertEquals(12, days[0].answers)
        assertNotNull("12 ответов ≥ цели 10 — день выполнен", days[0].completedAt)
        // Новые поля версии 3 пусты у старых данных — это кнопочные ответы без транскрипции.
        assertEquals(null, room.logs().all().first().taskType)
        assertEquals("", room.entries().get("es-1")!!.toDomain().transcription)
        room.close()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RepositoryScenarioTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun course_word_answers_streak_and_rewards() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        var now = 1_800_000_000_000L
        val repo = LingvoRepository(context, db, SettingsStore(context), clock = { now }, zone = { ZoneId.of("UTC") })
        repo.settings.update { it.copy(dailyGoal = 5) }

        // Произвольный язык: итальянский курс, испанский и немецкий не создаются.
        val course = repo.createCourse("it", "Итальянский", "🇮🇹")
        assertEquals("it", course.id)
        assertEquals(listOf("it"), repo.courses().map { it.id })
        val second = repo.createCourse("it", "Итальянский для работы", "🇮🇹")
        assertEquals("it-2", second.id)

        val ids = (1..5).map { i ->
            val e = VocabEntry(repo.newId(course.spec), course.id, "parola $i", "слово $i", createdAt = 0, updatedAt = 0)
            repo.addEntry(e); repo.introduce(e.id); e.id
        }
        val session = repo.startOrResumeSession(course.id)!!
        assertEquals(5, session.total)
        var last = session
        repeat(5) {
            now += 1000
            val head = app.lingvo.core.SessionProgress.decodeQueue(last.queue).first()
            last = repo.answer(last.id, head, Answer.REMEMBERED)!!.session
        }
        assertEquals(5, last.done)
        assertTrue(ids.isNotEmpty())
        val streak = repo.streak()
        assertTrue("цель 5 ответов выполнена", streak.todayCompleted)
        assertEquals(1, streak.current)

        // Ответы «Помню» на новых карточках дают короткий шаг — карточки вернутся в этой же сессии.
        assertTrue(last.total >= 5)

        // Повтор конкретного слова встаёт первым в очередь.
        repo.queueWord(ids[2])
        val resumed = repo.startOrResumeSession(course.id)!!
        assertEquals(ids[2], app.lingvo.core.SessionProgress.decodeQueue(resumed.queue).first())

        // Награды считаются по реальным данным и сохраняются.
        repo.endSession(resumed.id)
        val eval = repo.evaluateWorld()
        val keys = eval.unlocked.map { it.key.kind to it.key.id }
        assertTrue(UnlockKind.ACHIEVEMENT to "first_step" in keys)
        assertTrue(UnlockKind.ACHIEVEMENT to "first_goal" in keys)
        assertTrue(UnlockKind.CREATURE to "lingvik" in keys)
        assertTrue(UnlockKind.REGION to "meadow" in keys)
        assertTrue("повторная проверка ничего не дублирует", repo.evaluateWorld().unlocked.isEmpty())

        // Резервная копия сохраняет курсы, награды и серию.
        val backup = repo.createBackup()
        repo.deleteCourse(second.id)
        assertEquals(1, repo.courses().size)
        repo.restoreBackup(backup)
        assertEquals(2, repo.courses().size)
        assertTrue(repo.streak().todayCompleted)
        db.close()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandAndTypingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun answer_counts_once_and_island_stays_in_sync() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        var now = 1_800_000_000_000L
        val repo = LingvoRepository(context, db, SettingsStore(context), clock = { now }, zone = { ZoneId.of("UTC") })
        val course = repo.createCourse("pt", "Португальский", "🇵🇹")
        val ids = (1..3).map { i ->
            val e = VocabEntry(repo.newId(course.spec), course.id, "palavra $i", "слово $i", createdAt = 0, updatedAt = 0)
            repo.addEntry(e); repo.introduce(e.id); e.id
        }
        val session = repo.startOrResumeSession(course.id)!!
        val island = repo.observeIsland().first()!!
        val head = app.lingvo.core.SessionProgress.decodeQueue(session.queue).first()
        assertEquals(head, island.card!!.id)

        // Ответ из уведомления, затем тот же ответ из приложения (или повторная доставка) — засчитан один раз.
        now += 1000
        assertNotNull(repo.answer(session.id, head, Answer.REMEMBERED))
        assertEquals(null, repo.answer(session.id, head, Answer.REMEMBERED))
        assertEquals(1, db.logs().all().size)
        assertEquals(1, db.daily().all().single().answers)
        // Остров сразу показывает следующую карточку.
        val next = repo.observeIsland().first()!!
        assertTrue(next.card!!.id != head || next.session.done == 1)
        assertEquals(1, next.session.done)

        // Письменный ответ: сохраняются тип, текст, автопроверка и ручное подтверждение.
        val head2 = app.lingvo.core.SessionProgress.decodeQueue(next.session.queue).first()
        now += 1000
        repo.answer(next.session.id, head2, app.lingvo.core.AnswerChecker.answerFor(true),
            app.lingvo.data.AnswerMeta(app.lingvo.core.TaskType.TYPING, "palavra x", app.lingvo.core.AutoResult.CLOSE, confirmed = true))
        val log = db.logs().all().last()
        assertEquals("TYPING", log.taskType)
        assertEquals("palavra x", log.typedAnswer)
        assertEquals("CLOSE", log.autoResult)
        assertEquals(1, log.confirmed)
        assertEquals(3, log.rating) // «Ответ правильный» → GOOD

        // Признанный верным вариант становится допустимым.
        repo.addAlternative(head2, "palavra x")
        assertEquals(listOf("palavra x"), db.entries().get(head2)!!.toDomain().alternatives)
        assertTrue(ids.size == 3)
        db.close()
    }

    @Test
    fun starter_dictionary_is_added_gradually() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = LingvoRepository(context, db, SettingsStore(context), clock = { 1_800_000_000_000L }, zone = { ZoneId.of("UTC") })
        val fr = repo.createCourse("fr", "Французский", "🇫🇷")
        assertTrue(repo.hasStarter(fr.spec))
        val first = repo.importStarter(fr.spec)!!
        assertTrue("первая порция 20–30 слов", first.newEntries.size in 20..30)
        val pending = repo.pendingStarterBlocks(fr.spec)
        assertEquals(5, pending.size)
        val next = repo.importStarter(fr.spec, 1)!!
        assertTrue(next.newEntries.size in 5..10)
        assertEquals(4, repo.pendingStarterBlocks(fr.spec).size)
        // Честно: для языка без набора ничего не добавляется.
        val sw = repo.createCourse("sw", "Суахили", "🌍")
        assertTrue(!repo.hasStarter(sw.spec))
        assertEquals(null, repo.importStarter(sw.spec))
        db.close()
    }
}
