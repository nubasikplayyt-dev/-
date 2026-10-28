package app.lingvo.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.BuildConfig
import app.lingvo.core.Languages
import app.lingvo.core.ReminderPlanner
import app.lingvo.data.AppSettings
import app.lingvo.data.CapsuleContent
import app.lingvo.data.Direction
import app.lingvo.data.SettingsStore
import app.lingvo.island.IslandController
import app.lingvo.notify.Notifications
import app.lingvo.notify.ReminderReceiver
import app.lingvo.ui.Routes
import app.lingvo.ui.components.SectionTitle
import app.lingvo.ui.rememberSettings
import app.lingvo.ui.rememberToast
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, nav: NavHostController) {
    val settings by rememberSettings(container)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toast = rememberToast()
    fun update(f: (AppSettings) -> AppSettings) { scope.launch { container.settings.update(f) } }

    // Перечитываем системные разрешения при возвращении из настроек Android.
    var resumeTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val canNotify = remember(resumeTick) { Notifications.canPost(context) }
    val canOverlay = remember(resumeTick) { IslandController.canDrawOverlays(context) }
    val canPromote = remember(resumeTick) { Notifications.canPromote(context) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    var timeDialog by remember { mutableStateOf<TimeTarget?>(null) }
    var overlayExplain by remember { mutableStateOf(false) }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { TopAppBar(title = { Text("Настройки") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // ---------- Обучение ----------
            SectionTitle("Обучение")
            Languages.all.forEach { spec ->
                val l = settings.lang(spec.code)
                Text("${spec.flag} ${spec.nameRu}: новых слов в день", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 20).forEach { n ->
                        FilterChip(selected = l.newPerDay == n, onClick = {
                            update { s -> s.copy(languages = s.languages + (spec.code to s.lang(spec.code).copy(newPerDay = n))) }
                        }, label = { Text("$n") })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Direction.entries.forEach { d ->
                        FilterChip(selected = l.direction == d, onClick = {
                            update { s -> s.copy(languages = s.languages + (spec.code to s.lang(spec.code).copy(direction = d))) }
                        }, label = { Text(d.labelRu.replace("Изучаемый", spec.nameRu).replace("изучаемый", spec.nameRu.lowercase())) })
                    }
                }
            }
            SwitchRow("Произносить слово при показе ответа", settings.autoSpeak) { v -> update { it.copy(autoSpeak = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Languages.all.forEach { spec ->
                    OutlinedButton(onClick = {
                        container.speaker.speak(if (spec.code == "de") "Guten Tag" else "Buenos días", spec)?.let(toast)
                    }) { Text("Проверить голос ${spec.flag}") }
                }
            }

            // ---------- Напоминания ----------
            SectionTitle("Напоминания")
            if (!canNotify) {
                Text("Уведомления выключены — напоминания и прогресс сессии показываться не будут.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openAppNotificationSettings(context)
                }) { Text("Разрешить уведомления") }
            }
            val r = settings.reminder
            SwitchRow("Ежедневное напоминание", r.enabled) { v -> update { it.copy(reminder = it.reminder.copy(enabled = v)) } }
            if (r.enabled) {
                Text("Время (до трёх напоминаний в день)", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    r.times.sorted().forEach { t ->
                        InputChip(
                            selected = false,
                            onClick = { timeDialog = TimeTarget.Reminder(r.times.indexOf(t), t) },
                            label = { Text(SettingsStore.formatTime(t)) },
                            trailingIcon = {
                                if (r.times.size > 1) {
                                    Text("✕", Modifier.padding(start = 4.dp).clickable {
                                        update { s -> s.copy(reminder = s.reminder.copy(times = s.reminder.times - t)) }
                                    })
                                }
                            },
                        )
                    }
                    if (r.times.size < 3) AssistChip(onClick = { timeDialog = TimeTarget.Reminder(-1, LocalTime.of(9, 0)) }, label = { Text("+ время") })
                }
                Text("Дни недели", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(selected = d in r.days, onClick = {
                            update { s ->
                                val days = if (d in s.reminder.days) s.reminder.days - d else s.reminder.days + d
                                s.copy(reminder = s.reminder.copy(days = days))
                            }
                        }, label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ru"))) })
                    }
                }
                SwitchRow("Тихие часы", r.quietHoursEnabled) { v -> update { it.copy(reminder = it.reminder.copy(quietHoursEnabled = v)) } }
                if (r.quietHoursEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { timeDialog = TimeTarget.QuietStart(r.quietStart) }, label = { Text("с ${SettingsStore.formatTime(r.quietStart)}") })
                        AssistChip(onClick = { timeDialog = TimeTarget.QuietEnd(r.quietEnd) }, label = { Text("до ${SettingsStore.formatTime(r.quietEnd)}") })
                    }
                }
                Languages.all.forEach { spec ->
                    SwitchRow("Напоминать про ${spec.flag} ${spec.nameRu.lowercase()}", settings.lang(spec.code).remindersEnabled) { v ->
                        update { s -> s.copy(languages = s.languages + (spec.code to s.lang(spec.code).copy(remindersEnabled = v))) }
                    }
                }
                SwitchRow("Объединять языки в одно уведомление", settings.combineReminders) { v -> update { it.copy(combineReminders = v) } }
                val next = ReminderPlanner.nextTrigger(r, ZonedDateTime.now())
                Text(
                    next?.let { "Следующее: ${it.format(DateTimeFormatter.ofPattern("EEEE, HH:mm", Locale.forLanguageTag("ru")))} (Android может сдвинуть на несколько минут ради экономии батареи). Уведомление придёт, только если есть слова к повторению." }
                        ?: "Нет подходящего времени — проверьте дни недели и тихие часы.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    ReminderReceiver.enqueueCheck(context, snoozed = true)
                    toast("Проверка запущена: уведомление появится, если есть карточки к повторению")
                }) { Text("Проверить напоминание сейчас") }
            }

            // ---------- Компактный интерфейс ----------
            SectionTitle("Dynamic Island-подобный интерфейс")
            Text(
                "Android не даёт единого API «Dynamic Island». Приложение использует то, что поддерживает ваша система: " +
                    "обновляемое уведомление с прогрессом (на Android 16+ — Live Update с чипом в строке состояния) и, по желанию, " +
                    "плавающую капсулу поверх других приложений.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow("Прогресс сессии в уведомлении", settings.sessionNotification) { v -> update { it.copy(sessionNotification = v) } }
            Text(
                when (canPromote) {
                    null -> "Live Updates доступны с Android 16. На вашей версии (${Build.VERSION.RELEASE}) используется обычное уведомление с прогрессом."
                    true -> "Live Updates разрешены: прогресс может отображаться как чип в строке состояния."
                    false -> "Live Updates для приложения выключены в настройках уведомлений Android — будет обычное уведомление."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (canPromote == false) TextButton(onClick = { openAppNotificationSettings(context) }) { Text("Открыть настройки уведомлений") }

            SwitchRow("Плавающая капсула поверх приложений", settings.capsuleEnabled && canOverlay) { v ->
                if (v && !canOverlay) overlayExplain = true else update { it.copy(capsuleEnabled = v) }
            }
            if (settings.capsuleEnabled && canOverlay) {
                Text("Показывать в капсуле", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapsuleContent.entries.forEach { c ->
                        FilterChip(selected = settings.capsuleContent == c, onClick = { update { it.copy(capsuleContent = c) } }, label = { Text(c.labelRu) })
                    }
                }
                Text(
                    "Капсула появляется только во время незавершённой сессии, когда приложение свёрнуто. Нажатие — продолжить, " +
                        "перетаскивание — переместить, долгое нажатие — скрыть до следующей сессии.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SwitchRow("Анимации", settings.animations) { v -> update { it.copy(animations = v) } }

            // ---------- Приватность ----------
            SectionTitle("Приватность")
            SwitchRow("Показывать текущее слово в уведомлении (без перевода)", settings.showWordInNotification) { v ->
                update { it.copy(showWordInNotification = v) }
            }
            SwitchRow("Скрывать содержимое на экране блокировки", settings.hideOnLockScreen) { v -> update { it.copy(hideOnLockScreen = v) } }

            // ---------- Прочее ----------
            SectionTitle("Мотивация")
            SwitchRow("Достижения и серии", settings.motivation) { v -> update { it.copy(motivation = v) } }

            SectionTitle("Данные")
            OutlinedButton(onClick = { nav.navigate(Routes.import()) }, modifier = Modifier.fillMaxWidth()) {
                Text("Импорт, экспорт и резервные копии")
            }

            SectionTitle("Об устройстве")
            Text(
                "Lingvo Island ${BuildConfig.VERSION_NAME}\n${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                    "Уведомления: ${if (canNotify) "разрешены" else "выключены"} · поверх приложений: ${if (canOverlay) "разрешено" else "нет"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    timeDialog?.let { target ->
        TimeDialog(target.initial, onDismiss = { timeDialog = null }) { t ->
            timeDialog = null
            update { s ->
                when (target) {
                    is TimeTarget.Reminder -> {
                        val times = s.reminder.times.toMutableList()
                        if (target.index in times.indices) times[target.index] = t else times += t
                        s.copy(reminder = s.reminder.copy(times = times.distinct().take(3)))
                    }
                    is TimeTarget.QuietStart -> s.copy(reminder = s.reminder.copy(quietStart = t))
                    is TimeTarget.QuietEnd -> s.copy(reminder = s.reminder.copy(quietEnd = t))
                }
            }
        }
    }

    if (overlayExplain) {
        AlertDialog(
            onDismissRequest = { overlayExplain = false },
            title = { Text("Разрешение «Поверх других приложений»") },
            text = {
                Text(
                    "Чтобы показывать капсулу с прогрессом, когда приложение свёрнуто, Android требует отдельное разрешение. " +
                        "Капсула видна только во время незавершённой сессии и не работает в фоне постоянно. " +
                        "Без разрешения прогресс по-прежнему виден в уведомлении."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    overlayExplain = false
                    scope.launch { container.settings.update { it.copy(capsuleEnabled = true) } }
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Открыть настройки") }
            },
            dismissButton = { TextButton(onClick = { overlayExplain = false }) { Text("Не сейчас") } },
        )
    }
}

private sealed class TimeTarget(val initial: LocalTime) {
    class Reminder(val index: Int, t: LocalTime) : TimeTarget(t)
    class QuietStart(t: LocalTime) : TimeTarget(t)
    class QuietEnd(t: LocalTime) : TimeTarget(t)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
    HorizontalDivider()
}

private fun openAppNotificationSettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
