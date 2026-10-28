package app.lingvo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lingvo.core.Languages
import app.lingvo.core.ReminderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

enum class Direction(val labelRu: String) {
    TARGET_TO_RU("Изучаемый → русский"),
    RU_TO_TARGET("Русский → изучаемый"),
    MIXED("Смешанно"),
}

enum class CapsuleContent(val labelRu: String) {
    SESSION("Текущая сессия"),
    BOTH_QUEUES("Очереди обоих языков"),
}

data class LanguageSettings(
    val newPerDay: Int = 10,
    val direction: Direction = Direction.MIXED,
    val remindersEnabled: Boolean = true,
)

data class AppSettings(
    val languages: Map<String, LanguageSettings> = Languages.all.associate { it.code to LanguageSettings() },
    val autoSpeak: Boolean = false,
    val reminder: ReminderConfig = ReminderConfig(),
    val combineReminders: Boolean = true,
    val showWordInNotification: Boolean = false,
    val hideOnLockScreen: Boolean = true,
    val sessionNotification: Boolean = true,
    val capsuleEnabled: Boolean = false,
    val capsuleContent: CapsuleContent = CapsuleContent.SESSION,
    val animations: Boolean = true,
    val motivation: Boolean = true,
    val onboardingDone: Boolean = false,
) {
    fun lang(code: String): LanguageSettings = languages[code] ?: LanguageSettings()
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object K {
        fun newPerDay(code: String) = intPreferencesKey("new_per_day_$code")
        fun direction(code: String) = stringPreferencesKey("direction_$code")
        fun remind(code: String) = booleanPreferencesKey("remind_$code")
        val autoSpeak = booleanPreferencesKey("auto_speak")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val reminderTimes = stringPreferencesKey("reminder_times")
        val reminderDays = stringPreferencesKey("reminder_days")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = stringPreferencesKey("quiet_start")
        val quietEnd = stringPreferencesKey("quiet_end")
        val combine = booleanPreferencesKey("combine_reminders")
        val showWord = booleanPreferencesKey("show_word_in_notification")
        val hideLock = booleanPreferencesKey("hide_on_lock_screen")
        val sessionNotification = booleanPreferencesKey("session_notification")
        val capsule = booleanPreferencesKey("capsule_enabled")
        val capsuleContent = stringPreferencesKey("capsule_content")
        val animations = booleanPreferencesKey("animations")
        val motivation = booleanPreferencesKey("motivation")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    val flow: Flow<AppSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::read)

    suspend fun current(): AppSettings = flow.first()

    private fun read(p: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            languages = Languages.all.associate { spec ->
                spec.code to LanguageSettings(
                    newPerDay = p[K.newPerDay(spec.code)] ?: 10,
                    direction = p[K.direction(spec.code)]?.let { runCatching { Direction.valueOf(it) }.getOrNull() } ?: Direction.MIXED,
                    remindersEnabled = p[K.remind(spec.code)] ?: true,
                )
            },
            autoSpeak = p[K.autoSpeak] ?: false,
            reminder = ReminderConfig(
                enabled = p[K.remindersEnabled] ?: true,
                times = p[K.reminderTimes]?.let(::parseTimes)?.takeIf { it.isNotEmpty() } ?: defaults.reminder.times,
                days = p[K.reminderDays]?.let(::parseDays) ?: defaults.reminder.days,
                quietHoursEnabled = p[K.quietEnabled] ?: false,
                quietStart = p[K.quietStart]?.let(::parseTime) ?: defaults.reminder.quietStart,
                quietEnd = p[K.quietEnd]?.let(::parseTime) ?: defaults.reminder.quietEnd,
            ),
            combineReminders = p[K.combine] ?: true,
            showWordInNotification = p[K.showWord] ?: false,
            hideOnLockScreen = p[K.hideLock] ?: true,
            sessionNotification = p[K.sessionNotification] ?: true,
            capsuleEnabled = p[K.capsule] ?: false,
            capsuleContent = p[K.capsuleContent]?.let { runCatching { CapsuleContent.valueOf(it) }.getOrNull() } ?: CapsuleContent.SESSION,
            animations = p[K.animations] ?: true,
            motivation = p[K.motivation] ?: true,
            onboardingDone = p[K.onboarding] ?: false,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(read(prefs))
            write(prefs, next)
        }
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, s: AppSettings) {
        s.languages.forEach { (code, l) ->
            p[K.newPerDay(code)] = l.newPerDay
            p[K.direction(code)] = l.direction.name
            p[K.remind(code)] = l.remindersEnabled
        }
        p[K.autoSpeak] = s.autoSpeak
        p[K.remindersEnabled] = s.reminder.enabled
        p[K.reminderTimes] = s.reminder.times.joinToString(",") { formatTime(it) }
        p[K.reminderDays] = s.reminder.days.joinToString(",") { it.name }
        p[K.quietEnabled] = s.reminder.quietHoursEnabled
        p[K.quietStart] = formatTime(s.reminder.quietStart)
        p[K.quietEnd] = formatTime(s.reminder.quietEnd)
        p[K.combine] = s.combineReminders
        p[K.showWord] = s.showWordInNotification
        p[K.hideLock] = s.hideOnLockScreen
        p[K.sessionNotification] = s.sessionNotification
        p[K.capsule] = s.capsuleEnabled
        p[K.capsuleContent] = s.capsuleContent.name
        p[K.animations] = s.animations
        p[K.motivation] = s.motivation
        p[K.onboarding] = s.onboardingDone
    }

    /** Плоское представление для резервной копии. */
    suspend fun exportMap(): Map<String, String> =
        context.dataStore.data.first().asMap().mapKeys { it.key.name }.mapValues { it.value.toString() }

    suspend fun importMap(values: Map<String, String>) {
        // Типы ключей однозначны: new_per_day_* — числа, true/false — флаги, остальное — строки.
        context.dataStore.edit { p ->
            values.forEach { (name, raw) ->
                when {
                    name.startsWith("new_per_day_") -> raw.toIntOrNull()?.let { p[intPreferencesKey(name)] = it }
                    raw == "true" || raw == "false" -> p[booleanPreferencesKey(name)] = raw.toBoolean()
                    else -> p[stringPreferencesKey(name)] = raw
                }
            }
        }
    }

    companion object {
        fun formatTime(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)
        fun parseTime(s: String): LocalTime? = runCatching { LocalTime.parse(s.trim()) }.getOrNull()
        fun parseTimes(s: String): List<LocalTime> = s.split(',').mapNotNull { parseTime(it) }
        fun parseDays(s: String): Set<DayOfWeek> =
            s.split(',').mapNotNull { runCatching { DayOfWeek.valueOf(it.trim()) }.getOrNull() }.toSet()
    }
}
