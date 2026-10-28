package app.lingvo.data

import android.content.Context
import androidx.room.withTransaction
import app.lingvo.core.BackupCodec
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
import app.lingvo.core.Languages
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

data class AnswerResult(val session: LearningSessionEntity, val nextDue: Long)

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

    fun now(): Long = clock()
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

    // ---------- Редактирование словаря ----------

    fun newId(spec: LanguageSpec): String = "${spec.idPrefix}-${UUID.randomUUID().toString().take(12)}"

    /** Возвращает карточки того же языка с тем же нормализованным выражением. */
    suspend fun findDuplicates(language: String, term: String, article: String, excludeId: String? = null): List<VocabEntry> =
        entries.byKey(language, Normalizer.entryKey(term, article)).filter { it.id != excludeId }.map { it.toDomain() }

    suspend fun addEntry(entry: VocabEntry) = db.withTransaction {
        val t = now()
        val clean = entry.copy(createdAt = t, updatedAt = t, partOfSpeech = PartsOfSpeech.canonical(entry.partOfSpeech))
        entries.insert(listOf(VocabularyEntryEntity.from(clean, locallyEdited = true)))
        reviews.insertIgnore(listOf(ReviewStateEntity.fresh(clean.id, clean.language)))
    }

    /** Изменение текста карточки не сбрасывает прогресс повторений. */
    suspend fun updateEntry(entry: VocabEntry) = db.withTransaction {
        val old = entries.get(entry.id) ?: error("Карточка не найдена")
        val updated = entry.copy(language = old.language, createdAt = old.createdAt, updatedAt = now(),
            partOfSpeech = PartsOfSpeech.canonical(entry.partOfSpeech))
        entries.update(listOf(VocabularyEntryEntity.from(updated, locallyEdited = true)))
    }

    suspend fun deleteEntry(id: String) = entries.delete(id)

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
        var added = 0
        db.withTransaction {
            val seen = HashSet<String>()
            for (line in parsed.lines) {
                val article = if (spec.code == Languages.GERMAN.code) Normalizer.detectArticle(line.term, spec) else ""
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
                added++
            }
        }
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

    /** Стартовый набор из APK (те же канонические файлы, что лежат в репозитории). */
    suspend fun importStarter(spec: LanguageSpec): ImportPlan {
        val text = withContext(Dispatchers.IO) { context.assets.open(spec.fileName).bufferedReader().use { it.readText() } }
        return applyImport(parseVocabulary(text, spec), ConflictPolicy.KEEP_LOCAL, "Стартовый набор")
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
                        )
                    },
                    reviewStates = reviews.all().map {
                        BackupReviewState(it.entryId, it.state, it.stability, it.difficulty, it.due, it.lastReview,
                            it.reps, it.lapses, it.step, it.introducedAt, it.suspended)
                    },
                    reviewHistory = logs.all().map { BackupHistory(it.entryId, it.language, it.rating, it.reviewedAt, it.stateBefore, it.elapsedMs) },
                    settings = settings.exportMap(),
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
            entries.insert(backup.entries.map {
                VocabularyEntryEntity(
                    id = it.id, language = it.language, term = it.term, termKey = Normalizer.entryKey(it.term, it.article),
                    translation = it.translation, exampleTarget = it.exampleTarget, exampleRu = it.exampleRu,
                    partOfSpeech = it.partOfSpeech, article = it.article, plural = it.plural, level = it.level,
                    tags = VocabularyEntryEntity.joinList(it.tags), notes = it.notes, source = it.source,
                    grammarNotes = it.grammarNotes, collocations = VocabularyEntryEntity.joinList(it.collocations),
                    verbForms = it.verbFormsJson, regionalUsage = it.regionalUsage, extra = it.extra,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, locallyEdited = it.locallyEdited,
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
                    stateBefore = it.stateBefore, elapsedMs = it.elapsedMs)
            })
        }
        if (backup.settings.isNotEmpty()) settings.importMap(backup.settings)
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

    /** Предпросмотр следующего интервала для каждой оценки (подписи на кнопках). */
    suspend fun previewIntervals(entryId: String): Map<Rating, Long> {
        val st = reviews.get(entryId) ?: return emptyMap()
        val t = now()
        return Rating.entries.associateWith { fsrs.review(st.toSrs(), it, t).due - t }
    }

    /** Ответ сохраняется сразу: состояние, история и позиция сессии — в одной транзакции. */
    suspend fun answer(sessionId: Long, entryId: String, rating: Rating): AnswerResult = db.withTransaction {
        val t = now()
        val session = sessions.get(sessionId) ?: error("Сессия не найдена")
        val st = reviews.get(entryId) ?: error("Карточка не найдена")
        val before = st.toSrs()
        val after = fsrs.review(before, rating, t)
        reviews.upsert(st.withSrs(after))
        logs.insert(
            ReviewLogEntity(
                entryId = entryId, language = st.language, rating = rating.value, reviewedAt = t,
                stateBefore = before.state.name, elapsedMs = before.lastReview?.let { t - it } ?: 0L,
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
        AnswerResult(updated, after.due)
    }

    /** Завершение сессии не отменяет уже сохранённые ответы — оставшиеся карточки просто ждут следующего раза. */
    suspend fun endSession(sessionId: Long) = db.withTransaction {
        val s = sessions.get(sessionId) ?: return@withTransaction
        if (s.status == SessionStatus.ACTIVE) sessions.update(s.copy(status = SessionStatus.ENDED, finishedAt = now(), updatedAt = now()))
    }

    suspend fun activeSessions() = sessions.allActive()

    companion object {
        fun isNewState(state: ReviewStateEntity) = state.introducedAt == null && state.state == CardState.NEW.name
    }
}
