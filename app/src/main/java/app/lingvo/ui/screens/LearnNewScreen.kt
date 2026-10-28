package app.lingvo.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.data.Card
import app.lingvo.ui.Routes
import app.lingvo.ui.components.BackTopBar
import app.lingvo.ui.components.CardBody
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.EmptyState
import app.lingvo.ui.components.TermHeader
import app.lingvo.ui.rememberSettings
import app.lingvo.ui.rememberToast
import kotlinx.coroutines.launch

@Composable
fun LearnNewScreen(container: AppContainer, nav: NavHostController, lang: String) {
    val spec = app.lingvo.ui.rememberCourseSpec(container, lang) ?: run { Centered { CircularProgressIndicator() }; return }
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val settings by rememberSettings(container)
    var cards by remember { mutableStateOf<List<Card>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var introduced by remember { mutableIntStateOf(0) }

    LaunchedEffect(spec.code) {
        container.speaker.warmUp()
        cards = repo.newCardsForToday(spec).first
    }

    Scaffold(topBar = { BackTopBar("Новые слова · ${spec.flag} ${spec.nameRu}", onBack = { nav.popBackStack() }) }) { padding ->
        val list = cards ?: run { Centered { CircularProgressIndicator() }; return@Scaffold }
        if (list.isEmpty()) {
            EmptyState(
                Icons.Filled.School, "Новых слов на сегодня нет",
                "Норма выполнена или словарь пуст. Количество новых слов в день меняется в настройках.",
                Modifier.padding(padding),
            ) { OutlinedButton(onClick = { nav.navigate(Routes.add(spec.code)) }) { Text("Добавить слово") } }
            return@Scaffold
        }
        if (index >= list.size) {
            EmptyState(
                Icons.Filled.CheckCircle, "Готово!",
                "$introduced ${app.lingvo.core.SessionText.plural(introduced, "карточка добавлена", "карточки добавлены", "карточек добавлено")} в повторение. " +
                    "Сразу повторите их, чтобы закрепить — так они запомнятся лучше.",
                Modifier.padding(padding),
            ) {
                Button(onClick = { nav.navigate(Routes.review(spec.code)) { popUpTo(Routes.LEARN) { inclusive = true } } }) { Text("Повторить сейчас") }
            }
            return@Scaffold
        }
        val card = list[index]
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LinearProgressIndicator(progress = { index.toFloat() / list.size }, modifier = Modifier.fillMaxWidth())
            Text("${index + 1} из ${list.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = card,
                transitionSpec = { if (settings.animations) fadeIn() togetherWith fadeOut() else (fadeIn(snapSpec()) togetherWith fadeOut(snapSpec())) },
                modifier = Modifier.weight(1f),
                label = "learn-card",
            ) { c ->
                Card(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TermHeader(c.entry) { container.speaker.speak(c.entry.displayTerm, spec)?.let(toast) }
                        CardBody(c.entry)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Знакомство не означает, что слово выучено: оно сразу попадёт в интервальное повторение.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        repo.introduce(card.id)
                        introduced++
                        index++
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(if (index + 1 < list.size) "Ознакомился — дальше" else "Ознакомился — готово") }
        }
    }
}

fun <T> snapSpec() = androidx.compose.animation.core.snap<T>()
