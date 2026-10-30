package app.portfolio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.portfolio.core.Asset
import app.portfolio.core.Criterion
import app.portfolio.core.Metrics
import app.portfolio.core.RepoConfig
import app.portfolio.core.Verdict
import coil.compose.AsyncImage

/**
 * Логотип бумаги. Путь берётся из поля `logo` в watchlist.json; если файла
 * нет или он не загрузился — круг с первыми буквами тикера, цвет по хэшу.
 */
@Composable
fun Logo(asset: Asset, config: RepoConfig, size: Dp = 40.dp) {
    var broken by remember(asset.id) { mutableStateOf(false) }
    val logo = asset.logo

    if (logo != null && !broken) {
        val url = if (logo.startsWith("http")) logo else config.url(logo)
        AsyncImage(
            model = url,
            contentDescription = asset.displayTicker,
            contentScale = ContentScale.Crop,
            onError = { broken = true },
            modifier = Modifier.size(size).clip(CircleShape),
        )
        return
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(StatusColors.logo(asset.displayTicker)),
    ) {
        Text(
            text = asset.initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 3).sp,
        )
    }
}

@Composable
fun VerdictBadge(verdict: Verdict?) {
    val color = if (verdict == null) {
        MaterialTheme.colorScheme.outline
    } else {
        StatusColors.verdict(verdict)
    }

    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(
            text = verdict?.label ?: "Нет оценки",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Небольшая плашка: подпись слева, значение справа. */
@Composable
fun InfoChip(label: String?, value: String, valueColor: Color? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Критерии, которые затронуло событие. */
@Composable
fun CriterionChip(criterion: Criterion) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            Text(
                text = criterion.code,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = criterion.state.sign,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = StatusColors.criterion(criterion.state),
            )
            if (criterion.note.isNotBlank()) {
                Text(
                    text = criterion.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MetricChip(metric: app.portfolio.core.Metric) {
    val shown = Metrics.describe(metric)
    InfoChip(label = shown.label, value = shown.value)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun Hint(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
