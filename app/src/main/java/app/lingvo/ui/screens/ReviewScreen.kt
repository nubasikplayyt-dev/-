package app.lingvo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.Answer
import app.lingvo.core.CreatureDef
import app.lingvo.core.DayRecord
import app.lingvo.core.LanguageSpec
import app.lingvo.core.SessionProgress
import app.lingvo.core.SessionText
import app.lingvo.data.Card
import app.lingvo.data.Direction
import app.lingvo.data.LearningSessionEntity
import app.lingvo.notify.Notifications
import app.lingvo.ui.Routes
import app.lingvo.ui.components.BigActionButton
import app.lingvo.ui.components.CardBody
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.CreatureArt
import app.lingvo.ui.components.EmptyState
import app.lingvo.ui.components.Mood
import app.lingvo.ui.components.TermHeader
import app.lingvo.ui.components.formatInterval
import app.lingvo.ui.rememberAppState
import app.lingvo.ui.rememberToast
import app.lingvo.ui.theme.CardTermStyle
import app.lingvo.ui.theme.LocalGradients
import app.lingvo.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed interface ReviewUi {
    data object Loading : ReviewUi
    data class Empty(val nextDue: Long?) : ReviewUi
    data class Active(val session: LearningSessionEntity, val card: Card, val intervals: Map<Answer, Long>) : ReviewUi
    data class Finished(val session: LearningSessionEntity, val day: DayRecord?) : ReviewUi
}

@Composable
fun ReviewScreen(container: AppContainer, nav: NavHostController, lang: String) {
    val appState by rememberAppState(container)
    val course = appState?.courses?.firstOrNull { it.id == lang }
    if (appState == null) { Centered { CircularProgressIndicator() }; return }
    if (course == null) { Centered { Text("Курс не найден") }; return }
    val spec = course.spec
    val repo = container.repository
    val settings = appState!!.settings
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var ui by remember { mutableStateOf<ReviewUi>(ReviewUi.Loading) }
    var revealed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Answer?>(null) }
    var lastDay by remember { mutableStateOf<DayRecord?>(null) }
    var dayJustCompleted by remember { mutableStateOf(false) }

    suspend fun load(session: LearningSessionEntity?) {
        revealed = false
        if (session == null) { ui = ReviewUi.Empty(repo.nextDue(spec.code)); return }
        if (session.done >= session.total || session.queue.isBlank()) { ui = ReviewUi.Finished(session, lastDay); return }
        val headId = SessionProgress.decodeQueue(session.queue).first()
        val card = repo.cardForSession(headId)
        if (card == null || card.state.suspended) {
            // Карточку удалили или приостановили во время занятия — очередь очищается от неё.
            load(repo.startOrResumeSession(spec.code)); return
        }
        ui = ReviewUi.Active(session, card, repo.previewIntervals(headId))
    }

    LaunchedEffect(spec.code) {
        container.speaker.warmUp()
        load(repo.startOrResumeSession(spec.code))
    }

    fun answer(active: ReviewUi.Active, answer: Answer) {
        if (busy) return
        busy = true
        haptic.performHapticFeedback(if (answer == Answer.REMEMBERED) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
        scope.launch {
            try {
                val wasCompleted = lastDay?.completed ?: appState?.streak?.todayCompleted ?: false
                val result = repo.answer(active.session.id, active.card.id, answer)
                lastDay = result.day
                if (!wasCompleted && result.day.completed) dayJustCompleted = true
                feedback = answer
                if (settings.animations) delay(380)
                feedback = null
                load(result.session)
                if (result.session.done >= result.session.total && repo.dueCount(spec.code) == 0) Notifications.cancelReminders(context)
            } catch (e: Exception) {
                toast("Не удалось сохранить ответ: ${e.message}")
            } finally { busy = false }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val s = (ui as? ReviewUi.Active)?.session ?: (ui as? ReviewUi.Finished)?.session
                    if (s != null) SessionIsland(
                        spec, s.done, s.total, appState!!.streak.current, appState!!.companion?.first, feedback, settings.animations,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = ui is ReviewUi.Active) { Icon(Icons.Filled.MoreVert, "Меню") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val active = ui as? ReviewUi.Active
                        DropdownMenuItem(text = { Text("Открыть карточку") }, enabled = active != null, onClick = {
                            menu = false; active?.let { nav.navigate(Routes.entry(it.card.id)) }
                        })
                        DropdownMenuItem(text = { Text("Приостановить карточку") }, enabled = active != null, onClick = {
                            menu = false
                            active?.let { a -> scope.launch { repo.setSuspended(a.card.id, true); load(repo.startOrResumeSession(spec.code)) } }
                        })
                        DropdownMenuItem(text = { Text("Завершить занятие") }, onClick = { menu = false; confirmEnd = true })
                    }
                }
            }

            when (val state = ui) {
                ReviewUi.Loading -> Centered { CircularProgressIndicator() }
                is ReviewUi.Empty -> EmptyState(
                    Icons.Filled.Check, "Всё повторено",
                    state.nextDue?.let { "Следующее повторение через ${formatInterval(it - System.currentTimeMillis())}." }
                        ?: "Карточек к повторению нет. Познакомьтесь с новыми словами — они сразу попадут в повторение.",
                ) {
                    BigActionButton("Учить новые слова", LocalGradients.current.sunrise) {
                        nav.navigate(Routes.learn(spec.code)) { popUpTo(Routes.REVIEW) { inclusive = true } }
                    }
                }
                is ReviewUi.Finished -> FinishedView(spec, state.session, appState!!.streak.current, dayJustCompleted, appState!!.companion?.let { it.first to it.second }) {
                    nav.popBackStack()
                }
                is ReviewUi.Active -> ActiveCard(
                    spec = spec,
                    state = state,
                    direction = settings.lang(spec.code).direction,
                    revealed = revealed,
                    busy = busy,
                    animations = settings.animations,
                    onReveal = {
                        revealed = true
                        if (settings.autoSpeak) container.speaker.speak(state.card.entry.displayTerm, spec)
                    },
                    onSpeak = { container.speaker.speak(state.card.entry.displayTerm, spec)?.let(toast) },
                    onAnswer = { answer(state, it) },
                )
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("Завершить занятие?") },
            text = { Text("Все ответы уже сохранены. Оставшиеся карточки никуда не пропадут и будут ждать следующего занятия.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    (ui as? ReviewUi.Active)?.let { a -> scope.launch { repo.endSession(a.session.id); nav.popBackStack() } }
                }) { Text("Завершить") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Продолжить") } },
        )
    }
}

