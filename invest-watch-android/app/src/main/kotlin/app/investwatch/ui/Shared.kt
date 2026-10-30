package app.investwatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.investwatch.core.Asset
import app.investwatch.core.EventTypes
import app.investwatch.core.Format
import app.investwatch.core.MonitorEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshColumn(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        content()
    }
}

/** Лента событий: дата, бумага, заголовок, сводка, метрики и источники. */
@Composable
fun EventFeed(
    events: List<MonitorEvent>,
    assets: Map<String, Asset>,
    onOpenAsset: (String) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        header?.let { item { it() } }

        if (events.isEmpty()) {
            item {
                Card { Column(Modifier.padding(14.dp)) { Hint("Событий пока нет.") } }
            }
        }

        items(events, key = { it.id }) { event ->
            EventCard(
                event = event,
                asset = assets[event.assetId],
                onOpenAsset = onOpenAsset,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventCard(
    event: MonitorEvent,
    asset: Asset?,
    onOpenAsset: ((String) -> Unit)? = null,
    showAsset: Boolean = true,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onOpenAsset != null) {
                    Modifier.clickable { onOpenAsset(event.assetId) }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(event.importance.mark, style = MaterialTheme.typography.labelMedium)
                Hint(
                    buildString {
                        append(Format.date(event.date))
                        append(" · ")
                        append(EventTypes.label(event.type))
                        if (showAsset) {
                            append(" · ")
                            append(asset?.displayTicker ?: event.assetId)
                        }
                        event.verdict?.let { append(" · ${it.label.lowercase()}") }
                    },
                )
            }

            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (event.summary.isNotBlank()) Hint(event.summary)

            if (event.criteria.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    event.criteria.forEach { CriterionChip(it) }
                }
            }

            if (event.metrics.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    event.metrics.forEach { MetricChip(it.name, it.value) }
                }
            }

            event.sources.forEach { source ->
                TextButton(
                    onClick = { openInBrowser(context, source.url) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("${source.title} ↗", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Источники открываются во вкладке браузера поверх приложения. */
fun openInBrowser(context: android.content.Context, url: String) {
    runCatching {
        androidx.browser.customtabs.CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, url.toUri())
    }.onFailure {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
