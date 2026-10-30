package app.investwatch.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.investwatch.core.AssetCard
import app.investwatch.core.ChartPeriod
import app.investwatch.core.CriterionHelp
import app.investwatch.core.Dividends
import app.investwatch.core.MonitorEvent
import app.investwatch.core.PriceCandle
import app.investwatch.core.Prices
import app.investwatch.data.AppRepository
import app.investwatch.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssetViewModel @Inject constructor(
    private val repository: AppRepository,
    private val settings: SettingsStore,
    savedState: SavedStateHandle,
) : ViewModel() {

    val assetId: String = savedState.get<String>("assetId").orEmpty()

    private fun <T> stateOf(flow: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val card: StateFlow<AssetCard?> = stateOf(
        repository.cardsWithUnread.map { cards -> cards.firstOrNull { it.asset.id == assetId } },
        null,
    )

    val events: StateFlow<List<MonitorEvent>> = stateOf(repository.eventsOf(assetId), emptyList())

    val dividends: StateFlow<Dividends?> =
        stateOf(repository.dividends.map { it[assetId] }, null)

    val candles: StateFlow<List<PriceCandle>> = stateOf(repository.candles(assetId), emptyList())

    val muted: StateFlow<Boolean> =
        stateOf(settings.mutedAssets.map { assetId in it }, false)

    private val _period = MutableStateFlow(ChartPeriod.YEAR)
    val period: StateFlow<ChartPeriod> = _period.asStateFlow()

    private val _criteriaHelp = MutableStateFlow<Map<String, CriterionHelp>>(emptyMap())
    val criteriaHelp: StateFlow<Map<String, CriterionHelp>> = _criteriaHelp.asStateFlow()

    private val _priceError = MutableStateFlow<String?>(null)
    val priceError: StateFlow<String?> = _priceError.asStateFlow()

    init {
        viewModelScope.launch { _criteriaHelp.value = repository.criteriaHelp() }
        viewModelScope.launch { repository.markAssetSeen(assetId) }
        refreshPrices()
    }

    fun setPeriod(period: ChartPeriod) {
        _period.value = period
    }

    fun setMuted(muted: Boolean) = viewModelScope.launch { settings.setMuted(assetId, muted) }

    fun refreshPrices(force: Boolean = false) = viewModelScope.launch {
        _priceError.value = repository.refreshPrices(assetId, force)
    }

    /** Отрезок истории под выбранный период и изменение за него. */
    fun slice(): List<PriceCandle> = repository.chartSlice(candles.value, _period.value)

    fun changePct(): Double? = Prices.changePct(slice())
}
