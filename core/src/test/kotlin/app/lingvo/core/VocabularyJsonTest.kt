package app.lingvo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VocabularyJsonTest {
    private val now = 1_800_000_000_000L

    private val germanFile = """
        {
          "schema_version": 1,
          "language": "de",
          "updated_at": "2026-10-28T12:00:00Z",
          "entries": [
            {
              "id": "de-0001",
              "german": "die Voraussetzung",
              "russian": "предпосылка; условие",
              "article": "die",
              "plural": "Voraussetzungen",
              "part_of_speech": "noun",
              "example_de": "Das ist eine wichtige Voraussetzung.",
              "example_ru": "Это важное условие.",
              "collocations": ["eine Voraussetzung erfüllen"],
              "level": "b2",
              "tags": ["академическая лексика"],
              "custom_field": {"keep": true},
              "created_at": "2026-10-01T10:00:00Z",
              "updated_at": "2026-10-02T10:00:00Z"
            },
            { "german": "eine Entscheidung treffen", "russian": "принимать решение", "part_of_speech": "phrase" }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses valid german file with article plural and optional fields`() {
        val p = VocabularyJson.parse(germanFile, Languages.GERMAN, now)
        assertEquals("de", p.language.code)
        assertEquals(2, p.entries.size)
        val e = p.entries[0].entry
        assertEquals("die", e.article)
        assertEquals("Voraussetzungen", e.plural)
        assertEquals("B2", e.level)
        assertEquals(listOf("eine Voraussetzung erfüllen"), e.collocations)
        assertEquals(IsoTime.parseOrNull("2026-10-01T10:00:00Z"), e.createdAt)
        assertTrue(e.extraJson.contains("custom_field"))
        val second = p.entries[1]
        assertTrue(!second.hasId)
        assertEquals(now, second.entry.createdAt, "без даты — фактическое время импорта")
        assertEquals("", second.entry.article)
    }

    @Test
    fun `round trip keeps article plural and unknown fields`() {
        val p = VocabularyJson.parse(germanFile, Languages.GERMAN, now)
        val entries = p.entries.map { it.entry.copy(id = it.entry.id.ifEmpty { "de-x" }) }
        val out = VocabularyJson.serialize(Languages.GERMAN, entries, now)
        val again = VocabularyJson.parse(out, Languages.GERMAN, now)
        assertEquals(entries, again.entries.map { it.entry })
        assertTrue(out.contains("\"custom_field\""))
        assertTrue(out.contains("Voraussetzungen"))
    }

    @Test
    fun `detects german article from term when field missing`() {
        val text = """{"language":"de","entries":[{"german":"das Ergebnis","russian":"результат","part_of_speech":"noun"}]}"""
        val e = VocabularyJson.parse(text, Languages.GERMAN, now).entries.single().entry
        assertEquals("das", e.article)
        assertEquals("das Ergebnis", e.displayTerm)
    }

    @Test
    fun `corrupted json is rejected with readable message`() {
        val ex = assertFailsWith<VocabularyFormatException> { VocabularyJson.parse("{\"entries\": [", Languages.SPANISH, now) }
        assertTrue(ex.message!!.contains("повреждён"))
    }

    @Test
    fun `empty file is rejected`() {
        assertFailsWith<VocabularyFormatException> { VocabularyJson.parse("   ", Languages.SPANISH, now) }
    }

    @Test
    fun `wrong language is rejected`() {
        val ex = assertFailsWith<VocabularyFormatException> { VocabularyJson.parse(germanFile, Languages.SPANISH, now) }
        assertTrue(ex.message!!.contains("Немецкий"))
    }

    @Test
    fun `language inferred from term key for bare array`() {
        val text = """[{"spanish":"¿Qué tal?","russian":"как дела?"}]"""
        val p = VocabularyJson.parse(text, null, now)
        assertEquals("es", p.language.code)
        assertEquals("¿Qué tal?", p.entries.single().entry.term)
    }

    @Test
    fun `entries without required fields are reported, others kept`() {
        val text = """{"language":"es","entries":[{"spanish":"la mesa"},{"russian":"без слова"},{"spanish":"el año","russian":"год","article":"el"},42]}"""
        val p = VocabularyJson.parse(text, Languages.SPANISH, now)
        assertEquals(1, p.entries.size)
        assertEquals(3, p.problems.size)
        assertEquals("el año", p.entries.single().entry.term)
    }

    @Test
    fun `invalid german article is an entry error`() {
        val text = """{"language":"de","entries":[{"german":"Haus","russian":"дом","article":"le"}]}"""
        val p = VocabularyJson.parse(text, Languages.GERMAN, now)
        assertTrue(p.entries.isEmpty())
        assertEquals(1, p.problems.size)
    }

    @Test
    fun `newer schema is rejected`() {
        assertFailsWith<VocabularyFormatException> {
            VocabularyJson.parse("""{"schema_version":99,"language":"es","entries":[]}""", Languages.SPANISH, now)
        }
    }

    @Test
    fun `spanish orthography is preserved`() {
        val text = """{"language":"es","entries":[{"spanish":"¡Ojalá!","russian":"дай бог!","example_es":"¿Mañana? ¡Ojalá!"}]}"""
        val out = VocabularyJson.serialize(
            Languages.SPANISH,
            VocabularyJson.parse(text, Languages.SPANISH, now).entries.map { it.entry.copy(id = "es-1") },
            now,
        )
        assertTrue(out.contains("¡Ojalá!") && out.contains("¿Mañana?"))
    }

    @Test
    fun `normalizer handles case spaces punctuation and unicode forms`() {
        val decomposed = "año" // a + n + combining tilde + o
        assertEquals(Normalizer.key("año"), Normalizer.key(decomposed))
        assertEquals(Normalizer.key("¿Qué tal?"), Normalizer.key("  qué   TAL "))
        assertTrue(Normalizer.key("año") != Normalizer.key("ano"), "диакритика значима")
        assertEquals(Normalizer.entryKey("Voraussetzung", "die"), Normalizer.entryKey("die Voraussetzung", "die"))
        assertEquals("que tal", Normalizer.searchKey("¿Qué tal?"))
        assertEquals("ещё", Normalizer.key("ещё"))
        assertEquals("еще", Normalizer.searchKey("ещё"))
    }

    @Test
    fun `bulk parser reads separators and examples`() {
        val r = BulkParser.parse(
            """
            # комментарий
            die Voraussetzung — предпосылка | Das ist eine Voraussetzung. | Это условие.
            sich auf etwas verlassen - полагаться на что-либо
            E-Mail=электронное письмо
            без разделителя
            """.trimIndent()
        )
        assertEquals(3, r.lines.size)
        assertEquals("Это условие.", r.lines[0].exampleRu)
        assertEquals("E-Mail", r.lines[2].term)
        assertEquals(1, r.errors.size)
    }
}
