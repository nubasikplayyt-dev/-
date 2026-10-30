package app.portfolio.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.portfolio.core.Asset
import app.portfolio.core.Commodities
import app.portfolio.core.Criterion
import app.portfolio.core.CriterionHelp
import app.portfolio.core.Dates
import app.portfolio.core.EventTypes
import app.portfolio.core.Flags
import app.portfolio.core.MonitorEvent
import app.portfolio.core.RepoConfig
import app.portfolio.core.Section
import app.portfolio.core.Status
import app.portfolio.data.PortfolioData

/**
 * Экран бумаги: шапка, таблица критериев с расшифровкой по нажатию,
 * «Что отслеживаем», лента событий с фильтром по типу и вкладка «Дивиденды».
 */
@Composable
fun AssetScreen(
    data: PortfolioData,
    assetId: String,
    config: RepoConfig,
    contentPadding: PaddingValues,
    muted: Boolean,
    onMutedChange: (Boolean) -> Unit,
    onSeen: () -> Unit,
) {
    val found = data.watchlist.find(assetId)
    LaunchedEffect(assetId) { onSeen() }

    if (found == null) {
        Box(Modifier.padding(contentPadding).padding(16.dp)) {
            Hint("Бумага $assetId не найдена в config/watchlist.json.")
        }
        return
    }

    val (section, asset) = found
    val status = data.statuses[assetId]
    val events = data.eventsOf(assetId)
    val dividends = events.filter { it.type == "dividend" }

    // Тип dividend не попадает в фильтры: под него есть отдельная вкладка.
    val types = remember(events) {
        events.map { it.type }.distinct().filter { it != "dividend" }
            .sortedBy { EventTypes.label(it) }
    }

    var tab by remember(assetId) { mutableStateOf<Tab>(Tab.AllEvents) }
    var openCriterion by remember(assetId) { mutableStateOf<String?>(null) }

    val visible = when (val current = tab) {
        Tab.AllEvents -> events
        Tab.Dividends -> dividends
        is Tab.OfType -> events.filter { it.type == current.type }
    }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        item { AssetHeader(asset, section, status, config) }

        item { SectionTitle("Критерии") }
        item {
            Card {
                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    if (status == null || status.criteria.isEmpty()) {
                        Box(Modifier.padding(vertical = 14.dp)) { Hint("Критерии не заполнены.") }
                    }
                    status?.criteria?.forEachIndexed { index, criterion ->
                        CriterionRow(
                            criterion = criterion,
                            help = data.criteria[criterion.code],
                            expanded = openCriterion == criterion.code,
                            onToggle = {
                                openCriterion =
                                    if (openCriterion == criterion.code) null else criterion.code
                            },
                        )
                        if (index != status.criteria.lastIndex) HorizontalDivider()
                    }
                }
            }
        }

        item { SectionTitle("Что отслеживаем") }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (status == null || status.watch.isEmpty()) {
                        Hint("Пока ничего не отмечено.")
                    }
                    status?.watch?.forEach { item ->
                        Text("•  $item", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item { SectionTitle("Уведомления") }
        item {
            Card {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Сообщать о событиях", style = MaterialTheme.typography.bodyMedium)
                        Hint(
                            if (muted) "Сейчас выключены для этой бумаги"
                            else "Приходят при событиях с пометкой notify",
                        )
                    }
                    Switch(checked = !muted, onCheckedChange = { onMutedChange(!it) })
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                FilterChip(
                    selected = tab == Tab.AllEvents,
                    onClick = { tab = Tab.AllEvents },
                    label = { Text("Все события") },
                )
                if (dividends.isNotEmpty()) {
                    FilterChip(
                        selected = tab == Tab.Dividends,
                        onClick = { tab = Tab.Dividends },
                        label = { Text("Дивиденды") },
                    )
                }
            }
        }

        if (types.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { type ->
                        FilterChip(
                            selected = tab == Tab.OfType(type),
                            onClick = { tab = Tab.OfType(type) },
                            label = { Text(EventTypes.label(type)) },
                        )
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                Card {
                    Box(Modifier.padding(14.dp)) {
                        Hint(
                            if (events.isEmpty()) "Событий пока нет — агент ещё ничего не записал."
                            else "В этом разделе событий нет.",
                        )
                    }
                }
            }
        }

        items(visible, key = { it.id }) { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                EventBody(event)
            }
        }
    }
}

private sealed interface Tab {
    data object AllEvents : Tab
    data object Dividends : Tab
    data class OfType(val type: String) : Tab
}

@Composable
private fun AssetHeader(asset: Asset, section: Section, status: Status?, config: RepoConfig) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Logo(asset = asset, config = config, size = 56.dp)
                Column {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Hint(
                        buildString {
                            append(Flags.of(section.country))
                            append(' ')
                            append(asset.displayTicker)
                            if (asset.isin != null) append(" · ${asset.isin}")
                        },
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                VerdictBadge(status?.verdict)
                Box(Modifier.weight(1f))
                Hint("Обновлено ${Dates.format(status?.updated)}")
            }

            if (asset.qualifiedOnly || asset.isCommodity) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (asset.qualifiedOnly) {
                        InfoChip(label = null, value = "Только для квалифицированных")
                    }
                    if (asset.isCommodity) {
                        InfoChip(
                            label = "Сырьевая",
                            value = asset.commodity?.let { Commodities.label(it) } ?: "да",
                        )
                    }
                }
            }

            val links = listOfNotNull(
                asset.irUrl?.let { "Страница для инвесторов" to it },
                asset.disclosureUrl?.let { "Раскрытие" to it },
            )
            if (links.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    links.forEach { (title, url) ->
                        TextButton(onClick = { openLink(context, url) }) { Text("$title ↗") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CriterionRow(
    criterion: Criterion,
    help: CriterionHelp?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickableRow(onToggle)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = criterion.code,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = criterion.state.sign,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = StatusColors.criterion(criterion.state),
            )
            Text(
                text = criterion.note.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (expanded) {
            Card {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (help == null) {
                        Hint("Расшифровка для этого кода не найдена в CRITERIA.md.")
                    } else {
                        Text(
                            text = help.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (help.text.isNotBlank()) Hint(help.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventBody(event: MonitorEvent) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(event.importance.mark, style = MaterialTheme.typography.labelMedium)
            Hint(
                buildString {
                    append(Dates.format(event.date))
                    append(" · ")
                    append(EventTypes.label(event.type))
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                event.criteria.forEach { CriterionChip(it) }
            }
        }

        if (event.metrics.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                event.metrics.forEach { MetricChip(it) }
            }
        }

        event.sources.forEach { source ->
            TextButton(
                onClick = { openLink(context, source.url) },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("${source.title} ↗", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Источники открываем в браузере телефона. */
private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