/**
 * Компактный «остров» занятия: градиент, существо, курс, прогресс, серия и реакция на ответ.
 * Тот же стиль повторяют уведомление и плавающая капсула.
 */
@Composable
fun SessionIsland(
    spec: LanguageSpec, done: Int, total: Int, streak: Int, creature: CreatureDef?, feedback: Answer?, animate: Boolean,
) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    val shown by animateFloatAsState(fraction, if (animate) tween(450) else snap(), label = "island-progress")
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(feedback) {
        if (feedback != null && animate) { pulse.snapTo(1.08f); pulse.animateTo(1f, tween(300)) }
    }
    val brush = when (feedback) {
        Answer.REMEMBERED -> Brush.horizontalGradient(listOf(Color(0xFF1FB87F), Color(0xFF22C3A6)))
        Answer.FORGOT -> Brush.horizontalGradient(listOf(Color(0xFFFF6B5B), Color(0xFFFF8A5B)))
        null -> Brush.horizontalGradient(listOf(Color(0xFF2B2F6B), Color(0xFF5B3FD1), Color(0xFF2F6BFF)))
    }
    Row(
        Modifier
            .scale(pulse.value)
            .shadow(8.dp, RoundedCornerShape(50), spotColor = Color(0x665B3FD1))
            .clip(RoundedCornerShape(50))
            .background(brush)
            .padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp)
            .semantics { contentDescription = "${spec.nameRu}: пройдено $done из $total, серия $streak" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (creature != null) CreatureArt(creature, 1, size = 30.dp, mood = if (feedback == Answer.FORGOT) Mood.CALM else Mood.HAPPY, animate = animate)
        else Text(spec.flag, Modifier.padding(horizontal = 6.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${spec.flag} $done/$total", color = Color.White, style = MaterialTheme.typography.labelLarge)
                if (streak > 0) Text("  🔥$streak", color = Color(0xFFFFD166), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(3.dp))
            Box(Modifier.width(110.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(shown).height(4.dp).background(Brush.horizontalGradient(listOf(Color(0xFFFFE066), Color(0xFFFFB627)))))
            }
        }
    }
}

