package app.lingvo.data

import android.content.Context
import androidx.room.withTransaction
import app.lingvo.core.Answer
import app.lingvo.core.BackupCodec
import app.lingvo.core.BackupCourse
import app.lingvo.core.BackupDay
import app.lingvo.core.BackupUnlock
import app.lingvo.core.Course
import app.lingvo.core.DailyStreak
import app.lingvo.core.DayRecord
import app.lingvo.core.Evaluation
import app.lingvo.core.GameEngine
import app.lingvo.core.LanguageCatalog
import app.lingvo.core.ProgressSnapshot
import app.lingvo.core.StreakState
import app.lingvo.core.UnlockKey
import app.lingvo.core.UnlockKind
import app.lingvo.core.BackupEntry
import app.lingvo.core.BackupFile
import app.lingvo.core.BackupHistory
import app.lingvo.core.BackupReviewState
import app.lingvo.core.BulkParser
import app.lingvo.core.CardState
import app.lingvo.core.ConflictPolicy
import app.lingvo.core.Fsrs
import app.lingvo.core.ImportPlan
import app.lingvo.core.ImportPlanner
import app.lingvo.core.IsoTime
import app.lingvo.core.LanguageSpec
import app.lingvo.core.LearningStatus
import app.lingvo.core.LocalEntry
import app.lingvo.core.Normalizer
import app.lingvo.core.ParsedVocabulary
import app.lingvo.core.PartsOfSpeech
import app.lingvo.core.Rating
import app.lingvo.core.SessionProgress
import app.lingvo.core.StatusClassifier
import app.lingvo.core.VocabEntry
import app.lingvo.core.VocabularyJson
import app.lingvo.core.orderForReview
import app.lingvo.core.Starter
import app.lingvo.core.StarterBlock
import app.lingvo.core.TaskType
import app.lingvo.core.AutoResult
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Карточка с прогрессом и вычисленным статусом — модель для интерфейса. */
data class Card(
    val entry: VocabEntry,
    val state: ReviewStateEntity,
    val status: LearningStatus,
    val locallyEdited: Boolean,
) {
    val id: String get() = entry.id
}

data class AnswerResult(val session: LearningSessionEntity, val nextDue: Long, val day: DayRecord)

/** Как был получен ответ: кнопки или письменный режим с ручным подтверждением. */
data class AnswerMeta(
    val taskType: TaskType = TaskType.BUTTONS,
    val typedAnswer: String? = null,
    val autoResult: AutoResult? = null,
    /** Ручное подтверждение пользователя в письменном режиме. */
    val confirmed: Boolean? = null,
)

/** Состояние «острова»: текущая карточка активной сессии — общий источник для приложения, уведомления и капсулы. */
data class IslandState(
    val session: LearningSessionEntity,
    val course: Course,
    val card: VocabEntry?,
) {
    val remaining: Int get() = (session.total - session.done).coerceAtLeast(0)
}

