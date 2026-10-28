package app.lingvo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lingvo.core.Fsrs
import app.lingvo.core.LearningStatus
import app.lingvo.core.SessionText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
        },
        actions = { actions() },
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        action()
    }
}

@Composable
fun StatusBadge(status: LearningStatus) {
    val color = when (status) {
        LearningStatus.NEW -> MaterialTheme.colorScheme.outline
        LearningStatus.LEARNING -> Color(0xFFFB8C00)
        LearningStatus.REVIEW -> Color(0xFF1E88E5)
        LearningStatus.WELL_KNOWN -> Color(0xFF43A047)
        LearningStatus.MASTERED -> Color(0xFF2E7D32)
        LearningStatus.SUSPENDED -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(
            status.labelRu,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
fun ChipRow(items: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
    }
}

/** Человекочитаемый интервал: «1 мин», «10 мин», «3 дн.», «2 мес.». */
fun formatInterval(ms: Long): String {
    val minutes = ms / Fsrs.MIN_MS
    val days = ms.toDouble() / Fsrs.DAY_MS
    return when {
        minutes < 60 -> "${minutes.coerceAtLeast(1)} мин"
        days < 1 -> "${minutes / 60} ч"
        days < 31 -> "${days.toLong()} ${SessionText.plural(days.toLong().toInt(), "день", "дня", "дней")}"
        days < 365 -> "${(days / 30.4).toLong()} мес."
        else -> "%.1f г.".format(days / 365)
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun FullWidth(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) { content() }
}
