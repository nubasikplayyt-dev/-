package app.lingvo.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.ui.Routes
import app.lingvo.ui.components.BackTopBar
import app.lingvo.ui.components.BarChart
import app.lingvo.ui.components.BigActionButton
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.MenuRow
import app.lingvo.ui.components.SoftCard
import app.lingvo.ui.components.StatTile
import app.lingvo.ui.rememberAppState
import app.lingvo.ui.rememberToast
import app.lingvo.ui.theme.LocalGradients
import app.lingvo.ui.theme.Palette
import kotlinx.coroutines.launch

/** Раздел курса: показатели и все действия с ним. */
@Composable
fun LanguageScreen(container: AppContainer, nav: NavHostController, lang: String) {
    val state by rememberAppState(container)
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    var busy by remember { mutableStateOf(false) }
    val s = state ?: run { Centered { CircularProgressIndicator() }; return }
    val course = s.courses.firstOrNull { it.id == lang } ?: run { Centered { Text("Курс не найден") }; return }
    val spec = course.spec
    val o = s.overview(course.id) ?: return
    val g = LocalGradients.current
    Scaffold(topBar = {
        BackTopBar("${course.flag} ${course.name}", onBack = { nav.popBackStack() }) {
            IconButton(onClick = { nav.navigate(Routes.courseEdit(course.id)) }) { Icon(Icons.Filled.Edit, "Настройки курса") }
        }
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${o.total}", "в словаре", Palette.Ocean, Modifier.weight(1f))
                StatTile("${o.due}", "к повторению", Palette.Coral, Modifier.weight(1f))
                StatTile("${o.learned}", "выучено", Palette.Mint, Modifier.weight(1f))
            }
            SoftCard {
                Text("Неделя", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                BarChart(o.week, Palette.course(course.id))
            }
            BigActionButton(
                if (o.due > 0) "Начать повторение" else "Сейчас нечего повторять", g.sunrise,
                subtitle = if (o.due > 0) "${o.due} карточек ждут" else null, icon = Icons.Filled.PlayArrow, enabled = o.due > 0,
            ) { nav.navigate(Routes.review(course.id)) }
            BigActionButton(
                "Учить новые слова", g.violet,
                subtitle = when {
                    o.newLeftToday > 0 -> "${o.newLeftToday} из ${o.newLimit} на сегодня"
                    o.newAvailable == 0 -> "Новых слов нет — добавьте или импортируйте"
                    else -> "Норма на сегодня выполнена"
                },
                icon = Icons.Filled.School, enabled = o.newLeftToday > 0,
            ) { nav.navigate(Routes.learn(course.id)) }
            // Стартовый словарь — постепенно: следующий тематический блок (5–10 слов) по кнопке.
            var pending by remember(spec.code) { mutableStateOf<List<app.lingvo.core.StarterBlock>?>(null) }
            LaunchedEffect(spec.code, o.total) { pending = container.repository.pendingStarterBlocks(spec) }
            val next = pending?.firstOrNull()
            if (next != null) {
                val first = o.total == 0
                BigActionButton(
                    if (first) "Добавить стартовый словарь" else "Следующие слова: ${next.title}", g.mint,
                    subtitle = if (first) "Первые слова для новичков — приветствия, «я/ты», числа"
                    else "${next.size} простых слов · осталось тем: ${pending!!.size}",
                    icon = Icons.Filled.AutoAwesome, enabled = !busy,
                ) {
                    busy = true
                    scope.launch {
                        runCatching { container.repository.importStarter(spec, if (first) app.lingvo.core.Starter.FIRST_BLOCKS else 1) }
                            .onSuccess { plan -> toast("Добавлено слов: ${plan?.newEntries?.size ?: 0}") }
                            .onFailure { toast("Не удалось: ${it.message}") }
                        pending = container.repository.pendingStarterBlocks(spec)
                        busy = false
                    }
                }
            } else if (pending != null && !container.repository.hasStarter(spec) && o.total == 0) {
                Text("Для этого языка стартового словаря пока нет. Добавьте слова вручную, списком или импортом.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SoftCard(padding = 8.dp) {
                Item(Icons.AutoMirrored.Filled.MenuBook, Palette.Ocean, "Мой словарь", "${o.total} карточек") { nav.navigate(Routes.dictionary(course.id)) }
                Item(Icons.Filled.Add, Palette.Mint, "Добавить слово", "С переводом, примером и заметками") { nav.navigate(Routes.add(course.id)) }
                Item(Icons.AutoMirrored.Filled.PlaylistAdd, Palette.Violet, "Добавить списком", "Много слов за раз") { nav.navigate(Routes.bulk(course.id)) }
                Item(Icons.Filled.ImportExport, Palette.Sun, "Импорт и экспорт", spec.fileName) { nav.navigate(Routes.import(course.id)) }
            }
        }
    }
}

@Composable
private fun Item(icon: ImageVector, color: androidx.compose.ui.graphics.Color, title: String, subtitle: String, onClick: () -> Unit) =
    MenuRow(icon, color, title, subtitle, onClick)

@Composable
fun ButtonContent(icon: ImageVector, text: String) {
    Icon(icon, null, Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text)
}
