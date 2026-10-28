package app.lingvo.ui.screens

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.Rating
import app.lingvo.core.StatusClassifier
import app.lingvo.core.VocabEntry
import app.lingvo.ui.components.BackTopBar
import app.lingvo.ui.components.CardBody
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.EmptyState
import app.lingvo.ui.components.StatusBadge
import app.lingvo.ui.components.TermHeader
import app.lingvo.ui.components.formatInterval
import app.lingvo.ui.rememberToast
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Просмотр карточки: содержание, прогресс, история, действия. */
@Composable
fun EntryScreen(container: AppContainer, nav: NavHostController, id: String) {
    val repo = container.repository
    val card by remember(id) { repo.observeCard(id) }.collectAsStateWithLifecycle(initialValue = null)
    val logs by remember(id) { repo.observeEntryLogs(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    val c = card
    if (c == null) {
        androidx.compose.runtime.LaunchedEffect(id) { kotlinx.coroutines.delay(600); loaded = true }
        Scaffold(topBar = { BackTopBar("Карточка", onBack = { nav.popBackStack() }) }) { p ->
            if (loaded) EmptyState(Icons.Filled.SearchOff, "Карточка не найдена", "Возможно, она была удалена.", Modifier.padding(p))
            else Centered { CircularProgressIndicator() }
        }
        return
    }
    val spec = app.lingvo.ui.rememberCourseSpec(container, c.entry.language) ?: app.lingvo.core.LanguageSpec.forLanguage(c.entry.language)

    if (editing) {
        EditEntry(container, c.entry, onDone = { editing = false })
        return
    }

    Scaffold(topBar = {
        BackTopBar("${spec.flag} Карточка", onBack = { nav.popBackStack() }) {
            IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Редактировать") }
            IconButton(onClick = { confirm = "delete" }) { Icon(Icons.Filled.Delete, "Удалить") }
        }
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TermHeader(c.entry) { container.speaker.speak(c.entry.displayTerm, spec)?.let(toast) }
            CardBody(c.entry)
            WordPictureControls(container, c.entry, spec)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row { StatusBadge(c.status) }
                    Text(
                        StatusClassifier.explain(c.status, c.state.toSrs(), repo.retrievability(c.state)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val now = System.currentTimeMillis()
                    if (c.state.introducedAt != null && !c.state.suspended) {
                        Text(
                            if (c.state.due <= now) "Повторение: сейчас" else "Следующее повторение через ${formatInterval(c.state.due - now)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "Повторений: ${c.state.reps} · ошибок: ${c.state.lapses}" +
                            (if (c.state.reps > 0) " · сложность %.1f/10".format(c.state.difficulty) else ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
                    Text("Добавлена: ${df.format(Date(c.entry.createdAt))}" +
                        (c.state.introducedAt?.let { " · первое знакомство: ${df.format(Date(it))}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                    if (c.entry.source.isNotEmpty()) Text("Источник: ${c.entry.source}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (logs.isNotEmpty()) {
                Text("История ответов", style = MaterialTheme.typography.titleMedium)
                val dtf = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                logs.takeLast(15).reversed().forEach {
                    Text("${dtf.format(Date(it.reviewedAt))} — ${app.lingvo.core.Answer.of(Rating.of(it.rating)).labelRu}", style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            if (!c.state.suspended) {
                app.lingvo.ui.components.BigActionButton(
                    "Повторить это слово", app.lingvo.ui.theme.LocalGradients.current.sunrise,
                    subtitle = "Оно станет первым в занятии курса", icon = Icons.Filled.PlayArrow,
                ) {
                    scope.launch { repo.queueWord(c.id)?.let { course -> nav.navigate(app.lingvo.ui.Routes.review(course)) } }
                }
            }
            OutlinedButton(onClick = {
                scope.launch { repo.setSuspended(c.id, !c.state.suspended) }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (c.state.suspended) "Вернуть в повторения" else "Приостановить карточку") }
            if (c.state.reps > 0) {
                OutlinedButton(onClick = { confirm = "reset" }, modifier = Modifier.fillMaxWidth()) { Text("Сбросить прогресс") }
            }
        }
    }

    when (confirm) {
        "delete" -> ConfirmDialog(
            "Удалить карточку?",
            "«${c.entry.displayTerm}» и её прогресс будут удалены из приложения. Это не изменит JSON-файл, пока вы не сделаете экспорт.",
            "Удалить",
            onConfirm = { confirm = null; scope.launch { repo.deleteEntry(c.id); nav.popBackStack() } },
            onDismiss = { confirm = null },
        )
        "reset" -> ConfirmDialog(
            "Сбросить прогресс?",
            "Карточка снова станет новой. История ответов сохранится в статистике.",
            "Сбросить",
            onConfirm = { confirm = null; scope.launch { repo.resetProgress(c.id) } },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun EditEntry(container: AppContainer, entry: VocabEntry, onDone: () -> Unit) {
    val spec = app.lingvo.ui.rememberCourseSpec(container, entry.language) ?: app.lingvo.core.LanguageSpec.forLanguage(entry.language)
    var draft by remember(entry.id) { mutableStateOf(EntryDraft.from(entry)) }
    var dupes by remember { mutableStateOf<List<VocabEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    fun save(force: Boolean) {
        draft.validate(spec)?.let { toast(it); return }
        scope.launch {
            val updated = draft.toEntry(spec, entry.id, entry)
            val found = container.repository.findDuplicates(spec.code, updated.term, updated.article, excludeId = entry.id)
            if (found.isNotEmpty() && !force) { dupes = found; return@launch }
            try {
                container.repository.updateEntry(updated)
                toast("Сохранено. Прогресс повторений не изменился.")
                onDone()
            } catch (e: Exception) { toast("Ошибка сохранения: ${e.message}") }
        }
    }

    Scaffold(topBar = { BackTopBar("Редактирование", onBack = onDone) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            EntryForm(spec, draft, { draft = it })
            Spacer(Modifier.height(16.dp))
            Button(onClick = { save(false) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Сохранить") }
        }
    }
    if (dupes.isNotEmpty()) DuplicateDialog(dupes, onKeep = { dupes = emptyList(); save(true) }, onCancel = { dupes = emptyList() })
}

@Composable
fun DuplicateDialog(dupes: List<VocabEntry>, onKeep: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Похожая карточка уже есть") },
        text = {
            Text(
                dupes.joinToString("\n") { "• ${it.displayTerm} — ${it.translation}" } +
                    "\n\nЕсли это другое значение или конструкция, можно сохранить отдельную карточку."
            )
        },
        confirmButton = { TextButton(onClick = onKeep) { Text("Сохранить отдельно") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Отмена") } },
    )
}

@Composable
fun AddEntryScreen(container: AppContainer, nav: NavHostController, lang: String) {
    val spec = app.lingvo.ui.rememberCourseSpec(container, lang) ?: run { Centered { CircularProgressIndicator() }; return }
    var draft by remember { mutableStateOf(EntryDraft()) }
    var dupes by remember { mutableStateOf<List<VocabEntry>>(emptyList()) }
    var added by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    fun save(force: Boolean) {
        draft.validate(spec)?.let { toast(it); return }
        scope.launch {
            val e = draft.toEntry(spec, container.repository.newId(spec), null)
            val found = container.repository.findDuplicates(spec.code, e.term, e.article)
            if (found.isNotEmpty() && !force) { dupes = found; return@launch }
            try {
                container.repository.addEntry(e)
                added++
                toast("«${e.displayTerm}» добавлено")
                draft = EntryDraft(tags = draft.tags, level = draft.level)
            } catch (ex: Exception) { toast("Ошибка: ${ex.message}") }
        }
    }

    Scaffold(topBar = { BackTopBar("Новое слово · ${spec.flag} ${spec.nameRu}", onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            EntryForm(spec, draft, { draft = it })
            Spacer(Modifier.height(16.dp))
            Button(onClick = { save(false) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Добавить") }
            if (added > 0) {
                TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("Готово (добавлено: $added)") }
            }
        }
    }
    if (dupes.isNotEmpty()) DuplicateDialog(dupes, onKeep = { dupes = emptyList(); save(true) }, onCancel = { dupes = emptyList() })
}

@Composable
fun BulkAddScreen(container: AppContainer, nav: NavHostController, lang: String) {
    val spec = app.lingvo.ui.rememberCourseSpec(container, lang) ?: run { Centered { CircularProgressIndicator() }; return }
    var text by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val example = if (spec.languageCode == "de")
        "die Voraussetzung — предпосылка | Das ist eine Voraussetzung. | Это условие.\neine Entscheidung treffen — принимать решение"
    else if (spec.languageCode == "es") "la cita — встреча | Tengo una cita a las tres. | У меня встреча в три.\ndarse cuenta de — осознавать"
    else "слово — перевод | пример | перевод примера\nустойчивое выражение — перевод"

    Scaffold(topBar = { BackTopBar("Список слов · ${spec.flag}", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Одна карточка на строку: «выражение — перевод». Через | можно добавить пример и его перевод. " +
                    "Дубликаты пропускаются.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = text, onValueChange = { text = it }, minLines = 8,
                label = { Text("Слова") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Теги для всех (через запятую)") }, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = text.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val r = container.repository.bulkAdd(spec, text, tags.split(','))
                            result = buildString {
                                append("Добавлено: ${r.added}")
                                if (r.duplicates.isNotEmpty()) append("\nПропущено дубликатов: ${r.duplicates.size} (${r.duplicates.take(5).joinToString()})")
                                if (r.errors.isNotEmpty()) append("\n" + r.errors.joinToString("\n"))
                            }
                            if (r.added > 0) text = ""
                        } catch (e: Exception) {
                            result = "Ошибка: ${e.message}"
                        } finally { busy = false }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Добавить все") }
            result?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/**
 * Картинка к слову вручную: выбрать свою из галереи, найти в интернете (Википедия / Openverse) или убрать.
 * Картинка хранится в кэше по слову и показывается на карточке.
 */
@Composable
private fun WordPictureControls(container: AppContainer, entry: VocabEntry, spec: app.lingvo.core.LanguageSpec) {
    val images = container.wordImages
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val revision by images.revision.collectAsState()
    val has = remember(revision, entry.id, entry.term) { images.fileFor(entry, spec).exists() }
    var busy by remember { mutableStateOf(false) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { images.saveFromUri(entry, spec, uri) }
                .onSuccess { toast("Картинка добавлена") }.onFailure { toast("Не удалось: ${it.message}") }
            busy = false
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = {
            pick.launch(androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text(if (has) "Своя картинка" else "Добавить картинку") }
        OutlinedButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                runCatching { images.searchNow(entry, spec) }
                    .onSuccess { toast("Картинка найдена") }
                    .onFailure { toast("Не нашлось картинки: ${it.message}") }
                busy = false
            }
        }) { Text(if (busy) "Ищу…" else "Найти в интернете") }
        if (has) TextButton(enabled = !busy, onClick = { images.remove(entry, spec) }) { Text("Убрать") }
    }
}
