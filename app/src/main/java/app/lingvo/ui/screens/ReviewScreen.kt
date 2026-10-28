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
import app.lingvo.core.AnswerCheck
import app.lingvo.core.AnswerChecker
import app.lingvo.core.AutoResult
import app.lingvo.core.DiffKind
import app.lingvo.core.ReviewMode
import app.lingvo.core.TaskType
import app.lingvo.data.AnswerMeta
import app.lingvo.data.SessionStatus
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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
        if (session.status == SessionStatus.ENDED) { ui = ReviewUi.Empty(repo.nextDue(spec.code)); return }
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

    // Синхронизация с островом: ответ из уведомления, капсулы или острова в приложении сразу
    // меняет карточку и здесь. Свои ответы экран применяет сам (после короткой анимации).
    val sessionId = (ui as? ReviewUi.Active)?.session?.id
    LaunchedEffect(sessionId) {
        if (sessionId == null) return@LaunchedEffect
        repo.observeSession(sessionId).collect { s ->
            if (busy) return@collect
            val current = ui as? ReviewUi.Active ?: return@collect
            val head = s?.let { SessionProgress.decodeQueue(it.queue).firstOrNull() }
            if (s == null || s.status != SessionStatus.ACTIVE || head != current.card.id) {
                if (s != null && s.done > current.session.done) feedback = null
                load(s)
            }
        }
    }

    fun answer(active: ReviewUi.Active, answer: Answer, meta: AnswerMeta = AnswerMeta()) {
        if (busy) return
        busy = true
        haptic.performHapticFeedback(if (answer == Answer.REMEMBERED) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
        scope.launch {
            try {
                val wasCompleted = lastDay?.completed ?: appState?.streak?.todayCompleted ?: false
                val result = repo.answer(active.session.id, active.card.id, answer, meta)
                if (result == null) {
                    // Эту карточку уже ответили в другом месте (остров) — просто показываем актуальную.
                    load(repo.session(active.session.id)); return@launch
                }
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

    val reviewMode = settings.lang(spec.code).reviewMode
    fun setMode(m: ReviewMode) {
        scope.launch { container.settings.update { st -> st.withLang(spec.code) { it.copy(reviewMode = m) } } }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
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
                        ReviewMode.entries.forEach { m ->
                            DropdownMenuItem(
                                text = { Text((if (m == reviewMode) "✓ " else "    ") + m.labelRu) },
                                onClick = { menu = false; setMode(m) },
                            )
                        }
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
                is ReviewUi.Active -> if (reviewMode.taskFor(state.card.id, state.card.state.reps) == TaskType.TYPING) TypingCard(
                    spec = spec,
                    state = state,
                    busy = busy,
                    onSpeak = { container.speaker.speak(state.card.entry.displayTerm, spec)?.let(toast) },
                    onSwitchToButtons = { setMode(ReviewMode.BUTTONS) },
                    onConfirm = { check, correct, saveAlternative ->
                        scope.launch {
                            if (correct && saveAlternative && check.typed.isNotBlank()) repo.addAlternative(state.card.id, check.typed)
                            answer(state, AnswerChecker.answerFor(correct),
                                AnswerMeta(TaskType.TYPING, check.typed, check.result, confirmed = correct))
                        }
                    },
                ) else ActiveCard(
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
        if (creature != null) CreatureArt(creature, 1, size = 30.dp, mood = when (feedback) {
            Answer.FORGOT -> Mood.SUPPORT
            Answer.REMEMBERED -> Mood.CHEER
            null -> Mood.HAPPY
        }, animate = animate)
        else Text(spec.flag, Modifier.padding(horizontal = 6.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${spec.flag} $done/$total", color = Color.White, style = MaterialTheme.typography.labelLarge)
                if (streak > 0) Text("  🌍$streak", color = Color(0xFFFFD166), style = MaterialTheme.typography.labelLarge)
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
            Text("День засчитан! Серия $streak 🌍", style = MaterialTheme.typography.titleLarge, color = Color(0xFFE8890C))
        }
        Text("Прогресс сохранён.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigActionButton("Готово", LocalGradients.current.mint, onClick = onDone)
    }
}

/**
 * Письменный режим: показан перевод, пользователь пишет слово и нажимает «Проверить».
 * Автопроверка — только подсказка (пробелы, регистр там, где он не важен, сохранённые варианты;
 * диакритика не игнорируется). Окончательно решает пользователь: «Ответ правильный» / «Ответ неправильный».
 * Набранный текст и этап проверки переживают сворачивание и поворот экрана (rememberSaveable).
 */
@Composable
private fun TypingCard(
    spec: LanguageSpec,
    state: ReviewUi.Active,
    busy: Boolean,
    onSpeak: () -> Unit,
    onSwitchToButtons: () -> Unit,
    onConfirm: (AnswerCheck, Boolean, Boolean) -> Unit,
) {
    val entry = state.card.entry
    var typed by rememberSaveable(state.card.id) { mutableStateOf("") }
    var checked by rememberSaveable(state.card.id) { mutableStateOf(false) }
    var saveAlt by rememberSaveable(state.card.id) { mutableStateOf(false) }
    val check = remember(checked, typed, state.card.id) { if (checked) AnswerChecker.check(typed, entry, spec) else null }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.card.id, checked) { if (!checked) runCatching { focus.requestFocus() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(26.dp), spotColor = Color(0x333D5AFE))
                .clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Напишите на языке «${spec.nameRu}»", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(entry.translation, style = CardTermStyle)
            if (spec.articles.isNotEmpty() && entry.article.isNotEmpty() && !checked) {
                Text("Вместе с артиклем.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (check == null) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Ваш ответ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { checked = true }),
                textStyle = MaterialTheme.typography.titleLarge,
            )
            BigActionButton("Проверить", LocalGradients.current.hero, icon = Icons.Filled.Check, enabled = !busy) { checked = true }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { typed = ""; checked = true }) { Text("Не знаю — показать") }
                TextButton(onClick = onSwitchToButtons) { Text("К кнопкам «Помню»") }
            }
        } else {
            ConfirmCard(check, entry, saveAlt, { saveAlt = it }, onSpeak)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ConfirmButton("Ответ неправильный", false, !busy, Modifier.weight(1f)) { onConfirm(check, false, false) }
                ConfirmButton("Ответ правильный", true, !busy, Modifier.weight(1f)) { onConfirm(check, true, saveAlt) }
            }
            if (!check.result.looksCorrect) {
                Text(
                    "Решаете вы: если ваш вариант тоже верен — нажмите «Ответ правильный».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ConfirmCard(check: AnswerCheck, entry: app.lingvo.core.VocabEntry, saveAlt: Boolean, onSaveAlt: (Boolean) -> Unit, onSpeak: () -> Unit) {
    val ok = check.result.looksCorrect
    val tone = if (ok) Color(0xFF1FB87F) else if (check.result == AutoResult.WRONG || check.result == AutoResult.EMPTY) Color(0xFFFF8A5B) else Color(0xFFE8A20C)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .border(2.dp, tone.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(check.result.labelRu, style = MaterialTheme.typography.titleMedium, color = tone)
        Text("Твой ответ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (check.typed.isEmpty()) Text("—", style = MaterialTheme.typography.titleLarge)
        else Text(
            buildAnnotatedString {
                check.diff.forEach { part ->
                    when (part.kind) {
                        DiffKind.SAME -> append(part.text)
                        DiffKind.WRONG -> withStyle(SpanStyle(color = Color(0xFFE8590C), textDecoration = TextDecoration.Underline)) { append(part.text) }
                        DiffKind.EXTRA -> withStyle(SpanStyle(color = Color(0xFFE8590C), textDecoration = TextDecoration.LineThrough)) { append(part.text) }
                        DiffKind.MISSING -> withStyle(SpanStyle(color = Color(0xFF1FB87F), fontWeight = FontWeight.Bold)) { append(part.text) }
                    }
                }
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { contentDescription = "Ваш ответ: ${check.typed}" },
        )
        Text("Ожидаемый вариант", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TermHeader(entry, onSpeak)
        if (entry.transcription.isNotEmpty()) Text(entry.transcription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entry.alternatives.isNotEmpty()) {
            Text("Тоже подходит: " + entry.alternatives.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok && check.typed.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onSaveAlt(!saveAlt) }) {
                Checkbox(saveAlt, onSaveAlt)
                Text("Мой вариант тоже верный — запомнить его", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ConfirmButton(text: String, correct: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val brush = if (correct) Brush.linearGradient(listOf(Color(0xFF3DD68C), Color(0xFF1FB87F)))
    else Brush.linearGradient(listOf(Color(0xFFFF8A7A), Color(0xFFFF5A5F)))
    Box(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(22.dp)).background(brush).clickable(enabled = enabled, onClick = onClick).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (correct) Icons.Filled.Check else Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
