package app.lingvo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.lingvo.core.AnswerCheck
import app.lingvo.core.AnswerChecker
import app.lingvo.core.AutoResult
import app.lingvo.core.DiffKind
import app.lingvo.core.LanguageSpec
import app.lingvo.core.VocabEntry
import app.lingvo.ui.theme.CardTermStyle
import app.lingvo.ui.theme.LocalGradients

/**
 * Письменный ответ — общий блок для занятия и теста.
 *
 * Показывается вопрос, поле ввода и кнопка «Проверить». Автопроверка — только подсказка
 * (лишние пробелы, регистр там, где он не важен, сохранённые варианты; диакритика не игнорируется).
 * Окончательно решает пользователь: «Ответ правильный» / «Ответ неправильный».
 * Набранный текст и этап проверки переживают сворачивание и поворот экрана.
 *
 * @param askTerm true — пишем слово на изучаемом языке (показан перевод),
 *                false — пишем перевод (показано слово).
 */
@Composable
fun TypingAnswer(
    spec: LanguageSpec,
    entry: VocabEntry,
    askTerm: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onSpeak: () -> Unit,
    onSwitchToButtons: (() -> Unit)? = null,
    onConfirm: (check: AnswerCheck, correct: Boolean, saveAlternative: Boolean) -> Unit,
) {
    var typed by rememberSaveable(entry.id, askTerm) { mutableStateOf("") }
    var checked by rememberSaveable(entry.id, askTerm) { mutableStateOf(false) }
    var saveAlt by rememberSaveable(entry.id, askTerm) { mutableStateOf(false) }
    val check = remember(checked, typed, entry.id, askTerm) {
        if (!checked) null
        else if (askTerm) AnswerChecker.check(typed, entry, spec) else AnswerChecker.checkTranslation(typed, entry)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(entry.id, askTerm, checked) { if (!checked) runCatching { focus.requestFocus() } }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(26.dp), spotColor = Color(0x333D5AFE))
                .clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (askTerm) "Напишите на языке «${spec.nameRu}»" else "Напишите перевод",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(if (askTerm) entry.translation else entry.displayTerm, style = CardTermStyle)
            if (askTerm && spec.articles.isNotEmpty() && entry.article.isNotEmpty() && !checked) {
                Text("Вместе с артиклем.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (check == null) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Ваш ответ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { checked = true }),
                textStyle = MaterialTheme.typography.titleLarge,
            )
            BigActionButton("Проверить", LocalGradients.current.hero, icon = Icons.Filled.Check, enabled = !busy) {
                checked = true
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { typed = ""; checked = true }) { Text("Не знаю — показать") }
                onSwitchToButtons?.let { TextButton(onClick = it) { Text("К кнопкам «Помню»") } }
            }
        } else {
            ConfirmCard(check, entry, askTerm, saveAlt, { saveAlt = it }, onSpeak)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ConfirmButton("Ответ неправильный", false, !busy, Modifier.weight(1f)) { onConfirm(check, false, false) }
                ConfirmButton("Ответ правильный", true, !busy, Modifier.weight(1f)) { onConfirm(check, true, saveAlt) }
            }
            if (!check.result.looksCorrect) {
                Text(
                    "Решаете вы: если ваш вариант тоже верен — нажмите «Ответ правильный».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** Карточка подтверждения: что написал пользователь и что ожидалось. */
@Composable
private fun ConfirmCard(
    check: AnswerCheck,
    entry: VocabEntry,
    askTerm: Boolean,
    saveAlt: Boolean,
    onSaveAlt: (Boolean) -> Unit,
    onSpeak: () -> Unit,
) {
    val ok = check.result.looksCorrect
    val tone = if (ok) Color(0xFF1FB87F)
    else if (check.result == AutoResult.WRONG || check.result == AutoResult.EMPTY) Color(0xFFFF8A5B)
    else Color(0xFFE8A20C)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .border(2.dp, tone.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(check.result.labelRu, style = MaterialTheme.typography.titleMedium, color = tone)
        Text("Твой ответ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (check.typed.isEmpty()) Text("—", style = MaterialTheme.typography.titleLarge)
        else Text(
            buildAnnotatedString {
                check.diff.forEach { part ->
                    when (part.kind) {
                        DiffKind.SAME -> append(part.text)
                        DiffKind.WRONG -> withStyle(SpanStyle(color = Color(0xFFE8590C), textDecoration = TextDecoration.Underline)) { append(part.text) }
                        DiffKind.EXTRA -> withStyle(SpanStyle(color = Color(0xFFE8590C), textDecoration = TextDecoration.LineThrough)) { append(part.text) }
                        DiffKind.MISSING -> withStyle(SpanStyle(color = Color(0xFF1FB87F), fontWeight = FontWeight.Bold)) { append(part.text) }
                    }
                }
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { contentDescription = "Ваш ответ: ${check.typed}" },
        )
        Text("Ожидаемый вариант", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (askTerm) {
            TermHeader(entry, onSpeak)
            if (entry.transcription.isNotEmpty()) {
                Text(entry.transcription, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.alternatives.isNotEmpty()) {
                Text("Тоже подходит: " + entry.alternatives.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(entry.translation, style = MaterialTheme.typography.headlineSmall)
        }
        // Свой вариант можно запомнить только для слова: переводы хранятся одной строкой.
        if (askTerm && !ok && check.typed.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onSaveAlt(!saveAlt) }) {
                Checkbox(saveAlt, onSaveAlt)
                Text("Мой вариант тоже верный — запомнить его", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ConfirmButton(text: String, correct: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val brush = if (correct) Brush.linearGradient(listOf(Color(0xFF3DD68C), Color(0xFF1FB87F)))
    else Brush.linearGradient(listOf(Color(0xFFFF8A7A), Color(0xFFFF5A5F)))
    Box(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(22.dp)).background(brush)
            .clickable(enabled = enabled, onClick = onClick).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (correct) Icons.Filled.Check else Icons.Filled.Close, null, tint = Color.White,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
