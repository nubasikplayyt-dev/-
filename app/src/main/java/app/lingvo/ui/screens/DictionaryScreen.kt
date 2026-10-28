package app.lingvo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.CEFR_LEVELS
import app.lingvo.core.LearningStatus
import app.lingvo.core.Normalizer
import app.lingvo.core.PartsOfSpeech
import app.lingvo.core.TextExport
import app.lingvo.data.Card
import app.lingvo.ui.Routes
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.EmptyState
import app.lingvo.ui.components.StatusBadge
import app.lingvo.ui.components.formatInterval

enum class SortOrder(val labelRu: String) { ADDED("Сначала новые"), DUE("По дате повторения"), ALPHA("По алфавиту") }
enum class DueFilter(val labelRu: String) { ANY("Любая дата"), TODAY("К повторению сейчас"), WEEK("В течение недели") }

/** Фильтрация словаря — вынесена из UI, чтобы её можно было тестировать. */
data class DictionaryFilter(
    val language: String? = null,
    val query: String = "",
    val status: LearningStatus? = null,
    val partOfSpeech: String? = null,
    val tag: String? = null,
    val level: String? = null,
    val due: DueFilter = DueFilter.ANY,
    val sort: SortOrder = SortOrder.ADDED,
) {
    fun apply(cards: List<Card>, now: Long): List<Card> {
        val q = Normalizer.searchKey(query)
        return cards.asSequence()
            .filter { language == null || it.entry.language == language }
            .filter {
                q.isEmpty() || Normalizer.searchKey(it.entry.displayTerm).contains(q) ||
                    Normalizer.searchKey(it.entry.translation).contains(q)
            }
            .filter { status == null || it.status == status }
            .filter { partOfSpeech == null || it.entry.partOfSpeech == partOfSpeech }
            .filter { tag == null || it.entry.tags.any { t -> t.equals(tag, ignoreCase = true) } }
            .filter { level == null || it.entry.level.equals(level, ignoreCase = true) }
            .filter {
                val introduced = it.state.introducedAt != null && !it.state.suspended
                when (due) {
                    DueFilter.ANY -> true
                    DueFilter.TODAY -> introduced && it.state.due <= now
                    DueFilter.WEEK -> introduced && it.state.due <= now + 7 * 86_400_000L
                }
            }
            .let { seq ->
                when (sort) {
                    SortOrder.ADDED -> seq.sortedByDescending { it.entry.createdAt }
                    SortOrder.DUE -> seq.sortedBy { if (it.state.introducedAt == null) Long.MAX_VALUE else it.state.due }
                    SortOrder.ALPHA -> seq.sortedBy { Normalizer.key(it.entry.term) }
                }
            }
            .toList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(container: AppContainer, nav: NavHostController, initialLang: String?) {
    val cardsState by remember(container) { container.repository.observeAllCards() }.collectAsStateWithLifecycle(initialValue = null)
    var filter by remember { mutableStateOf(DictionaryFilter(language = initialLang)) }
    var query by rememberSaveable { mutableStateOf("") }
    var pickLanguage by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val courses = app.lingvo.ui.rememberAppState(container).value?.courses.orEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Мой словарь") },
                actions = {
                    IconButton(onClick = {
                        val all = cardsState.orEmpty()
                        val shown = filter.copy(query = query).apply(all, System.currentTimeMillis())
                        if (shown.isEmpty()) return@IconButton
                        // Тот же список, что на экране, — по языкам, читаемым текстом.
                        val text = courses.map { it.spec }.mapNotNull { spec ->
                            val part = shown.filter { it.entry.language == spec.code }.map { it.entry }
                            if (part.isEmpty()) null else TextExport.plain(spec, part)
                        }.joinToString("\n")
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, text)
                        context.startActivity(android.content.Intent.createChooser(send, "Отправить слова"))
                    }) { Icon(Icons.Filled.Share, contentDescription = "Поделиться списком слов") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val l = filter.language
                if (l != null) nav.navigate(Routes.add(l)) else pickLanguage = true
            }) { Icon(Icons.Filled.Add, contentDescription = "Добавить слово") }
        },
    ) { padding ->
        val all = cardsState ?: run { Centered { CircularProgressIndicator() }; return@Scaffold }
        val f = filter.copy(query = query)
        val list = remember(all, f) { f.apply(all, System.currentTimeMillis()) }
        val scopeCards = all.filter { f.language == null || it.entry.language == f.language }
        val tags = remember(scopeCards) { scopeCards.flatMap { it.entry.tags }.distinctBy { it.lowercase() }.sorted() }

        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter.language == null, { filter = filter.copy(language = null, tag = null) }, { Text("Все курсы") })
                    courses.forEach { c ->
                        FilterChip(filter.language == c.id, { filter = filter.copy(language = c.id, tag = null) }, { Text("${c.flag} ${c.name}") })
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Очистить") } },
                    placeholder = { Text("Поиск по слову или переводу") },
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterDropdown("Статус", filter.status?.labelRu, LearningStatus.entries.map { it.labelRu }) { i ->
                        filter = filter.copy(status = i?.let { LearningStatus.entries[it] })
                    }
                    FilterDropdown("Часть речи", filter.partOfSpeech?.let(PartsOfSpeech::labelRu), PartsOfSpeech.labelsRu.values.toList()) { i ->
                        filter = filter.copy(partOfSpeech = i?.let { PartsOfSpeech.labelsRu.keys.toList()[it] })
                    }
                    FilterDropdown("Категория", filter.tag, tags) { i -> filter = filter.copy(tag = i?.let { tags[it] }) }
                    FilterDropdown("Уровень", filter.level, CEFR_LEVELS) { i -> filter = filter.copy(level = i?.let { CEFR_LEVELS[it] }) }
                    FilterDropdown("Повторение", filter.due.takeIf { it != DueFilter.ANY }?.labelRu, DueFilter.entries.drop(1).map { it.labelRu }) { i ->
                        filter = filter.copy(due = i?.let { DueFilter.entries.drop(1)[it] } ?: DueFilter.ANY)
                    }
                    FilterDropdown("Сортировка", filter.sort.labelRu, SortOrder.entries.map { it.labelRu }, allowClear = false) { i ->
                        filter = filter.copy(sort = i?.let { SortOrder.entries[it] } ?: SortOrder.ADDED)
                    }
                }
                Text(
                    "Найдено: ${list.size}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (list.isEmpty()) {
                EmptyState(
                    Icons.AutoMirrored.Filled.MenuBook,
                    if (all.isEmpty()) "Словарь пуст" else "Ничего не найдено",
                    if (all.isEmpty()) "Добавьте слова вручную или импортируйте словарь из Claude." else "Измените запрос или фильтры.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(list, key = { it.id }) { card ->
                        DictionaryRow(card, courses.firstOrNull { it.id == card.entry.language }?.flag.orEmpty()) { nav.navigate(Routes.entry(card.id)) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (pickLanguage) {
        LanguagePickerDialog("Добавить слово", courses, onPick = { pickLanguage = false; nav.navigate(Routes.add(it)) }, onDismiss = { pickLanguage = false })
    }
}

@Composable
private fun DictionaryRow(card: Card, flag: String, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
        headlineContent = { Text("$flag ${card.entry.displayTerm}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(card.entry.translation, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                StatusBadge(card.status)
                if (card.state.introducedAt != null && !card.state.suspended) {
                    Text(
                        if (card.state.due <= now) "пора" else "через ${formatInterval(card.state.due - now)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
fun FilterDropdown(label: String, selected: String?, options: List<String>, allowClear: Boolean = true, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null && allowClear,
            onClick = { open = true },
            label = { Text(selected ?: label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowClear) DropdownMenuItem(text = { Text("Все") }, onClick = { open = false; onSelect(null) })
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(i) }) }
            if (options.isEmpty()) DropdownMenuItem(text = { Text("Нет вариантов") }, onClick = { open = false }, enabled = false)
        }
    }
}
