package app.portfolio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.portfolio.core.Asset
import app.portfolio.core.Dates
import app.portfolio.core.Flags
import app.portfolio.core.Importance
import app.portfolio.core.MonitorEvent
import app.portfolio.core.RepoConfig
import app.portfolio.core.Section
import app.portfolio.core.Status
import app.portfolio.data.PortfolioData

/**
 * Главный экран: лента «Важное» и разделы по странам с карточками бумаг.
 * Разделы и бумаги берутся из watchlist.json — код о них ничего не знает.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    data: PortfolioData,
    config: RepoConfig,
    contentPadding: PaddingValues,
    hasUnread: (String) -> Boolean,
    onOpenAsset: (String) -> Unit,
) {
    val important = data.feed.filter {
        it.importance == Importance.CRITICAL || it.importance == Importance.HIGH
    }.take(5)

    val titleById = data.assets.associate { (_, asset) -> asset.id to asset.displayTicker }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        if (important.isNotEmpty()) {
            item { SectionTitle("Важное") }
            item {
                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                        important.forEachIndexed { index, event ->
                            FeedRow(
                                event = event,
                                assetTitle = titleById[event.assetId] ?: event.assetId,
                                onClick = { onOpenAsset(event.assetId) },
                            )
                            if (index != important.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }

        for (section in data.watchlist.sections) {
            item(key = "section-${section.id}") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(Flags.of(section.country), style = MaterialTheme.typography.titleLarge)
                    SectionTitle(section.title)
                }
            }

            if (section.assets.isEmpty()) {
                item(key = "empty-${section.id}") {
                    Card { Box(Modifier.padding(14.dp)) { Hint("В разделе пока нет бумаг.") } }
                }
            }

            items(section.assets, key = { "asset-${it.id}" }) { asset ->
                AssetCard(
                    asset = asset,
                    section = section,
                    status = data.statuses[asset.id],
                    config = config,
                    unread = hasUnread(asset.id),
                    onClick = { onOpenAsset(asset.id) },
                )
            }
        }
    }
}

@Composable
private fun FeedRow(event: MonitorEvent, assetTitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickableRow(onClick)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(event.importance.mark, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "$assetTitle · ${Dates.format(event.date)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AssetCard(
    asset: Asset,
    section: Section,
    status: Status?,
    config: RepoConfig,
    unread: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickableRow(onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Logo(asset = asset, config = config)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = asset.displayTicker,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (unread) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                        )
                    }
                }
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (section.currency != null) {
                    Text(
                        text = "Обновлено ${Dates.format(status?.updated)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            VerdictBadge(status?.verdict)
        }
    }
}
