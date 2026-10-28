package app.lingvo.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Канонические словари в репозитории должны всегда читаться приложением без ошибок. */
class CanonicalFilesTest {
    private fun file(spec: LanguageSpec) = File("../vocabulary/${spec.fileName}")

    @Test
    fun `canonical vocabulary files are valid and consistent`() {
        for (spec in Languages.legacy) {
            val f = file(spec)
            assertTrue(f.exists(), "Нет файла ${f.path}")
            val parsed = VocabularyJson.parse(f.readText(Charsets.UTF_8), spec, 0L)
            assertEquals(spec.code, parsed.language.code)
            assertTrue(parsed.problems.isEmpty(), "Ошибки в ${spec.fileName}: ${parsed.problems}")
            assertTrue(parsed.entries.all { it.hasId }, "У всех карточек должен быть ID")
            val ids = parsed.entries.map { it.entry.id }
            assertEquals(ids.size, ids.toSet().size, "ID повторяются")
            val keys = parsed.entries.map { Normalizer.entryKey(it.entry.term, it.entry.article) }
            assertEquals(keys.size, keys.toSet().size, "Выражения повторяются")
            if (spec == Languages.GERMAN) {
                parsed.entries.filter { it.entry.partOfSpeech == PartsOfSpeech.NOUN }.forEach {
                    assertTrue(it.entry.article in spec.articles, "Нет артикля у ${it.entry.term}")
                }
            }
        }
    }
}
