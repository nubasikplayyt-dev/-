package app.portfolio.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.portfolio.core.NewEvents
import app.portfolio.core.RepoConfig
import app.portfolio.data.PortfolioData
import app.portfolio.data.Repository
import app.portfolio.data.Settings
import app.portfolio.work.UpdateWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val data: PortfolioData? = null,
    val error: String? = null,
)

class AppViewModel(private val context: Context) : ViewModel() {

    private val settings = Settings(context)
    private val repository = Repository(context.cacheDir)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val seenEvents: StateFlow<Set<String>> = settings.seenEvents
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val mutedAssets: StateFlow<Set<String>> = settings.mutedAssets
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val notificationsEnabled: StateFlow<Boolean> = settings.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val checkIntervalHours: StateFlow<Long> = settings.checkIntervalHours
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings.DEFAULT_INTERVAL_HOURS)

    val repoConfig: StateFlow<RepoConfig> = settings.repoConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, RepoConfig())

    val lastCheck: StateFlow<Long> = settings.lastCheck
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    init {
        load(force = false)
    }

    fun load(force: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val data = repository.load(settings.repoConfigNow(), force)
                _state.value = UiState(loading = false, data = data, error = null)

                // Открытое приложение — это тоже проверка: отмечаем первый
                // запуск пройденным, чтобы фоновая задача не прислала пачку
                // уведомлений обо всём, что уже лежит в репозитории.
                if (settings.isFirstRun()) {
                    settings.rememberSeen(
                        NewEvents.select(data.allEvents, emptySet(), firstRun = true).seen,
                    )
                    settings.markFirstRunDone()
                }
                settings.markChecked()
            } catch (error: Exception) {
                _state.value = UiState(
                    loading = false,
                    data = _state.value.data,
                    error = error.message ?: "Не удалось загрузить данные",
                )
            }
        }
    }

    /** Экран бумаги открыт — её события считаются прочитанными. */
    fun markAssetSeen(assetId: String) {
        val data = _state.value.data ?: return
        viewModelScope.launch {
            settings.rememberSeen(
                data.eventsOf(assetId).filter { it.notify }.map { it.id }.toSet(),
            )
        }
    }

    fun hasUnread(assetId: String): Boolean {
        val data = _state.value.data ?: return false
        return NewEvents.hasUnread(data.eventsOf(assetId), seenEvents.value)
    }

    fun setMuted(assetId: String, muted: Boolean) {
        viewModelScope.launch { settings.setMuted(assetId, muted) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setNotificationsEnabled(enabled)
            if (enabled) UpdateWorker.schedule(context, settings.checkIntervalHours.first())
            else UpdateWorker.cancel(context)
        }
    }

    fun setCheckIntervalHours(hours: Long) {
        viewModelScope.launch {
            settings.setCheckIntervalHours(hours)
            if (settings.notificationsEnabled.first()) {
                UpdateWorker.schedule(context, settings.checkIntervalHours.first())
            }
        }
    }

    fun setRepo(owner: String, repo: String, branch: String) {
        viewModelScope.launch {
            settings.setRepo(owner, repo, branch)
            load(force = true)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(context.applicationContext) as T
            }
    }
}
