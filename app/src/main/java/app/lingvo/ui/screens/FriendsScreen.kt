package app.lingvo.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.cloud.Friend
import app.lingvo.cloud.FriendRequest
import app.lingvo.core.Competition
import app.lingvo.core.LeaderboardPeriod
import app.lingvo.core.ShareCodes
import app.lingvo.core.Standing
import app.lingvo.ui.components.BackTopBar
import app.lingvo.ui.components.BigActionButton
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.GradientCard
import app.lingvo.ui.components.SoftCard
import app.lingvo.ui.rememberToast
import app.lingvo.ui.theme.LocalGradients
import app.lingvo.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Друзья и соревнование. Обмен идёт по короткому коду: кому дали код — тот видит вашу сводку
 * (ник и счётчики), слова и заметки не передаются. Чтобы закрыть доступ, код меняется.
 */
@Composable
fun FriendsScreen(container: AppContainer, nav: NavHostController) {
    val settings by container.settings.flow.collectAsStateWithLifecycle(null)
    val friendsFlow = remember { container.friends.observeFriends() }
    val friends by friendsFlow.collectAsState(emptyList())
    val account by container.cloud.account.collectAsState()
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val context = LocalContext.current

    var period by remember { mutableStateOf(LeaderboardPeriod.WEEK) }
    var board by remember { mutableStateOf<List<Standing>>(emptyList()) }
    var requests by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var nickDialog by remember { mutableStateOf(false) }
    var confirmRotate by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Friend?>(null) }

    val s = settings
    LaunchedEffect(friends, period, s?.nick, s?.shareStats) {
        if (s?.friendsEnabled == true) board = runCatching { container.friends.leaderboard(period) }.getOrDefault(emptyList())
    }
    LaunchedEffect(s?.friendsEnabled) {
        if (s?.friendsEnabled == true) {
            container.friends.forgetStale()
            requests = container.friends.incomingRequests()
        }
    }

    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            container.friends.refreshAll()?.let { toast(it) }
            requests = container.friends.incomingRequests()
            board = runCatching { container.friends.leaderboard(period) }.getOrDefault(emptyList())
            busy = false
        }
    }

    Scaffold(topBar = {
        BackTopBar("Друзья", onBack = { nav.popBackStack() }, actions = {
            if (s?.friendsEnabled == true) {
                IconButton(onClick = { refresh() }, enabled = !busy) { Icon(Icons.Filled.Refresh, "Обновить") }
            }
        })
    }) { padding ->
        if (s == null) { Centered { CircularProgressIndicator() }; return@Scaffold }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!container.cloud.isConfigured) {
                SoftCard {
                    Text("Соревнование недоступно в этой сборке", style = MaterialTheme.typography.titleMedium)
                    Text("В приложение не добавлены настройки Firebase (файл google-services.json). " +
                        "Всё остальное работает как обычно.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Соревноваться с друзьями", style = MaterialTheme.typography.titleMedium)
                        Text("Вы делитесь только ником и счётчиками. Слова, переводы и заметки не передаются.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(s.friendsEnabled, onCheckedChange = { on ->
                        scope.launch {
                            container.settings.update { it.copy(friendsEnabled = on) }
                            if (on) {
                                if (s.nick.isBlank()) container.settings.update { it.copy(nick = Competition.defaultNick()) }
                                container.friends.publish()?.let(toast)
                            } else {
                                container.friends.unpublish()
                            }
                        }
                    })
                }
            }
            if (!s.friendsEnabled) return@Column

            // Мой код и приглашение
            GradientCard(LocalGradients.current.hero) {
                Text("Мой код", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Text(ShareCodes.pretty(s.shareCode.ifBlank { "········" }), color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(s.nick.ifBlank { "Без ника" } + " · нажмите, чтобы изменить ник", color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { nickDialog = true })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Код Lingvo", s.shareCode))
                        toast("Код скопирован")
                    }) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Копировать") }
                    OutlinedButton(onClick = {
                        val text = "Давай соревноваться в Lingvo World! Мой код: ${ShareCodes.pretty(s.shareCode)}\n" +
                            ShareCodes.link(s.shareCode)
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Пригласить друга"))
                    }) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Пригласить") }
                }
            }

            BigActionButton("Добавить друга по коду", LocalGradients.current.violet, icon = Icons.Filled.PersonAdd,
                subtitle = "Введите код друга или ссылку-приглашение", enabled = !busy) { addDialog = true }

            if (requests.isNotEmpty()) {
                SoftCard {
                    Text("Вас добавили в друзья", style = MaterialTheme.typography.titleMedium)
                    Text("Чтобы видеть их прогресс, добавьте в ответ.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    requests.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.nick, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = {
                                scope.launch {
                                    container.friends.addFriend(r.code)?.let(toast) ?: toast("${r.nick} в списке друзей")
                                    container.friends.dismissRequest(r.code)
                                    requests = container.friends.incomingRequests()
                                }
                            }) { Text("Добавить") }
                            TextButton(onClick = {
                                scope.launch { container.friends.dismissRequest(r.code); requests = container.friends.incomingRequests() }
                            }) { Text("Скрыть") }
                        }
                    }
                }
            }

            // Таблица лидеров
            Text("Таблица лидеров", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeaderboardPeriod.entries.forEach { p ->
                    FilterChip(period == p, { period = p }, { Text(p.labelRu) })
                }
            }
            if (period == LeaderboardPeriod.WEEK) {
                Text("Недельное соревнование: очки обнуляются в понедельник.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (friends.isEmpty()) {
                SoftCard {
                    Text("Пока только вы", style = MaterialTheme.typography.titleMedium)
                    Text("Отправьте свой код другу или добавьте его код — и увидите, кто за неделю наберёт больше очков.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            board.forEachIndexed { index, row -> StandingRow(index + 1, row, onRemove = { friends.firstOrNull { it.code == row.code }?.let { removing = it } }) }

            s.sharePublishedAt.takeIf { it > 0 }?.let {
                Text("Последнее обновление: " + android.text.format.DateUtils.getRelativeTimeSpanString(it),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }

            // Приватность
            Text("Что видят друзья", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            PrivacyRow("Статистика: слова и очки", s.shareStats) { v -> scope.launch { container.settings.update { it.copy(shareStats = v) }; container.friends.publish() } }
            PrivacyRow("Дни подряд", s.shareStreak) { v -> scope.launch { container.settings.update { it.copy(shareStreak = v) }; container.friends.publish() } }
            PrivacyRow("Какие языки изучаю", s.shareCourses) { v -> scope.launch { container.settings.update { it.copy(shareCourses = v) }; container.friends.publish() } }
            PrivacyRow("Уведомлять, когда друг обошёл за неделю", s.friendNotifications) { v ->
                scope.launch { container.settings.update { it.copy(friendNotifications = v) } }
            }

            TextButton(onClick = { confirmRotate = true }) { Text("Сменить код (закрыть доступ всем)") }

            // Вход через Google — необязателен
            SoftCard {
                Text("Аккаунт Google", style = MaterialTheme.typography.titleMedium)
                val a = account
                if (a != null && !a.anonymous) {
                    Text("${a.name}${if (a.email.isNotBlank()) " · ${a.email}" else ""}", style = MaterialTheme.typography.bodyMedium)
                    Text("Вход нужен, только чтобы ник оставался за вами. Прогресс всё равно хранится на телефоне.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { scope.launch { container.cloud.signOut(); toast("Вы вышли") } }) { Text("Выйти") }
                } else {
                    Text("Вход не обязателен: соревнование работает и без него. Войдите, если хотите закрепить ник за собой.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            container.cloud.signInWithGoogle(context)?.let(toast) ?: toast("Вы вошли")
                            busy = false
                        }
                    }) { Text("Войти через Google") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (addDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Добавить друга") },
            text = {
                Column {
                    Text("Введите код друга или вставьте ссылку-приглашение.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(code, { code = it }, singleLine = true, label = { Text("Код") },
                        placeholder = { Text("ABCD-EFGH") })
                }
            },
            confirmButton = {
                TextButton(enabled = code.isNotBlank(), onClick = {
                    addDialog = false
                    busy = true
                    scope.launch {
                        container.friends.addFriend(code)?.let(toast) ?: toast("Друг добавлен")
                        busy = false
                    }
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Отмена") } },
        )
    }

    if (nickDialog) {
        var nick by remember { mutableStateOf(settings?.nick.orEmpty()) }
        AlertDialog(
            onDismissRequest = { nickDialog = false },
            title = { Text("Ник для друзей") },
            text = { OutlinedTextField(nick, { nick = it.take(24) }, singleLine = true, label = { Text("Ник") }) },
            confirmButton = {
                TextButton(onClick = {
                    nickDialog = false
                    scope.launch {
                        container.settings.update { it.copy(nick = nick.trim().ifBlank { Competition.defaultNick() }) }
                        container.friends.publish()
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { nickDialog = false }) { Text("Отмена") } },
        )
    }

    if (confirmRotate) {
        AlertDialog(
            onDismissRequest = { confirmRotate = false },
            title = { Text("Сменить код?") },
            text = { Text("Старый код перестанет работать: друзья, у которых он есть, больше не увидят ваш прогресс. " +
                "Новый код нужно будет разослать заново. Ваши друзья в списке останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRotate = false
                    scope.launch { container.friends.rotateCode()?.let(toast) ?: toast("Код обновлён") }
                }) { Text("Сменить") }
            },
            dismissButton = { TextButton(onClick = { confirmRotate = false }) { Text("Отмена") } },
        )
    }

    removing?.let { friend ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Убрать ${friend.progress.nick}?") },
            text = { Text("Данные друга удалятся с вашего телефона. Если вы давали ему свой код, он по-прежнему " +
                "сможет видеть ваш прогресс — чтобы закрыть доступ, смените код.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.friends.removeFriend(friend.code) }
                    removing = null
                }) { Text("Убрать") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun PrivacyRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}

@Composable
private fun StandingRow(place: Int, row: Standing, onRemove: () -> Unit) {
    SoftCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .padding(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (place) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$place" },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.nick + if (row.isMe) " — вы" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (row.isMe) Palette.Violet else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (row.isMe) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    "${row.words} ${wordsPlural(row.words)}" + if (row.streak > 0) " · ${row.streak} дн. подряд" else "",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${row.points}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(" очк.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!row.isMe) {
                TextButton(onClick = onRemove) { Text("Убрать") }
            }
        }
    }
}

private fun wordsPlural(n: Int) = app.lingvo.core.SessionText.plural(n, "слово", "слова", "слов")
