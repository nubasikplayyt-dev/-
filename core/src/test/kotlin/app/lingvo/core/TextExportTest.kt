package app.lingvo.core

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextExportTest {
    private val entries = listOf(
        VocabEntry(id = "de-2", language = "de", term = "eine Entscheidung treffen", translation = "принимать решение",
            exampleTarget = "Wir müssen eine Entscheidung treffen.", exampleRu = "Нам нужно принять решение.", createdAt = 1, updatedAt = 1),
        VocabEntry(id = "de-1", language = "de", term = "die Voraussetzung", translation = "условие, \"предпосылка\"", article = "die",
            plural = "Voraussetzungen", partOfSpeech = "noun", level = "B2", grammarNotes = "R&D <test>", createdAt = 1, updatedAt = 1),
    )

    @Test
    fun `plain text is readable, sorted ignoring article, with examples`() {
        val t = TextExport.plain(Languages.GERMAN, entries)
        val lines = t.lines()
        assertEquals("🇩🇪 Немецкий — 2 слова", lines[0])
        assertTrue(lines[2].startsWith("eine Entscheidung treffen — принимать решение"))
        assertTrue(t.contains("die Voraussetzung — условие, \"предпосылка\" (мн. ч. Voraussetzungen, существительное, B2)"))
        assertTrue(t.contains("   Wir müssen eine Entscheidung treffen. — Нам нужно принять решение."))
        val noExamples = TextExport.plain(Languages.GERMAN, entries, TextExportOptions(includeExamples = false))
        assertTrue(!noExamples.contains("Wir müssen"))
    }

    @Test
    fun `csv escapes quotes and commas and has bom`() {
        val c = TextExport.csv(Languages.GERMAN, entries)
        assertTrue(c.startsWith("﻿Немецкий,Перевод"))
        assertTrue(c.contains("\"условие, \"\"предпосылка\"\"\""))
        assertEquals(3, c.trim().lines().size)
    }

    @Test
    fun `markdown escapes special characters`() {
        val m = TextExport.markdown(Languages.GERMAN, entries, TextExportOptions(includeGrammar = true))
        assertTrue(m.startsWith("# 🇩🇪 Немецкий"))
        assertTrue(m.contains("- **die Voraussetzung** — условие"))
    }

    @Test
    fun `docx is a valid zip with escaped document xml`() {
        val bytes = TextExport.docx(Languages.GERMAN, entries, TextExportOptions(includeGrammar = true))
        val names = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                names[e.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml"), names.keys)
        val doc = names.getValue("word/document.xml")
        assertTrue(doc.contains("R&amp;D &lt;test&gt;"))
        assertTrue(doc.contains("die Voraussetzung"))
        // XML корректен
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(doc.byteInputStream())
    }
}
