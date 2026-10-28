package app.lingvo.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.Languages
import app.lingvo.core.SessionText
import app.lingvo.notify.Notifications
import app.lingvo.ui.LanguageOverview
import app.lingvo.ui.OverviewCalculator
import app.lingvo.ui.Routes
import app.lingvo.ui.components.BarChart
import app.lingvo.ui.components.Centered
import app.lingvo.ui.rememberOverviews
import app.lingvo.ui.rememberSettings
import app.lingvo.ui.rememberToast
import app.lingvo.ui.theme.LangColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(container: AppContainer, nav: NavHostController) {
    val overviews by rememberOverviews(container)
    val settings by rememberSettings(container)
    val sessions by remember(container) { container.repository.observeActiveSessions() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var addLanguagePicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast("Без разрешения напоминания не будут приходить. Его можно включить в настройках.")
        scope.launch { container.settings.update { it.copy(onboardingDone = true) } }
    }

    val list = overviews ?: run { Centered { CircularProgressIndicator() }; return }
    val totalWords = list.sumOf { it.total }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Lingvo Island", style = MaterialTheme.typography.headlineMedium)
        Text(
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (totalWords == 0) {
            OnboardingCard(
                busy = busy,
                onStarter = {
                    busy = true
                    scope.launch {
                        try {
                            val added = Languages.all.sumOf { container.repository.importStarter(it).newEntries.size }
                            toast("Добавлено $added карточек стартового набора")
                        } catch (e: Exception) {
                            toast("Не удалось загрузить стартовый набор: ${e.message}")
                        } finally { busy = false }
                    }
                },
                onImport = { nav.navigate(Routes.import()) },
                onAdd = { addLanguagePicker = true },
            )
        }

        if (!settings.onboardingDone && Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(ctx)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Напоминания о повторении", style = MaterialTheme.typography.titleSmall)
                        Text("Раз в день, в 19:00 и только если есть слова к повторению.", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Включить") }
                }
            }
        }

        sessions.filter { it.done < it.total }.forEach { s ->
            val spec = Languages.byCode(s.language) ?: return@forEach
            Card(
                onClick = { nav.navigate(Routes.review(spec.code)) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Продолжить тренировку", style = MaterialTheme.typography.labelLarge)
                    Text(SessionText.progress(spec, s.done, s.total), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { if (s.total == 0) 0f else s.done.toFloat() / s.total }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        OverviewCalculator.needsAttention(list)?.let { focus ->
            if (list.count { it.due > 0 } >= 1) {
                val overdueDays = focus.overdueMs / 86_400_000L
                Text(
                    buildString {
                        append("Сейчас важнее: ${focus.spec.flag} ${focus.spec.nameRu} — ${focus.due} ${SessionText.plural(focus.due, "карточка ждёт", "карточки ждут", "карточек ждут")}")
                        if (overdueDays >= 1) append(" (просрочено на $overdueDays ${SessionText.plural(overdueDays.toInt(), "день", "дня", "дней")})")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        list.forEach { o ->
            LanguageCard(
                o,
                onOpen = { nav.navigate(Routes.language(o.spec.code)) },
                onReview = { nav.navigate(Routes.review(o.spec.code)) },
                onLearn = { nav.navigate(Routes.learn(o.spec.code)) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { nav.navigate(Routes.dictionary()) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Мой словарь")
            }
            OutlinedButton(onClick = { addLanguagePicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Добавить")
            }
        }
        OutlinedButton(onClick = { nav.navigate(Routes.import()) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Импортировать словарь")
        }
        Spacer(Modifier.height(8.dp))
    }

    if (addLanguagePicker) {
        LanguagePickerDialog(
            title = "Добавить слово",
            onPick = { addLanguagePicker = false; nav.navigate(Routes.add(it)) },
            onDismiss = { addLanguagePicker = false },
        )
    }
}

@Composable
fun LanguagePickerDialog(title: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Languages.all.forEach { spec ->
                    Text(
                        "${spec.flag}  ${spec.nameRu}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(spec.code) }.padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun OnboardingCard(busy: Boolean, onStarter: () -> Unit, onImport: () -> Unit, onAdd: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Добро пожаловать!", style = MaterialTheme.typography.titleLarge)
            Text(
                "Словари пока пусты. Загрузите стартовый набор, импортируйте словарь из Claude (JSON) или добавьте первые слова.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onStarter, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Загрузить стартовый набор 🇪🇸 🇩🇪")
            }
            FilledTonalButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Импортировать словарь") }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Добавить первое слово") }
        }
    }
}

@Composable
private fun LanguageCard(o: LanguageOverview, onOpen: () -> Unit, onReview: () -> Unit, onLearn: () -> Unit) {
    val accent = LangColors.of(o.spec.code)
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.spec.flag, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.spec.nameRu, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${o.total} ${SessionText.plural(o.total, "карточка", "карточки", "карточек")} · выучено ${o.learned}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (o.streak > 0) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = "Серия дней", tint = accent, modifier = Modifier.size(20.dp))
                    Text("${o.streak}", style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric("к повторению", o.due, highlight = o.due > 0)
                Metric("новых", o.newAvailable)
                Metric("сегодня", o.reviewsToday)
            }
            BarChart(o.week, accent, height = 36.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReview, enabled = o.due > 0, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text(if (o.due > 0) "Повторить" else "Всё повторено")
                }
                FilledTonalButton(onClick = onLearn, enabled = o.newLeftToday > 0, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.School, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                    Text(if (o.newLeftToday > 0) "Новые (${o.newLeftToday})" else "Новых нет")
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, highlight: Boolean = false) {
    Column {
        Text(
            "$value",
            style = MaterialTheme.typography.titleLarge,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
