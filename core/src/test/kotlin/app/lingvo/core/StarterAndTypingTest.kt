package app.lingvo.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Стартовые словари: каждый файл читается, блоки идут по порядку, первая порция — 20–30 слов. */
class StarterTest {
    private fun spec(key: String): LanguageSpec {
        val (lang, tr) = key.split('-')
        val info = LanguageCatalog.infoOrCustom(lang)
        val trInfo = LanguageCatalog.infoOrCustom(tr)
        return LanguageSpec.of(Course(lang, lang, info.nameRu, info.flag, tr, trInfo.nameRu, 0L, 0))
    }

    private fun text(key: String) = File("../app/src/main/assets/starters/$key.json").readText(Charsets.UTF_8)

    @Test
    fun `every advertised starter exists and parses without problems`() {
        val files = File("../app/src/main/assets/starters").listFiles()!!.map { it.name.removeSuffix(".json") }.toSet()
        assertEquals(Starter.available, files, "Список Starter.available должен совпадать с файлами")
        for (key in Starter.available) {
            val spec = spec(key)
            val raw = text(key)
            val blocks = Starter.blocks(raw, spec.code)
            assertEquals((1..blocks.size).toList(), blocks.map { it.number }, "$key: блоки по порядку")
            val first = blocks.take(Starter.FIRST_BLOCKS).sumOf { it.size }
            assertTrue(first in 20..30, "$key: первая порция $first слов")
            blocks.drop(Starter.FIRST_BLOCKS).forEach { assertTrue(it.size in 5..10, "$key: блок ${it.number} — ${it.size} слов") }

            val parsed = VocabularyJson.parse(Starter.select(raw, blocks.map { it.number }.toSet(), spec), spec, 0L)
            assertTrue(parsed.problems.isEmpty(), "$key: ${parsed.problems}")
            val entries = parsed.entries.map { it.entry }
            assertTrue(entries.all { it.translation.isNotBlank() && it.partOfSpeech.isNotBlank() && it.level == "A1" }, key)
            assertTrue(entries.all { it.tags.isNotEmpty() }, "$key: категория")
            val keys = entries.map { Normalizer.entryKey(it.term, it.article) }
            assertEquals(keys.size, keys.toSet().size, "$key: повторы")
            assertTrue(entries.all { it.id.startsWith("start-${spec.code}-") }, key)
            assertTrue(entries.none { it.extraJson.contains("block") }, "$key: служебные поля не попадают в карточку")
            if (spec.languageCode in setOf("zh", "ja", "ko", "ar")) {
                assertTrue(entries.all { it.transcription.isNotBlank() }, "$key: нужна транскрипция")
            }
            if (spec.languageCode in setOf("de", "es", "fr", "it", "pt", "nl")) {
                entries.filter { it.partOfSpeech == PartsOfSpeech.NOUN }.forEach {
                    assertTrue(it.article.isNotEmpty(), "$key: нет артикля у ${it.term}")
                }
            }
            // Примеры появляются позже — у глаголов.
            assertTrue(entries.filter { it.partOfSpeech == PartsOfSpeech.VERB }.all { it.exampleTarget.isNotBlank() && it.exampleRu.isNotBlank() }, key)
        }
    }

    @Test
    fun `pending blocks skip already imported ones`() {
        val spec = spec("de-ru")
        val blocks = Starter.blocks(text("de-ru"), spec.code)
        val have = blocks.take(3).flatMap { it.entryIds }.toSet()
        assertEquals(4, Starter.pendingBlocks(blocks, have).first().number)
        assertFalse(Starter.has("xx", "ru"))
    }
}

class AnswerCheckerTest {
    private val de = Languages.GERMAN
    private val es = Languages.SPANISH
    private fun entry(term: String, article: String = "", alts: List<String> = emptyList()) =
        VocabEntry(id = "x", language = "de", term = term, translation = "t", article = article, alternatives = alts, createdAt = 0, updatedAt = 0)

    @Test
    fun `exact, spaces and alternatives`() {
        assertEquals(AutoResult.EXACT, AnswerChecker.check("  die   Mutter ", entry("die Mutter", "die"), de).result)
        assertEquals(AutoResult.ALTERNATIVE, AnswerChecker.check("Mama", entry("die Mutter", "die", listOf("Mama")), de).result)
    }

    @Test
    fun `case matters only where it is significant`() {
        assertEquals(AutoResult.CASE, AnswerChecker.check("die mutter", entry("die Mutter", "die"), de).result)
        assertEquals(AutoResult.EXACT, AnswerChecker.check("HOLA", entry("hola"), es).result)
    }

    @Test
    fun `diacritics are not silently ignored`() {
        val r = AnswerChecker.check("cafe", entry("café"), es)
        assertEquals(AutoResult.DIACRITICS, r.result)
        assertFalse(r.result.looksCorrect)
        assertEquals("café", r.expected)
    }

    @Test
    fun `missing article, typo, wrong and empty`() {
        assertEquals(AutoResult.MISSING_ARTICLE, AnswerChecker.check("Mutter", entry("die Mutter", "die"), de).result)
        assertEquals(AutoResult.CLOSE, AnswerChecker.check("die Muter", entry("die Mutter", "die"), de).result)
        assertEquals(AutoResult.WRONG, AnswerChecker.check("der Vater", entry("die Mutter", "die"), de).result)
        assertEquals(AutoResult.EMPTY, AnswerChecker.check("  ", entry("die Mutter", "die"), de).result)
    }

    @Test
    fun `diff marks the differing letters`() {
        val d = AnswerChecker.diff("Muter", "Mutter")
        assertEquals("Mutter", d.filter { it.kind != DiffKind.EXTRA }.joinToString("") { it.text })
        assertTrue(d.any { it.kind == DiffKind.MISSING })
    }

    @Test
    fun `typed translation accepts any of its meanings`() {
        val entry = VocabEntry(id = "x", language = "de", term = "die Mutter", translation = "мама; мать, матушка",
            article = "die", createdAt = 0, updatedAt = 0)
        assertEquals(AutoResult.EXACT, AnswerChecker.checkTranslation("мама; мать, матушка", entry).result)
        assertEquals(AutoResult.ALTERNATIVE, AnswerChecker.checkTranslation("мать", entry).result)
        assertEquals(AutoResult.ALTERNATIVE, AnswerChecker.checkTranslation("Матушка", entry).result, "регистр перевода не важен")
        assertEquals(AutoResult.CLOSE, AnswerChecker.checkTranslation("мамв", entry).result, "одна опечатка")
        assertEquals(AutoResult.WRONG, AnswerChecker.checkTranslation("отец", entry).result)
        assertEquals(AutoResult.EMPTY, AnswerChecker.checkTranslation("  ", entry).result)
    }

    @Test
    fun `manual confirmation decides the FSRS grade`() {
        assertEquals(Answer.REMEMBERED, AnswerChecker.answerFor(true))
        assertEquals(Answer.FORGOT, AnswerChecker.answerFor(false))
        assertEquals(Rating.GOOD, AnswerChecker.answerFor(true).rating)
        assertEquals(Rating.AGAIN, AnswerChecker.answerFor(false).rating)
    }
}
