package app.lingvo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordImagePromptTest {
    private fun e(term: String, tr: String, article: String = "", extra: String = "") =
        VocabEntry("x", "c", term, tr, article = article, extraJson = extra, createdAt = 0, updatedAt = 0)

    @Test
    fun `same word gives same cache key regardless of case, spaces and course`() {
        val de = Languages.GERMAN
        assertEquals(WordImagePrompt.cacheKey("de", e("die  Mutter", "мама", "die")), WordImagePrompt.cacheKey("de", e("die Mutter", "мать", "die")))
        assertTrue(WordImagePrompt.cacheKey("de", e("Bank", "")) != WordImagePrompt.cacheKey("de", e("Tisch", "")))
        assertEquals(de.languageCode, "de")
    }

    @Test
    fun `subject prefers english`() {
        val de = Languages.GERMAN
        assertEquals("mother", WordImagePrompt.build(e("die Mutter", "мама", "die", """{"gloss_en":"mother"}"""), de,
            WordImagePrompt.glossEn("""{"gloss_en":"mother"}""")).subject)
        assertEquals("\"die Mutter\" (мама)", WordImagePrompt.subject(e("die Mutter", "мама", "die"), de, null))
        assertTrue(WordImagePrompt.build(e("x", "y"), de, null).prompt.startsWith("a simple cute flat illustration of"))
    }

    @Test
    fun `only local network servers are allowed`() {
        listOf("http://192.168.1.20:7860", "http://10.0.0.5:7860", "http://localhost:7860", "http://172.20.1.1",
            "http://my-pc.local:7860").forEach { assertTrue(WordImagePrompt.isLocalAddress(it), it) }
        listOf("http://8.8.8.8:7860", "https://api.example.com", "ftp://192.168.1.2", "", "http://172.40.0.1")
            .forEach { assertFalse(WordImagePrompt.isLocalAddress(it), it) }
    }

    @Test
    fun `web query uses the word without article in its own wikipedia`() {
        val q = WordImagePrompt.webQuery(e("die Mutter", "мама", "die"), Languages.GERMAN, "mother")
        assertEquals("de", q.wikiLang)
        assertEquals("Mutter", q.wikiQuery)
        assertEquals("mother", q.englishQuery)
        assertEquals(null, WordImagePrompt.webQuery(e("la mesa", "стол", "la"), Languages.SPANISH, null).englishQuery)
    }
}