class LingvoRepository(
    private val context: Context,
    private val db: AppDatabase,
    val settings: SettingsStore,
    private val fsrs: Fsrs = Fsrs(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val entries = db.entries()
    private val reviews = db.reviews()
    private val logs = db.logs()
    private val sessions = db.sessions()
    private val imports = db.imports()
    private val courses = db.courses()
    private val unlocks = db.unlocks()
    private val daily = db.daily()

    fun now(): Long = clock()
    fun today(): Long = LocalDate.now(zone()).toEpochDay()
    fun startOfToday(): Long = LocalDate.now(zone()).atStartOfDay(zone()).toInstant().toEpochMilli()

    // ---------- Наблюдение ----------

    fun observeCards(language: String): Flow<List<Card>> =
        entries.observeWithState(language).map { list -> toCards(list, recentRatingsByEntry()) }

    fun observeAllCards(): Flow<List<Card>> =
        entries.observeAllWithState().map { list -> toCards(list, recentRatingsByEntry()) }

    fun observeCard(id: String): Flow<Card?> = entries.observe(id).map { e ->
        e?.let { toCards(listOf(it), recentRatingsByEntry(it.entry.id)).firstOrNull() }
    }

    fun observeLogs(): Flow<List<ReviewLogEntity>> = logs.observeAll()
    fun observeEntryLogs(id: String): Flow<List<ReviewLogEntity>> = logs.observeForEntry(id)
    fun observeActiveSessions(): Flow<List<LearningSessionEntity>> = sessions.observeActive()
    fun observeSession(id: Long): Flow<LearningSessionEntity?> = sessions.observe(id)
    fun observeRecentSessions(): Flow<List<LearningSessionEntity>> = sessions.observeRecent(30)
    fun observeAllSessions(): Flow<List<LearningSessionEntity>> = sessions.observeAll()
    fun observeImportHistory() = imports.observeRecent()
    fun observeTotalEntries(): Flow<Int> = entries.observeTotal()

    private suspend fun recentRatingsByEntry(onlyId: String? = null): Map<String, List<Rating>> =
        logs.all().asSequence()
            .filter { onlyId == null || it.entryId == onlyId }
            .groupBy { it.entryId }
            .mapValues { (_, l) -> l.takeLast(3).map { Rating.of(it.rating) } }

    private fun toCards(list: List<EntryWithState>, ratings: Map<String, List<Rating>>): List<Card> = list.map { ews ->
        val state = ews.state ?: ReviewStateEntity.fresh(ews.entry.id, ews.entry.language)
        Card(
            entry = ews.entry.toDomain(),
            state = state,
            status = StatusClassifier.classify(state.toSrs(), state.introducedAt != null, state.suspended, ratings[ews.entry.id].orEmpty()),
            locallyEdited = ews.entry.locallyEdited,
        )
    }

    suspend fun counts(language: String) = reviews.counts(language, now())
    suspend fun dueCount(language: String) = reviews.dueCount(language, now())
    suspend fun lastActivity(): Long? = logs.lastActivity()
    suspend fun nextDue(language: String): Long? = reviews.nextDue(language, now())
    fun retrievability(state: ReviewStateEntity): Double = fsrs.retrievability(state.toSrs(), now())

    // ---------- Курсы ----------

    fun observeCourses(): Flow<List<Course>> = courses.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun courses(): List<Course> = courses.all().map { it.toDomain() }
    suspend fun course(id: String): Course? = courses.get(id)?.toDomain()
    suspend fun spec(id: String): LanguageSpec? = course(id)?.spec

    /**
     * Создаёт курс. ID — код языка, если такой курс ещё не создан (совместимость со словарями
     * spanish/german_vocabulary.json), иначе код с суффиксом.
     */
    suspend fun createCourse(
        languageCode: String, name: String, flag: String, translationCode: String = "ru", translationName: String = "Русский",
    ): Course = db.withTransaction {
        val code = languageCode.trim().lowercase().ifEmpty { "xx" }.replace(Regex("[^a-z0-9-]"), "")
        val existing = courses.all().map { it.id }.toSet()
        val base = if (translationCode == "ru") code else "$code-$translationCode"
        var id = base
        var n = 2
        while (id in existing) id = "$base-${n++}"
        val course = Course(
            id = id, languageCode = code, name = name.trim().ifEmpty { LanguageCatalog.infoOrCustom(code).nameRu },
            flag = flag.ifBlank { LanguageCatalog.infoOrCustom(code).flag }, translationCode = translationCode,
            translationName = translationName, createdAt = now(), sortOrder = existing.size,
        )
        courses.upsert(CourseEntity.from(course))
        course
    }

    suspend fun updateCourse(course: Course) = courses.upsert(CourseEntity.from(course))

    /** Удаляет курс вместе с его карточками и прогрессом. История ответов остаётся для общей статистики и серий. */
    suspend fun deleteCourse(id: String) {
        autoBackup("before-delete-course")
        db.withTransaction {
            entries.deleteByLanguage(id)
            sessions.deleteByLanguage(id)
            courses.delete(id)
        }
        settings.removeCourse(id)
    }

    /** Есть ли встроенный стартовый набор для курса (сейчас — испанский и немецкий с переводом на русский). */
    fun hasStarter(spec: LanguageSpec): Boolean = Starter.has(spec.languageCode, spec.translationCode)

    private suspend fun starterText(spec: LanguageSpec): String? = if (!hasStarter(spec)) null else withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(Starter.assetPath(spec.languageCode, spec.translationCode)).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    /** Блоки стартового словаря, которые ещё не добавлены в курс. */
    suspend fun pendingStarterBlocks(spec: LanguageSpec): List<StarterBlock> {
        val text = starterText(spec) ?: return emptyList()
        val ids = entries.allIds().toSet()
        return Starter.pendingBlocks(Starter.blocks(text, spec.code), ids)
    }

    // ---------- Редактирование словаря ----------

    fun newId(spec: LanguageSpec): String = "${spec.idPrefix}-${UUID.randomUUID().toString().take(12)}"

    /** Возвращает карточки того же языка с тем же нормализованным выражением. */
    suspend fun findDuplicates(language: String, term: String, article: String, excludeId: String? = null): List<VocabEntry> =
        entries.byKey(language, Normalizer.entryKey(term, article)).filter { it.id != excludeId }.map { it.toDomain() }

    /** Вызывается после того, как пользователь добавил слова (вручную или списком), — например, чтобы нарисовать картинки. */
    var onWordsAdded: ((List<VocabEntry>, LanguageSpec) -> Unit)? = null

    suspend fun addEntry(entry: VocabEntry) {
        val clean = db.withTransaction {
            val t = now()
            val clean = entry.copy(createdAt = t, updatedAt = t, partOfSpeech = PartsOfSpeech.canonical(entry.partOfSpeech))
            entries.insert(listOf(VocabularyEntryEntity.from(clean, locallyEdited = true)))
            reviews.insertIgnore(listOf(ReviewStateEntity.fresh(clean.id, clean.language)))
            clean
        }
        spec(clean.language)?.let { sp -> runCatching { onWordsAdded?.invoke(listOf(clean), sp) } }
    }

    /** Изменение текста карточки не сбрасывает прогресс повторений. */
    suspend fun updateEntry(entry: VocabEntry) = db.withTransaction {
        val old = entries.get(entry.id) ?: error("Карточка не найдена")
        val updated = entry.copy(language = old.language, createdAt = old.createdAt, updatedAt = now(),
            partOfSpeech = PartsOfSpeech.canonical(entry.partOfSpeech))
        entries.update(listOf(VocabularyEntryEntity.from(updated, locallyEdited = true)))
    }

    suspend fun deleteEntry(id: String) = entries.delete(id)

    /** Письменный режим: пользователь признал свой вариант верным — он становится допустимым для этой карточки. */
    suspend fun addAlternative(id: String, variant: String) = db.withTransaction {
        val e = entries.get(id) ?: return@withTransaction
        val clean = Normalizer.clean(variant)
        val list = VocabularyEntryEntity.splitList(e.alternatives.orEmpty())
        if (clean.isEmpty() || list.any { it.equals(clean, ignoreCase = false) }) return@withTransaction
        entries.update(listOf(e.copy(alternatives = VocabularyEntryEntity.joinList(list + clean), updatedAt = now(), locallyEdited = true)))
    }

    suspend fun setSuspended(id: String, suspended: Boolean) = db.withTransaction {
        val st = reviews.get(id) ?: return@withTransaction
        reviews.upsert(st.copy(suspended = suspended))
    }

    /** Сбрасывает прогресс одной карточки (по явному запросу пользователя). */
    suspend fun resetProgress(id: String) = db.withTransaction {
        val st = reviews.get(id) ?: return@withTransaction
        reviews.upsert(ReviewStateEntity.fresh(id, st.language))
    }

    data class BulkResult(val added: Int, val duplicates: List<String>, val errors: List<String>)

    suspend fun bulkAdd(spec: LanguageSpec, text: String, tags: List<String>): BulkResult {
        val parsed = BulkParser.parse(text)
        val dupes = ArrayList<String>()
        val addedEntries = ArrayList<VocabEntry>()
        var added = 0
        db.withTransaction {
            val seen = HashSet<String>()
            for (line in parsed.lines) {
                val article = if (spec.articles.isNotEmpty()) Normalizer.detectArticle(line.term, spec) else ""
                val key = Normalizer.entryKey(line.term, article)
                if (!seen.add(key) || entries.byKey(spec.code, key).isNotEmpty()) { dupes += line.term; continue }
                val t = now()
                val e = VocabEntry(
                    id = newId(spec), language = spec.code, term = line.term, translation = line.translation,
                    exampleTarget = line.example, exampleRu = line.exampleRu, article = article,
                    partOfSpeech = if (article.isNotEmpty()) PartsOfSpeech.NOUN else "",
                    tags = Normalizer.tags(tags), source = "Массовое добавление", createdAt = t, updatedAt = t,
                )
                entries.insert(listOf(VocabularyEntryEntity.from(e, locallyEdited = true)))
                reviews.insertIgnore(listOf(ReviewStateEntity.fresh(e.id, e.language)))
                addedEntries += e
                added++
            }
        }
        if (addedEntries.isNotEmpty()) runCatching { onWordsAdded?.invoke(addedEntries, spec) }
        return BulkResult(added, dupes, parsed.errors)
    }

    // ---------- Импорт / экспорт ----------

    fun parseVocabulary(text: String, spec: LanguageSpec?): ParsedVocabulary = VocabularyJson.parse(text, spec, now())

    suspend fun planImport(parsed: ParsedVocabulary, policy: ConflictPolicy): ImportPlan = withContext(Dispatchers.IO) {
        val local = entries.byLanguage(parsed.language.code).map { LocalEntry(it.toDomain(), it.locallyEdited) }
        ImportPlanner().plan(parsed, local, entries.idsOutside(parsed.language.code).toSet(), policy, now())
    }

    /**
     * Применяет импорт в одной транзакции: при любой ошибке база остаётся без изменений.
     * Перед применением сохраняется автоматическая резервная копия.
     */
    suspend fun applyImport(parsed: ParsedVocabulary, policy: ConflictPolicy, sourceName: String): ImportPlan {
        autoBackup("before-import")
        return db.withTransaction {
            val local = entries.byLanguage(parsed.language.code)
            val editedById = local.associate { it.id to it.locallyEdited }
            val plan = ImportPlanner().plan(
                parsed,
                local.map { LocalEntry(it.toDomain(), it.locallyEdited) },
                entries.idsOutside(parsed.language.code).toSet(),
                policy,
                now(),
            )
            if (plan.newEntries.isNotEmpty()) {
                entries.insert(plan.newEntries.map { VocabularyEntryEntity.from(it, locallyEdited = false) })
                reviews.insertIgnore(plan.newEntries.map { ReviewStateEntity.fresh(it.id, it.language) })
            }
            val changed = plan.changed
            if (changed.isNotEmpty()) {
                entries.update(changed.map { VocabularyEntryEntity.from(it.after, editedById[it.after.id] ?: false) })
            }
            imports.insert(
                VocabularyImportHistoryEntity(
                    language = parsed.language.code, importedAt = now(), sourceName = sourceName,
                    added = plan.newEntries.size, updated = changed.size, unchanged = plan.unchangedTotal,
                    skipped = plan.skipped.size, errors = plan.problems.size,
                )
            )
            plan
        }
    }

    suspend fun exportVocabulary(spec: LanguageSpec): String = withContext(Dispatchers.IO) {
        VocabularyJson.serialize(spec, entries.byLanguage(spec.code).map { it.toDomain() }, now())
    }

    suspend fun entriesOf(spec: LanguageSpec): List<VocabEntry> = withContext(Dispatchers.IO) {
        entries.byLanguage(spec.code).map { it.toDomain() }
    }

    /**
     * Добавляет в курс следующие [blocks] блоков стартового словаря (по умолчанию — первую порцию).
     * Уже добавленные блоки пропускаются, совпадающие слова пользователя не перезаписываются.
     * null — для этого языка стартового словаря нет.
     */
    suspend fun importStarter(spec: LanguageSpec, blocks: Int = Starter.FIRST_BLOCKS): ImportPlan? {
        val text = starterText(spec) ?: return null
        val pending = Starter.pendingBlocks(Starter.blocks(text, spec.code), entries.allIds().toSet()).take(blocks)
        if (pending.isEmpty()) return null
        val json = Starter.select(text, pending.map { it.number }.toSet(), spec)
        val title = pending.joinToString(", ") { it.title }
        return applyImport(parseVocabulary(json, spec), ConflictPolicy.KEEP_LOCAL, "Стартовый словарь: $title")
    }

    // ---------- Резервные копии ----------

    suspend fun createBackup(): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            val all = entries.all()
            BackupCodec.encode(
                BackupFile(
                    createdAt = IsoTime.format(now()),
                    entries = all.map {
                        BackupEntry(
                            id = it.id, language = it.language, term = it.term, translation = it.translation,
                            exampleTarget = it.exampleTarget, exampleRu = it.exampleRu, partOfSpeech = it.partOfSpeech,
                            article = it.article, plural = it.plural, level = it.level,
                            tags = VocabularyEntryEntity.splitList(it.tags), notes = it.notes, source = it.source,
                            grammarNotes = it.grammarNotes, collocations = VocabularyEntryEntity.splitList(it.collocations),
                            verbFormsJson = it.verbForms, regionalUsage = it.regionalUsage, extra = it.extra,
                            createdAt = it.createdAt, updatedAt = it.updatedAt, locallyEdited = it.locallyEdited,
                            transcription = it.transcription.orEmpty(),
                            alternatives = VocabularyEntryEntity.splitList(it.alternatives.orEmpty()),
                        )
                    },
                    reviewStates = reviews.all().map {
                        BackupReviewState(it.entryId, it.state, it.stability, it.difficulty, it.due, it.lastReview,
                            it.reps, it.lapses, it.step, it.introducedAt, it.suspended)
                    },
                    reviewHistory = logs.all().map {
                        BackupHistory(it.entryId, it.language, it.rating, it.reviewedAt, it.stateBefore, it.elapsedMs,
                            it.taskType, it.typedAnswer, it.autoResult, it.confirmed?.let { c -> c == 1 })
                    },
                    settings = settings.exportMap(),
                    courses = courses.all().map {
                        BackupCourse(it.id, it.languageCode, it.name, it.flag, it.translationCode, it.translationName, it.createdAt, it.sortOrder)
                    },
                    unlocks = unlocks.all().map { BackupUnlock(it.kind, it.itemId, it.unlockedAt, it.level) },
                    dailyProgress = daily.all().map { BackupDay(it.epochDay, it.answers, it.remembered, it.goal, it.completedAt) },
                )
            )
        }
    }

    /** Полностью заменяет данные содержимым резервной копии (после подтверждения пользователя). */
    suspend fun restoreBackup(text: String): Int {
        val backup = BackupCodec.decode(text) // проверка до любых изменений
        autoBackup("before-restore")
        db.withTransaction {
            sessions.deleteAll()
            logs.deleteAll()
            reviews.deleteAll()
            entries.deleteAll()
            courses.deleteAll()
            unlocks.deleteAll()
            daily.deleteAll()
            courses.upsertAll(BackupCodec.coursesOf(backup).map {
                CourseEntity(it.id, it.languageCode, it.name, it.flag, it.translationCode, it.translationName, it.createdAt, it.sortOrder)
            })
            // Полученное ранее не отнимается: анимации уже были показаны.
            unlocks.upsertAll(backup.unlocks.map { UnlockEntity(it.kind, it.itemId, it.unlockedAt, it.level, seen = true) })
            daily.upsertAll(backup.dailyProgress.map { DailyProgressEntity(it.epochDay, it.answers, it.remembered, it.goal, it.completedAt) })
            entries.insert(backup.entries.map {
                VocabularyEntryEntity(
                    id = it.id, language = it.language, term = it.term, termKey = Normalizer.entryKey(it.term, it.article),
                    translation = it.translation, exampleTarget = it.exampleTarget, exampleRu = it.exampleRu,
                    partOfSpeech = it.partOfSpeech, article = it.article, plural = it.plural, level = it.level,
                    tags = VocabularyEntryEntity.joinList(it.tags), notes = it.notes, source = it.source,
                    grammarNotes = it.grammarNotes, collocations = VocabularyEntryEntity.joinList(it.collocations),
                    verbForms = it.verbFormsJson, regionalUsage = it.regionalUsage, extra = it.extra,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, locallyEdited = it.locallyEdited,
                    transcription = it.transcription, alternatives = VocabularyEntryEntity.joinList(it.alternatives),
                )
            })
            val statesById = backup.reviewStates.associateBy { it.entryId }
            reviews.upsertAll(backup.entries.map { e ->
                statesById[e.id]?.let {
                    ReviewStateEntity(it.entryId, e.language, it.state, it.stability, it.difficulty, it.due, it.lastReview,
                        it.reps, it.lapses, it.step, it.introducedAt, it.suspended)
                } ?: ReviewStateEntity.fresh(e.id, e.language)
            })
            logs.insertAll(backup.reviewHistory.map {
                ReviewLogEntity(entryId = it.entryId, language = it.language, rating = it.rating, reviewedAt = it.reviewedAt,
                    stateBefore = it.stateBefore, elapsedMs = it.elapsedMs, taskType = it.taskType,
                    typedAnswer = it.typedAnswer, autoResult = it.autoResult, confirmed = it.confirmed?.let { c -> if (c) 1 else 0 })
            })
        }
        if (backup.settings.isNotEmpty()) settings.importMap(backup.settings)
        if (backup.dailyProgress.isEmpty()) rebuildDailyFromLogs()
        evaluateWorld()
        return backup.entries.size
    }

    fun autoBackupDir(): File = File(context.filesDir, "backups").apply { mkdirs() }

    fun listAutoBackups(): List<File> =
        autoBackupDir().listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }.orEmpty()

    /** Автоматическая копия перед массовыми изменениями; хранятся 5 последних. */
    suspend fun autoBackup(reason: String) = withContext(Dispatchers.IO) {
        if (entries.allIds().isEmpty()) return@withContext
        val file = File(autoBackupDir(), "auto-$reason-${now()}.json")
        file.writeText(createBackup())
        listAutoBackups().drop(5).forEach { it.delete() }
    }

    // ---------- Обучение ----------

    suspend fun newCardsForToday(spec: LanguageSpec): Pair<List<Card>, Int> {
        val limit = settings.current().lang(spec.code).newPerDay
        val introduced = reviews.introducedSince(spec.code, startOfToday())
        val remaining = (limit - introduced).coerceAtLeast(0)
        val list = if (remaining == 0) emptyList() else reviews.newEntries(spec.code, remaining)
        return toCards(list, emptyMap()) to remaining
    }

    /** Знакомство с карточкой: она сразу попадает в очередь повторения, но не считается выученной. */
    suspend fun introduce(entryId: String) = db.withTransaction {
        val st = reviews.get(entryId) ?: return@withTransaction
        if (st.introducedAt != null) return@withTransaction
        val t = now()
        reviews.upsert(st.copy(introducedAt = t, due = t))
    }

    /** Продолжает незавершённую сессию языка или начинает новую из карточек к повторению. */
    suspend fun startOrResumeSession(language: String, maxCards: Int = 200): LearningSessionEntity? = db.withTransaction {
        val active = sessions.active(language)
        if (active != null) {
            val ids = SessionProgress.decodeQueue(active.queue)
            val existing = reviews.getMany(ids).filter { !it.suspended }.map { it.entryId }.toSet()
            val progress = SessionProgress(ids, active.done, active.total).retainOnly(existing)
            if (!progress.isFinished) {
                val resumed = active.copy(queue = SessionProgress.encodeQueue(progress.queue), total = progress.total, updatedAt = now())
                sessions.update(resumed)
                return@withTransaction resumed
            }
            sessions.update(active.copy(status = SessionStatus.FINISHED, finishedAt = now(), queue = ""))
        }
        val due = reviews.dueEntries(language, now(), maxCards)
            .orderForReview({ it.state?.due ?: 0L }, { it.state?.lapses ?: 0 })
        if (due.isEmpty()) return@withTransaction null
        val progress = SessionProgress.start(due.map { it.entry.id })
        val t = now()
        val entity = LearningSessionEntity(
            language = language, queue = SessionProgress.encodeQueue(progress.queue), done = 0, total = progress.total,
            correct = 0, startedAt = t, updatedAt = t, finishedAt = null, status = SessionStatus.ACTIVE,
        )
        entity.copy(id = sessions.insert(entity))
    }

    suspend fun session(id: Long) = sessions.get(id)

    suspend fun cardForSession(entryId: String): Card? {
        val e = entries.get(entryId) ?: return null
        val st = reviews.get(entryId) ?: return null
        return toCards(listOf(EntryWithState(e, st)), recentRatingsByEntry(entryId)).first()
    }

    /** Предпросмотр следующего интервала для обеих кнопок. */
    suspend fun previewIntervals(entryId: String): Map<Answer, Long> {
        val st = reviews.get(entryId) ?: return emptyMap()
        val t = now()
        return Answer.entries.associateWith { fsrs.review(st.toSrs(), it, t).due - t }
    }

    /**
     * Ответ «Помню»/«Не помню» (или подтверждённый письменный ответ) сохраняется сразу: состояние FSRS,
     * история, позиция сессии и прогресс дня — в одной транзакции.
     *
     * Ответ принимается только для карточки, которая сейчас первая в очереди активной сессии.
     * Поэтому повторное нажатие, ответ из уведомления после ответа в приложении (и наоборот)
     * или повторная доставка действия ничего не засчитывают дважды — возвращается null.
     */
    suspend fun answer(sessionId: Long, entryId: String, answer: Answer, meta: AnswerMeta = AnswerMeta()): AnswerResult? {
        val goal = settings.current().dailyGoal
        return db.withTransaction { answerInTransaction(sessionId, entryId, answer.rating, goal, meta) }
    }

    private suspend fun answerInTransaction(sessionId: Long, entryId: String, rating: Rating, goal: Int, meta: AnswerMeta): AnswerResult? {
        val t = now()
        val session = sessions.get(sessionId) ?: return null
        if (session.status != SessionStatus.ACTIVE) return null
        if (SessionProgress.decodeQueue(session.queue).firstOrNull() != entryId) return null
        val st = reviews.get(entryId) ?: return null
        val before = st.toSrs()
        val after = fsrs.review(before, rating, t)
        reviews.upsert(st.withSrs(after))
        logs.insert(
            ReviewLogEntity(
                entryId = entryId, language = st.language, rating = rating.value, reviewedAt = t,
                stateBefore = before.state.name, elapsedMs = before.lastReview?.let { t - it } ?: 0L,
                taskType = meta.taskType.name, typedAnswer = meta.typedAnswer, autoResult = meta.autoResult?.name,
                confirmed = meta.confirmed?.let { if (it) 1 else 0 },
            )
        )
        val progress = SessionProgress(SessionProgress.decodeQueue(session.queue), session.done, session.total)
            .answer(entryId, after.due, t)
        val updated = session.copy(
            queue = SessionProgress.encodeQueue(progress.queue),
            done = progress.done,
            total = progress.total,
            correct = session.correct + if (rating != Rating.AGAIN) 1 else 0,
            updatedAt = t,
            status = if (progress.isFinished) SessionStatus.FINISHED else SessionStatus.ACTIVE,
            finishedAt = if (progress.isFinished) t else null,
        )
        sessions.update(updated)
        val todayDay = today()
        val day = DailyStreak.record(daily.get(todayDay)?.toDomain(), todayDay, goal, rating != Rating.AGAIN, t)
        daily.upsert(DailyProgressEntity.from(day))
        return AnswerResult(updated, after.due, day)
    }

    /**
     * Повторить конкретное слово: оно ставится первым в очередь занятия курса
     * (текущее занятие продолжается, ничего не теряется).
     */
    suspend fun queueWord(entryId: String): String? = db.withTransaction {
        val st = reviews.get(entryId) ?: return@withTransaction null
        val t = now()
        if (st.introducedAt == null) reviews.upsert(st.copy(introducedAt = t, due = t))
        val active = sessions.active(st.language)
        if (active != null) {
            val ids = SessionProgress.decodeQueue(active.queue).filter { it != entryId }
            sessions.update(active.copy(queue = SessionProgress.encodeQueue(listOf(entryId) + ids),
                total = active.done + ids.size + 1, updatedAt = t))
        } else {
            sessions.insert(
                LearningSessionEntity(
                    language = st.language, queue = entryId, done = 0, total = 1, correct = 0,
                    startedAt = t, updatedAt = t, finishedAt = null, status = SessionStatus.ACTIVE,
                )
            )
        }
        st.language
    }

    /** Завершение сессии не отменяет уже сохранённые ответы — оставшиеся карточки просто ждут следующего раза. */
    suspend fun endSession(sessionId: Long) = db.withTransaction {
        val s = sessions.get(sessionId) ?: return@withTransaction
        if (s.status == SessionStatus.ACTIVE) sessions.update(s.copy(status = SessionStatus.ENDED, finishedAt = now(), updatedAt = now()))
    }

    suspend fun activeSessions() = sessions.allActive()

    /**
     * Текущее состояние острова: самая свежая активная сессия и её первая карточка.
     * Один источник для экрана повторения, острова в приложении, уведомления и капсулы —
     * ответ в любом месте сразу виден во всех остальных.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeIsland(): Flow<IslandState?> =
        combine(sessions.observeActive(), courses.observeAll()) { active, all ->
            val s = active.maxByOrNull { it.updatedAt } ?: return@combine null
            val c = all.firstOrNull { it.id == s.language }?.toDomain() ?: return@combine null
            s to c
        }.distinctUntilChanged().flatMapLatest { pair ->
            if (pair == null) flowOf(null) else {
                val (s, c) = pair
                val head = SessionProgress.decodeQueue(s.queue).firstOrNull()
                if (head == null) flowOf(IslandState(s, c, null))
                else entries.observe(head).map { IslandState(s, c, it?.entry?.toDomain()) }
            }
        }

    /** Island-сессия по ID с текущей карточкой — для одноразовых действий (уведомление, капсула). */
    suspend fun islandFor(sessionId: Long): IslandState? {
        val s = sessions.get(sessionId) ?: return null
        val c = courses.get(s.language)?.toDomain() ?: return null
        val head = SessionProgress.decodeQueue(s.queue).firstOrNull()
        return IslandState(s, c, head?.let { entries.get(it)?.toDomain() })
    }

    // ---------- Серии, достижения, карта, существа ----------

    fun observeDays(): Flow<List<DayRecord>> = daily.observeAll().map { l -> l.map { it.toDomain() } }
    suspend fun days(): List<DayRecord> = daily.all().map { it.toDomain() }
    fun observeUnlocks(): Flow<List<UnlockEntity>> = unlocks.observeAll()

    suspend fun streak(): StreakState = DailyStreak.state(daily.all().map { it.toDomain() }, today(), settings.current().dailyGoal)

    /** Изменение цели: если сегодня уже ответов достаточно — день засчитывается сразу. */
    suspend fun setDailyGoal(goal: Int) {
        settings.update { it.copy(dailyGoal = goal) }
        db.withTransaction {
            DailyStreak.applyGoal(daily.get(today())?.toDomain(), goal, now())?.let { daily.upsert(DailyProgressEntity.from(it)) }
        }
        evaluateWorld()
    }

    /** Восстанавливает дневной прогресс по истории ответов (копии старой версии без серий). */
    suspend fun rebuildDailyFromLogs() = db.withTransaction {
        val goal = DailyStreak.DEFAULT_GOAL
        logs.all().groupBy { java.time.Instant.ofEpochMilli(it.reviewedAt).atZone(zone()).toLocalDate().toEpochDay() }
            .forEach { (day, l) ->
                var rec: DayRecord? = null
                l.sortedBy { it.reviewedAt }.forEach { rec = DailyStreak.record(rec, day, goal, it.rating > 1, it.reviewedAt) }
                rec?.let { daily.upsert(DailyProgressEntity.from(it)) }
            }
    }

    /** Реальные показатели пользователя для достижений, карты и существ. */
    suspend fun snapshot(): ProgressSnapshot = withContext(Dispatchers.Default) {
        ProgressCalculator.snapshot(
            cards = toCards(entries.allWithState(), recentRatingsByEntry()),
            logs = logs.all(),
            sessions = sessions.all(),
            courseIds = courses.all().map { it.id }.toSet(),
            streak = streak(),
            listens = settings.current().listens,
        )
    }

    /**
     * Проверяет, что открыто, и сохраняет новое. Полученное никогда не отнимается.
     * Вызывается после ответов, завершения занятия, импорта, добавления слов и при запуске.
     */
    suspend fun evaluateWorld(): Evaluation {
        val owned = unlocks.all().associate { UnlockKey(UnlockKind.valueOf(it.kind), it.itemId) to it.level }
        val result = GameEngine.evaluate(snapshot(), owned)
        val t = now()
        db.withTransaction {
            unlocks.insertIgnore(result.unlocked.map { UnlockEntity(it.key.kind.name, it.key.id, t, it.level, seen = false) })
            result.levelUps.forEach { (id, level) -> unlocks.setCreatureLevel(id, level) }
        }
        return result
    }

    suspend fun markSeen(kind: UnlockKind, id: String) = unlocks.markSeen(kind.name, id)
    suspend fun markAllSeen() = unlocks.markAllSeen()

    companion object {
        fun isNewState(state: ReviewStateEntity) = state.introducedAt == null && state.state == CardState.NEW.name
    }
}
