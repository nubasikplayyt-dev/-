package app.investwatch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.investwatch.core.Favorites
import app.investwatch.core.QuietHours
import app.investwatch.core.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Настройки приложения и его память между запусками. */
@Singleton
class SettingsStore @Inject constructor(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColors = booleanPreferencesKey("dynamic_colors")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietFrom = intPreferencesKey("quiet_from")
        val quietTo = intPreferencesKey("quiet_to")
        val mutedAssets = stringSetPreferencesKey("muted_assets")
        val seenEvents = stringSetPreferencesKey("seen_events")
        val favoritesOrder = stringPreferencesKey("favorites_order")
        val pendingAdded = stringSetPreferencesKey("pending_added")
        val pendingRemoved = stringSetPreferencesKey("pending_removed")
        val favoritesDirty = booleanPreferencesKey("favorites_dirty")
        val lastSync = longPreferencesKey("last_sync")
        val firstRunDone = booleanPreferencesKey("first_run_done")
        val demoMode = booleanPreferencesKey("demo_mode")
    }

    val theme: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.from(it[Keys.theme]) }

    val dynamicColors: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.dynamicColors] ?: true }

    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.notificationsEnabled] ?: true }

    val quietHours: Flow<QuietHours> = context.dataStore.data.map { preferences ->
        QuietHours(
            enabled = preferences[Keys.quietEnabled] ?: false,
            fromMinutes = preferences[Keys.quietFrom] ?: (23 * 60),
            toMinutes = preferences[Keys.quietTo] ?: (8 * 60),
        )
    }

    val mutedAssets: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.mutedAssets] ?: emptySet() }

    val seenEvents: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.seenEvents] ?: emptySet() }

    /** Порядок избранного задаётся перетаскиванием и живёт локально. */
    val favoritesOrder: Flow<List<String>> = context.dataStore.data.map { preferences ->
        preferences[Keys.favoritesOrder].orEmpty().split('\n').filter { it.isNotBlank() }
    }

    val pending: Flow<Favorites.Pending> = context.dataStore.data.map { preferences ->
        Favorites.Pending(
            added = preferences[Keys.pendingAdded] ?: emptySet(),
            removed = preferences[Keys.pendingRemoved] ?: emptySet(),
        )
    }

    /** Порядок или состав избранного изменились и ещё не уехали в репозиторий. */
    val favoritesDirty: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.favoritesDirty] ?: false }

    val lastSync: Flow<Long> = context.dataStore.data.map { it[Keys.lastSync] ?: 0L }

    val demoMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.demoMode] ?: false }

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.theme] = mode.raw }

    suspend fun setDynamicColors(enabled: Boolean) = edit { it[Keys.dynamicColors] = enabled }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        edit { it[Keys.notificationsEnabled] = enabled }

    suspend fun setQuietHours(hours: QuietHours) = edit {
        it[Keys.quietEnabled] = hours.enabled
        it[Keys.quietFrom] = hours.fromMinutes
        it[Keys.quietTo] = hours.toMinutes
    }

    suspend fun setMuted(assetId: String, muted: Boolean) = edit { preferences ->
        val current = preferences[Keys.mutedAssets] ?: emptySet()
        preferences[Keys.mutedAssets] = if (muted) current + assetId else current - assetId
    }

    suspend fun setDemoMode(enabled: Boolean) = edit { it[Keys.demoMode] = enabled }

    /** Храним не больше 2000 идентификаторов — этого хватает надолго. */
    suspend fun rememberSeen(ids: Set<String>) {
        if (ids.isEmpty()) return
        edit { preferences ->
            val merged = (preferences[Keys.seenEvents] ?: emptySet()) + ids
            preferences[Keys.seenEvents] = merged.toList().takeLast(2000).toSet()
        }
    }

    suspend fun setFavoritesOrder(order: List<String>) =
        edit { it[Keys.favoritesOrder] = order.joinToString("\n") }

    suspend fun setPending(pending: Favorites.Pending) = edit {
        it[Keys.pendingAdded] = pending.added
        it[Keys.pendingRemoved] = pending.removed
    }

    suspend fun setFavoritesDirty(dirty: Boolean) = edit { it[Keys.favoritesDirty] = dirty }

    suspend fun markSynced(at: Long = System.currentTimeMillis()) = edit { it[Keys.lastSync] = at }

    suspend fun isFirstRun(): Boolean = context.dataStore.data.first()[Keys.firstRunDone] != true

    suspend fun markFirstRunDone() = edit { it[Keys.firstRunDone] = true }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
