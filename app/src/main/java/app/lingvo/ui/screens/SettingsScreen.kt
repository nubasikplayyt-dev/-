package app.lingvo.ui.screens

import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.BuildConfig
import app.lingvo.core.AppIcons
import app.lingvo.core.Creatures
import app.lingvo.core.DailyStreak
import app.lingvo.core.ReminderPlanner
import app.lingvo.core.UnlockKind
import app.lingvo.data.AppSettings
import app.lingvo.data.Direction
import app.lingvo.data.SettingsStore
import app.lingvo.data.ThemeMode
import app.lingvo.island.IslandController
import app.lingvo.notify.Notifications
import app.lingvo.notify.ReminderReceiver
import app.lingvo.ui.Routes
import app.lingvo.ui.components.BackTopBar
import app.lingvo.ui.components.MenuRow
import app.lingvo.ui.components.ScreenHeader
import app.lingvo.ui.components.SoftCard
import app.lingvo.ui.rememberAppState
import app.lingvo.ui.rememberSettings
import app.lingvo.ui.rememberToast
import app.lingvo.ui.theme.Palette
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")

/** Главный экран настроек: пять понятных разделов. */
@Composable
fun SettingsScreen(container: AppContainer, nav: NavHostController) {
    val settings by rememberSettings(container)
    val state by rememberAppState(container)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Настройки")
        SoftCard(padding = 8.dp) {
            MenuRow(Icons.Filled.School, Palette.Ocean, "Обучение",
                "Цель дня: ${settings.dailyGoal} ответов · новые слова и направление") { nav.navigate(Routes.SETTINGS_LEARNING) }
            MenuRow(Icons.Filled.Language, Palette.Mint, "Языки и словари",
                "Курсов: ${state?.courses?.size ?: 0} · импорт и экспорт") { nav.navigate(Routes.COURSES) }
            MenuRow(Icons.Filled.Notifications, Palette.Violet, "Напоминания и уведомления",
                if (settings.reminder.enabled) "Время: ${settings.reminder.times.sorted().joinToString { SettingsStore.formatTime(it) }}" else "Выключены") {
                nav.navigate(Routes.SETTINGS_REMINDERS)
            }
            MenuRow(Icons.Filled.Palette, Palette.Coral, "Внешний вид",
                "Тема: ${settings.theme.labelRu.lowercase()} · иконка · спутник") { nav.navigate(Routes.SETTINGS_APPEARANCE) }
            MenuRow(Icons.Filled.Backup, Palette.Sun, "Данные и приложение", "Резервные копии, восстановление, о приложении") {
                nav.navigate(Routes.SETTINGS_DATA)
            }
        }
        Text("Lingvo World ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsPage(title: String, nav: NavHostController, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { BackTopBar(title, onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Group(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    SoftCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

// ------------------------------------------------------------ Обучение

@Composable
fun SettingsLearningScreen(container: AppContainer, nav: NavHostController) {
    val settings by rememberSettings(container)
    val state by rememberAppState(container)
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    fun update(f: (AppSettings) -> AppSettings) { scope.launch { container.settings.update(f) } }
    SettingsPage("Обучение", nav) {
        Group("Ежедневная цель", "Сколько ответов в день засчитывает день в серию.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DailyStreak.GOAL_OPTIONS.forEach { n ->
                    FilterChip(settings.dailyGoal == n, { scope.launch { container.repository.setDailyGoal(n) } }, { Text("$n") })
                }
            }
        }
        state?.courses?.forEach { c ->
            val l = settings.lang(c.id)
            Group("${c.flag} ${c.name}") {
                Text("Новых слов в день", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 20).forEach { n ->
                        FilterChip(l.newPerDay == n, { update { it.withLang(c.id) { x -> x.copy(newPerDay = n) } } }, { Text("$n") })
                    }
                }
                Text("Как отвечать", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.lingvo.core.ReviewMode.entries.forEach { m ->
                        FilterChip(l.reviewMode == m, { update { it.withLang(c.id) { x -> x.copy(reviewMode = m) } } }, { Text(m.labelRu) })
                    }
                }
                Text("«Написать слово»: виден перевод, вы пишете слово, приложение подсказывает расхождения, " +
                    "а правильность подтверждаете вы сами.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Направление проверки (для кнопок)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Direction.entries.forEach { d ->
                        FilterChip(l.direction == d, { update { it.withLang(c.id) { x -> x.copy(direction = d) } } }, { Text(d.labelRu) })
                    }
                }
            }
        }
        Group("Произношение") {
            SwitchRow("Произносить слово при показе ответа", settings.autoSpeak) { v -> update { it.copy(autoSpeak = v) } }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state?.courses?.forEach { c ->
                    OutlinedButton(onClick = { container.speaker.speak(c.name, c.spec)?.let(toast) }) { Text("Проверить ${c.flag}") }
                }
            }
        }
        WordImagesGroup(container, settings) { f -> update(f) }
    }
}

