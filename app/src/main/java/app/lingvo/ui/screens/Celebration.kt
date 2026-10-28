package app.lingvo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.Achievements
import app.lingvo.core.AppIcons
import app.lingvo.core.Creatures
import app.lingvo.core.UnlockKind
import app.lingvo.core.WorldMap
import app.lingvo.data.UnlockEntity
import app.lingvo.ui.Routes
import app.lingvo.ui.components.CreatureArt
import app.lingvo.ui.components.Emblem
import app.lingvo.ui.components.Mood
import app.lingvo.ui.components.PopIn
import app.lingvo.ui.rememberAppState
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Prize(val unlock: UnlockEntity, val kind: UnlockKind, val title: String, val subtitle: String)

private fun describe(u: UnlockEntity): Prize? {
    val kind = runCatching { UnlockKind.valueOf(u.kind) }.getOrNull() ?: return null
    return when (kind) {
        UnlockKind.ACHIEVEMENT -> Achievements.byId(u.itemId)?.let { Prize(u, kind, "Достижение: ${it.title}", it.description) }
        UnlockKind.REGION -> WorldMap.byId(u.itemId)?.let { Prize(u, kind, "Открыта область: ${it.name}", "Находка: ${it.itemSymbol} ${it.itemName}") }
        UnlockKind.CREATURE -> Creatures.byId(u.itemId)?.let {
            if (u.level > 1) Prize(u, kind, "${it.name} вырос!", "Новый облик: ${it.stageName(u.level)}")
            else Prize(u, kind, "Новое существо: ${it.name}", it.aspect + " · " + it.rarity.labelRu)
        }
        // Иконки, доступные с самого начала, не празднуем.
        UnlockKind.ICON -> AppIcons.byId(u.itemId)?.takeIf { it.achievementId != null }?.let {
            Prize(u, kind, "Новая иконка: ${it.name}", "Выберите её в разделе «Иконки приложения»")
        }
    }
}

/** Показывает новые (ещё не увиденные) награды. Одна — красивым окном, несколько — списком. */
@Composable
fun CelebrationHost(container: AppContainer, nav: NavHostController) {
    val state by rememberAppState(container)
    val scope = rememberCoroutineScope()
    val s = state ?: return
    val unseen = s.unlocks.filter { !it.seen }
    if (unseen.isEmpty()) return
    val prizes = unseen.mapNotNull(::describe)
    // Технические записи (стартовая иконка и т. п.) отмечаем без показа.
    LaunchedEffect(unseen.size, prizes.size) {
        val silent = unseen.filter { u -> prizes.none { it.unlock == u } }
        silent.forEach { container.repository.markSeen(UnlockKind.valueOf(it.kind), it.itemId) }
    }
    if (prizes.isEmpty()) return
    val markAll: () -> Unit = { scope.launch { prizes.forEach { container.repository.markSeen(it.kind, it.unlock.itemId) } } }

    if (prizes.size == 1) {
        val p = prizes.first()
        AlertDialog(
            onDismissRequest = markAll,
            icon = { Box(contentAlignment = Alignment.Center) { Confetti(); PopIn(p.unlock) { PrizeArt(p, 110) } } },
            title = { Text(p.title, textAlign = TextAlign.Center) },
            text = { Text(p.subtitle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = markAll) { Text("Отлично!") } },
            dismissButton = {
                TextButton(onClick = {
                    markAll()
                    nav.navigate(when (p.kind) {
                        UnlockKind.ACHIEVEMENT -> Routes.ACHIEVEMENTS
                        UnlockKind.REGION -> "world"
                        UnlockKind.CREATURE -> Routes.creature(p.unlock.itemId)
                        UnlockKind.ICON -> Routes.ICONS
                    }) { launchSingleTop = true }
                }) { Text("Посмотреть") }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = markAll,
            icon = { Box(contentAlignment = Alignment.Center) { Confetti(); Text("🎉", fontSize = 44.sp) } },
            title = { Text("Новые награды: ${prizes.size}") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    prizes.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PrizeArt(p, 44)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(p.title, style = MaterialTheme.typography.titleSmall)
                                Text(p.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = markAll) { Text("Здорово!") } },
        )
    }
}

@Composable
private fun PrizeArt(p: Prize, sizeDp: Int) {
    val size = sizeDp.dp
    when (p.kind) {
        UnlockKind.ACHIEVEMENT -> Achievements.byId(p.unlock.itemId)?.let { Emblem(it.art, it.symbol, true, size) }
        UnlockKind.REGION -> WorldMap.byId(p.unlock.itemId)?.let { Text(it.symbol, fontSize = (sizeDp * 0.6f).sp) }
        UnlockKind.CREATURE -> Creatures.byId(p.unlock.itemId)?.let { CreatureArt(it, p.unlock.level, size = size, mood = Mood.CHEER) }
        UnlockKind.ICON -> Emblem(p.unlock.itemId, "🖼️", true, size)
    }
}

/** Лёгкое конфетти: несколько частиц, одна короткая анимация. */
@Composable
private fun Confetti() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1400, easing = LinearEasing)) }
    val parts = remember {
        val r = Random(7)
        List(24) { Triple(r.nextFloat() * 6.28f, 0.5f + r.nextFloat() * 0.5f, listOf(0xFFFF6B5B, 0xFFFFB627, 0xFF7B5CFF, 0xFF22C3A6, 0xFF4CC9F0)[it % 5]) }
    }
    Canvas(Modifier.size(170.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val p = progress.value
        parts.forEach { (a, speed, color) ->
            val d = size.width * 0.5f * p * speed
            drawCircle(Color(color).copy(alpha = 1f - p), 4.dp.toPx(), Offset(c.x + cos(a) * d, c.y + sin(a) * d + p * p * 40f))
        }
    }
}
