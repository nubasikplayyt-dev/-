package app.lingvo.core

import java.util.UUID

/** Локальная карточка и признак того, что пользователь редактировал её в приложении. */
data class LocalEntry(val entry: VocabEntry, val locallyEdited: Boolean)

enum class ConflictPolicy {
    /** Оставить значения, изменённые в приложении (по умолчанию). */
    KEEP_LOCAL,
    /** Взять значения из файла. */
    TAKE_FILE,
}

data class FieldConflict(val field: String, val localValue: String, val fileValue: String)

data class PlannedUpdate(
    val before: VocabEntry,
    /** Итог с учётом выбранной политики конфликтов. */
    val after: VocabEntry,
    val changedFields: List<String>,
    val conflicts: List<FieldConflict>,
    val matchedBy: MatchKind,
)

enum class MatchKind { ID, TERM }

data class SkippedEntry(val label: String, val reason: String)

data class ImportPlan(
    val language: LanguageSpec,
    val newEntries: List<VocabEntry>,
    val updates: List<PlannedUpdate>,
    val unchanged: Int,
    val skipped: List<SkippedEntry>,
    val problems: List<EntryProblem>,
) {
    /** Обновления, которые реально меняют карточку при выбранной политике. */
    val changed: List<PlannedUpdate> get() = updates.filter { it.changedFields.isNotEmpty() }
    val conflicts: List<PlannedUpdate> get() = updates.filter { it.conflicts.isNotEmpty() }
    /** Карточки, совпавшие с файлом, но оставленные без изменений (включая решённые в пользу локальных). */
    val unchangedTotal: Int get() = unchanged + updates.count { it.changedFields.isEmpty() }
    val hasChanges: Boolean get() = newEntries.isNotEmpty() || changed.isNotEmpty()

    fun summaryRu(): String = buildString {
        append("Новых: ${newEntries.size}, изменённых: ${changed.size}, без изменений: $unchangedTotal")
        if (skipped.isNotEmpty()) append(", пропущено: ${skipped.size}")
        if (problems.isNotEmpty()) append(", ошибок: ${problems.size}")
        if (conflicts.isNotEmpty()) append(", конфликтов: ${conflicts.size}")
    }
}

/**
 * Правила слияния (документированы в docs/IMPORT_EXPORT.md):
 *  1. Совпадение по ID внутри языка; если ID нет или он неизвестен — по нормализованному
 *     выражению с артиклем. Несколько совпадений → запись пропускается (неоднозначность).
 *  2. Пустое поле в файле никогда не стирает локальное значение.
 *  3. Теги и словосочетания объединяются.
 *  4. Заметки (notes) и поля, изменённые пользователем в приложении, при расхождении
 *     считаются конфликтом и решаются политикой [ConflictPolicy].
 *  5. Карточки, отсутствующие в файле, не удаляются. Прогресс обучения не затрагивается.
 *  6. Дубликаты внутри файла пропускаются, повторный импорт того же файла ничего не меняет.
 */
