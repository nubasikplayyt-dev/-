package app.lingvo.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.GameEngine
import app.lingvo.core.SessionText
import app.lingvo.core.UnlockKind
import app.lingvo.notify.Notifications
import app.lingvo.ui.AppState
import app.lingvo.ui.Routes
import app.lingvo.ui.Tab
import app.lingvo.ui.components.BigActionButton
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.CreatureArt
import app.lingvo.ui.components.Emblem
import app.lingvo.ui.components.GradientBar
import app.lingvo.ui.components.GradientCard
import app.lingvo.ui.components.Mood
import app.lingvo.ui.components.ProgressRing
import app.lingvo.ui.components.SoftCard
import app.lingvo.ui.components.StreakGlobe
import app.lingvo.ui.navigateTab
import app.lingvo.ui.rememberAppState
import app.lingvo.ui.rememberToast
import app.lingvo.ui.theme.LocalGradients
import app.lingvo.ui.theme.Palette
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun HomeScreen(container: AppContainer, nav: NavHostController) {
    val state by rememberAppState(container)
    val s = state ?: run { Centered { CircularProgressIndicator() }; return }
    if (s.courses.isEmpty()) { Centered { CircularProgressIndicator() }; return }
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val ctx = LocalContext.current
    val g = LocalGradients.current
    val course = s.activeCourse ?: return
    val o = s.overview(course.id)
    var notifAsked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifAsked = true
        if (!granted) toast("Без разрешения напоминания не будут приходить. Включить можно в настройках.")
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Приветствие и серия
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting(), style = MaterialTheme.typography.headlineMedium)
                Text("Lingvo World", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(g.fire).clickable { nav.navigate(Routes.STREAK) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔥", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(4.dp))
                Text("${s.streak.current}", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        // Выбор курса
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.courses.forEach { c ->
                val selected = c.id == course.id
                Row(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (selected) Brush.linearGradient(listOf(Palette.course(c.id), Palette.course(c.id).copy(alpha = 0.75f)))
                        else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)))
                        .clickable { scope.launch { container.settings.update { it.copy(activeCourseId = c.id) } } }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.flag)
                    Spacer(Modifier.width(6.dp))
                    Text(c.name, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                    .clickable { nav.navigate(Routes.COURSE_NEW) }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Курс", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }

        // Главный блок: спутник, цель дня и главное действие
        GradientCard(g.hero) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val comp = s.companion
                if (comp != null) {
                    CreatureArt(comp.first, comp.second, size = 92.dp, mood = moodFor(s), animate = s.settings.animations)
                } else {
                    Text("🌍", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(companionLine(s), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (s.streak.todayCompleted) "Цель дня выполнена! ✨"
                        else "Цель дня: ${s.streak.todayAnswers} из ${s.streak.todayGoal}",
                        color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
                    )
                }
                ProgressRing(
                    s.streak.todayProgress, size = 64.dp, stroke = 8.dp,
                    brush = Brush.sweepGradient(listOf(Color(0xFFFFE066), Color(0xFFFFB627), Color(0xFFFFE066))),
                    track = Color.White.copy(alpha = 0.25f), animate = s.settings.animations,
                ) { Text("${(s.streak.todayProgress * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelLarge) }
            }
            Spacer(Modifier.height(16.dp))
            val due = o?.due ?: 0
            val newLeft = o?.newLeftToday ?: 0
            when {
                due > 0 -> BigActionButton(
                    "Начать повторение", g.sunrise, subtitle = "${course.flag} $due ${SessionText.plural(due, "карточка ждёт", "карточки ждут", "карточек ждут")}",
                    icon = Icons.Filled.PlayArrow,
                ) { nav.navigate(Routes.review(course.id)) }
                newLeft > 0 -> BigActionButton(
                    "Учить новые слова", g.sunrise, subtitle = "$newLeft на сегодня — после знакомства сразу повторим",
                    icon = Icons.Filled.School,
                ) { nav.navigate(Routes.learn(course.id)) }
                (o?.total ?: 0) == 0 -> BigActionButton("Добавить первые слова", g.sunrise, subtitle = "или импортировать словарь", icon = Icons.Filled.Add) {
                    nav.navigate(Routes.language(course.id))
                }
                else -> BigActionButton("Всё повторено 🎉", g.mint, subtitle = "Можно добавить новые слова", icon = Icons.Filled.Add) {
                    nav.navigate(Routes.add(course.id))
                }
            }
        }

        // Продолжить прерванное занятие
        s.sessions.filter { it.status == "ACTIVE" && it.done in 1 until it.total }.forEach { ses ->
            val c = s.courses.firstOrNull { it.id == ses.language } ?: return@forEach
            SoftCard(onClick = { nav.navigate(Routes.review(c.id)) }) {
                Text("Продолжить занятие", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(SessionText.progress(c.spec, ses.done, ses.total), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                GradientBar(ses.done.toFloat() / ses.total)
            }
        }

        // Серия и сегодня
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SoftCard(Modifier.weight(1f), onClick = { nav.navigate(Routes.STREAK) }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreakGlobe(s.streak.current, s.streak.todayCompleted, size = 64.dp, animate = s.settings.animations)
                    Column(Modifier.padding(start = 6.dp)) {
                        Text("${s.streak.current}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(SessionText.plural(s.streak.current, "день", "дня", "дней") + " подряд",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("рекорд ${s.streak.best}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            SoftCard(Modifier.weight(1f), onClick = { nav.navigateTab(Tab.STATS) }, padding = 14.dp) {
                val today = s.overviews.sumOf { it.reviewsToday }
                val correct = s.overviews.sumOf { it.correctToday }
                Text("Сегодня", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$today", style = MaterialTheme.typography.headlineSmall, color = Palette.Ocean, fontWeight = FontWeight.Bold)
                Text(SessionText.plural(today, "ответ", "ответа", "ответов") +
                    if (today > 0) " · ${correct * 100 / today}% помню" else "",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Уведомления (Android 13+) — спрашиваем один раз, с объяснением
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(ctx) && !notifAsked) {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, null, tint = Palette.Violet)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Напоминания о занятиях", style = MaterialTheme.typography.titleSmall)
                        Text("Раз в день и только пока цель не выполнена.", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Включить") }
                }
            }
        }

        // Ближайшее достижение
        if (s.settings.motivation) {
            GameEngine.nearestAchievements(s.snapshot, s.owned, 1).firstOrNull()?.let { a ->
                SoftCard(onClick = { nav.navigate(Routes.ACHIEVEMENTS) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Emblem(a.art, a.symbol, unlocked = false, size = 52.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ближайшее достижение", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(a.title, style = MaterialTheme.typography.titleMedium)
                            Text("${a.requirement.value(s.snapshot).coerceAtMost(a.requirement.target)} / ${a.requirement.target} · ${a.description}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GradientBar(a.requirement.progress(s.snapshot), brush = g.sunrise)
                }
            }

            // Мини-карта
            val current = GameEngine.currentRegion(s.owned)
            val next = GameEngine.nextRegion(s.owned)
            GradientCard(Brush.linearGradient(listOf(Color(current.colorA), Color(current.colorB))), onClick = { nav.navigateTab(Tab.WORLD) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(current.symbol, style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Вы здесь", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                        Text(current.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
                        val req = next?.requirement
                        if (next != null && req != null) {
                            Text("Дальше: ${next.name} — ${req.value(s.snapshot).coerceAtMost(req.target)}/${req.target} ${req.metric.labelRu}",
                                color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                        } else Text("Весь мир открыт! 🌌", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
                next?.requirement?.let {
                    Spacer(Modifier.height(10.dp))
                    GradientBar(it.progress(s.snapshot), brush = Brush.horizontalGradient(listOf(Color.White, Color(0xFFFFF3B0))))
                }
            }
        }

        // Быстрые переходы
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile("Словарь", Icons.AutoMirrored.Filled.MenuBook, Palette.Ocean, Modifier.weight(1f)) { nav.navigateTab(Tab.DICTIONARY) }
            QuickTile("Статистика", Icons.Filled.BarChart, Palette.Mint, Modifier.weight(1f)) { nav.navigateTab(Tab.STATS) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile("Достижения", Icons.Filled.EmojiEvents, Palette.Sun, Modifier.weight(1f),
                badge = "${s.owned.count { it.kind == UnlockKind.ACHIEVEMENT }}") { nav.navigate(Routes.ACHIEVEMENTS) }
            QuickTile("Существа", Icons.Filled.Pets, Palette.Violet, Modifier.weight(1f),
                badge = "${s.owned.count { it.kind == UnlockKind.CREATURE }}") { nav.navigate(Routes.CREATURES) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuickTile(title: String, icon: ImageVector, color: Color, modifier: Modifier, badge: String? = null, onClick: () -> Unit) {
    SoftCard(modifier, onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color)
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (badge != null) Text(badge, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Доброе утро! ☀️"
    in 12..17 -> "Добрый день! 👋"
    in 18..22 -> "Добрый вечер! 🌙"
    else -> "Доброй ночи! ✨"
}

fun moodFor(s: AppState): Mood = when {
    s.streak.todayCompleted -> Mood.CHEER
    s.streak.todayAnswers > 0 -> Mood.HAPPY
    s.streak.atRisk && LocalTime.now().hour >= 20 -> Mood.SLEEPY
    else -> Mood.CALM
}

private fun companionLine(s: AppState): String {
    val name = s.companion?.first?.name ?: "Lingvo"
    return when {
        s.streak.todayCompleted -> "$name счастлив — так держать!"
        s.streak.atRisk -> "$name ждёт: серия ${s.streak.current} 🔥 под угрозой"
        s.streak.todayAnswers > 0 -> "$name радуется: хороший темп!"
        else -> "$name готов к занятию"
    }
}
