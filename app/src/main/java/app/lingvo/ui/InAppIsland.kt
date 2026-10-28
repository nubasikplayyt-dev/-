package app.lingvo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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

/**
 * Остров внутри приложения: пока идёт занятие, внизу любого экрана (кроме самого занятия, над панелью вкладок)
 * висит тёмная капсула — существо, текущее слово и прогресс. Нажатие раскрывает её: «Показать перевод»,
 * затем «Не помню» / «Помню». Ответ сохраняется тем же кодом, что и на экране занятия,
 * поэтому остров, уведомление и экран повторения всегда показывают одну и ту же карточку.
 */
@Composable
fun InAppIsland(container: AppContainer, nav: NavHostController, modifier: Modifier = Modifier) {
    val repo = container.repository
    val islandFlow = remember { repo.observeIsland() }
    val island by islandFlow.collectAsStateWithLifecycle(null)
    val settings by container.settings.flow.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lastMood by remember { mutableStateOf(Mood.HAPPY) }
    val card = island?.card
    LaunchedEffect(card?.id) { revealed = false }

    AnimatedVisibility(
        visible = island != null && card != null && settings != null,
        modifier = modifier.padding(vertical = 6.dp),
        enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
    ) {
        val s = island ?: return@AnimatedVisibility
        val c = card ?: return@AnimatedVisibility
        val st = settings ?: return@AnimatedVisibility
        val creature = Creatures.byId(st.favoriteCreature) ?: Creatures.all.first()
        Column(
            Modifier
                .animateContentSize()
                .widthIn(max = if (expanded) 360.dp else 240.dp)
                .shadow(12.dp, RoundedCornerShape(if (expanded) 28.dp else 50.dp))
                .clip(RoundedCornerShape(if (expanded) 28.dp else 50.dp))
                .background(Color(0xF2141320))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { contentDescription = "Остров занятия: ${c.displayTerm}, ${s.session.done} из ${s.session.total}" },
        ) {
            Row(
                Modifier.clickable { if (expanded) nav.navigate(Routes.review(s.course.id)) { launchSingleTop = true } else expanded = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CreatureArt(creature, 1, size = 36.dp, mood = lastMood, animate = st.animations)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(c.displayTerm, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                    Text("${s.course.flag} ${s.session.done}/${s.session.total}" + if (expanded) " · открыть занятие" else "",
                        color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                if (expanded) Text("Свернуть", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { expanded = false }.padding(8.dp))
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                if (!revealed) {
                    IslandButton("Показать перевод", Color(0xFF5B3FD1), Modifier.fillMaxWidth()) { revealed = true }
                } else {
                    Text(c.translation, color = Color.White, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Answer.FORGOT to Color(0xFFFF6B6B), Answer.REMEMBERED to Color(0xFF2FBF71)).forEach { (a, col) ->
                            IslandButton(a.labelRu, col, Modifier.weight(1f)) {
                                if (busy) return@IslandButton
                                busy = true
                                scope.launch {
                                    try {
                                        repo.answer(s.session.id, c.id, a)
                                        lastMood = if (a == Answer.REMEMBERED) Mood.CHEER else Mood.SUPPORT
                                    } finally { busy = false }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun IslandButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(18.dp)).background(color).clickable(onClick = onClick).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold) }
}
