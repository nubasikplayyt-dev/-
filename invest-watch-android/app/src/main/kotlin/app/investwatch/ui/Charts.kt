package app.investwatch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.investwatch.core.DividendYear
import app.investwatch.core.Format
import app.investwatch.core.PriceCandle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

/**
 * График цены — линия, не свечи.
 *
 * Цвет линии зелёный, если за выбранный период цена выросла, и красный,
 * если упала. Касание показывает дату и цену этой точки.
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

    val lineColor = StatusColors.change(changePct)
    val producer = remember { CartesianChartModelProducer() }

    LaunchedEffect(candles) {
        producer.runTransaction {
            lineSeries { series(candles.indices.map { it.toDouble() }, candles.map { it.close }) }
        }
    }

    val dateFormatter = CartesianValueFormatter { _, value, _ ->
        candles.getOrNull(value.toInt())?.date?.let { Format.date(it) }.orEmpty()
    }

    // Подпись при касании: дата и цена именно этой точки.
    val markerFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
        val candle = targets.firstOrNull()?.x?.toInt()?.let { candles.getOrNull(it) }
        if (candle == null) "" else "${Format.date(candle.date)} · ${Format.money(candle.close, currency)}"
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        LineCartesianLayer.LineFill.single(Fill(lineColor)),
                    ),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFormatter),
            marker = rememberDefaultCartesianMarker(
                label = rememberTextComponent(),
                valueFormatter = markerFormatter,
            ),
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT.dp),
    )
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

    val columnColor: Color = MaterialTheme.colorScheme.primary
    val producer = remember { CartesianChartModelProducer() }

    LaunchedEffect(years) {
        producer.runTransaction {
            columnSeries { series(years.indices.map { it.toDouble() }, years.map { it.total }) }
        }
    }

    val yearFormatter = CartesianValueFormatter { _, value, _ ->
        years.getOrNull(value.toInt())?.year?.toString().orEmpty()
    }

    val markerFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
        val year = targets.firstOrNull()?.x?.toInt()?.let { years.getOrNull(it) }
        if (year == null) "" else "${year.year} · ${Format.money(year.total, currency)}"
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = Fill(columnColor), thickness = 14.dp),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = yearFormatter),
            marker = rememberDefaultCartesianMarker(
                label = rememberTextComponent(),
                valueFormatter = markerFormatter,
            ),
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT.dp),
    )
}

private const val CHART_HEIGHT = 220
