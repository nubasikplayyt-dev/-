package app.portfolio.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.portfolio.core.RepoConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: androidx.datastore.core.DataStore<Preferences> by
    preferencesDataStore(name = "settings")

/**
 * Настройки и память приложения: откуда читать данные, какие события уже
 * показаны и по каким бумагам уведомления выключены.
 */
class Settings(private val context: Context) {

    private object Keys {
        val owner = stringPreferencesKey("owner")
        val repo = stringPreferencesKey("repo")
        val branch = stringPreferencesKey("branch")
        val seenEvents = stringSetPreferencesKey("seen_events")
        val mutedAssets = stringSetPreferencesKey("muted_assets")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val checkIntervalHours = longPreferencesKey("check_interval_hours")
        val firstRunDone = booleanPreferencesKey("first_run_done")
        val lastCheck = longPreferencesKey("last_check")
    }

    val repoConfig: Flow<RepoConfig> = context.dataStore.data.map { preferences ->
        RepoConfig(
            owner = preferences[Keys.owner] ?: RepoConfig.DEFAULT_OWNER,
            repo = preferences[Keys.repo] ?: RepoConfig.DEFAULT_REPO,
            branch = preferences[Keys.branch] ?: RepoConfig.DEFAULT_BRANCH,
        )
    }

    val seenEvents: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.seenEvents] ?: emptySet() }

    val mutedAssets: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.mutedAssets] ?: emptySet() }

    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.notificationsEnabled] ?: true }

    val checkIntervalHours: Flow<Long> =
        context.dataStore.data.map { it[Keys.checkIntervalHours] ?: DEFAULT_INTERVAL_HOURS }

    val lastCheck: Flow<Long> = context.dataStore.data.map { it[Keys.lastCheck] ?: 0L }

    suspend fun repoConfigNow(): RepoConfig = repoConfig.first()

    suspend fun setRepo(owner: String, repo: String, branch: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.owner] = owner.trim().ifEmpty { RepoConfig.DEFAULT_OWNER }
            preferences[Keys.repo] = repo.trim().ifEmpty { RepoConfig.DEFAULT_REPO }
            preferences[Keys.branch] = branch.trim().ifEmpty { RepoConfig.DEFAULT_BRANCH }
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notificationsEnabled] = enabled }
    }

    suspend fun setCheckIntervalHours(hours: Long) {
        context.dataStore.edit { it[Keys.checkIntervalHours] = hours.coerceIn(1L, 24L) }
    }

    suspend fun setMuted(assetId: String, muted: Boolean) {
        context.dataStore.edit { preferences ->
            val current = preferences[Keys.mutedAssets] ?: emptySet()
            preferences[Keys.mutedAssets] = if (muted) current + assetId else current - assetId
        }
    }

    /** Отмечает события показанными. Храним не больше 2000 — этого хватает надолго. */
    suspend fun rememberSeen(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { preferences ->
            val merged = (preferences[Keys.seenEvents] ?: emptySet()) + ids
            preferences[Keys.seenEvents] = merged.toList().takeLast(2000).toSet()
        }
    }

    /**
     * Первый запуск: события, уже лежащие в репозитории, не должны прилететь
     * пачкой уведомлений сразу после установки.
     */
    suspend fun isFirstRun(): Boolean = context.dataStore.data.first()[Keys.firstRunDone] != true

    suspend fun markFirstRunDone() {
        context.dataStore.edit { it[Keys.firstRunDone] = true }
    }

    suspend fun markChecked(at: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[Keys.lastCheck] = at }
    }

    companion object {
        const val DEFAULT_INTERVAL_HOURS = 3L
    }
}
