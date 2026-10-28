package app.lingvo.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.IconSwitcher
import app.lingvo.core.AchievementCategory
import app.lingvo.core.Achievements
import app.lingvo.core.AppIcons
import app.lingvo.core.Creatures
import app.lingvo.core.DailyStreak
import app.lingvo.core.GameEngine
import app.lingvo.core.RegionDef
import app.lingvo.core.SessionText
import app.lingvo.core.UnlockKind
import app.lingvo.core.WorldMap
import app.lingvo.ui.AppState
import app.lingvo.ui.Routes
import app.lingvo.ui.components.BackTopBar
import app.lingvo.ui.components.BigActionButton
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.CreatureArt
import app.lingvo.ui.components.Emblem
import app.lingvo.ui.components.GradientBar
import app.lingvo.ui.components.GradientCard
import app.lingvo.ui.components.MenuRow
import app.lingvo.ui.components.Mood
import app.lingvo.ui.components.Pill
import app.lingvo.ui.components.PopIn
import app.lingvo.ui.components.ProgressRing
import app.lingvo.ui.components.ScreenHeader
import app.lingvo.ui.components.SoftCard
import app.lingvo.ui.components.StreakGlobe
import app.lingvo.ui.components.artRes
import app.lingvo.ui.rememberAppState
import app.lingvo.ui.rememberToast
import app.lingvo.ui.theme.LocalGradients
import app.lingvo.ui.theme.Palette
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")
private fun date(ms: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM, ru).format(Date(ms))

// ============================================================ Карта мира