class ImportPlanner(
    private val idGenerator: (LanguageSpec) -> String = { spec -> "${spec.idPrefix}-${UUID.randomUUID().toString().take(12)}" },
) {
    fun plan(
        parsed: ParsedVocabulary,
        local: List<LocalEntry>,
        /** ID карточек других языков: ID уникальны во всей базе. */
        foreignIds: Set<String>,
        policy: ConflictPolicy,
        now: Long,
    ): ImportPlan {
        val spec = parsed.language
        val ownLocal = local.filter { it.entry.language == spec.code }
        val byId = ownLocal.associateBy { it.entry.id }
        val byKey = ownLocal.groupBy { Normalizer.entryKey(it.entry.term, it.entry.article) }

        val newEntries = ArrayList<VocabEntry>()
        val updates = ArrayList<PlannedUpdate>()
        val skipped = ArrayList<SkippedEntry>()
        var unchanged = 0

        val seenIds = HashSet<String>()
        val seenKeys = HashSet<String>()
        val touchedLocalIds = HashSet<String>()
        val usedIds = HashSet<String>().apply { addAll(foreignIds); addAll(byId.keys) }

        for (p in parsed.entries) {
            val incoming = p.entry
            val key = Normalizer.entryKey(incoming.term, incoming.article)
            val label = incoming.displayTerm

            if (p.hasId && !seenIds.add(incoming.id)) {
                skipped += SkippedEntry(label, "ID «${incoming.id}» повторяется в файле."); continue
            }
            if (!seenKeys.add(key)) {
                skipped += SkippedEntry(label, "Такое же выражение уже встречается в файле выше."); continue
            }

            var matchKind = MatchKind.ID
            var match: LocalEntry? = if (p.hasId) byId[incoming.id] else null
            if (match == null) {
                val candidates = byKey[key].orEmpty()
                when {
                    candidates.size == 1 -> { match = candidates.first(); matchKind = MatchKind.TERM }
                    candidates.size > 1 -> {
                        skipped += SkippedEntry(label, "В словаре несколько карточек с таким выражением — выберите вручную."); continue
                    }
                }
            }

            if (match == null) {
                val id = when {
                    p.hasId && incoming.id !in usedIds -> incoming.id
                    else -> generateUniqueId(spec, usedIds)
                }
                usedIds += id
                newEntries += incoming.copy(
                    id = id,
                    createdAt = if (p.hasCreatedAt) incoming.createdAt else now,
                    updatedAt = if (p.hasCreatedAt) incoming.updatedAt else now,
                )
                continue
            }

            if (!touchedLocalIds.add(match.entry.id)) {
                skipped += SkippedEntry(label, "Совпадает с карточкой, которая уже обновлена другой записью файла."); continue
            }
            val update = merge(match, incoming, policy, now, matchKind)
            if (update == null) unchanged++ else updates += update
        }
        return ImportPlan(spec, newEntries, updates, unchanged, skipped, parsed.problems)
    }

    private fun generateUniqueId(spec: LanguageSpec, used: Set<String>): String {
        while (true) {
            val id = idGenerator(spec)
            if (id !in used) return id
        }
    }

    private class Field(
        val name: String,
        val get: (VocabEntry) -> String,
        val set: (VocabEntry, String) -> VocabEntry,
        val personal: Boolean = false,
    )

    private val fields = listOf(
        Field("term", { it.term }, { e, v -> e.copy(term = v) }),
        Field("russian", { it.translation }, { e, v -> e.copy(translation = v) }),
        Field("example", { it.exampleTarget }, { e, v -> e.copy(exampleTarget = v) }),
        Field("example_ru", { it.exampleRu }, { e, v -> e.copy(exampleRu = v) }),
        Field("part_of_speech", { it.partOfSpeech }, { e, v -> e.copy(partOfSpeech = v) }),
        Field("article", { it.article }, { e, v -> e.copy(article = v) }),
        Field("plural", { it.plural }, { e, v -> e.copy(plural = v) }),
        Field("level", { it.level }, { e, v -> e.copy(level = v) }),
        Field("grammar_notes", { it.grammarNotes }, { e, v -> e.copy(grammarNotes = v) }),
        Field("verb_forms", { it.verbFormsJson }, { e, v -> e.copy(verbFormsJson = v) }),
        Field("regional_usage", { it.regionalUsage }, { e, v -> e.copy(regionalUsage = v) }),
        Field("source", { it.source }, { e, v -> e.copy(source = v) }),
        Field("transcription", { it.transcription }, { e, v -> e.copy(transcription = v) }),
        Field("notes", { it.notes }, { e, v -> e.copy(notes = v) }, personal = true),
    )

    private fun merge(local: LocalEntry, incoming: VocabEntry, policy: ConflictPolicy, now: Long, kind: MatchKind): PlannedUpdate? {
        val before = local.entry
        var after = before
        val changed = ArrayList<String>()
        val conflicts = ArrayList<FieldConflict>()

        for (f in fields) {
            val lv = f.get(before)
            val fv = f.get(incoming)
            if (fv.isEmpty() || sameValue(f.name, lv, fv)) continue
            // "term" при совпадении по выражению отличается лишь регистром/пробелами — это не изменение.
            if (f.name == "term" && kind == MatchKind.TERM) continue
            val isConflict = lv.isNotEmpty() && (f.personal || local.locallyEdited)
            if (isConflict) {
                conflicts += FieldConflict(f.name, lv, fv)
                if (policy == ConflictPolicy.KEEP_LOCAL) continue
            }
            after = f.set(after, fv)
            changed += f.name
        }

        val mergedTags = Normalizer.tags(before.tags + incoming.tags)
        if (mergedTags.size != before.tags.size) { after = after.copy(tags = mergedTags); changed += "tags" }
        val mergedAlt = (before.alternatives + incoming.alternatives).distinctBy { Normalizer.clean(it) }
        if (mergedAlt.size != before.alternatives.size) { after = after.copy(alternatives = mergedAlt); changed += "alternatives" }
        val mergedColl = (before.collocations + incoming.collocations)
            .distinctBy { Normalizer.key(it) }
        if (mergedColl.size != before.collocations.size) { after = after.copy(collocations = mergedColl); changed += "collocations" }
        if (incoming.extraJson.isNotEmpty() && incoming.extraJson != before.extraJson && before.extraJson.isEmpty()) {
            after = after.copy(extraJson = incoming.extraJson); changed += "extra"
        }

        if (changed.isEmpty() && conflicts.isEmpty()) return null
        if (changed.isNotEmpty()) after = after.copy(updatedAt = now)
        return PlannedUpdate(before, after, changed, conflicts, kind)
    }

    private fun sameValue(field: String, a: String, b: String): Boolean = when (field) {
        "verb_forms" -> a.replace(Regex("\\s"), "") == b.replace(Regex("\\s"), "")
        "notes", "grammar_notes" -> a.trim() == b.trim()
        else -> Normalizer.clean(a) == Normalizer.clean(b)
    }
}
