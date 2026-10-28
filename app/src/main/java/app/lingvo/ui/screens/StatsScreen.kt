package app.lingvo.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.Achievements
import app.lingvo.core.Creatures
import app.lingvo.core.SessionText
import app.lingvo.core.UnlockKind
import app.lingvo.core.WorldMap
import app.lingvo.ui.LanguageOverview
import app.lingvo.ui.OverviewCalculator
import app.lingvo.ui.Routes
import app.lingvo.ui.components.ActivityCalendar
import app.lingvo.ui.components.BarChart
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.EmptyState
import app.lingvo.ui.components.GradientBar
import app.lingvo.ui.components.ScreenHeader
import app.lingvo.ui.components.SoftCard
import app.lingvo.ui.components.StatTile
import app.lingvo.ui.rememberAppState
import app.lingvo.ui.theme.LocalGradients
import app.lingvo.ui.theme.Palette
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@Composable
fun StatsScreen(container: AppContainer, nav: NavHostController) {
    val state by rememberAppState(container)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val s = state ?: run { Centered { CircularProgressIndicator() }; return }
    val zone = ZoneId.systemDefault()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Статистика", "Только реальные данные ваших занятий")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected == null, { selected = null }, { Text("Все курсы") })
            s.courses.forEach { c -> FilterChip(selected == c.id, { selected = c.id }, { Text("${c.flag} ${c.name}") }) }
        }

        val shown = s.overviews.filter { selected == null || it.spec.code == selected }
        val logs = s.logs.filter { selected == null || it.language == selected }
        if (logs.isEmpty() && shown.sumOf { it.total } == 0) {
            EmptyState(Icons.Filled.BarChart, "Пока нет данных", "Добавьте слова и проведите первое занятие — здесь появится ваш прогресс.")
            return@Column
        }

        val total = shown.sumOf { it.total }
        val today = shown.sumOf { it.reviewsToday }
        val answers = shown.sumOf { it.totalReviews }
        val correct = shown.sumOf { it.totalCorrect }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("$today", "ответов сегодня", Palette.Ocean, Modifier.weight(1f))
            StatTile("${shown.sumOf { it.learned }}", "выучено слов", Palette.Mint, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("$total", "в словарях", Palette.Violet, Modifier.weight(1f))
            StatTile(if (answers == 0) "—" else "${correct * 100 / answers}%", "«Помню» за всё время", Palette.Coral, Modifier.weight(1f))
        }

        SoftCard(onClick = { nav.navigate(Routes.STREAK) }) {
            Text("Серия", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                StatTile("${s.streak.current} 🔥", "текущая", Palette.Sun, Modifier.weight(1f))
                StatTile("${s.streak.best}", "рекорд", Palette.Coral, Modifier.weight(1f))
                StatTile("${s.streak.totalGoalDays}", "дней с целью", Palette.Mint, Modifier.weight(1f))
            }
        }

        val week = OverviewCalculator.activity(logs, zone, LocalDate.now(zone), 7)
        val month = OverviewCalculator.activity(logs, zone, LocalDate.now(zone), 30)
        SoftCard {
            Text("Неделя: ${week.sumOf { it.reviews }} ответов", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            BarChart(week, Palette.Ocean)
            Spacer(Modifier.height(14.dp))
            Text("Динамика «Помню» за 30 дней", style = MaterialTheme.typography.titleMedium)
            Text(
                month.filter { it.reviews > 0 }.let { d -> if (d.isEmpty()) "Нет занятий" else "Средняя доля: ${d.sumOf { it.correct } * 100 / d.sumOf { it.reviews }}%" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            BarChart(month.map { it.copy(reviews = it.correct) }, Palette.Mint, showLabels = false)
        }

        shown.forEach { CourseStats(it) }

        SoftCard {
            Text("Учебная активность (13 недель)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ActivityCalendar(OverviewCalculator.activity(logs, zone, LocalDate.now(zone), 91),
                selected?.let { Palette.course(it) } ?: Palette.Violet)
        }

        if (s.settings.motivation) {
            SoftCard {
                Text("Мир и коллекция", style = MaterialTheme.typography.titleMedium)
                Progress("Области карты", s.owned.count { it.kind == UnlockKind.REGION }, WorldMap.regions.size)
                Progress("Существа", s.owned.count { it.kind == UnlockKind.CREATURE }, Creatures.all.size)
                Progress("Достижения", s.owned.count { it.kind == UnlockKind.ACHIEVEMENT }, Achievements.all.size)
            }
        }

        val recent = s.sessions.filter { it.done > 0 && (selected == null || it.language == selected) }.sortedByDescending { it.startedAt }.take(10)
        if (recent.isNotEmpty()) {
            SoftCard {
                Text("История занятий", style = MaterialTheme.typography.titleMedium)
                val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                recent.forEach { ses ->
                    val flag = s.courses.firstOrNull { it.id == ses.language }?.flag.orEmpty()
                    val acc = ses.correct * 100 / ses.done
                    Text(
                        "${df.format(Date(ses.startedAt))} $flag ${ses.done} ${SessionText.plural(ses.done, "ответ", "ответа", "ответов")}, помню $acc%" +
                            if (ses.status == "ACTIVE") " · не завершено" else "",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Progress(label: String, value: Int, total: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$value / $total", style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(4.dp))
    GradientBar(value.toFloat() / total, height = 8.dp, brush = LocalGradients.current.violet)
}

@Composable
private fun CourseStats(o: LanguageOverview) {
    val color = Palette.course(o.spec.code)
    SoftCard {
        Text("${o.spec.flag} ${o.spec.nameRu}", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            StatTile("${o.total}", "карточек", color, Modifier.weight(1f))
            StatTile("${o.newAvailable}", "новых", color, Modifier.weight(1f))
            StatTile("${o.due}", "к повторению", color, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            StatTile("${o.learned}", "хорошо помню", Palette.Mint, Modifier.weight(1f))
            StatTile("${o.mastered}", "усвоено", Palette.Leaf, Modifier.weight(1f))
            StatTile("${(o.hardShare * 100).toInt()}%", "сложных", Palette.Coral, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text("30 дней: ${o.month.sumOf { it.reviews }} ответов", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        BarChart(o.month, color, showLabels = false)
        Text("«Выучено» считается по алгоритму FSRS и истории ответов, а не по числу просмотров.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 6.dp))
    }
}
