package app.lingvo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.lingvo.AppContainer
import app.lingvo.core.Languages
import app.lingvo.ui.Routes
import app.lingvo.ui.components.BackTopBar
import app.lingvo.ui.components.BarChart
import app.lingvo.ui.components.Centered
import app.lingvo.ui.components.LabeledValue
import app.lingvo.ui.rememberOverviews
import app.lingvo.ui.theme.LangColors

@Composable
fun LanguageScreen(container: AppContainer, nav: NavHostController, lang: String) {
    val spec = Languages.byCode(lang) ?: Languages.SPANISH
    val overviews by rememberOverviews(container)
    Scaffold(topBar = { BackTopBar("${spec.flag} ${spec.nameRu}", onBack = { nav.popBackStack() }) }) { padding ->
        val o = overviews?.firstOrNull { it.spec.code == spec.code } ?: run { Centered { CircularProgressIndicator() }; return@Scaffold }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        LabeledValue("в словаре", "${o.total}")
                        LabeledValue("к повторению", "${o.due}")
                        LabeledValue("новых", "${o.newAvailable}")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        LabeledValue("выучено", "${o.learned}")
                        LabeledValue("сегодня", "${o.reviewsToday}")
                        LabeledValue("серия, дней", "${o.streak}")
                    }
                    Text("Неделя", style = MaterialTheme.typography.labelLarge)
                    BarChart(o.week, LangColors.of(spec.code))
                }
            }
            Button(onClick = { nav.navigate(Routes.review(spec.code)) }, enabled = o.due > 0, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.PlayArrow, if (o.due > 0) "Начать повторение (${o.due})" else "Сейчас нечего повторять")
            }
            FilledTonalButton(onClick = { nav.navigate(Routes.learn(spec.code)) }, enabled = o.newLeftToday > 0, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(
                    Icons.Filled.School,
                    when {
                        o.newLeftToday > 0 -> "Учить новые слова (${o.newLeftToday} из ${o.newLimit} на сегодня)"
                        o.newAvailable == 0 -> "Новых слов нет — добавьте или импортируйте"
                        else -> "Норма новых слов на сегодня выполнена"
                    },
                )
            }
            OutlinedButton(onClick = { nav.navigate(Routes.dictionary(spec.code)) }, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.AutoMirrored.Filled.MenuBook, "Мой словарь")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.navigate(Routes.add(spec.code)) }, modifier = Modifier.weight(1f)) {
                    ButtonContent(Icons.Filled.Add, "Слово")
                }
                OutlinedButton(onClick = { nav.navigate(Routes.bulk(spec.code)) }, modifier = Modifier.weight(1f)) {
                    ButtonContent(Icons.AutoMirrored.Filled.PlaylistAdd, "Списком")
                }
            }
            OutlinedButton(onClick = { nav.navigate(Routes.import(spec.code)) }, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.ImportExport, "Импорт и экспорт")
            }
        }
    }
}

@Composable
fun ButtonContent(icon: ImageVector, text: String) {
    Icon(icon, null, Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text)
}
