package app.investwatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.investwatch.core.ChartPeriod
import app.investwatch.core.Commodities
import app.investwatch.core.Criterion
import app.investwatch.core.CriterionHelp
import app.investwatch.core.DividendPayment
import app.investwatch.core.EventTypes
import app.investwatch.core.Format
import app.investwatch.core.Prices

/**
 * Экран бумаги: шапка, график цены, дивиденды, критерии, метрики,
 * «что отслеживается» и лента событий.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetScreen(
    mainModel: MainViewModel,
    onBack: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val model: AssetViewModel = hiltViewModel()
    val card by model.card.collectAsStateWithLifecycle()
    val events by model.events.collectAsStateWithLifecycle()
    val dividends by model.dividends.collectAsStateWithLifecycle()
    val candles by model.candles.collectAsStateWithLifecycle()
    val period by model.period.collectAsStateWithLifecycle()
    val muted by model.muted.collectAsStateWithLifecycle()
    val help by model.criteriaHelp.collectAsStateWithLifecycle()
    val priceError by model.priceError.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var openCriterion by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var typeFilter by remember { mutableStateOf<String?>(null) }

    val current = card
    if (current == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Hint("Бумага не найдена в config/watchlist.json")
        }
        return
    }

    val asset = current.asset
    val status = current.status
    val slice = remember(candles, period) { model.slice() }
    val changePct = remember(slice) { Prices.changePct(slice) }
    val visibleEvents = events.filter { typeFilter == null || it.type == typeFilter }
    val eventTypes = remember(events) { events.map { it.type }.distinct().sortedBy { EventTypes.label(it) } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // ─────────────────────────────── шапка ───────────────────────────────
        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                        Box(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                mainModel.toggleFavorite(asset.id, onFirstFavorite = onRequestNotifications)
                            },
                        ) {
                            Icon(
                                imageVector = if (current.isFavorite) Icons.Filled.Star
                                else Icons.Outlined.StarBorder,
                                contentDescription = "Избранное",
                                tint = if (current.isFavorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Logo(asset, size = 56.dp)
                        Column {
                            Text(
                                text = asset.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Hint(
                                buildString {
                                    current.market?.let { append(it.flag).append(' ') }
                                    append(asset.displayTicker)
                                    asset.isin?.let { append(" · ").append(it) }
                                    asset.exchange?.let { append(" · ").append(it) }
                                    current.kind?.let { append(" · ").append(it.title) }
                                },
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        VerdictBadge(current.verdict)
                        Box(Modifier.weight(1f))
                        Hint("Оценка от ${Format.date(status?.updated)}")
                    }

                    if (asset.qualifiedOnly || asset.isCommodity) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        asset.irUrl?.let {
                            TextButton(onClick = { openInBrowser(context, it) }) {
                                Text("Страница для инвесторов ↗")
                            }
                        }
                        asset.disclosureUrl?.let {
                            TextButton(onClick = { openInBrowser(context, it) }) {
                                Text("Раскрытие ↗")
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Уведомления по бумаге", style = MaterialTheme.typography.bodyMedium)
                            Hint(if (muted) "Выключены" else "Приходят по событиям с notify")
                        }
                        Switch(checked = !muted, onCheckedChange = { model.setMuted(!it) })
                    }
                }
            }
        }

        // ────────────────────────────── график ───────────────────────────────
        item { SectionTitle("Цена") }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChartPeriod.entries.forEach { option ->
                            FilterChip(
                                selected = period == option,
                                onClick = { model.setPeriod(option) },
                                label = { Text(option.label) },
                            )
                        }
                    }

                    PriceChart(
                        candles = slice,
                        currency = current.quote?.currency ?: current.market?.currency,
                        changePct = changePct,
                    )

                    if (changePct != null) {
                        Text(
                            text = "За период ${Format.percent(changePct, withSign = true)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusColors.change(changePct),
                        )
                    }
                    priceError?.let { Hint(it) }
                }
            }
        }

        // ───────────────────────────── дивиденды ─────────────────────────────
        item { SectionTitle("Дивиденды по годам") }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DividendYearsChart(
                        years = dividends?.byYear.orEmpty().takeLast(10),
                        currency = dividends?.currency ?: current.market?.currency,
                    )
                    dividends?.let {
                        if (it.origin != app.investwatch.core.DividendOrigin.REPO) {
                            Hint("Данные ${it.origin.label}")
                        }
                    }
                }
            }
        }

        val payments = dividends?.payments.orEmpty()
        if (payments.isNotEmpty()) {
            item { SectionTitle("Выплаты") }
            item {
                Card {
                    Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                        payments.take(40).forEachIndexed { index, payment ->
                            PaymentRow(
                                payment = payment,
                                currency = dividends?.currency ?: current.market?.currency,
                                candles = candles,
                            )
                            if (index != payments.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }

        // ───────────────────────────── критерии ──────────────────────────────
        item { SectionTitle("Критерии") }
        item {
            Card {
                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    val criteria = status?.criteria.orEmpty()
                    if (criteria.isEmpty()) {
                        Box(Modifier.padding(vertical = 14.dp)) { Hint("Критерии не заполнены.") }
                    }
                    criteria.forEachIndexed { index, criterion ->
                        CriterionRow(
                            criterion = criterion,
                            help = help[criterion.code],
                            expanded = openCriterion == criterion.code,
                            onToggle = {
                                openCriterion =
                                    if (openCriterion == criterion.code) null else criterion.code
                            },
                        )
                        if (index != criteria.lastIndex) HorizontalDivider()
                    }
                }
            }
        }

        // ────────────────────────────── метрики ──────────────────────────────
        val metrics = status?.metrics.orEmpty()
        if (metrics.isNotEmpty()) {
            item { SectionTitle("Метрики") }
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            metrics.forEach { MetricChip(it.name, it.value) }
                        }
                        status?.metricsDate?.let { Hint("На ${Format.date(it)}") }
                    }
                }
            }
        }

        // ───────────────────────── что отслеживается ─────────────────────────
        item { SectionTitle("Что отслеживается") }
        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val watch = status?.watch.orEmpty()
                    if (watch.isEmpty()) Hint("Пока ничего не отмечено.")
                    watch.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        // ─────────────────────────────── события ─────────────────────────────
        item { SectionTitle("События") }
        item {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Все") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("По типу") })
            }
        }
        if (tab == 1) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    eventTypes.forEach { type ->
                        FilterChip(
                            selected = typeFilter == type,
                            onClick = { typeFilter = if (typeFilter == type) null else type },
                            label = { Text(EventTypes.label(type)) },
                        )
                    }
                }
            }
        }

        val shown = if (tab == 0) events else visibleEvents
        if (shown.isEmpty()) {
            item { Card { Column(Modifier.padding(14.dp)) { Hint("Событий пока нет.") } } }
        }
        items(shown, key = { it.id }) { event ->
            EventCard(event = event, asset = asset, showAsset = false)
        }
    }
}

@Composable
private fun PaymentRow(
    payment: DividendPayment,
    currency: String?,
    candles: List<app.investwatch.core.PriceCandle>,
) {
    val yieldPct = remember(payment, candles) { Prices.paymentYield(payment, candles) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Format.date(payment.recordDate),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Format.money(payment.amount, currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = yieldPct?.let { Format.percent(it) } ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
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
            Column {
                Text(
                    text = criterion.note.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (criterion.fromOverride) Hint("решение владельца")
            }
        }

        if (expanded) {
            Card {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (help == null) {
                        Hint("Расшифровка не найдена в CRITERIA.md.")
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
