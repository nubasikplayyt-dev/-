package app.lingvo.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.lingvo.core.CEFR_LEVELS
import app.lingvo.core.Categories
import app.lingvo.core.LanguageSpec
import app.lingvo.core.Normalizer
import app.lingvo.core.PartsOfSpeech
import app.lingvo.core.VocabEntry

/** Редактируемые поля карточки (строки, чтобы форма не теряла ввод). */
data class EntryDraft(
    val term: String = "",
    val translation: String = "",
    val article: String = "",
    val plural: String = "",
    val partOfSpeech: String = "",
    val exampleTarget: String = "",
    val exampleRu: String = "",
    val level: String = "",
    val tags: String = "",
    val collocations: String = "",
    val grammarNotes: String = "",
    val notes: String = "",
    val regionalUsage: String = "",
) {
    /** Сообщение об ошибке для пользователя или null. */
    fun validate(spec: LanguageSpec): String? = when {
        Normalizer.clean(term).isEmpty() -> "Введите слово или выражение."
        Normalizer.clean(translation).isEmpty() -> "Введите перевод."
        article.isNotEmpty() && spec.articles.isNotEmpty() && article !in spec.articles -> "Недопустимый артикль."
        else -> null
    }

    fun toEntry(spec: LanguageSpec, id: String, base: VocabEntry?): VocabEntry {
        // Если артикль выбран отдельно и уже написан в слове, не дублируем его.
        val cleanArticle = Normalizer.clean(article).lowercase()
        val t = Normalizer.clean(term)
        val termOut = if (cleanArticle.isNotEmpty()) Normalizer.withArticle(t, cleanArticle) else t
        val now = System.currentTimeMillis()
        return (base ?: VocabEntry(id = id, language = spec.code, term = "", translation = "", createdAt = now, updatedAt = now)).copy(
            term = termOut,
            translation = Normalizer.clean(translation),
            article = cleanArticle,
            plural = Normalizer.clean(plural),
            partOfSpeech = if (cleanArticle.isNotEmpty() && partOfSpeech.isEmpty()) PartsOfSpeech.NOUN else partOfSpeech,
            exampleTarget = Normalizer.clean(exampleTarget),
            exampleRu = Normalizer.clean(exampleRu),
            level = level,
            tags = Normalizer.tags(tags.split(',')),
            collocations = collocations.lines().map(Normalizer::clean).filter { it.isNotEmpty() },
            grammarNotes = grammarNotes.trim(),
            notes = notes.trim(),
            regionalUsage = Normalizer.clean(regionalUsage),
            source = base?.source?.ifEmpty { "Приложение" } ?: "Приложение",
        )
    }

    companion object {
        fun from(e: VocabEntry) = EntryDraft(
            term = e.term, translation = e.translation, article = e.article, plural = e.plural,
            partOfSpeech = e.partOfSpeech, exampleTarget = e.exampleTarget, exampleRu = e.exampleRu, level = e.level,
            tags = e.tags.joinToString(", "), collocations = e.collocations.joinToString("\n"),
            grammarNotes = e.grammarNotes, notes = e.notes, regionalUsage = e.regionalUsage,
        )
    }
}

@Composable
fun EntryForm(spec: LanguageSpec, draft: EntryDraft, onChange: (EntryDraft) -> Unit, modifier: Modifier = Modifier) {
    val hasArticles = spec.articles.isNotEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field("${spec.flag} Слово или выражение *", draft.term, { onChange(draft.copy(term = it)) }, capitalize = false)
        Field("Перевод *", draft.translation, { onChange(draft.copy(translation = it)) })
        if (spec.articles.isNotEmpty()) {
            Text("Артикль (для существительных)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                (listOf("") + spec.articles.toList()).forEach { a ->
                    androidx.compose.material3.FilterChip(
                        selected = draft.article == a,
                        onClick = { onChange(draft.copy(article = a)) },
                        label = { Text(a.ifEmpty { "нет" }) },
                    )
                }
            }
        }
        if (hasArticles || draft.plural.isNotEmpty()) {
            Field("Множественное число", draft.plural, { onChange(draft.copy(plural = it)) })
        }
        FilterDropdown(
            "Часть речи",
            draft.partOfSpeech.takeIf { it.isNotEmpty() }?.let(PartsOfSpeech::labelRu),
            PartsOfSpeech.labelsRu.values.toList(),
        ) { i -> onChange(draft.copy(partOfSpeech = i?.let { PartsOfSpeech.labelsRu.keys.toList()[it] } ?: "")) }
        Field("Пример (${spec.nameRu.lowercase()})", draft.exampleTarget, { onChange(draft.copy(exampleTarget = it)) }, singleLine = false)
        Field("Перевод примера", draft.exampleRu, { onChange(draft.copy(exampleRu = it)) }, singleLine = false)
        FilterDropdown("Уровень", draft.level.takeIf { it.isNotEmpty() }, CEFR_LEVELS) { i ->
            onChange(draft.copy(level = i?.let { CEFR_LEVELS[it] } ?: ""))
        }
        Field("Категории и теги (через запятую)", draft.tags, { onChange(draft.copy(tags = it)) })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Categories.defaults.forEach { c ->
                AssistChip(onClick = {
                    val current = draft.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    if (current.none { it.equals(c, true) }) onChange(draft.copy(tags = (current + c).joinToString(", ")))
                }, label = { Text("+ $c") })
            }
        }
        Field("Устойчивые сочетания (по одному в строке)", draft.collocations, { onChange(draft.copy(collocations = it)) }, singleLine = false)
        Field("Грамматика (управление, падеж, формы)", draft.grammarNotes, { onChange(draft.copy(grammarNotes = it)) }, singleLine = false)
        Field("Где употребляется (регион, стиль)", draft.regionalUsage, { onChange(draft.copy(regionalUsage = it)) })
        Field("Личные заметки", draft.notes, { onChange(draft.copy(notes = it)) }, singleLine = false)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true, capitalize: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),
        modifier = Modifier.fillMaxWidth(),
    )
}
