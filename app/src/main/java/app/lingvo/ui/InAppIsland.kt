package app.lingvo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.Answer
import app.lingvo.core.Creatures
import app.lingvo.ui.components.CreatureArt
import app.lingvo.ui.components.Mood
import kotlinx.coroutines.launch

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Остров внутри приложения в стиле системного музыкального плеера.
 *
 * - Компактно: пилюля сверху по центру поверх строки состояния — существо, курс, счётчик и тонкая полоса прогресса.
 * - Тап или свайп вниз — пилюля плавно раскрывается в карточку на том же месте: вопрос и «Не помню» / «Помню».
 * - Свайп вверх, тап вне карточки или ответ — плавно сворачивается обратно.
 * - Элемент не перетаскивается: позиция фиксирована. Нижнее меню приложения он не закрывает.
 * - Когда карточек больше нет — плавно исчезает.
 * Ответ сохраняется тем же кодом, что и на экране занятия.
 */
@Composable
fun BoxScope.InAppIsland(container: AppContainer, nav: NavHostController) {
    val repo = container.repository
    val islandFlow = remember { repo.observeIsland() }
    val island by islandFlow.collectAsStateWithLifecycle(null)
    val settings by container.settings.flow.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lastMood by remember { mutableStateOf(Mood.HAPPY) }
    val card = island?.card
    val visible = island != null && card != null && settings != null
    // Новая карточка (после ответа здесь или в другом месте) — остров сворачивается.
    LaunchedEffect(card?.id) { expanded = false }
    LaunchedEffect(visible) { if (!visible) expanded = false }

    val animate = settings?.animations != false
    val spec = tween<Float>(if (animate) 380 else 0, easing = Emphasized)
    val t by animateFloatAsState(if (expanded) 1f else 0f, spec, label = "island-expand")
    val progress by animateFloatAsState(
        island?.session?.let { if (it.total == 0) 0f else it.done.toFloat() / it.total } ?: 0f,
        tween(if (animate) 420 else 0, easing = Emphasized), label = "island-progress",
    )
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val width by animateDpAsState(if (expanded) minOf(360.dp, screenW - 16.dp) else 230.dp,
        tween(if (animate) 380 else 0, easing = Emphasized), label = "island-width")

    // Тап вне раскрытой карточки — свернуть (прозрачный слой под карточкой ловит касание).
    if (visible && expanded) {
        Box(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { expanded = false }
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
        enter = fadeIn(tween(260)) + scaleIn(tween(260, easing = Emphasized), initialScale = 0.85f),
        exit = fadeOut(tween(260)) + scaleOut(tween(260, easing = Emphasized), targetScale = 0.85f),
    ) {
        val s = island ?: return@AnimatedVisibility
        val c = card ?: return@AnimatedVisibility
        val st = settings ?: return@AnimatedVisibility
        val creature = Creatures.byId(st.favoriteCreature) ?: Creatures.all.first()
        val shape = RoundedCornerShape((19 + 11 * t).dp)
        Column(
            Modifier
                .width(width)
                .shadow(12.dp, shape)
                .clip(shape)
                .background(Color(0xFA121118))
                // Только тап и вертикальный свайп; сам элемент никуда не сдвигается.
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -24.dp.toPx()) expanded = false
                            else if (total > 24.dp.toPx() && st.islandAnswers) expanded = true
                        },
                    ) { change, dy -> change.consume(); total += dy }
                }
                .padding(horizontal = (5 + 9 * t).dp, vertical = (5 + 7 * t).dp)
                .semantics {
                    contentDescription = if (expanded) "Вопрос: ${c.displayTerm}. Свайп вверх — свернуть"
                    else "Остров занятия: ${s.course.name}, ${s.session.done} из ${s.session.total}. Нажмите, чтобы ответить"
                },
        ) {
            // Шапка — общая для пилюли и карточки.
            Row(
                Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                    if (expanded || !st.islandAnswers) nav.navigate(Routes.review(s.course.id)) { launchSingleTop = true }
                    else expanded = true
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CreatureArt(creature, 1, size = (28 + 12 * t).dp, mood = lastMood, animate = st.animations)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.course.flag} ${s.course.name}", color = Color.White, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f))
                        Text("${s.session.done}/${s.session.total}", color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(Color(creature.colorB)))
                    }
                }
                Spacer(Modifier.width(4.dp))
            }
            // Содержимое карточки появляется во второй половине раскрытия.
            if (t > 0.01f) {
                val a = ((t - 0.45f) / 0.55f).coerceIn(0f, 1f)
                Column(Modifier.graphicsLayer { alpha = a }.height((150 * t).dp)) {
                    Spacer(Modifier.height(12.dp))
                    Text(c.displayTerm, color = Color.White, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Вспомните перевод · нажмите на шапку, чтобы открыть занятие", color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(Answer.FORGOT to Color(0xFFFF6B6B), Answer.REMEMBERED to Color(0xFF2FBF71)).forEach { (ans, col) ->
                            IslandButton(ans.labelRu, col, Modifier.weight(1f), enabled = expanded) {
                                if (busy) return@IslandButton
                                busy = true
                                expanded = false
                                scope.launch {
                                    try {
                                        repo.answer(s.session.id, c.id, ans)
                                        lastMood = if (ans == Answer.REMEMBERED) Mood.CHEER else Mood.SUPPORT
                                    } finally { busy = false }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandButton(text: String, color: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp)).background(color)
            .clickable(enabled = enabled, onClick = onClick).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold) }
}
