package app.lingvo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lingvo.AppContainer
import app.lingvo.core.Languages
import app.lingvo.core.SessionText
import app.lingvo.ui.LanguageOverview
import app.lingvo.ui.OverviewCalculator
import app.lingvo.ui.components.ActivityCalendar
import app.lingvo.ui.components.BarChart
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.LabeledValue
import app.lingvo.ui.components.SectionTitle
import app.lingvo.ui.rememberOverviews
import app.lingvo.ui.rememberSettings
import app.lingvo.ui.theme.LangColors
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(container: AppContainer) {
    val overviews by rememberOverviews(container)
    val settings by rememberSettings(container)
    val logs by remember(container) { container.repository.observeLogs() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by remember(container) { container.repository.observeRecentSessions() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { TopAppBar(title = { Text("Статистика") }) }) { padding ->
        val list = overviews ?: run { Centered { CircularProgressIndicator() }; return@Scaffold }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val options = listOf<String?>(null) + Languages.all.map { it.code }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, code ->
                    SegmentedButton(
                        selected = selected == code,
                        onClick = { selected = code },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(code?.let { c -> Languages.require(c).let { "${it.flag} ${it.nameRu}" } } ?: "Оба") }
                }
            }

            val shown = list.filter { selected == null || it.spec.code == selected }
            if (selected == null) {
                // Объединённая статистика, но с отдельными показателями каждого языка.
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Всего", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabeledValue("карточек", "${list.sumOf { it.total }}")
                            LabeledValue("повторений сегодня", "${list.sumOf { it.reviewsToday }}")
                            LabeledValue("выучено", "${list.sumOf { it.learned }}")
                        }
                        list.forEach { o ->
                            Text(
                                "${o.spec.flag} ${o.total} карт. · сегодня ${o.reviewsToday} · выучено ${o.learned} · серия ${o.streak}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            shown.forEach { LanguageStats(it) }

            val zone = ZoneId.systemDefault()
            val filteredLogs = logs.filter { selected == null || it.language == selected }
            SectionTitle("Календарь активности (13 недель)")
            ActivityCalendar(
                OverviewCalculator.activity(filteredLogs, zone, LocalDate.now(zone), 91),
                selected?.let { LangColors.of(it) } ?: MaterialTheme.colorScheme.primary,
            )

            val recent = sessions.filter { selected == null || it.language == selected }.take(10)
            if (recent.isNotEmpty()) {
                SectionTitle("История занятий")
                val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                recent.forEach { s ->
                    val spec = Languages.byCode(s.language)
                    val acc = if (s.done == 0) 0 else s.correct * 100 / s.done
                    Text(
                        "${df.format(Date(s.startedAt))} ${spec?.flag.orEmpty()} ${s.done} ${SessionText.plural(s.done, "ответ", "ответа", "ответов")}, " +
                            "верно $acc%" + if (s.status == "ACTIVE") " · не завершена" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (settings.motivation) {
                SectionTitle("Достижения")
                val achievements = remember(list, logs) { OverviewCalculator.achievements(list, logs, zone, LocalDate.now(zone)) }
                achievements.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (a.unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock, null,
                            tint = if (a.unlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(a.title, style = MaterialTheme.typography.bodyLarge)
                            Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Достижения можно отключить в настройках.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun LanguageStats(o: LanguageOverview) {
    val color = LangColors.of(o.spec.code)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${o.spec.flag} ${o.spec.nameRu}", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("карточек", "${o.total}")
                LabeledValue("новых", "${o.newAvailable}")
                LabeledValue("сегодня", "${o.reviewsToday}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("успешных ответов", "${o.totalCorrect} (${(o.successRate * 100).toInt()}%)")
                LabeledValue("сложных", "${(o.hardShare * 100).toInt()}%")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue("хорошо помню", "${o.learned}")
                LabeledValue("усвоено", "${o.mastered}")
                LabeledValue("серия / рекорд", "${o.streak} / ${o.longestStreak}")
            }
            Text("Неделя", style = MaterialTheme.typography.labelLarge)
            BarChart(o.week, color)
            Text("30 дней: ${o.month.sumOf { it.reviews }} повторений", style = MaterialTheme.typography.labelLarge)
            BarChart(o.month, color, showLabels = false)
            Text(
                "«Выучено» считается по алгоритму FSRS и истории ответов, а не по числу просмотров.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
