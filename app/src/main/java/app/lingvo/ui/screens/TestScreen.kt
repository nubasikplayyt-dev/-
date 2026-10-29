package app.lingvo.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.Practice
import app.lingvo.core.PracticeState
import app.lingvo.core.SessionText
import app.lingvo.data.Card
import app.lingvo.ui.components.BackTopBar
import app.lingvo.ui.components.BigActionButton
import app.lingvo.ui.components.CardBody
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.CreatureArt
import app.lingvo.ui.components.EmptyState
import app.lingvo.ui.components.GradientBar
import app.lingvo.ui.components.Mood
import app.lingvo.ui.components.SoftCard
import app.lingvo.ui.components.TermHeader
import app.lingvo.ui.rememberAppState
import app.lingvo.ui.rememberCourseSpec
import app.lingvo.ui.rememberToast
import app.lingvo.ui.theme.LocalGradients
import app.lingvo.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Тест по всем пройденным словам курса, вперемешку. Запускается в любой момент сколько угодно раз.
 *
 * Это проверка себя: ответы **не меняют** расписание повторений, дневную цель и серию
 * (иначе каждый тест сдвигал бы интервалы). Слова с ошибками можно одной кнопкой
 * отправить в обычное повторение.
 */
@Composable
fun TestScreen(container: AppContainer, nav: NavHostController, lang: String) {
    val spec = rememberCourseSpec(container, lang) ?: run { Centered { CircularProgressIndicator() }; return }
    val appState by rememberAppState(container)
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val toast = rememberToast()

    var cards by remember(lang) { mutableStateOf<Map<String, Card>?>(null) }
    var state by rememberSaveable(lang, stateSaver = PracticeSaver) { mutableStateOf<PracticeState?>(null) }
    var revealed by rememberSaveable(lang) { mutableStateOf(false) }

    LaunchedEffect(lang) {
        val learned = repo.learnedCards(lang)
        cards = learned.associateBy { it.id }
        if (state == null) state = Practice.start(learned.map { it.id })
    }

    fun answer(correct: Boolean) {
        state = state?.let { Practice.answer(it, correct) }
        revealed = false
    }

    Scaffold(topBar = { BackTopBar("Тест · ${spec.flag} ${spec.nameRu}", onBack = { nav.popBackStack() }) }) { padding ->
        val all = cards
        val s = state
        if (all == null || s == null) { Centered { CircularProgressIndicator() }; return@Scaffold }

        if (s.total < Practice.MIN_WORDS) {
            EmptyState(
                Icons.Filled.Check, "Пока нечего проверять",
                "Тест идёт по словам, с которыми вы уже знакомились. Их нужно хотя бы ${Practice.MIN_WORDS}. " +
                    "Познакомьтесь с новыми словами — и возвращайтесь.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Прогресс теста
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${s.answered}/${s.total}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(10.dp))
                Box(Modifier.weight(1f)) { GradientBar(s.progress) }
                Spacer(Modifier.size(10.dp))
                Text("✓ ${s.correct}", style = MaterialTheme.typography.labelLarge, color = Palette.Mint)
            }
            Spacer(Modifier.height(12.dp))

            if (s.finished) {
                FinishedTest(container, s, appState?.companion?.first, onRetryWrong = {
                    state = Practice.retryWrong(s); revealed = false
                }, onRestart = {
                    state = Practice.start(all.keys.toList()); revealed = false
                }, onQueueWrong = {
                    scope.launch {
                        s.wrong.forEach { repo.queueWord(it) }
                        toast("Добавлено в повторение: ${s.wrong.size}")
                    }
                }, onDone = { nav.popBackStack() })
                return@Scaffold
            }

            val card = s.currentId?.let { all[it] }
            if (card == null) {
                // Слово удалили во время теста — просто идём дальше.
                LaunchedEffect(s.currentId) { answer(true) }
                Centered { CircularProgressIndicator() }
                return@Scaffold
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Вспомните перевод", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TermHeader(card.entry) { container.speaker.speak(card.entry.displayTerm, spec)?.let(toast) }
                if (revealed) CardBody(card.entry)
            }

            Spacer(Modifier.height(14.dp))
            if (!revealed) {
                BigActionButton("Показать ответ", LocalGradients.current.hero, icon = Icons.Filled.Visibility) {
                    revealed = true
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TestButton("Не помню", false, Modifier.weight(1f)) { answer(false) }
                    TestButton("Помню", true, Modifier.weight(1f)) { answer(true) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Ответы в тесте не меняют расписание повторений и не засчитываются в цель дня.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun FinishedTest(
    container: AppContainer,
    s: PracticeState,
    creature: app.lingvo.core.CreatureDef?,
    onRetryWrong: () -> Unit,
    onRestart: () -> Unit,
    onQueueWrong: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        if (creature != null) {
            CreatureArt(creature, 1, size = 120.dp, mood = if (s.wrong.isEmpty()) Mood.CHEER else Mood.HAPPY)
        }
        Text("Тест пройден", style = MaterialTheme.typography.headlineSmall)
        Text(Practice.summaryRu(s), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (s.wrong.isNotEmpty()) {
            SoftCard {
                Text("Ошибок: ${s.wrong.size} ${SessionText.plural(s.wrong.size, "слово", "слова", "слов")}",
                    style = MaterialTheme.typography.titleSmall)
                Text("Можно пройти только их или отправить в обычное повторение, чтобы они вернулись по расписанию.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BigActionButton("Повторить ошибки", LocalGradients.current.sunrise, onClick = onRetryWrong)
            TextButton(onClick = onQueueWrong) { Text("Отправить ошибки в повторение") }
        }
        BigActionButton("Пройти заново", LocalGradients.current.violet, onClick = onRestart)
        TextButton(onClick = onDone) { Text("Готово") }
    }
}

@Composable
private fun TestButton(text: String, correct: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val brush = if (correct) Brush.linearGradient(listOf(Color(0xFF3DD68C), Color(0xFF1FB87F)))
    else Brush.linearGradient(listOf(Color(0xFFFF8A7A), Color(0xFFFF5A5F)))
    Box(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(22.dp)).background(brush)
            .clickable(onClick = onClick).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (correct) Icons.Filled.Check else Icons.Filled.Close, null, tint = Color.White,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(6.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** Тест переживает поворот экрана и сворачивание приложения. */
private val PracticeSaver = androidx.compose.runtime.saveable.listSaver<PracticeState?, Any>(
    save = {
        if (it == null) emptyList() else listOf(
            it.queue.joinToString("\u0001"), it.index, it.correct, it.wrong.joinToString("\u0001"),
        )
    },
    restore = {
        if (it.isEmpty()) null else PracticeState(
            queue = (it[0] as String).split('\u0001').filter(String::isNotEmpty),
            index = it[1] as Int,
            correct = it[2] as Int,
            wrong = (it[3] as String).split('\u0001').filter(String::isNotEmpty),
        )
    },
)
