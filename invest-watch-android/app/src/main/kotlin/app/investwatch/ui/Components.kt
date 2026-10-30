package app.investwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.investwatch.core.Asset
import app.investwatch.core.AssetCard
import app.investwatch.core.Criterion
import app.investwatch.core.Format
import app.investwatch.core.Metrics
import app.investwatch.core.Verdict
import coil.compose.AsyncImage

/**
 * Картинка бумаги. Берём иконку сайта компании по домену из watchlist.json
 * через публичные сервисы без ключа; если не вышло — круг с буквами тикера,
 * цвет по хэшу. Поле `logo`, если появится, имеет приоритет над доменом.
 */
@Composable
fun Logo(asset: Asset, size: Dp = 40.dp) {
    val sources = remember(asset.id) { logoUrls(asset) }
    var attempt by remember(asset.id) { mutableIntStateOf(0) }

    if (attempt < sources.size) {
        AsyncImage(
            model = sources[attempt],
            contentDescription = asset.displayTicker,
            contentScale = ContentScale.Crop,
            onError = { attempt += 1 },
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
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

private fun logoUrls(asset: Asset): List<String> {
    val logo = asset.logo
    if (logo != null && logo.startsWith("http")) return listOf(logo)

    val domain = asset.domain?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    // Clearbit закрылся в конце 2025 года, поэтому берём два сервиса иконок
    // сайтов, которые работают без ключа, и пробуем по очереди.
    return listOf(
        "https://www.google.com/s2/favicons?domain=$domain&sz=128",
        "https://icons.duckduckgo.com/ip3/$domain.ico",
    )
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

@Composable
fun InfoChip(label: String?, value: String, valueColor: Color? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
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
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun MetricChip(name: String, value: String) {
    val shown = Metrics.describe(name, value)
    InfoChip(label = shown.label, value = shown.value)
}

/** Критерий, затронутый событием: код, значок статуса и пометка. */
@Composable
fun CriterionChip(criterion: Criterion) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
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
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * Карточка бумаги: картинка, тикер, название, флаг рынка, тип, вердикт,
 * цена с изменением за день и дивдоходность против порога рынка.
 */
@Composable
fun AssetCardView(
    card: AssetCard,
    modifier: Modifier = Modifier,
    showFavoriteToggle: Boolean = true,
    onToggleFavorite: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        ) {
            Logo(card.asset)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = card.asset.displayTicker,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    card.market?.let { Text(it.flag, style = MaterialTheme.typography.bodyMedium) }
                    if (card.hasUnread) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                        )
                    }
                }

                Text(
                    text = card.asset.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                card.kind?.let { Hint(it.title) }

                PriceLine(card)
                YieldLine(card)

                if (card.notChecked) Hint("Ещё не проверена")
            }

            Column(horizontalAlignment = Alignment.End) {
                VerdictBadge(card.verdict)
                if (showFavoriteToggle && onToggleFavorite != null) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (card.isFavorite) Icons.Filled.Star
                            else Icons.Outlined.StarBorder,
                            contentDescription = if (card.isFavorite) "Убрать из избранного"
                            else "В избранное",
                            tint = if (card.isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceLine(card: AssetCard) {
    val quote = card.quote ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = Format.money(quote.price, quote.currency ?: card.market?.currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        quote.changePct?.let {
            Text(
                text = Format.percent(it, withSign = true),
                style = MaterialTheme.typography.bodySmall,
                color = StatusColors.change(it),
            )
        }
    }
}

@Composable
private fun YieldLine(card: AssetCard) {
    val yieldPct = card.yieldPct ?: return
    val floor = card.market?.yieldFloorPct
    val text = if (floor == null) {
        Format.percent(yieldPct)
    } else {
        "${Format.percent(yieldPct)} / порог ${Format.percent(floor)}"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = when (card.passesYieldFloor) {
            true -> StatusColors.change(1.0)
            false -> StatusColors.change(-1.0)
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
