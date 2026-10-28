package app.lingvo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.lingvo.core.Languages
import app.lingvo.core.PartsOfSpeech
import app.lingvo.core.VocabEntry
import app.lingvo.ui.theme.CardTermStyle

/** Заголовок карточки: выражение (с артиклем), кнопка произношения. */
@Composable
fun TermHeader(entry: VocabEntry, onSpeak: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(entry.displayTerm, style = CardTermStyle, modifier = Modifier.weight(1f, fill = false))
        if (onSpeak != null) {
            IconButton(onClick = onSpeak) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Произнести") }
        }
    }
    val meta = buildList {
        if (entry.partOfSpeech.isNotEmpty()) add(PartsOfSpeech.labelRu(entry.partOfSpeech).lowercase())
        if (entry.plural.isNotEmpty()) add("мн. ч.: ${entry.plural}")
        if (entry.level.isNotEmpty()) add(entry.level)
    }
    if (meta.isNotEmpty()) {
        Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Перевод, примеры, грамматика, словосочетания. */
@Composable
fun CardBody(entry: VocabEntry, showTranslation: Boolean = true) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showTranslation) Text(entry.translation, style = MaterialTheme.typography.headlineSmall)
        if (entry.exampleTarget.isNotEmpty()) {
            HorizontalDivider()
            Text(entry.exampleTarget, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
            if (entry.exampleRu.isNotEmpty()) {
                Text(entry.exampleRu, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (entry.grammarNotes.isNotEmpty()) InfoLine("Грамматика", entry.grammarNotes)
        if (entry.verbFormsJson.isNotEmpty()) InfoLine("Формы глагола", prettyVerbForms(entry.verbFormsJson))
        if (entry.collocations.isNotEmpty()) InfoLine("Сочетания", entry.collocations.joinToString("\n• ", prefix = "• "))
        if (entry.regionalUsage.isNotEmpty()) InfoLine("Где употребляется", entry.regionalUsage)
        if (entry.notes.isNotEmpty()) InfoLine("Заметки", entry.notes)
        if (entry.tags.isNotEmpty()) {
            Text(entry.tags.joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun InfoLine(title: String, text: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** {"present":"trifft","past":"traf"} → «present: trifft, past: traf». */
fun prettyVerbForms(json: String): String = runCatching {
    val el = app.lingvo.core.VocabularyJson.json.parseToJsonElement(json)
    when (el) {
        is kotlinx.serialization.json.JsonObject -> el.entries.joinToString("\n") { (k, v) ->
            "$k: ${(v as? kotlinx.serialization.json.JsonPrimitive)?.content ?: v.toString()}"
        }
        is kotlinx.serialization.json.JsonArray -> el.joinToString(", ") { (it as? kotlinx.serialization.json.JsonPrimitive)?.content ?: it.toString() }
        is kotlinx.serialization.json.JsonPrimitive -> el.content
    }
}.getOrDefault(json)

fun languageOf(entry: VocabEntry) = app.lingvo.core.LanguageSpec.forLanguage(entry.language)
