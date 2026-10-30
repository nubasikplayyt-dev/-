package app.investwatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.investwatch.core.AssetFilter
import app.investwatch.core.AssetList
import app.investwatch.core.AssetSort
import app.investwatch.core.CommodityFilter
import app.investwatch.core.Verdict

/**
 * Вкладка рынка: сегмент «Акции / Фонды недвижимости», поиск, фильтры,
 * сортировка и карточки бумаг со звёздочкой.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketScreen(
    model: MainViewModel,
    marketId: String,
    onOpenAsset: (String) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val watchlist by model.watchlist.collectAsStateWithLifecycle()
    val cards by model.cards.collectAsStateWithLifecycle()
    val sync by model.sync.collectAsStateWithLifecycle()

    val kinds = watchlist.kinds
    var kindId by remember(kinds) { mutableStateOf(kinds.firstOrNull()?.id) }
    var filter by remember { mutableStateOf(AssetFilter()) }
    var sort by remember { mutableStateOf(AssetSort.YIELD) }
    var sortMenu by remember { mutableStateOf(false) }

    val visible = AssetList.apply(
        cards.filter { it.asset.marketId == marketId && (kindId == null || it.asset.kindId == kindId) },
        filter,
        sort,
    )

    PullToRefreshColumn(refreshing = sync.running, onRefresh = { model.refresh(force = true) }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (kinds.size > 1) {
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        kinds.forEachIndexed { index, kind ->
                            SegmentedButton(
                                selected = kindId == kind.id,
                                onClick = { kindId = kind.id },
                                shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                            ) {
                                Text(kind.title)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { filter = filter.copy(query = it) },
                    label = { Text("Поиск по тикеру и названию") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Verdict.entries.forEach { verdict ->
                        FilterChip(
                            selected = verdict in filter.verdicts,
                            onClick = {
                                filter = filter.copy(
                                    verdicts = if (verdict in filter.verdicts) {
                                        filter.verdicts - verdict
                                    } else {
                                        filter.verdicts + verdict
                                    },
                                )
                            },
                            label = { Text(verdict.label) },
                        )
                    }
                }
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = filter.passesYieldFloorOnly,
                        onClick = {
                            filter = filter.copy(passesYieldFloorOnly = !filter.passesYieldFloorOnly)
                        },
                        label = { Text("Проходит порог") },
                    )
                    FilterChip(
                        selected = filter.commodity == CommodityFilter.ONLY,
                        onClick = {
                            filter = filter.copy(
                                commodity = if (filter.commodity == CommodityFilter.ONLY) {
                                    CommodityFilter.ANY
                                } else {
                                    CommodityFilter.ONLY
                                },
                            )
                        },
                        label = { Text("Только сырьевые") },
                    )
                    FilterChip(
                        selected = filter.commodity == CommodityFilter.EXCLUDE,
                        onClick = {
                            filter = filter.copy(
                                commodity = if (filter.commodity == CommodityFilter.EXCLUDE) {
                                    CommodityFilter.ANY
                                } else {
                                    CommodityFilter.EXCLUDE
                                },
                            )
                        },
                        label = { Text("Без сырьевых") },
                    )
                    // Поле in_universe появляется после первого скрининга —
                    // пока его нет, фильтр не показываем.
                    if (watchlist.hasUniverseFlag) {
                        FilterChip(
                            selected = filter.inUniverseOnly,
                            onClick = {
                                filter = filter.copy(inUniverseOnly = !filter.inUniverseOnly)
                            },
                            label = { Text("В составе индекса") },
                        )
                    }
                }
            }

            item {
                Column {
                    TextButton(onClick = { sortMenu = true }) {
                        Text("Сортировка: ${sort.label.lowercase()}")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        AssetSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sort = option
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Hint(
                                if (cards.none { it.asset.marketId == marketId }) {
                                    "Бумаг этого рынка в репозитории пока нет."
                                } else {
                                    "Под эти условия ничего не подошло."
                                },
                            )
                        }
                    }
                }
            }

            items(visible, key = { it.asset.id }) { card ->
                AssetCardView(
                    card = card,
                    onToggleFavorite = {
                        model.toggleFavorite(card.asset.id, onFirstFavorite = onRequestNotifications)
                    },
                    onClick = { onOpenAsset(card.asset.id) },
                )
            }
        }
    }
}
