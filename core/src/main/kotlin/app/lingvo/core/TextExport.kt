package app.lingvo.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Форматы «человеческого» экспорта (в отличие от JSON-словаря для импорта). */
enum class TextExportFormat(val labelRu: String, val extension: String, val mimeType: String) {
    PLAIN("Текст", "txt", "text/plain"),
    MARKDOWN("Markdown", "md", "text/markdown"),
    CSV("Таблица CSV", "csv", "text/csv"),
    DOCX("Документ Word", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
}

data class TextExportOptions(
    val includeExamples: Boolean = true,
    val includeGrammar: Boolean = false,
)

/**
 * Экспорт слов в читаемом виде: текст для чата/заметок, Markdown, CSV для таблиц,
 * документ .docx (Word, Google Docs). Минимальный DOCX собирается вручную — без зависимостей.
 */
object TextExport {
    fun title(spec: LanguageSpec, count: Int) =
        "${spec.flag} ${spec.nameRu} — ${count} ${SessionText.plural(count, "слово", "слова", "слов")}"

    private fun sorted(entries: List<VocabEntry>) =
        entries.sortedBy { Normalizer.searchKey(it.term.removePrefix(it.article).trim()) }

    private fun meta(e: VocabEntry): String = buildList {
        if (e.plural.isNotEmpty()) add("мн. ч. ${e.plural}")
        if (e.partOfSpeech.isNotEmpty()) add(PartsOfSpeech.labelRu(e.partOfSpeech).lowercase())
        if (e.level.isNotEmpty()) add(e.level)
    }.joinToString(", ")

    fun plain(spec: LanguageSpec, entries: List<VocabEntry>, o: TextExportOptions = TextExportOptions()): String = buildString {
        appendLine(title(spec, entries.size))
        appendLine()
        sorted(entries).forEach { e ->
            append(e.displayTerm).append(" — ").append(e.translation)
            meta(e).takeIf { it.isNotEmpty() }?.let { append(" (").append(it).append(")") }
            appendLine()
            if (o.includeExamples && e.exampleTarget.isNotEmpty()) {
                append("   ").append(e.exampleTarget)
                if (e.exampleRu.isNotEmpty()) append(" — ").append(e.exampleRu)
                appendLine()
            }
            if (o.includeGrammar && e.grammarNotes.isNotEmpty()) appendLine("   ${e.grammarNotes}")
        }
    }

    fun markdown(spec: LanguageSpec, entries: List<VocabEntry>, o: TextExportOptions = TextExportOptions()): String = buildString {
        appendLine("# ${title(spec, entries.size)}")
        appendLine()
        sorted(entries).forEach { e ->
            append("- **").append(md(e.displayTerm)).append("** — ").append(md(e.translation))
            meta(e).takeIf { it.isNotEmpty() }?.let { append(" _(").append(md(it)).append(")_") }
            appendLine()
            if (o.includeExamples && e.exampleTarget.isNotEmpty()) {
                append("  - _").append(md(e.exampleTarget)).append("_")
                if (e.exampleRu.isNotEmpty()) append(" — ").append(md(e.exampleRu))
                appendLine()
            }
            if (o.includeGrammar && e.grammarNotes.isNotEmpty()) appendLine("  - ${md(e.grammarNotes)}")
        }
    }

    /** CSV с BOM — Excel и Google Таблицы правильно открывают кириллицу и ñ/ß. */
    fun csv(spec: LanguageSpec, entries: List<VocabEntry>): String = buildString {
        append('﻿')
        appendLine(listOf(spec.nameRu, "Перевод", "Артикль", "Мн. ч.", "Часть речи", "Пример", "Перевод примера", "Уровень", "Теги", "Грамматика")
            .joinToString(",") { csvCell(it) })
        sorted(entries).forEach { e ->
            appendLine(
                listOf(e.term, e.translation, e.article, e.plural, PartsOfSpeech.labelRu(e.partOfSpeech), e.exampleTarget,
                    e.exampleRu, e.level, e.tags.joinToString("; "), e.grammarNotes).joinToString(",") { csvCell(it) }
            )
        }
    }

    fun text(format: TextExportFormat, spec: LanguageSpec, entries: List<VocabEntry>, o: TextExportOptions): String = when (format) {
        TextExportFormat.PLAIN -> plain(spec, entries, o)
        TextExportFormat.MARKDOWN -> markdown(spec, entries, o)
        TextExportFormat.CSV -> csv(spec, entries)
        TextExportFormat.DOCX -> plain(spec, entries, o)
    }

    fun bytes(format: TextExportFormat, spec: LanguageSpec, entries: List<VocabEntry>, o: TextExportOptions): ByteArray =
        if (format == TextExportFormat.DOCX) docx(spec, entries, o) else text(format, spec, entries, o).toByteArray(Charsets.UTF_8)

    fun docx(spec: LanguageSpec, entries: List<VocabEntry>, o: TextExportOptions = TextExportOptions()): ByteArray {
        val body = StringBuilder()
        body.append(paragraph(run(title(spec, entries.size), bold = true, size = 32), spacingAfter = 240))
        sorted(entries).forEach { e ->
            val runs = StringBuilder()
            runs.append(run(e.displayTerm, bold = true)).append(run(" — ${e.translation}"))
            meta(e).takeIf { it.isNotEmpty() }?.let { runs.append(run(" ($it)", color = "666666")) }
            body.append(paragraph(runs.toString(), spacingAfter = if (o.includeExamples && e.exampleTarget.isNotEmpty()) 0 else 120))
            if (o.includeExamples && e.exampleTarget.isNotEmpty()) {
                val ex = StringBuilder(run(e.exampleTarget, italic = true))
                if (e.exampleRu.isNotEmpty()) ex.append(run(" — ${e.exampleRu}", color = "666666"))
                body.append(paragraph(ex.toString(), indent = 360, spacingAfter = 120))
            }
            if (o.includeGrammar && e.grammarNotes.isNotEmpty()) {
                body.append(paragraph(run(e.grammarNotes, size = 20, color = "444444"), indent = 360, spacingAfter = 120))
            }
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="709" w:footer="709" w:gutter="0"/></w:sectPr></w:body></w:document>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rels)
            put("word/document.xml", document)
        }
        return out.toByteArray()
    }

    private fun paragraph(runs: String, indent: Int = 0, spacingAfter: Int = 120): String =
        "<w:p><w:pPr><w:spacing w:after=\"$spacingAfter\"/>" + (if (indent > 0) "<w:ind w:left=\"$indent\"/>" else "") + "</w:pPr>$runs</w:p>"

    private fun run(text: String, bold: Boolean = false, italic: Boolean = false, size: Int = 0, color: String = ""): String {
        val props = buildString {
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            if (color.isNotEmpty()) append("<w:color w:val=\"$color\"/>")
            if (size > 0) append("<w:sz w:val=\"$size\"/>")
        }
        return "<w:r>" + (if (props.isNotEmpty()) "<w:rPr>$props</w:rPr>" else "") + "<w:t xml:space=\"preserve\">${xml(text)}</w:t></w:r>"
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it >= ' ' }

    private fun md(s: String) = s.replace(Regex("([\\\\`*_\\[\\]#|])"), "\\\\$1")

    private fun csvCell(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == ';' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
