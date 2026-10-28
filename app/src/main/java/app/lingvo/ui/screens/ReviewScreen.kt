package app.lingvo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.LanguageSpec
import app.lingvo.core.Languages
import app.lingvo.core.Rating
import app.lingvo.core.SessionProgress
import app.lingvo.data.Card
import app.lingvo.data.Direction
import app.lingvo.data.LearningSessionEntity
import app.lingvo.notify.Notifications
import app.lingvo.ui.Routes
import app.lingvo.ui.components.CardBody
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.EmptyState
import app.lingvo.ui.components.TermHeader
import app.lingvo.ui.components.formatInterval
import app.lingvo.ui.rememberSettings
import app.lingvo.ui.rememberToast
import app.lingvo.ui.theme.CardTermStyle
import app.lingvo.ui.theme.LangColors
import kotlinx.coroutines.launch
import kotlin.math.abs

private sealed interface ReviewUi {
    data object Loading : ReviewUi
    data class Empty(val nextDue: Long?) : ReviewUi
    data class Active(val session: LearningSessionEntity, val card: Card, val intervals: Map<Rating, Long>) : ReviewUi
    data class Finished(val session: LearningSessionEntity) : ReviewUi
}

@Composable
fun ReviewScreen(container: AppContainer, nav: NavHostController, lang: String) {
    val spec = Languages.byCode(lang) ?: Languages.SPANISH
    val repo = container.repository
    val settings by rememberSettings(container)
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val context = androidx.compose.ui.platform.LocalContext.current

    var ui by remember { mutableStateOf<ReviewUi>(ReviewUi.Loading) }
    var revealed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    suspend fun load(session: LearningSessionEntity?) {
        revealed = false
        if (session == null) { ui = ReviewUi.Empty(repo.nextDue(spec.code)); return }
        if (session.done >= session.total || session.queue.isBlank()) { ui = ReviewUi.Finished(session); return }
        val headId = SessionProgress.decodeQueue(session.queue).first()
        val card = repo.cardForSession(headId)
        if (card == null || card.state.suspended) {
            // Карточку удалили или приостановили во время сессии — очередь очищается от неё.
            load(repo.startOrResumeSession(spec.code)); return
        }
        ui = ReviewUi.Active(session, card, repo.previewIntervals(headId))
    }

    LaunchedEffect(spec.code) {
        container.speaker.warmUp()
        load(repo.startOrResumeSession(spec.code))
    }

    fun answer(active: ReviewUi.Active, rating: Rating) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = repo.answer(active.session.id, active.card.id, rating)
                load(result.session)
                if (result.session.done >= result.session.total && repo.dueCount(spec.code) == 0) {
                    Notifications.cancelReminders(context)
                }
            } catch (e: Exception) {
                toast("Не удалось сохранить ответ: ${e.message}")
            } finally { busy = false }
        }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val s = (ui as? ReviewUi.Active)?.session ?: (ui as? ReviewUi.Finished)?.session
                    if (s != null) SessionIsland(spec, s.done, s.total, settings.animations)
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
                        DropdownMenuItem(text = { Text("Завершить сессию") }, onClick = { menu = false; confirmEnd = true })
                    }
                }
            }

            when (val state = ui) {
                ReviewUi.Loading -> Centered { CircularProgressIndicator() }
                is ReviewUi.Empty -> EmptyState(
                    Icons.Filled.CheckCircle, "Всё повторено",
                    state.nextDue?.let { "Следующее повторение через ${formatInterval(it - System.currentTimeMillis())}." }
                        ?: "Карточек к повторению нет. Познакомьтесь с новыми словами — они сразу попадут в повторение.",
                ) {
                    Button(onClick = { nav.navigate(Routes.learn(spec.code)) { popUpTo(Routes.REVIEW) { inclusive = true } } }) { Text("Учить новые слова") }
                }
                is ReviewUi.Finished -> FinishedView(spec, state.session) { nav.popBackStack() }
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
                    onRate = { answer(state, it) },
                )
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("Завершить сессию?") },
            text = { Text("Все ответы уже сохранены. Оставшиеся карточки никуда не пропадут и будут ждать следующего повторения.") },
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

/** Компактная «капсула» прогресса — тот же вид, что и в уведомлении/плавающей капсуле. */
@Composable
fun SessionIsland(spec: LanguageSpec, done: Int, total: Int, animate: Boolean) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    val shown by animateFloatAsState(fraction, if (animate) tween(400) else snap(), label = "island-progress")
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF111111))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = "${spec.nameRu}: пройдено $done из $total" },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${spec.flag} ${spec.nameRu} · $done/$total", color = Color.White, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(120.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(shown).height(3.dp).background(LangColors.of(spec.code)))
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
    onRate: (Rating) -> Unit,
) {
    val entry = state.card.entry
    val reverse = when (direction) {
        Direction.TARGET_TO_RU -> false
        Direction.RU_TO_TARGET -> true
        // Смешанный режим: направление стабильно для карточки и меняется от повторения к повторению.
        Direction.MIXED -> abs(entry.id.hashCode() + state.card.state.reps) % 2 == 1
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        AnimatedContent(
            targetState = state.card.id to revealed,
            transitionSpec = { if (animations) fadeIn(tween(200)) togetherWith fadeOut(tween(150)) else fadeIn(snap()) togetherWith fadeOut(snap()) },
            modifier = Modifier.weight(1f),
            label = "review-card",
        ) { (_, isRevealed) ->
            Card(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        if (reverse) "Русский → ${spec.nameRu.lowercase()}" else "${spec.nameRu} → русский",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!reverse) {
                        TermHeader(entry, onSpeak)
                        if (!isRevealed) Hint("Вспомните перевод, затем откройте ответ.")
                        else CardBody(entry)
                    } else {
                        Text(entry.translation, style = CardTermStyle)
                        if (!isRevealed) {
                            Hint(
                                if (spec.code == Languages.GERMAN.code && entry.article.isNotEmpty())
                                    "Вспомните слово по-немецки вместе с артиклем."
                                else "Вспомните выражение на языке «${spec.nameRu}»."
                            )
                        } else {
                            TermHeader(entry, onSpeak)
                            CardBody(entry, showTranslation = false)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AnimatedVisibility(!revealed) {
            Button(onClick = onReveal, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("Показать ответ") }
        }
        AnimatedVisibility(revealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Rating.entries.forEach { r ->
                    val colors = when (r) {
                        Rating.AGAIN -> ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)
                        Rating.HARD -> ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00), contentColor = Color.White)
                        Rating.GOOD -> ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C), contentColor = Color.White)
                        Rating.EASY -> ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2), contentColor = Color.White)
                    }
                    Button(
                        onClick = { onRate(r) },
                        enabled = !busy,
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(r.labelRu, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
                            state.intervals[r]?.let { Text(formatInterval(it), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FinishedView(spec: LanguageSpec, session: LearningSessionEntity, onDone: () -> Unit) {
    val accuracy = if (session.done == 0) 0 else session.correct * 100 / session.done
    EmptyState(
        Icons.Filled.Celebration, "Сессия завершена",
        "${spec.flag} ${spec.nameRu}: ${session.done} ответов, правильно вспомнено $accuracy%. Прогресс сохранён.",
    ) { OutlinedButton(onClick = onDone) { Text("Готово") } }
}