@Composable
fun WorldScreen(container: AppContainer, nav: NavHostController) {
    val state by rememberAppState(container)
    val s = state ?: run { Centered { CircularProgressIndicator() }; return }
    var selected by remember { mutableStateOf<RegionDef?>(null) }
    val next = GameEngine.nextRegion(s.owned)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Карта мира", "Открыто ${s.owned.count { it.kind == UnlockKind.REGION }} из ${WorldMap.regions.size} областей")
        WorldMapView(s, s.settings.animations) { selected = it }

        if (next?.requirement != null) {
            SoftCard {
                Text("Следующая цель", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(next.symbol, fontSize = 34.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(next.name, style = MaterialTheme.typography.titleMedium)
                        Text("${next.requirement.value(s.snapshot).coerceAtMost(next.requirement.target)} из ${next.requirement.describe()}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                GradientBar(next.requirement.progress(s.snapshot), brush = Brush.horizontalGradient(listOf(Color(next.colorA), Color(next.colorB))))
            }
        } else {
            GradientCard(LocalGradients.current.violet) {
                Text("Весь мир открыт! 🌌", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("Вы — настоящий исследователь языков.", color = Color.White.copy(alpha = 0.9f))
            }
        }

        val found = WorldMap.regions.filter { s.isOwned(UnlockKind.REGION, it.id) }
        SoftCard {
            Text("Находки", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WorldMap.regions.forEach { r ->
                    val have = r in found
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (have) Brush.linearGradient(listOf(Color(r.colorA), Color(r.colorB)))
                            else Brush.linearGradient(listOf(Color(0xFFE4E7F2), Color(0xFFD3D8EA)))),
                            contentAlignment = Alignment.Center) { Text(if (have) r.itemSymbol else "?", fontSize = 26.sp) }
                        Text(if (have) r.itemName else "Не найдено", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }

        SoftCard {
            Text("История открытий", style = MaterialTheme.typography.titleMedium)
            found.reversed().forEach { r ->
                val at = s.unlock(UnlockKind.REGION, r.id)?.unlockedAt
                Text("${r.symbol} ${r.name}" + (at?.let { " — ${date(it)}" } ?: ""), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }

        SoftCard(padding = 8.dp) {
            MenuRow(Icons.Filled.Pets, Palette.Violet, "Существа-полиглоты",
                "Собрано ${s.owned.count { it.kind == UnlockKind.CREATURE }} из ${Creatures.all.size}") { nav.navigate(Routes.CREATURES) }
            MenuRow(Icons.Filled.EmojiEvents, Palette.Sun, "Достижения",
                "Получено ${s.owned.count { it.kind == UnlockKind.ACHIEVEMENT }} из ${Achievements.all.size}") { nav.navigate(Routes.ACHIEVEMENTS) }
            MenuRow(Icons.Filled.Apps, Palette.Ocean, "Иконки приложения",
                "Доступно ${s.owned.count { it.kind == UnlockKind.ICON }} из ${AppIcons.all.size}") { nav.navigate(Routes.ICONS) }
        }
        Spacer(Modifier.height(8.dp))
    }

    selected?.let { r -> RegionDialog(s, r) { selected = null } }
}

@Composable
private fun WorldMapView(s: AppState, animate: Boolean, onRegion: (RegionDef) -> Unit) {
    val t = rememberInfiniteTransition(label = "map")
    val pulse by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "pulse")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "drift")
    val current = GameEngine.currentRegion(s.owned)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(560.dp).clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2B2F6B), Color(0xFF5B3FD1), Color(0xFF4CC9F0), Color(0xFFBFF3D6))))
            .semantics { contentDescription = "Карта мира. Текущая область: ${current.name}" },
    ) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            // звёзды в небе над верхними областями
            for (i in 0 until 26) {
                val x = ((i * 0.137f + drift * 0.02f) % 1f) * size.width
                val y = ((i * 0.293f) % 0.35f) * size.height
                drawCircle(Color.White.copy(alpha = 0.5f), 2.dp.toPx() * (1 + i % 3) / 2f, Offset(x, y))
            }
            // облака
            for (i in 0 until 3) {
                val x = ((drift + i / 3f) % 1f) * size.width * 1.3f - size.width * 0.15f
                drawOval(Color.White.copy(alpha = 0.18f), Offset(x, size.height * (0.35f + i * 0.17f)), androidx.compose.ui.geometry.Size(120.dp.toPx(), 34.dp.toPx()))
            }
            // тропа между областями
            val pts = WorldMap.regions.map { Offset(it.x * size.width, it.y * size.height) }
            for (i in 1 until pts.size) {
                val open = s.isOwned(UnlockKind.REGION, WorldMap.regions[i].id)
                drawLine(
                    if (open) Color(0xFFFFE066) else Color.White.copy(alpha = 0.35f), pts[i - 1], pts[i],
                    strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (open) null else PathEffect.dashPathEffect(floatArrayOf(14f, 14f)),
                )
            }
        }
        WorldMap.regions.forEach { r ->
            val open = s.isOwned(UnlockKind.REGION, r.id)
            val isCurrent = r.id == current.id
            val node = 74.dp
            Box(
                Modifier.offset(w * r.x - node / 2, h * r.y - node / 2).size(node)
                    .scale(if (isCurrent && animate) pulse else 1f)
                    .clip(CircleShape)
                    .background(if (open) Brush.linearGradient(listOf(Color(r.colorA), Color(r.colorB)))
                    else Brush.linearGradient(listOf(Color(0xFF8C93B5), Color(0xFF666D90))))
                    .border(if (isCurrent) 4.dp else 2.dp, if (isCurrent) Color(0xFFFFE066) else Color.White.copy(alpha = 0.6f), CircleShape)
                    .clickable { onRegion(r) }
                    .semantics { contentDescription = r.name + if (open) ", открыта" else ", закрыта" },
                contentAlignment = Alignment.Center,
            ) { Text(if (open) r.symbol else "🔒", fontSize = if (open) 32.sp else 24.sp) }
            Text(
                r.name, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(w * r.x - 60.dp, h * r.y + node / 2 + 2.dp).width(120.dp),
            )
            // существа, живущие в открытой области
            if (open) {
                Creatures.all.filter { it.homeRegion == r.id && s.isOwned(UnlockKind.CREATURE, it.id) }.take(2).forEachIndexed { i, c ->
                    Box(Modifier.offset(w * r.x + node / 2 - 6.dp + (i * 22).dp, h * r.y - node / 2 - 6.dp)) {
                        CreatureArt(c, s.creatureLevel(c.id), size = 30.dp, mood = Mood.HAPPY, animate = animate)
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionDialog(s: AppState, r: RegionDef, onDismiss: () -> Unit) {
    val open = s.isOwned(UnlockKind.REGION, r.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(if (open) r.symbol else "🔒", fontSize = 40.sp) },
        title = { Text(r.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.description)
                if (open) {
                    s.unlock(UnlockKind.REGION, r.id)?.let { Text("Открыта ${date(it.unlockedAt)}", style = MaterialTheme.typography.bodySmall) }
                    Text("Находка: ${r.itemSymbol} ${r.itemName}", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val idx = WorldMap.regions.indexOf(r)
                    val prev = WorldMap.regions.getOrNull(idx - 1)
                    if (prev != null && !s.isOwned(UnlockKind.REGION, prev.id)) Text("Сначала откройте «${prev.name}».")
                    r.requirement?.let {
                        Text("Условие: ${it.describe()} (сейчас ${it.value(s.snapshot)})")
                        GradientBar(it.progress(s.snapshot))
                    }
                }
                val living = Creatures.all.filter { it.homeRegion == r.id }
                if (living.isNotEmpty()) {
                    Text("Здесь живут: " + living.joinToString { if (s.isOwned(UnlockKind.CREATURE, it.id)) it.name else "???" },
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } },
    )
}

// ============================================================ Достижения

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AchievementsScreen(container: AppContainer, nav: NavHostController) {
    val state by rememberAppState(container)
    var category by rememberSaveable { mutableStateOf<AchievementCategory?>(null) }
    Scaffold(topBar = { BackTopBar("Достижения", onBack = { nav.popBackStack() }) }) { padding ->
        val s = state ?: run { Centered { CircularProgressIndicator() }; return@Scaffold }
        val total = Achievements.all.size
        val got = s.owned.count { it.kind == UnlockKind.ACHIEVEMENT }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientCard(LocalGradients.current.sunrise) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(got.toFloat() / total, size = 70.dp, track = Color.White.copy(alpha = 0.3f),
                        brush = Brush.sweepGradient(listOf(Color.White, Color(0xFFFFF3B0), Color.White))) {
                        Text("$got", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Получено $got из $total", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text("Награды — новые иконки приложения", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(category == null, { category = null }, { Text("Все") })
                AchievementCategory.entries.forEach { c -> FilterChip(category == c, { category = c }, { Text(c.labelRu) }) }
            }
            Achievements.all.filter { category == null || it.category == category }
                .sortedBy { if (s.isOwned(UnlockKind.ACHIEVEMENT, it.id)) 0 else 1 }
                .forEach { a ->
                    val unlocked = s.unlock(UnlockKind.ACHIEVEMENT, a.id)
                    SoftCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PopIn(unlocked?.unlockedAt, enabled = false) { Emblem(a.art, a.symbol, unlocked != null, size = 64.dp) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.title, style = MaterialTheme.typography.titleMedium)
                                Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                if (unlocked != null) {
                                    Text("Получено ${date(unlocked.unlockedAt)}", style = MaterialTheme.typography.labelMedium, color = Palette.Mint)
                                } else {
                                    val v = a.requirement.value(s.snapshot).coerceAtMost(a.requirement.target)
                                    Text("$v / ${a.requirement.target}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    GradientBar(a.requirement.progress(s.snapshot), height = 7.dp)
                                }
                                a.rewardIcon?.let { Text("Награда: иконка «${AppIcons.byId(it)?.name}»", style = MaterialTheme.typography.labelSmall, color = Palette.Violet) }
                            }
                            Pill(a.category.labelRu, MaterialTheme.colorScheme.surfaceVariant, textColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
        }
    }
}

// ============================================================ Серия

@Composable
fun StreakScreen(container: AppContainer, nav: NavHostController) {
    val state by rememberAppState(container)
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { BackTopBar("Ежедневная серия", onBack = { nav.popBackStack() }) }) { padding ->
        val s = state ?: run { Centered { CircularProgressIndicator() }; return@Scaffold }
        val st = s.streak
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GradientCard(LocalGradients.current.hero) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    StreakGlobe(st.current, st.todayCompleted, size = 180.dp, animate = s.settings.animations)
                    Text("${st.current} ${SessionText.plural(st.current, "день", "дня", "дней")} подряд", color = Color.White,
                        style = MaterialTheme.typography.headlineMedium)
                    Text("Рекорд: ${st.best}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            st.todayCompleted -> "Сегодня цель выполнена — глобус сияет ярче ✨"
                            st.atRisk -> "Выполните цель дня, чтобы не потерять серию"
                            else -> "Выполните цель дня, чтобы начать серию"
                        },
                        color = Color.White, textAlign = TextAlign.Center,
                    )
                }
            }
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(st.todayProgress, size = 76.dp) { Text("${st.todayAnswers}/${st.todayGoal}", style = MaterialTheme.typography.labelLarge) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Цель дня", style = MaterialTheme.typography.titleMedium)
                        Text("День засчитывается, когда дано столько ответов. Открытие приложения не считается.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DailyStreak.GOAL_OPTIONS.forEach { n ->
                        FilterChip(s.settings.dailyGoal == n, { scope.launch { container.repository.setDailyGoal(n) } }, { Text("$n") })
                    }
                }
            }
            st.nextMilestone?.let { m ->
                SoftCard {
                    Text("Ближайшая отметка: $m ${SessionText.plural(m, "день", "дня", "дней")}", style = MaterialTheme.typography.titleMedium)
                    Text("Осталось ${m - st.current}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    GradientBar(st.current.toFloat() / m, brush = LocalGradients.current.fire)
                    Spacer(Modifier.height(10.dp))
                    Text("Награды за серию", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Achievements.all.filter { it.requirement.metric == app.lingvo.core.Metric.STREAK }.forEach { a ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                                Emblem(a.art, a.symbol, s.isOwned(UnlockKind.ACHIEVEMENT, a.id), size = 56.dp)
                                Text("${a.requirement.target} дн.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Creatures.byId("flaro")?.let { f ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                                CreatureArt(f, s.creatureLevel(f.id).coerceAtLeast(1), size = 56.dp, locked = !s.isOwned(UnlockKind.CREATURE, f.id))
                                Text("Фларо", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            SoftCard { StreakCalendar(st.completedDays, s.days.associateBy { it.epochDay }.mapValues { it.value.answers }) }
            Text(
                "Правила: день засчитывается один раз — когда выполнена цель. Дата берётся по часам телефона в момент ответа, " +
                    "поэтому смена часового пояса не меняет прошлые дни. Пропущенный день обнуляет серию, рекорд сохраняется.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreakCalendar(done: Set<Long>, answers: Map<Long, Int>) {
    val today = LocalDate.now()
    var monthOffset by remember { mutableStateOf(0L) }
    val month = today.withDayOfMonth(1).minusMonths(monthOffset)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { monthOffset++ }) { Text("‹") }
        Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, ru).replaceFirstChar { it.uppercase() } + " ${month.year}",
            Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { if (monthOffset > 0) monthOffset-- }, enabled = monthOffset > 0) { Text("›") }
    }
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach {
            Text(it.getDisplayName(TextStyle.SHORT, ru), Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val first = month.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val cells = first + days
    for (row in 0 until (cells + 6) / 7) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            for (col in 0 until 7) {
                val idx = row * 7 + col - first
                Box(Modifier.weight(1f).height(38.dp), contentAlignment = Alignment.Center) {
                    if (idx in 0 until days) {
                        val d = month.plusDays(idx.toLong())
                        val ep = d.toEpochDay()
                        val isDone = ep in done
                        val partial = !isDone && (answers[ep] ?: 0) > 0
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(
                                    when {
                                        isDone -> Brush.linearGradient(listOf(Color(0xFFFFD166), Color(0xFFFF6B35)))
                                        partial -> Brush.linearGradient(listOf(Color(0xFFDDE6FF), Color(0xFFDDE6FF)))
                                        else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    }
                                )
                                .let { if (d == today) it.border(2.dp, Palette.Ocean, CircleShape) else it },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (isDone) "🔥" else "${d.dayOfMonth}", fontSize = if (isDone) 15.sp else 13.sp,
                                color = if (d.isAfter(today)) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ Существа

@Composable
fun CreaturesScreen(container: AppContainer, nav: NavHostController) {
    val state by rememberAppState(container)
    Scaffold(topBar = { BackTopBar("Существа-полиглоты", onBack = { nav.popBackStack() }) }) { padding ->
        val s = state ?: run { Centered { CircularProgressIndicator() }; return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Собрано ${s.owned.count { it.kind == UnlockKind.CREATURE }} из ${Creatures.all.size}. Существа приходят за реальный прогресс и растут вместе с вами.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Creatures.all.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c ->
                        val owned = s.isOwned(UnlockKind.CREATURE, c.id)
                        val level = s.creatureLevel(c.id).coerceAtLeast(1)
                        SoftCard(Modifier.weight(1f), onClick = { nav.navigate(Routes.creature(c.id)) }, padding = 12.dp) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                CreatureArt(c, level, size = 96.dp, locked = !owned, mood = Mood.HAPPY, animate = s.settings.animations)
                                Text(if (owned) c.name else "???", style = MaterialTheme.typography.titleMedium)
                                Pill(c.rarity.labelRu, Color(c.rarity.color))
                                Spacer(Modifier.height(4.dp))
                                Text(if (owned) "${c.stageName(level)} · ур. $level" else c.aspect,
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (c.id == s.settings.favoriteCreature) Text("★ Спутник", style = MaterialTheme.typography.labelSmall, color = Palette.Violet)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun CreatureScreen(container: AppContainer, nav: NavHostController, id: String) {
    val state by rememberAppState(container)
    val scope = rememberCoroutineScope()
    val c = Creatures.byId(id) ?: run { Centered { Text("Существо не найдено") }; return }
    Scaffold(topBar = { BackTopBar(c.name, onBack = { nav.popBackStack() }) }) { padding ->
        val s = state ?: run { Centered { CircularProgressIndicator() }; return@Scaffold }
        val owned = s.isOwned(UnlockKind.CREATURE, c.id)
        val level = s.creatureLevel(c.id).coerceAtLeast(1)
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GradientCard(Brush.linearGradient(listOf(Color(c.colorA), Color(c.colorB)))) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CreatureArt(c, level, size = 190.dp, locked = !owned, mood = Mood.CHEER, animate = s.settings.animations)
                    Text(if (owned) c.name else "???", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("${c.rarity.labelRu} · ${c.aspect}", color = Color.White.copy(alpha = 0.9f))
                }
            }
            SoftCard {
                Text(if (owned) c.description else "Это существо ещё не встретилось вам. Выполните условие, чтобы оно появилось.",
                    style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text("Живёт: ${WorldMap.byId(c.homeRegion)?.let { "${it.symbol} ${it.name}" }}", style = MaterialTheme.typography.bodySmall)
            }
            SoftCard {
                Text(if (owned) "Как было получено" else "Как получить", style = MaterialTheme.typography.titleMedium)
                c.requirements.forEach { r ->
                    Text("${r.describe()} — сейчас ${r.value(s.snapshot)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    if (!owned) GradientBar(r.progress(s.snapshot), height = 7.dp)
                }
                s.unlock(UnlockKind.CREATURE, c.id)?.let { Text("Получено ${date(it.unlockedAt)}", style = MaterialTheme.typography.labelMedium, color = Palette.Mint,
                    modifier = Modifier.padding(top = 6.dp)) }
            }
            if (owned) {
                SoftCard {
                    Text("Развитие: ${c.stageName(level)} (уровень $level из ${c.maxLevel})", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        (1..c.maxLevel).forEach { l ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CreatureArt(c, l, size = 64.dp, locked = l > level, animate = false)
                                Text(c.stageName(l), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    c.nextThreshold(level)?.let { th ->
                        val v = c.evolutionMetric.value(s.snapshot)
                        Text("До следующего облика: $v / $th ${c.evolutionMetric.labelRu}", style = MaterialTheme.typography.bodySmall)
                        GradientBar(v.toFloat() / th, brush = Brush.horizontalGradient(listOf(Color(c.colorA), Color(c.colorB))))
                    } ?: Text("Достигнут высший облик ✨", style = MaterialTheme.typography.bodyMedium)
                }
                val fav = s.settings.favoriteCreature == c.id
                BigActionButton(
                    if (fav) "Это ваш спутник ★" else "Сделать спутником", LocalGradients.current.violet,
                    subtitle = "Показывается на главном экране и в «острове» занятия", enabled = !fav,
                ) { scope.launch { container.settings.update { it.copy(favoriteCreature = c.id) } } }
            }
        }
    }
}

// ============================================================ Иконки

@Composable
fun IconsScreen(container: AppContainer, nav: NavHostController) {
    val state by rememberAppState(container)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toast = rememberToast()
    var confirm by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { BackTopBar("Иконки приложения", onBack = { nav.popBackStack() }) }) { padding ->
        val s = state ?: run { Centered { CircularProgressIndicator() }; return@Scaffold }
        val current = remember(s.settings.appIcon) { IconSwitcher.current(context) }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Иконки открываются за достижения. Выбранная иконка применяется только по вашему решению; лаунчер может обновить значок с задержкой.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppIcons.all.groupBy { it.collection }.forEach { (collection, icons) ->
                Text(collection, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                icons.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { icon ->
                            val open = s.isOwned(UnlockKind.ICON, icon.id)
                            val selected = current == icon.id
                            val ach = icon.achievementId?.let { Achievements.byId(it) }
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                    .clickable(enabled = open && !selected) { confirm = icon.id }
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                artRes(icon.id)?.let {
                                    Image(painterResource(it), icon.name, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
                                        alpha = if (open) 1f else 0.4f,
                                        colorFilter = if (open) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
                                }
                                Text(icon.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                                Text(
                                    when {
                                        selected -> "Сейчас"
                                        open -> "Доступна"
                                        ach != null -> "🔒 ${ach.title}"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                                    color = if (selected) Palette.Ocean else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    confirm?.let { id ->
        val icon = AppIcons.byId(id)!!
        ConfirmDialog(
            "Поставить иконку «${icon.name}»?",
            "Значок на рабочем столе сменится в течение нескольких секунд. Некоторые лаунчеры убирают ярлык с рабочего стола — " +
                "тогда добавьте его заново из списка приложений.",
            "Поставить",
            onConfirm = {
                confirm = null
                IconSwitcher.apply(context, id)
                scope.launch { container.settings.update { it.copy(appIcon = id) } }
                toast("Иконка «${icon.name}» выбрана")
            },
            onDismiss = { confirm = null },
        )
    }
}