@Composable
private fun ActiveCard(
    spec: LanguageSpec,
    state: ReviewUi.Active,
    direction: Direction,
    revealed: Boolean,
    busy: Boolean,
    animations: Boolean,
    onReveal: () -> Unit,
    onSpeak: () -> Unit,
    onAnswer: (Answer) -> Unit,
) {
    val entry = state.card.entry
    val reverse = when (direction) {
        Direction.TARGET_TO_RU -> false
        Direction.RU_TO_TARGET -> true
        // Вперемешку: направление стабильно для карточки и меняется от повторения к повторению.
        Direction.MIXED -> abs(entry.id.hashCode() + state.card.state.reps) % 2 == 1
    }
    val flip = remember(state.card.id) { Animatable(0f) }
    LaunchedEffect(revealed, state.card.id) {
        if (revealed) { if (animations) flip.animateTo(1f, tween(320)) else flip.snapTo(1f) } else flip.snapTo(0f)
    }
    val enter = remember(state.card.id) { Animatable(if (animations) 0.92f else 1f) }
    LaunchedEffect(state.card.id) { if (animations) enter.animateTo(1f, tween(220)) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .scale(enter.value)
                .graphicsLayer {
                    // Небольшой «переворот» карточки при показе ответа.
                    rotationY = if (flip.value < 0.5f) flip.value * 30f else (1f - flip.value) * 30f
                    cameraDistance = 16 * density
                }
                .shadow(10.dp, RoundedCornerShape(30.dp), spotColor = Color(0x333D5AFE))
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surface)
                .let { if (!revealed) it.clickable(onClick = onReveal) else it },
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(Brush.horizontalGradient(listOf(Palette.course(spec.code), Palette.Violet))))
            Column(
                Modifier.fillMaxSize().padding(22.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (reverse) "Перевод → ${spec.nameRu.lowercase()}" else "${spec.nameRu} → перевод",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!reverse) {
                    TermHeader(entry, onSpeak)
                    if (!revealed) Hint("Вспомните перевод и нажмите «Показать ответ».") else CardBody(entry)
                } else {
                    Text(entry.translation, style = CardTermStyle)
                    if (!revealed) {
                        Hint(if (spec.articles.isNotEmpty() && entry.article.isNotEmpty()) "Вспомните слово вместе с артиклем."
                        else "Вспомните выражение на языке «${spec.nameRu}».")
                    } else {
                        TermHeader(entry, onSpeak)
                        CardBody(entry, showTranslation = false)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (!revealed) {
            BigActionButton("Показать ответ", LocalGradients.current.hero, icon = Icons.Filled.Visibility, enabled = !busy, onClick = onReveal)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AnswerButton(Answer.FORGOT, state.intervals[Answer.FORGOT], !busy, Modifier.weight(1f)) { onAnswer(Answer.FORGOT) }
                AnswerButton(Answer.REMEMBERED, state.intervals[Answer.REMEMBERED], !busy, Modifier.weight(1f)) { onAnswer(Answer.REMEMBERED) }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun AnswerButton(answer: Answer, interval: Long?, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val remembered = answer == Answer.REMEMBERED
    val brush = if (remembered) Brush.linearGradient(listOf(Color(0xFF3DD68C), Color(0xFF1FB87F)))
    else Brush.linearGradient(listOf(Color(0xFFFF8A7A), Color(0xFFFF5A5F)))
    Column(
        modifier
            .heightIn(min = 92.dp)
            .shadow(8.dp, RoundedCornerShape(26.dp), spotColor = if (remembered) Color(0x5522C38E) else Color(0x55FF6B5B))
            .clip(RoundedCornerShape(26.dp))
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp)
            .semantics { contentDescription = answer.labelRu + (interval?.let { ", следующее повторение через ${formatInterval(it)}" } ?: "") },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (remembered) Icons.Filled.Check else Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Text(answer.labelRu, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        interval?.let { Text("через ${formatInterval(it)}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(28.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FinishedView(
    spec: LanguageSpec, session: LearningSessionEntity, streak: Int, dayCompleted: Boolean,
    companion: Pair<CreatureDef, Int>?, onDone: () -> Unit,
) {
    val accuracy = if (session.done == 0) 0 else session.correct * 100 / session.done
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        if (companion != null) CreatureArt(companion.first, companion.second, size = 140.dp, mood = Mood.CHEER)
        else Text("🎉", style = MaterialTheme.typography.displayLarge)
        Text("Занятие завершено!", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "${spec.flag} ${session.done} ${SessionText.plural(session.done, "ответ", "ответа", "ответов")} · помню $accuracy%",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(dayCompleted, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Text("День засчитан! Серия $streak 🔥", style = MaterialTheme.typography.titleLarge, color = Color(0xFFE8890C))
        }
        Text("Прогресс сохранён.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigActionButton("Готово", LocalGradients.current.mint, onClick = onDone)
    }
}
