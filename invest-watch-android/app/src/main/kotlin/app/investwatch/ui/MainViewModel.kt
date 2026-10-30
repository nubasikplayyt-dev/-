package app.investwatch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.investwatch.core.AssetCard
import app.investwatch.core.MonitorEvent
import app.investwatch.core.QuietHours
import app.investwatch.core.ThemeMode
import app.investwatch.core.Watchlist
import app.investwatch.data.AppRepository
import app.investwatch.data.SecureStore
import app.investwatch.data.SettingsStore
import app.investwatch.data.SyncError
import app.investwatch.data.net.PriceSources
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncState(
    val running: Boolean = false,
    val error: String? = null,
    val needsToken: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AppRepository,
    private val settings: SettingsStore,
    private val secureStore: SecureStore,
    private val priceSources: PriceSources,
) : ViewModel() {

    private fun <T> stateOf(flow: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val watchlist: StateFlow<Watchlist> =
        stateOf(repository.watchlist, Watchlist(emptyList(), emptyList(), emptyList()))

    val cards: StateFlow<List<AssetCard>> = stateOf(repository.cardsWithUnread, emptyList())

    val favorites: StateFlow<List<String>> = stateOf(repository.favorites, emptyList())

    val events: StateFlow<List<MonitorEvent>> = stateOf(repository.events, emptyList())

    val theme: StateFlow<ThemeMode> = stateOf(settings.theme, ThemeMode.SYSTEM)

    val dynamicColors: StateFlow<Boolean> = stateOf(settings.dynamicColors, true)

    val notificationsEnabled: StateFlow<Boolean> = stateOf(settings.notificationsEnabled, true)

    val quietHours: StateFlow<QuietHours> = stateOf(settings.quietHours, QuietHours())

    val demoMode: StateFlow<Boolean> = stateOf(settings.demoMode, false)

    val lastSync: StateFlow<Long> = stateOf(repository.lastSync, 0L)

    /** Карточки избранного в порядке, который задал владелец перетаскиванием. */
    val favoriteCards: StateFlow<List<AssetCard>> = stateOf(
        kotlinx.coroutines.flow.combine(repository.cardsWithUnread, repository.favorites) { cards, order ->
            val byId = cards.associateBy { it.asset.id }
            order.mapNotNull { byId[it] }
        },
        emptyList(),
    )

    /** Лента новостей только по избранным бумагам. */
    val favoriteEvents: StateFlow<List<MonitorEvent>> = stateOf(
        kotlinx.coroutines.flow.combine(repository.events, repository.favorites) { events, favorites ->
            events.filter { it.assetId in favorites }
        },
        emptyList(),
    )

    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private val _hasGitHubToken = MutableStateFlow(secureStore.has(SecureStore.GITHUB_TOKEN))
    val hasGitHubToken: StateFlow<Boolean> = _hasGitHubToken.asStateFlow()

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        if (_sync.value.running) return
        viewModelScope.launch {
            _sync.value = SyncState(running = true)
            _sync.value = try {
                repository.sync(force)
                SyncState()
            } catch (error: SyncError) {
                SyncState(error = error.message, needsToken = error.needsToken)
            } catch (error: Exception) {
                SyncState(error = error.message ?: "Не удалось обновить данные")
            }
        }
    }

    fun dismissError() {
        _sync.value = _sync.value.copy(error = null)
    }

    fun toggleFavorite(assetId: String, onFirstFavorite: () -> Unit = {}) {
        viewModelScope.launch {
            val wasEmpty = repository.favorites.first().isEmpty()
            repository.toggleFavorite(assetId)
            if (wasEmpty) onFirstFavorite()
            repository.pushFavorites()
        }
    }

    fun reorderFavorites(order: List<String>) {
        viewModelScope.launch {
            repository.reorderFavorites(order)
            repository.pushFavorites()
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setTheme(mode) }

    fun setDynamicColors(enabled: Boolean) =
        viewModelScope.launch { settings.setDynamicColors(enabled) }

    fun setNotificationsEnabled(enabled: Boolean) =
        viewModelScope.launch { settings.setNotificationsEnabled(enabled) }

    fun setQuietHours(hours: QuietHours) = viewModelScope.launch { settings.setQuietHours(hours) }

    fun setDemoMode(enabled: Boolean) = viewModelScope.launch {
        settings.setDemoMode(enabled)
        refresh(force = true)
    }

    // ───────────────────────────── токены ────────────────────────────────────

    fun gitHubTokenMask(): String? = secureStore.masked(SecureStore.GITHUB_TOKEN)

    fun priceKeyMask(): String? = secureStore.masked(SecureStore.PRICE_API_KEY)

    fun saveGitHubToken(token: String) {
        secureStore.write(SecureStore.GITHUB_TOKEN, token)
        _hasGitHubToken.value = secureStore.has(SecureStore.GITHUB_TOKEN)
        refresh(force = true)
    }

    fun clearGitHubToken() {
        secureStore.write(SecureStore.GITHUB_TOKEN, null)
        _hasGitHubToken.value = false
    }

    fun savePriceKey(key: String) = secureStore.write(SecureStore.PRICE_API_KEY, key)

    fun clearPriceKey() = secureStore.write(SecureStore.PRICE_API_KEY, null)

    /** Кнопка «проверить» рядом с ключом источника цен. */
    fun checkPriceKey(key: String, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(priceSources.eodhdSource.checkKey(key)) }
    }
}
