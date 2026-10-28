package app.lingvo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportPlannerTest {
    private val now = 1_800_000_000_000L
    private var counter = 0
    private val planner = ImportPlanner { spec -> "${spec.code}-gen-${++counter}" }

    private fun entry(id: String, term: String, tr: String, lang: String = "es", notes: String = "", article: String = "") =
        VocabEntry(id = id, language = lang, term = term, translation = tr, notes = notes, article = article, createdAt = 1, updatedAt = 1)

    private fun parse(text: String, spec: LanguageSpec = Languages.SPANISH) = VocabularyJson.parse(text, spec, now)

    private val file = """
        {"language":"es","entries":[
          {"id":"es-1","spanish":"la mesa","russian":"стол","example_es":"La mesa es grande."},
          {"spanish":"el libro","russian":"книга"},
          {"id":"es-3","spanish":"¡Ojalá!","russian":"дай бог"}
        ]}
    """.trimIndent()

    @Test
    fun `import into empty dictionary creates all and generates missing ids`() {
        val plan = planner.plan(parse(file), emptyList(), emptySet(), ConflictPolicy.KEEP_LOCAL, now)
        assertEquals(3, plan.newEntries.size)
        assertEquals(listOf("es-1", "es-gen-1", "es-3"), plan.newEntries.map { it.id })
        assertEquals(now, plan.newEntries[1].createdAt)
    }

    @Test
    fun `reimport of the same file creates no duplicates and no changes`() {
        val first = planner.plan(parse(file), emptyList(), emptySet(), ConflictPolicy.KEEP_LOCAL, now)
        val local = first.newEntries.map { LocalEntry(it, locallyEdited = false) }
        val second = planner.plan(parse(file), local, emptySet(), ConflictPolicy.KEEP_LOCAL, now + 1000)
        assertTrue(second.newEntries.isEmpty())
        assertTrue(second.changed.isEmpty())
        assertEquals(3, second.unchangedTotal)
        assertTrue(!second.hasChanges)
    }

    @Test
    fun `entries missing from file are never deleted`() {
        val local = listOf(LocalEntry(entry("es-99", "el perro", "собака"), false))
        val plan = planner.plan(parse(file), local, emptySet(), ConflictPolicy.KEEP_LOCAL, now)
        // План содержит только добавления и обновления — операции удаления нет вовсе.
        assertEquals(3, plan.newEntries.size)
        assertTrue(plan.updates.none { it.before.id == "es-99" })
    }

    @Test
    fun `update by id fills new fields and keeps empty ones`() {
        val local = listOf(LocalEntry(entry("es-1", "la mesa", "стол").copy(level = "A1"), false))
        val plan = planner.plan(parse(file), local, emptySet(), ConflictPolicy.KEEP_LOCAL, now)
        val u = plan.changed.single()
        assertEquals("La mesa es grande.", u.after.exampleTarget)
        assertEquals("A1", u.after.level, "пустое поле в файле не стирает локальное")
        assertEquals(now, u.after.updatedAt)
        assertEquals(1L, u.after.createdAt)
    }

    @Test
    fun `match by normalized term when id missing keeps local id`() {
        val local = listOf(LocalEntry(entry("es-local", "El  Libro", "книжка"), false))
        val plan = planner.plan(parse(file), local, emptySet(), ConflictPolicy.KEEP_LOCAL, now)
        val u = plan.changed.single { it.before.id == "es-local" }
        assertEquals(MatchKind.TERM, u.matchedBy)
        assertEquals("es-local", u.after.id)
        assertEquals("книга", u.after.translation)
        assertEquals("El  Libro", u.after.term, "написание термина при совпадении по выражению не меняется")
    }

    @Test
    fun `locally edited translation is a conflict, kept by default and replaced with TAKE_FILE`() {
        val local = listOf(LocalEntry(entry("es-1", "la mesa", "столик"), locallyEdited = true))
        val keep = planner.plan(parse(file), local, emptySet(), ConflictPolicy.KEEP_LOCAL, now)
        val k = keep.updates.single()
        assertEquals("столик", k.after.translation)
        assertEquals(1, keep.conflicts.size)
        assertEquals("russian", k.conflicts.single().field)

        val take = planner.plan(parse(file), local, emptySet(), ConflictPolicy.TAKE_FILE, now)
        assertEquals("стол", take.updates.single().after.translation)
    }

    @Test
    fun `local notes are never overwritten silently`() {
        val text = """{"language":"es","entries":[{"id":"es-1","spanish":"la mesa","russian":"стол","notes":"из файла"}]}"""
        val local = listOf(LocalEntry(entry("es-1", "la mesa", "стол", notes = "моя заметка"), locallyEdited = false))
        val plan = planner.plan(parse(text), local, emptySet(), ConflictPolicy.KEEP_LOCAL, now)
        assertEquals("моя заметка", plan.updates.single().after.notes)
        assertEquals("notes", plan.conflicts.single().conflicts.single().field)
    }

    @Test
    fun `duplicates inside file are skipped`() {
        val text = """{"language":"es","entries":[
            {"spanish":"la casa","russian":"дом"},
            {"spanish":"La  Casa","russian":"дом"},
            {"id":"x","spanish":"el gato","russian":"кот"},
            {"id":"x","spanish":"el perro","russian":"собака"}]}"""
        val plan = planner.plan(parse(text), emptyList(), emptySet(), ConflictPolicy.KEEP_LOCAL, now)
        assertEquals(2, plan.newEntries.size)
        assertEquals(2, plan.skipped.size)
    }

    @Test
    fun `ambiguous term match is skipped instead of guessing`() {
        val local = listOf(
            LocalEntry(entry("a", "el libro", "книга"), false),
            LocalEntry(entry("b", "el libro", "книга (2)"), false),
        )
        val plan = planner.plan(parse(file), local, emptySet(), ConflictPolicy.KEEP_LOCAL, now)
        assertTrue(plan.skipped.any { it.label == "el libro" })
    }

    @Test
    fun `spanish import does not touch german cards and avoids foreign id collision`() {
        val german = LocalEntry(entry("es-1", "die Mesa", "x", lang = "de", article = "die"), false)
        val plan = planner.plan(parse(file), listOf(german), foreignIds = setOf("es-1"), ConflictPolicy.TAKE_FILE, now)
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.newEntries.none { it.id == "es-1" }, "ID чужого языка не переиспользуется")
        assertTrue(plan.newEntries.all { it.language == "es" })
    }

    @Test
    fun `similar but different constructions stay separate`() {
        val text = """{"language":"de","entries":[
            {"german":"sich auf etwas verlassen","russian":"полагаться на что-либо"},
            {"german":"sich verlassen","russian":"полагаться"}]}"""
        val plan = planner.plan(parse(text, Languages.GERMAN), emptyList(), emptySet(), ConflictPolicy.KEEP_LOCAL, now)
        assertEquals(2, plan.newEntries.size)
    }

    @Test
    fun `tags and collocations are merged, german article is preserved`() {
        val local = LocalEntry(
            entry("de-1", "die Voraussetzung", "условие", lang = "de", article = "die").copy(tags = listOf("B2"), collocations = listOf("eine Voraussetzung erfüllen")),
            false,
        )
        val text = """{"language":"de","entries":[{"id":"de-1","german":"die Voraussetzung","russian":"условие","article":"die","plural":"Voraussetzungen",
            "tags":["академическая лексика","b2"],"collocations":["Voraussetzung schaffen","eine Voraussetzung erfüllen"]}]}"""
        val u = planner.plan(parse(text, Languages.GERMAN), listOf(local), emptySet(), ConflictPolicy.KEEP_LOCAL, now).changed.single()
        assertEquals(listOf("B2", "академическая лексика"), u.after.tags)
        assertEquals(2, u.after.collocations.size)
        assertEquals("die", u.after.article)
        assertEquals("Voraussetzungen", u.after.plural)
    }
}
