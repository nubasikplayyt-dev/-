package app.investwatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Главный экран. Вкладка «Бумаги» — избранное карточками, порядок меняется
 * перетаскиванием. Вкладка «Новости» — события только по избранным бумагам.
 */
@Composable
fun FavoritesScreen(model: MainViewModel, onOpenAsset: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val cards by model.favoriteCards.collectAsStateWithLifecycle()
    val events by model.favoriteEvents.collectAsStateWithLifecycle()
    val sync by model.sync.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Бумаги") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Новости") })
        }

        if (cards.isEmpty() && events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Добавьте бумаги звёздочкой во вкладках рынков",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        PullToRefreshColumn(refreshing = sync.running, onRefresh = { model.refresh(force = true) }) {
            if (tab == 0) {
                FavoriteCards(model, cards.map { it }, onOpenAsset)
            } else {
                EventFeed(
                    events = events,
                    assets = cards.associate { it.asset.id to it.asset },
                    onOpenAsset = onOpenAsset,
                )
            }
        }
    }
}

@Composable
private fun FavoriteCards(
    model: MainViewModel,
    cards: List<app.investwatch.core.AssetCard>,
    onOpenAsset: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var order by remember(cards.map { it.asset.id }) {
        androidx.compose.runtime.mutableStateOf(cards.map { it.asset.id })
    }
    val byId = cards.associateBy { it.asset.id }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val updated = order.toMutableList()
        val fromIndex = updated.indexOf(from.key as? String ?: return@rememberReorderableLazyListState)
        val toIndex = updated.indexOf(to.key as? String ?: return@rememberReorderableLazyListState)
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        updated.add(toIndex, updated.removeAt(fromIndex))
        order = updated
        model.reorderFavorites(updated)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(order, key = { it }) { assetId ->
            val card = byId[assetId]
            ReorderableItem(reorderState, key = assetId) {
                if (card != null) {
                    AssetCardView(
                        card = card,
                        modifier = Modifier.longPressDraggableHandle(),
                        onToggleFavorite = { model.toggleFavorite(assetId) },
                        onClick = { onOpenAsset(assetId) },
                    )
                }
            }
        }

        item {
            Hint(
                text = "Удерживайте карточку, чтобы поменять порядок. " +
                    "Порядок уезжает в репозиторий вместе с составом избранного.",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