/** Картинки к словам: локальная нейросеть (Stable Diffusion на компьютере в домашней сети). */
@Composable
private fun WordImagesGroup(container: AppContainer, settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    var url by remember(settings.imageServerUrl) { mutableStateOf(settings.imageServerUrl) }
    var status by remember { mutableStateOf<String?>(null) }
    var busyImages by remember { mutableStateOf(false) }
    var cache by remember { mutableStateOf(container.wordImages.cacheSize()) }
    val device = remember { app.lingvo.images.DeviceCapability.check(container.wordImages.appContext) }
    val urlOk = url.isBlank() || app.lingvo.core.WordImagePrompt.isLocalAddress(url)
    Group("Картинки к словам",
        "Картинку к слову можно выбрать самому на странице слова (из галереи или поиском в интернете). " +
            "Здесь — автоматический подбор для новых слов: он идёт в фоне, карточка показывается сразу.") {
        SwitchRow("Подбирать картинки к новым словам", settings.imageGeneration) { v -> update { it.copy(imageGeneration = v) } }
        Text(
            "Рисование нейросетью прямо на телефоне не входит в приложение: модель весит больше гигабайта и " +
                "требует мощного устройства. " + device.reasonRu + ". Поэтому картинки ищутся в интернете " +
                "или рисуются на вашем компьютере.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.lingvo.data.ImageSource.entries.forEach { src ->
                FilterChip(settings.imageSource == src, { update { it.copy(imageSource = src) } }, { Text(src.labelRu) })
            }
        }
        if (settings.imageSource == app.lingvo.data.ImageSource.WEB) {
            Text("Ищет готовую картинку: главное фото статьи Википедии на языке курса, иначе — свободные картинки " +
                "Openverse. Для этого слово отправляется в Википедию/Openverse.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
        Text("Stable Diffusion на вашем компьютере в той же Wi‑Fi-сети (Automatic1111, Forge или SD.Next с флагами " +
            "--api --listen). Слова уходят только на адрес домашней сети.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text("В кэше: ${cache.first} картинок, ${cache.second / 1024} КБ", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (settings.imageGeneration) {
            TextButton(enabled = !busyImages, onClick = {
                busyImages = true
                scope.launch {
                    var total = 0
                    container.repository.courses().forEach { c ->
                        val spec = c.spec
                        total += container.wordImages.requestMissing(container.repository.entriesOf(spec), spec, settings)
                    }
                    toast(if (total > 0) "Ищем картинки: $total слов" else "У всех слов уже есть картинки")
                    cache = container.wordImages.cacheSize()
                    busyImages = false
                }
            }) { Text("Подобрать картинки ко всем словам") }
        }
        if (cache.first > 0) TextButton(onClick = {
            toast("Удалено картинок: ${container.wordImages.clearCache()}"); cache = container.wordImages.cacheSize()
        }) { Text("Очистить кэш картинок") }
    }
}

// ------------------------------------------------------------ Напоминания и уведомления

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRemindersScreen(container: AppContainer, nav: NavHostController) {
    val settings by rememberSettings(container)
    val state by rememberAppState(container)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toast = rememberToast()
    fun update(f: (AppSettings) -> AppSettings) { scope.launch { container.settings.update(f) } }

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
    val r = settings.reminder

    SettingsPage("Напоминания", nav) {
        if (!canNotify) {
            Group("Уведомления выключены", "Без разрешения не будет ни напоминаний, ни прогресса занятия.") {
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else openAppNotificationSettings(context)
                }) { Text("Разрешить уведомления") }
            }
        }
        Group("Расписание", "Напоминание придёт, только если цель дня ещё не выполнена.") {
            SwitchRow("Ежедневное напоминание", r.enabled) { v -> update { it.copy(reminder = it.reminder.copy(enabled = v)) } }
            if (r.enabled) {
                Text("Время занятий (до трёх в день)", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    r.times.sorted().forEach { t ->
                        InputChip(
                            selected = false,
                            onClick = { timeDialog = TimeTarget.Reminder(r.times.indexOf(t), t) },
                            label = { Text(SettingsStore.formatTime(t)) },
                            trailingIcon = {
                                if (r.times.size > 1) Text("✕", Modifier.padding(start = 4.dp).clickable {
                                    update { s -> s.copy(reminder = s.reminder.copy(times = s.reminder.times - t)) }
                                })
                            },
                        )
                    }
                    if (r.times.size < 3) AssistChip(onClick = { timeDialog = TimeTarget.Reminder(-1, LocalTime.of(9, 0)) }, label = { Text("+ время") })
                }
                Text("Дни недели", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(d in r.days, {
                            update { s -> s.copy(reminder = s.reminder.copy(days = if (d in s.reminder.days) s.reminder.days - d else s.reminder.days + d)) }
                        }, { Text(d.getDisplayName(TextStyle.SHORT, ru)) })
                    }
                }
                SwitchRow("Тихие часы", r.quietHoursEnabled) { v -> update { it.copy(reminder = it.reminder.copy(quietHoursEnabled = v)) } }
                if (r.quietHoursEnabled) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { timeDialog = TimeTarget.QuietStart(r.quietStart) }, label = { Text("с ${SettingsStore.formatTime(r.quietStart)}") })
                    AssistChip(onClick = { timeDialog = TimeTarget.QuietEnd(r.quietEnd) }, label = { Text("до ${SettingsStore.formatTime(r.quietEnd)}") })
                }
                val next = ReminderPlanner.nextTrigger(r, ZonedDateTime.now())
                Text(next?.let { "Следующее: ${it.format(DateTimeFormatter.ofPattern("EEEE, HH:mm", ru))}. Android может сдвинуть его на несколько минут ради экономии батареи." }
                    ?: "Нет подходящего времени — проверьте дни и тихие часы.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (r.enabled) Group("Курсы") {
            state?.courses?.forEach { c ->
                SwitchRow("Напоминать про ${c.flag} ${c.name}", settings.lang(c.id).remindersEnabled) { v ->
                    update { it.withLang(c.id) { x -> x.copy(remindersEnabled = v) } }
                }
            }
            SwitchRow("Объединять курсы в одно уведомление", settings.combineReminders) { v -> update { it.copy(combineReminders = v) } }
            TextButton(onClick = {
                ReminderReceiver.enqueueCheck(context, snoozed = true)
                toast("Проверка запущена: уведомление появится, если цель дня ещё не выполнена")
            }) { Text("Проверить напоминание сейчас") }
        }
        Group("Остров занятия",
            "Dynamic Island есть только у iPhone. Здесь — своя рабочая версия: уведомление-остров с существом, " +
                "текущим словом и кнопками «Не помню» / «Помню» (на Android 16+ — Live Update), такой же остров " +
                "внизу экранов приложения и, по желанию, плавающая капсула. Ответ в любом из них сразу виден везде.") {
            SwitchRow("Остров в уведомлении", settings.sessionNotification) { v -> update { it.copy(sessionNotification = v) } }
            SwitchRow("Кнопки «Помню» / «Не помню» в уведомлении и капсуле", settings.islandAnswers) { v -> update { it.copy(islandAnswers = v) } }
            Text(when (canPromote) {
                null -> "Live Updates доступны с Android 16. На вашей версии (${Build.VERSION.RELEASE}) — обычное уведомление с прогрессом."
                true -> "Live Updates разрешены: прогресс может появляться чипом в строке состояния."
                false -> "Live Updates выключены в настройках Android — будет обычное уведомление."
            }, style = MaterialTheme.typography.bodySmall)
            if (canPromote == false) TextButton(onClick = { openAppNotificationSettings(context) }) { Text("Открыть настройки уведомлений") }
            SwitchRow("Плавающая капсула поверх приложений", settings.capsuleEnabled && canOverlay) { v ->
                if (v && !canOverlay) overlayExplain = true else update { it.copy(capsuleEnabled = v) }
            }
            if (settings.capsuleEnabled && canOverlay) {
                Text("Капсула видна только во время незавершённого занятия, когда приложение свёрнуто. Нажатие — раскрыть " +
                    "перевод и кнопки ответа, нажатие на заголовок — открыть занятие, перетаскивание — переместить, " +
                    "долгое нажатие — скрыть.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Group("Приватность") {
            SwitchRow("Скрывать содержимое на экране блокировки", settings.hideOnLockScreen) { v -> update { it.copy(hideOnLockScreen = v) } }
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
            text = { Text("Чтобы показывать капсулу, когда приложение свёрнуто, Android требует отдельное разрешение. Капсула видна только " +
                "во время незавершённого занятия и не работает в фоне постоянно. Без разрешения прогресс по-прежнему виден в уведомлении.") },
            confirmButton = {
                TextButton(onClick = {
                    overlayExplain = false
                    scope.launch { container.settings.update { it.copy(capsuleEnabled = true) } }
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Открыть настройки") }
            },
            dismissButton = { TextButton(onClick = { overlayExplain = false }) { Text("Не сейчас") } },
        )
    }
}

// ------------------------------------------------------------ Внешний вид

@Composable
fun SettingsAppearanceScreen(container: AppContainer, nav: NavHostController) {
    val settings by rememberSettings(container)
    val state by rememberAppState(container)
    val scope = rememberCoroutineScope()
    fun update(f: (AppSettings) -> AppSettings) { scope.launch { container.settings.update(f) } }
    SettingsPage("Внешний вид", nav) {
        Group("Тема", "Светлая — основная тема Lingvo World.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m -> FilterChip(settings.theme == m, { update { it.copy(theme = m) } }, { Text(m.labelRu) }) }
            }
            SwitchRow("Анимации", settings.animations) { v -> update { it.copy(animations = v) } }
        }
        Group("Мотивация") {
            SwitchRow("Достижения, карта и существа на главном экране", settings.motivation) { v -> update { it.copy(motivation = v) } }
        }
        SoftCard(padding = 8.dp) {
            MenuRow(Icons.Filled.Apps, Palette.Ocean, "Иконка приложения",
                AppIcons.byId(settings.appIcon)?.name ?: "Lingvo World") { nav.navigate(Routes.ICONS) }
            val fav = Creatures.byId(settings.favoriteCreature)
            MenuRow(Icons.Filled.Palette, Palette.Violet, "Существо-спутник",
                fav?.name ?: if (state?.owned?.any { it.kind == UnlockKind.CREATURE } == true) "Не выбрано" else "Пока нет существ") {
                nav.navigate(Routes.CREATURES)
            }
        }
    }
}

// ------------------------------------------------------------ Данные и приложение

@Composable
fun SettingsDataScreen(container: AppContainer, nav: NavHostController) {
    val context = LocalContext.current
    SettingsPage("Данные и приложение", nav) {
        SoftCard(padding = 8.dp) {
            MenuRow(Icons.Filled.Backup, Palette.Sun, "Резервные копии, импорт и экспорт",
                "Полная копия: словари, прогресс, серии, достижения, настройки") { nav.navigate(Routes.import()) }
            MenuRow(Icons.Filled.Language, Palette.Mint, "Языковые курсы", "Добавить, переименовать, удалить") { nav.navigate(Routes.COURSES) }
        }
        Group("О приложении") {
            Text(
                "Lingvo World ${BuildConfig.VERSION_NAME}\n${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                    "Уведомления: ${if (Notifications.canPost(context)) "разрешены" else "выключены"} · " +
                    "поверх приложений: ${if (IslandController.canDrawOverlays(context)) "разрешено" else "нет"}\n" +
                    "Все данные хранятся только на этом устройстве, без регистрации и сервера.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------ Общие элементы

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
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun openAppNotificationSettings(context: android.content.Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
