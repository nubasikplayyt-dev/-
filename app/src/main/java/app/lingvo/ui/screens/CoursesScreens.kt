package app.lingvo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.DailyStreak
import app.lingvo.core.LanguageCatalog
import app.lingvo.core.LanguageInfo
import app.lingvo.core.LanguageSpec
import app.lingvo.core.SessionText
import app.lingvo.core.Starter
import app.lingvo.ui.Routes
import app.lingvo.ui.components.BackTopBar
import app.lingvo.ui.components.BigActionButton
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.SoftCard
import app.lingvo.ui.goHome
import app.lingvo.ui.rememberAppState
import app.lingvo.ui.rememberToast
import app.lingvo.ui.theme.LocalGradients
import app.lingvo.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Выбор языков. При первом запуске ничего не создаётся автоматически: пользователь сам выбирает
 * один или несколько языков, язык перевода и дневную цель. Тот же экран добавляет новые курсы.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(container: AppContainer, nav: NavHostController, firstRun: Boolean) {
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val g = LocalGradients.current
    val selected = remember { mutableStateListOf<LanguageInfo>() }
    var query by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf(LanguageCatalog.byCode("ru")!!) }
    var goal by remember { mutableStateOf(DailyStreak.DEFAULT_GOAL) }
    var starter by remember { mutableStateOf(true) }
    var customDialog by remember { mutableStateOf(false) }
    var translationDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val languages = LanguageCatalog.all.filter { it.code != translation.code } + selected.filter { LanguageCatalog.byCode(it.code) == null }
    val shown = languages.filter { query.isBlank() || it.nameRu.contains(query.trim(), ignoreCase = true) || it.code.equals(query.trim(), true) }
    val withStarter = selected.filter { Starter.has(it.code, translation.code) }
    val withoutStarter = selected.filter { !Starter.has(it.code, translation.code) }
    val starterAvailable = withStarter.isNotEmpty()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!firstRun) {
            TextButton(onClick = { nav.popBackStack() }) { Text("← Назад") }
        } else {
            Text("🌍", style = MaterialTheme.typography.displayMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text("Добро пожаловать в Lingvo World", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
        Text(if (firstRun) "Какие языки будем изучать? Можно выбрать несколько — для каждого появится свой курс."
            else "Новый языковой курс: выберите язык (или несколько).",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Найти язык") })

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { lang ->
                val on = selected.any { it.code == lang.code }
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (on) Palette.Ocean else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (on) Palette.Ocean else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .clickable { if (on) selected.removeAll { it.code == lang.code } else selected.add(lang) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(lang.flag)
                    Spacer(Modifier.width(6.dp))
                    Text(lang.nameRu, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { customDialog = true }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) { Text("＋ Другой язык", color = MaterialTheme.colorScheme.onSecondaryContainer) }
        }

        SoftCard(onClick = { translationDialog = true }) {
            Text("Язык перевода", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${translation.flag} ${translation.nameRu}", style = MaterialTheme.typography.titleMedium)
            Text("На этом языке будут переводы и примеры. Нажмите, чтобы изменить.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (firstRun) {
            Text("Ежедневная цель", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DailyStreak.GOAL_OPTIONS.forEach { n ->
                    FilterChip(selected = goal == n, onClick = { goal = n }, label = { Text("$n") })
                }
            }
            Text("Столько ответов в день засчитывает день в серию. Изменить можно в настройках.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected.isNotEmpty()) {
            SoftCard {
                Text("Начинаем с нуля", style = MaterialTheme.typography.titleMedium)
                Text("Новый курс пустой — никаких чужих слов. Для новичков есть стартовый словарь: сначала " +
                    "приветствия, «я/ты» и вопросы, числа (около 28 слов), а потом по 5–10 слов из следующих тем: " +
                    "семья, дом, еда, главные глаголы, простые описания.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (starterAvailable) {
                    Row(Modifier.fillMaxWidth().clickable { starter = !starter }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(starter, onCheckedChange = { starter = it })
                        Text("Добавить стартовый словарь: " + withStarter.joinToString(", ") { "${it.flag} ${it.nameRu}" })
                    }
                }
                if (withoutStarter.isNotEmpty()) {
                    Text(
                        "Для " + withoutStarter.joinToString(", ") { "«${it.nameRu}»" } +
                            (if (translation.code != "ru") " с переводом на «${translation.nameRu}»" else "") +
                            " стартового словаря пока нет — курс начнётся пустым. Слова можно добавить вручную, списком или импортом JSON.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        BigActionButton(
            text = if (firstRun) "Начать путешествие" else "Создать курс",
            brush = g.hero,
            subtitle = if (selected.isEmpty()) "Выберите хотя бы один язык"
            else selected.joinToString(" ") { it.flag } + " → ${translation.flag}",
            enabled = selected.isNotEmpty() && !busy,
        ) {
            busy = true
            scope.launch {
                try {
                    val created = selected.map { lang ->
                        repo.createCourse(lang.code, lang.nameRu, lang.flag, translation.code, translation.nameRu)
                    }
                    var startWith: String? = null
                    if (starter && starterAvailable) {
                        created.filter { repo.hasStarter(it.spec) }.forEach {
                            if (repo.importStarter(it.spec) != null && startWith == null) startWith = it.id
                        }
                    }
                    container.settings.update { s ->
                        s.copy(
                            activeCourseId = if (firstRun || s.activeCourseId.isEmpty()) created.first().id else created.first().id,
                            dailyGoal = if (firstRun) goal else s.dailyGoal,
                        )
                    }
                    if (firstRun) {
                        nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    } else nav.goHome()
                    // Стартовый словарь добавлен — сразу к знакомству с первыми словами.
                    startWith?.let { nav.navigate(Routes.learn(it)) }
                } catch (e: Exception) {
                    toast("Не удалось создать курс: ${e.message}")
                } finally { busy = false }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (customDialog) {
        CustomLanguageDialog(onDismiss = { customDialog = false }) { info -> customDialog = false; selected.add(info) }
    }
    if (translationDialog) {
        AlertDialog(
            onDismissRequest = { translationDialog = false },
            title = { Text("Язык перевода") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LanguageCatalog.all.forEach { l ->
                        Text("${l.flag}  ${l.nameRu}", Modifier.fillMaxWidth().clickable {
                            translation = l; selected.removeAll { it.code == l.code }; translationDialog = false
                        }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { translationDialog = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun CustomLanguageDialog(onDismiss: () -> Unit, onAdd: (LanguageInfo) -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var flag by remember { mutableStateOf("🌐") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой язык") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(code, { code = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(12) },
                    label = { Text("Код (например, tlh)") }, singleLine = true)
                OutlinedTextField(flag, { flag = it.take(8) }, label = { Text("Значок (эмодзи)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val c = code.ifBlank { name.trim().lowercase().filter { it.isLetter() }.take(6).ifEmpty { "lang" } }
                onAdd(LanguageInfo(c, name.trim(), flag.ifBlank { "🌐" }))
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Управление курсами: порядок, переименование, удаление, добавление. */
@Composable
fun CoursesScreen(container: AppContainer, nav: NavHostController) {
    val state by rememberAppState(container)
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = { BackTopBar("Языковые курсы", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.COURSE_NEW) }, containerColor = Palette.Ocean, contentColor = Color.White) {
                Icon(Icons.Filled.Add, "Добавить курс")
            }
        },
    ) { padding ->
        val s = state ?: run { Centered { CircularProgressIndicator() }; return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            s.courses.forEachIndexed { i, c ->
                val o = s.overview(c.id)
                SoftCard(onClick = { nav.navigate(Routes.language(c.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Palette.course(c.id).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center) { Text(c.flag, style = MaterialTheme.typography.headlineSmall) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Text("${c.flag} → ${LanguageCatalog.infoOrCustom(c.translationCode, c.translationName).flag} ${c.translationName} · " +
                                "${o?.total ?: 0} ${SessionText.plural(o?.total ?: 0, "слово", "слова", "слов")}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(enabled = i > 0, onClick = { scope.launch { swap(container, s.courses.map { it.id }, i, i - 1) } }) {
                            Icon(Icons.Filled.ArrowUpward, "Выше")
                        }
                        IconButton(enabled = i < s.courses.lastIndex, onClick = { scope.launch { swap(container, s.courses.map { it.id }, i, i + 1) } }) {
                            Icon(Icons.Filled.ArrowDownward, "Ниже")
                        }
                        IconButton(onClick = { nav.navigate(Routes.courseEdit(c.id)) }) { Icon(Icons.Filled.Edit, "Изменить") }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

private suspend fun swap(container: AppContainer, ids: List<String>, a: Int, b: Int) {
    val order = ids.toMutableList()
    val t = order[a]; order[a] = order[b]; order[b] = t
    order.forEachIndexed { index, id -> container.repository.course(id)?.let { container.repository.updateCourse(it.copy(sortOrder = index)) } }
}

@Composable
fun CourseEditScreen(container: AppContainer, nav: NavHostController, id: String) {
    val state by rememberAppState(container)
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val s = state ?: return
    val course = s.courses.firstOrNull { it.id == id } ?: run { Centered { Text("Курс не найден") }; return }
    var name by remember(id) { mutableStateOf(course.name) }
    var flag by remember(id) { mutableStateOf(course.flag) }
    var confirmDelete by remember { mutableStateOf(false) }
    val words = s.overview(id)?.total ?: 0
    Scaffold(topBar = {
        BackTopBar("Курс", onBack = { nav.popBackStack() }) {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Удалить курс") }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Название курса") }, singleLine = true)
            OutlinedTextField(flag, { flag = it.take(8) }, Modifier.fillMaxWidth(), label = { Text("Значок (эмодзи)") }, singleLine = true)
            Text("Изучаемый язык: ${LanguageCatalog.infoOrCustom(course.languageCode).nameRu} (${course.languageCode}) · перевод: ${course.translationName}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Файл словаря для обмена с Claude: ${LanguageSpec.of(course).fileName}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigActionButton("Сохранить", LocalGradients.current.ocean, enabled = name.isNotBlank()) {
                scope.launch {
                    container.repository.updateCourse(course.copy(name = name.trim(), flag = flag.ifBlank { course.flag }))
                    toast("Сохранено"); nav.popBackStack()
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Удалить курс «${course.name}»?",
            "Будут удалены $words ${SessionText.plural(words, "карточка", "карточки", "карточек")} этого курса и их прогресс. " +
                "Перед удалением сохранится автоматическая резервная копия. История ответов останется в общей статистике и сериях.",
            "Удалить",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    container.repository.deleteCourse(id)
                    container.settings.update { st -> if (st.activeCourseId == id) st.copy(activeCourseId = "") else st }
                    nav.popBackStack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Выбор курса (например, куда добавить слово). */
@Composable
fun LanguagePickerDialog(title: String, courses: List<app.lingvo.core.Course>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                courses.forEach { c ->
                    Text(
                        "${c.flag}  ${c.name}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(c.id) }.padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
