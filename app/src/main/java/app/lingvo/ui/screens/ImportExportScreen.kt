package app.lingvo.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import app.lingvo.core.TextExport
import app.lingvo.core.TextExportFormat
import app.lingvo.core.TextExportOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.IncomingImport
import app.lingvo.core.BackupCodec
import app.lingvo.core.ConflictPolicy
import app.lingvo.core.ImportPlan
import app.lingvo.core.VocabularyJson
import app.lingvo.core.ParsedVocabulary
import app.lingvo.core.VocabularyFormatException
import app.lingvo.data.UrlFetcher
import app.lingvo.ui.components.BackTopBar
import app.lingvo.ui.components.SectionTitle
import app.lingvo.ui.rememberToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private const val MAX_FILE_BYTES = 20L * 1024 * 1024

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    container: AppContainer,
    nav: NavHostController,
    initialLang: String?,
    incoming: IncomingImport?,
    onIncomingConsumed: () -> Unit,
) {
    val repo = container.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    var lang by remember { mutableStateOf(initialLang) }
    var policy by remember { mutableStateOf(ConflictPolicy.KEEP_LOCAL) }
    var parsed by remember { mutableStateOf<ParsedVocabulary?>(null) }
    var plan by remember { mutableStateOf<ImportPlan?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<String, String>?>(null) }
    var exportLang by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf("") }
    val courses = app.lingvo.ui.rememberAppState(container).value?.courses.orEmpty()
    val specs = courses.map { it.spec }
    fun specOf(id: String) = specs.firstOrNull { it.code == id }
    /** Язык файла, для которого ещё нет курса, — предлагаем создать курс. */
    var missingLanguage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { url = container.settings.current().importUrl }
    val history by remember(repo) { repo.observeImportHistory() }.collectAsStateWithLifecycle(initialValue = emptyList())

    fun analyze(text: String, name: String) {
        rawText = text; sourceName = name; error = null; plan = null; parsed = null
        scope.launch {
            busy = true
            try {
                missingLanguage = null
                var target = lang?.let { specOf(it) }
                if (target == null) {
                    // «Авто»: курс по языку файла.
                    val fileLang = VocabularyJson.peekLanguage(text)
                        ?: throw VocabularyFormatException("Не удалось определить язык файла. Выберите курс вручную.")
                    val matches = specs.filter { it.languageCode == fileLang }
                    when {
                        matches.size == 1 -> target = matches.first()
                        matches.size > 1 -> throw VocabularyFormatException("Для этого языка несколько курсов — выберите нужный выше.")
                        else -> { missingLanguage = fileLang; return@launch }
                    }
                }
                val p = withContext(Dispatchers.Default) { repo.parseVocabulary(text, target) }
                parsed = p
                lang = p.language.code
                plan = repo.planImport(p, policy)
            } catch (e: VocabularyFormatException) {
                error = e.message
            } catch (e: Exception) {
                error = "Не удалось прочитать данные: ${e.message}"
            } finally { busy = false }
        }
    }

    fun readUri(uri: Uri, then: (String, String) -> Unit) {
        scope.launch {
            try {
                val (name, text) = withContext(Dispatchers.IO) { readText(context, uri) }
                then(text, name)
            } catch (e: Exception) {
                error = e.message ?: "Не удалось открыть файл"
            }
        }
    }

    val openVocab = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readUri(it) { text, name -> analyze(text, name) } }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readUri(it) { text, name -> pendingRestore = name to text } }
    }
    val saveVocab = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val code = exportLang
        if (uri != null && code != null) scope.launch {
            try {
                val json = repo.exportVocabulary(specOf(code) ?: return@launch)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) } }
                toast("Словарь сохранён")
            } catch (e: Exception) { toast("Не удалось сохранить: ${e.message}") }
        }
    }
    var textFormat by remember { mutableStateOf(TextExportFormat.PLAIN) }
    var includeExamples by remember { mutableStateOf(true) }
    var includeGrammar by remember { mutableStateOf(false) }
    var textExportLang by remember { mutableStateOf<String?>(null) }
    val saveText = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        val code = textExportLang
        if (uri != null && code != null) scope.launch {
            try {
                val spec = specOf(code) ?: return@launch
                val bytes = withContext(Dispatchers.Default) {
                    TextExport.bytes(textFormat, spec, repo.entriesOf(spec), TextExportOptions(includeExamples, includeGrammar))
                }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) } }
                toast("Сохранено: ${textFormat.labelRu}")
            } catch (e: Exception) { toast("Не удалось сохранить: ${e.message}") }
        }
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = repo.createBackup()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) } }
                toast("Резервная копия сохранена")
            } catch (e: Exception) { toast("Не удалось создать копию: ${e.message}") }
        }
    }

    LaunchedEffect(incoming) {
        when (val inc = incoming) {
            is IncomingImport.FromUri -> readUri(inc.uri) { text, name -> analyze(text, name) }
            is IncomingImport.FromText -> analyze(inc.text, "Поделились текстом")
            null -> Unit
        }
        if (incoming != null) onIncomingConsumed()
    }

    Scaffold(topBar = { BackTopBar("Импорт и экспорт", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Импорт словаря")
            Text(
                "JSON-словарь из Claude (например, spanish_vocabulary.json). Существующие карточки и прогресс сохраняются, " +
                    "повторный импорт не создаёт дубликатов, ничего не удаляется.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(lang == null, { lang = null; rawText?.let { analyze(it, sourceName) } }, { Text("Авто") })
                courses.forEach { c ->
                    FilterChip(lang == c.id, { lang = c.id; rawText?.let { analyze(it, sourceName) } }, { Text("${c.flag} ${c.name}") })
                }
            }
            missingLanguage?.let { code ->
                val info = app.lingvo.core.LanguageCatalog.infoOrCustom(code)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("В файле словарь языка «${info.nameRu}», а такого курса ещё нет.")
                        Button(onClick = {
                            scope.launch {
                                val c = repo.createCourse(info.code, info.nameRu, info.flag)
                                lang = c.id; missingLanguage = null
                                rawText?.let { analyze(it, sourceName) }
                            }
                        }) { Text("Создать курс ${info.flag} ${info.nameRu} и продолжить") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openVocab.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                    ButtonContent(Icons.Filled.FolderOpen, "Файл")
                }
                FilledTonalButton(onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (text.isNullOrBlank()) toast("Буфер обмена пуст") else analyze(text, "Буфер обмена")
                }, modifier = Modifier.weight(1f)) { ButtonContent(Icons.Filled.ContentPaste, "Из буфера") }
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Или HTTPS-ссылка на файл") },
                placeholder = { Text("https://raw.githubusercontent.com/…/german_vocabulary.json") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                enabled = url.isNotBlank() && !busy,
                onClick = {
                    UrlFetcher.validate(url)?.let { toast(it); return@OutlinedButton }
                    scope.launch {
                        busy = true
                        try {
                            val text = UrlFetcher.fetch(url)
                            container.settings.update { it.copy(importUrl = url.trim()) }
                            analyze(text, UrlFetcher.normalize(url).substringAfterLast('/'))
                        } catch (e: Exception) {
                            error = e.message ?: "Не удалось загрузить файл."
                        } finally { busy = false }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { ButtonContent(Icons.Filled.CloudDownload, "Загрузить по ссылке") }

            if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            plan?.let { p ->
                ImportPreview(
                    p, sourceName, policy,
                    onPolicy = { newPolicy ->
                        policy = newPolicy
                        parsed?.let { pv -> scope.launch { plan = repo.planImport(pv, newPolicy) } }
                    },
                    onApply = {
                        val pv = parsed ?: return@ImportPreview
                        scope.launch {
                            busy = true
                            try {
                                val result = repo.applyImport(pv, policy, sourceName)
                                toast("Импорт выполнен. ${result.summaryRu()}")
                                plan = null; parsed = null; rawText = null
                            } catch (e: Exception) {
                                error = "Импорт отменён, база данных не изменена: ${e.message}"
                            } finally { busy = false }
                        }
                    },
                    onCancel = { plan = null; parsed = null; rawText = null },
                )
            }

            SectionTitle("Экспорт словаря")
            Text(
                "Экспорт содержит только слова (без истории повторений) — его можно отправить Claude для пополнения.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            specs.forEach { spec ->
                Text("${spec.flag} ${spec.nameRu}", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { exportLang = spec.code; saveVocab.launch(spec.fileName) }, modifier = Modifier.weight(1f)) {
                        ButtonContent(Icons.Filled.Save, "Файл")
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            try { shareJson(context, spec.fileName, repo.exportVocabulary(spec)) } catch (e: Exception) { toast("Ошибка: ${e.message}") }
                        }
                    }, modifier = Modifier.weight(1f)) { ButtonContent(Icons.Filled.Share, "Отправить") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val json = repo.exportVocabulary(spec)
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(spec.fileName, json))
                            toast("Скопировано: ${json.length} символов")
                        }
                    }, modifier = Modifier.weight(1f)) { ButtonContent(Icons.Filled.ContentCopy, "Копия") }
                }
            }

            SectionTitle("Экспорт списком слов")
            Text(
                "Читаемый список: текст для чата или заметок, Markdown, таблица CSV (Excel, Google Таблицы) " +
                    "или документ Word (.docx, открывается и в Google Документах). Обратно в приложение не импортируется — для этого JSON.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextExportFormat.entries.forEach { f ->
                    FilterChip(selected = textFormat == f, onClick = { textFormat = f }, label = { Text(f.labelRu) })
                }
            }
            if (textFormat != TextExportFormat.CSV) {
                Row(Modifier.fillMaxWidth().toggleable(includeExamples, role = Role.Checkbox) { includeExamples = it }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeExamples, onCheckedChange = null)
                    Text("С примерами", Modifier.padding(start = 8.dp))
                }
                Row(Modifier.fillMaxWidth().toggleable(includeGrammar, role = Role.Checkbox) { includeGrammar = it }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeGrammar, onCheckedChange = null)
                    Text("С грамматическими пояснениями", Modifier.padding(start = 8.dp))
                }
            }
            val exportOptions = TextExportOptions(includeExamples, includeGrammar)
            specs.forEach { spec ->
                Text("${spec.flag} ${spec.nameRu}", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        textExportLang = spec.code
                        saveText.launch(textFileName(spec, textFormat) to textFormat.mimeType)
                    }, modifier = Modifier.weight(1f)) { ButtonContent(Icons.Filled.Save, "Файл") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            try {
                                val list = repo.entriesOf(spec)
                                if (list.isEmpty()) { toast("Словарь пуст"); return@launch }
                                shareTextExport(context, spec, list, textFormat, exportOptions)
                            } catch (e: Exception) { toast("Ошибка: ${e.message}") }
                        }
                    }, modifier = Modifier.weight(1f)) { ButtonContent(Icons.Filled.Share, "Отправить") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val list = repo.entriesOf(spec)
                            val fmt = if (textFormat == TextExportFormat.DOCX) TextExportFormat.PLAIN else textFormat
                            val text = TextExport.text(fmt, spec, list, exportOptions)
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText(spec.nameRu, text))
                            toast("Скопировано ${list.size} ${app.lingvo.core.SessionText.words(list.size)} текстом")
                        }
                    }, modifier = Modifier.weight(1f)) { ButtonContent(Icons.Filled.ContentCopy, "Текст") }
                }
            }

            SectionTitle("Резервная копия")
            Text(
                "Полная копия: оба словаря, прогресс, история повторений и настройки. Перед каждым импортом и восстановлением " +
                    "приложение само сохраняет автоматическую копию (последние 5).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { saveBackup.launch("lingvo-backup-${java.time.LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) {
                    ButtonContent(Icons.Filled.Save, "Создать")
                }
                OutlinedButton(onClick = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                    ButtonContent(Icons.Filled.Restore, "Восстановить")
                }
            }
            val autos = remember(history, busy) { repo.listAutoBackups() }
            if (autos.isNotEmpty()) {
                Text("Автоматические копии", style = MaterialTheme.typography.titleSmall)
                val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                autos.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(df.format(Date(f.lastModified())), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            scope.launch { pendingRestore = f.name to withContext(Dispatchers.IO) { f.readText() } }
                        }) { Text("Восстановить") }
                    }
                }
            }

            if (history.isNotEmpty()) {
                SectionTitle("История импорта")
                val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                history.forEach { h ->
                    val spec = specOf(h.language)
                    Text(
                        "${df.format(Date(h.importedAt))} ${spec?.flag.orEmpty()} ${h.sourceName}: +${h.added}, изменено ${h.updated}, " +
                            "без изменений ${h.unchanged}" + (if (h.skipped + h.errors > 0) ", пропущено ${h.skipped + h.errors}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    pendingRestore?.let { (name, text) ->
        val info = remember(text) {
            runCatching { BackupCodec.decode(text) }.fold(
                { "Копия от ${it.createdAt}: ${it.entries.size} карточек, ${it.reviewHistory.size} ответов в истории." to true },
                { (it.message ?: "Файл не является резервной копией.") to false },
            )
        }
        ConfirmDialog(
            title = if (info.second) "Восстановить из копии?" else "Нельзя восстановить",
            text = if (info.second) "$name\n${info.first}\n\nТекущие карточки и прогресс будут заменены содержимым копии. " +
                "Перед этим будет сохранена автоматическая копия текущего состояния." else info.first,
            confirmText = if (info.second) "Восстановить" else "Понятно",
            onConfirm = {
                pendingRestore = null
                if (info.second) scope.launch {
                    busy = true
                    try { toast("Восстановлено карточек: ${repo.restoreBackup(text)}") }
                    catch (e: Exception) { toast("Восстановление отменено: ${e.message}") }
                    finally { busy = false }
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }
}

@Composable
private fun ImportPreview(
    plan: ImportPlan,
    source: String,
    policy: ConflictPolicy,
    onPolicy: (ConflictPolicy) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Предварительный отчёт · ${plan.language.flag} ${plan.language.nameRu}", style = MaterialTheme.typography.titleMedium)
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReportLine("Новые записи", plan.newEntries.size, plan.newEntries.map { "${it.displayTerm} — ${it.translation}" })
            ReportLine("Изменённые записи", plan.changed.size, plan.changed.map { "${it.after.displayTerm}: ${it.changedFields.joinToString()}" })
            ReportLine("Без изменений", plan.unchangedTotal, emptyList())
            ReportLine("Пропущенные", plan.skipped.size, plan.skipped.map { "${it.label}: ${it.reason}" })
            ReportLine("Ошибки в данных", plan.problems.size, plan.problems.map { "${it.label}: ${it.message}" })
            if (plan.conflicts.isNotEmpty()) {
                ReportLine(
                    "Конфликты", plan.conflicts.size,
                    plan.conflicts.flatMap { u -> u.conflicts.map { "${u.before.displayTerm} · ${it.field}: «${it.localValue}» ↔ «${it.fileValue}»" } },
                )
                Text("При расхождении с вашими правками:", style = MaterialTheme.typography.labelLarge)
                PolicyOption("Оставить мои значения", policy == ConflictPolicy.KEEP_LOCAL) { onPolicy(ConflictPolicy.KEEP_LOCAL) }
                PolicyOption("Взять значения из файла", policy == ConflictPolicy.TAKE_FILE) { onPolicy(ConflictPolicy.TAKE_FILE) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("Отмена") }
                Button(onClick = onApply, enabled = plan.hasChanges, modifier = Modifier.weight(1f)) {
                    Text(if (plan.hasChanges) "Импортировать" else "Нечего импортировать")
                }
            }
        }
    }
}

@Composable
private fun ReportLine(title: String, count: Int, details: List<String>) {
    var open by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().selectable(selected = open, enabled = details.isNotEmpty(), role = Role.Button) { open = !open },
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("$count" + if (details.isNotEmpty()) (if (open) "  ▲" else "  ▼") else "", style = MaterialTheme.typography.bodyMedium)
        }
        if (open) details.take(100).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        if (open && details.size > 100) Text("…и ещё ${details.size - 100}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PolicyOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(text, Modifier.padding(start = 8.dp))
    }
}

private fun readText(context: Context, uri: Uri): Pair<String, String> {
    val resolver = context.contentResolver
    var name = uri.lastPathSegment ?: "файл"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    if (size > MAX_FILE_BYTES) throw IllegalStateException("Файл слишком большой (больше 20 МБ).")
    val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        ?: throw IllegalStateException("Не удалось открыть файл.")
    return name to text
}

/** Создание документа с заданным MIME-типом: вход — (имя файла, тип). */
private class CreateTypedDocument : androidx.activity.result.contract.ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second).putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}

private fun textFileName(spec: app.lingvo.core.LanguageSpec, f: TextExportFormat) =
    "${spec.fileName.substringBefore('_')}_words.${f.extension}"

/** Отправка списка слов через Sharesheet: файл нужного формата + текст в EXTRA_TEXT для мессенджеров. */
fun shareTextExport(context: Context, spec: app.lingvo.core.LanguageSpec, entries: List<app.lingvo.core.VocabEntry>, f: TextExportFormat, o: TextExportOptions) {
    val plain = TextExport.text(if (f == TextExportFormat.DOCX || f == TextExportFormat.CSV) TextExportFormat.PLAIN else f, spec, entries, o)
    val send = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_SUBJECT, TextExport.title(spec, entries.size))
        if (f == TextExportFormat.PLAIN) {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, plain)
        } else {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, textFileName(spec, f)).apply { writeBytes(TextExport.bytes(f, spec, entries, o)) }
            type = f.mimeType
            putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.files", file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(send, "Отправить слова").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun shareJson(context: Context, fileName: String, json: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName).apply { writeText(json) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Отправить словарь").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
