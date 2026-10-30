package app.investwatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.investwatch.core.DividendYear
import app.investwatch.core.Format
import app.investwatch.core.PriceCandle
import kotlin.math.roundToInt

/**
 * Графики нарисованы на Canvas вручную.
 *
 * Готовые библиотеки графиков сейчас собраны под Compose 1.10+, который
 * требует Android Gradle plugin 9.1 и compileSdk 37; ради двух простых
 * графиков тащить за собой смену тулчейна не стоит.
 */

private const val CHART_HEIGHT = 200

/**
 * График цены — линия, не свечи. Зелёная, если за период цена выросла,
 * красная — если упала. Касание показывает дату и цену точки.
 */
@Composable
fun PriceChart(
    candles: List<PriceCandle>,
    currency: String?,
    changePct: Double?,
    modifier: Modifier = Modifier,
) {
    if (candles.size < 2) {
        Box(
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT.dp),
            contentAlignment = Alignment.Center,
        ) {
            Hint("Данных для графика пока нет")
        }
        return
    }

    var touched by remember(candles) { mutableStateOf<Int?>(null) }
    val lineColor = StatusColors.change(changePct)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val values = remember(candles) { candles.map { it.close } }
    val low = remember(values) { values.min() }
    val high = remember(values) { values.max() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val shown = touched?.let { candles.getOrNull(it) }
        Text(
            text = if (shown != null) {
                "${Format.date(shown.date)} · ${Format.money(shown.close, currency)}"
            } else {
                "Коснитесь графика, чтобы увидеть дату и цену"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (shown != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (shown != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT.dp)
                .trackTouch(candles.size) { touched = it },
        ) {
            drawHorizontalGrid(gridColor)

            val path = Path()
            candles.forEachIndexed { index, candle ->
                val point = pointAt(index, candles.size, candle.close, low, high)
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))

            touched?.let { index ->
                val candle = candles.getOrNull(index) ?: return@let
                val point = pointAt(index, candles.size, candle.close, low, high)
                drawLine(
                    color = gridColor,
                    start = Offset(point.x, 0f),
                    end = Offset(point.x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = point)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Hint(Format.date(candles.first().date))
            Hint(Format.date(candles.last().date))
        }
    }
}

/** Столбчатая диаграмма сумм дивидендов по годам. */
@Composable
fun DividendYearsChart(
    years: List<DividendYear>,
    currency: String?,
    modifier: Modifier = Modifier,
) {
    if (years.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(CHART_HEIGHT.dp),
            contentAlignment = Alignment.Center,
        ) {
            Hint("Выплат за последние годы не записано")
        }
        return
    }

    var touched by remember(years) { mutableStateOf<Int?>(null) }
    val barColor = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val high = remember(years) { years.maxOf { it.total }.coerceAtLeast(0.0001) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val shown = touched?.let { years.getOrNull(it) }
        Text(
            text = if (shown != null) {
                "${shown.year} · ${Format.money(shown.total, currency)}"
            } else {
                "Коснитесь столбца, чтобы увидеть сумму за год"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (shown != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (shown != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT.dp)
                .trackTouch(years.size) { touched = it },
        ) {
            drawHorizontalGrid(gridColor)

            val slot = size.width / years.size
            val barWidth = slot * 0.6f
            years.forEachIndexed { index, year ->
                val barHeight = (year.total / high * (size.height - VERTICAL_PADDING)).toFloat()
                val left = slot * index + (slot - barWidth) / 2f
                drawRect(
                    color = if (index == touched) highlight else barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Hint(years.first().year.toString())
            Hint(years.last().year.toString())
        }
    }
}

// ────────────────────────────── общая механика ───────────────────────────────

private const val VERTICAL_PADDING = 16f

/** Палец ведёт по графику — отдаём индекс ближайшей точки, отпустил — null. */
private fun Modifier.trackTouch(count: Int, onIndex: (Int?) -> Unit): Modifier =
    pointerInput(count) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.any { it.pressed }
                if (!pressed) {
                    onIndex(null)
                    continue
                }
                val x = event.changes.firstOrNull()?.position?.x ?: continue
                val width = size.width.toFloat().coerceAtLeast(1f)
                val index = (x / width * (count - 1)).roundToInt().coerceIn(0, count - 1)
                onIndex(index)
                event.changes.forEach { it.consume() }
            }
        }
    }

private fun DrawScope.drawHorizontalGrid(color: Color) {
    val lines = 4
    repeat(lines + 1) { line ->
        val y = size.height / lines * line
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.pointAt(
    index: Int,
    count: Int,
    value: Double,
    low: Double,
    high: Double,
): Offset {
    val x = if (count <= 1) 0f else size.width / (count - 1) * index
    val span = (high - low).takeIf { it > 0 } ?: 1.0
    val usable = size.height - VERTICAL_PADDING * 2
    val y = (VERTICAL_PADDING + usable * (1 - (value - low) / span)).toFloat()
    return Offset(x, y)
}
