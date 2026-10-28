package app.lingvo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lingvo.ui.DayCount
import java.time.format.TextStyle
import java.util.Locale

/** Столбики повторений по дням. Описание для TalkBack перечисляет значения. */
@Composable
fun BarChart(days: List<DayCount>, color: Color, height: Dp = 64.dp, showLabels: Boolean = true) {
    val max = (days.maxOfOrNull { it.reviews } ?: 0).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val description = days.joinToString { "${it.date.dayOfMonth}: ${it.reviews}" }
    Column {
        Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = "Повторения по дням: $description" }) {
            val gap = 4.dp.toPx()
            val w = (size.width - gap * (days.size - 1)) / days.size
            days.forEachIndexed { i, d ->
                val x = i * (w + gap)
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(4.dp.toPx()))
                val h = size.height * d.reviews / max
                if (h > 0) drawRoundRect(color, Offset(x, size.height - h), Size(w, h), CornerRadius(4.dp.toPx()))
            }
        }
        if (showLabels && days.size <= 7) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach {
                    Text(
                        it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ru")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Календарь активности: недели по столбцам, дни недели по строкам. */
@Composable
fun ActivityCalendar(days: List<DayCount>, color: Color) {
    val max = (days.maxOfOrNull { it.reviews } ?: 0).coerceAtLeast(1)
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val firstOffset = days.firstOrNull()?.date?.dayOfWeek?.value?.minus(1) ?: 0
    val weeks = (days.size + firstOffset + 6) / 7
    val active = days.count { it.reviews > 0 }
    Canvas(
        Modifier.fillMaxWidth().height(110.dp)
            .semantics { contentDescription = "Календарь активности: занятия в $active из ${days.size} дней" }
    ) {
        val gap = 3.dp.toPx()
        val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
        days.forEachIndexed { i, d ->
            val idx = i + firstOffset
            val col = idx / 7
            val row = idx % 7
            val c = if (d.reviews == 0) empty else color.copy(alpha = 0.3f + 0.7f * d.reviews / max)
            drawRoundRect(c, Offset(col * (cell + gap), row * (cell + gap)), Size(cell, cell), CornerRadius(3.dp.toPx()))
        }
    }
}
