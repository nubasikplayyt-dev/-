package app.lingvo.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lingvo.core.AppIcons
import app.lingvo.core.DailyStreak
import app.lingvo.core.ReminderConfig
import app.lingvo.core.ReviewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

enum class Direction(val labelRu: String) {
    TARGET_TO_RU("Слово → перевод"),
    RU_TO_TARGET("Перевод → слово"),
    MIXED("Вперемешку"),
}

enum class CapsuleContent(val labelRu: String) {
    SESSION("Текущее занятие"),
    BOTH_QUEUES("Очереди всех курсов"),
}

/** Откуда брать картинки к словам. */
enum class ImageSource(val labelRu: String) {
    WEB("Найти в интернете"),
    AI_SERVER("Нарисовать нейросетью (свой сервер)"),
}

enum class ThemeMode(val labelRu: String) {
    LIGHT("Светлая"),
    DARK("Тёмная"),
    SYSTEM("Как в системе"),
}

/** Настройки одного курса. */
data class LanguageSettings(
    val newPerDay: Int = 10,
    val direction: Direction = Direction.MIXED,
    val remindersEnabled: Boolean = true,
    /** Кнопки «Помню/Не помню», письменный ответ или вперемешку. */
    val reviewMode: ReviewMode = ReviewMode.BUTTONS,
)

data class AppSettings(
    /** Курс → настройки. Отсутствующий курс получает значения по умолчанию. */
    val languages: Map<String, LanguageSettings> = emptyMap(),
    val dailyGoal: Int = DailyStreak.DEFAULT_GOAL,
    val activeCourseId: String = "",
    val autoSpeak: Boolean = false,
    val reminder: ReminderConfig = ReminderConfig(),
    val combineReminders: Boolean = true,
    val showWordInNotification: Boolean = false,
    val hideOnLockScreen: Boolean = true,
    val sessionNotification: Boolean = true,
    /** Кнопки «Помню»/«Не помню» прямо в острове-уведомлении и капсуле. */
    val islandAnswers: Boolean = true,
    /** Подбирать картинки к новым словам автоматически (по умолчанию выключено). */
    val imageGeneration: Boolean = false,
    val imageSource: ImageSource = ImageSource.WEB,
    /** Адрес Stable Diffusion на компьютере в домашней сети (API Automatic1111/Forge/SD.Next). */
    val imageServerUrl: String = "",
    val capsuleEnabled: Boolean = false,
    val capsuleContent: CapsuleContent = CapsuleContent.SESSION,
    val animations: Boolean = true,
    val motivation: Boolean = true,
    val theme: ThemeMode = ThemeMode.LIGHT,
    val favoriteCreature: String = "",
    val appIcon: String = AppIcons.DEFAULT,
    /** Сколько раз пользователь слушал произношение (для достижения «Чуткое ухо»). */
    val listens: Int = 0,
    val onboardingDone: Boolean = false,
    /** Последняя HTTPS-ссылка на словарь (например, raw-файл в GitHub). */
    val importUrl: String = "",
) {
    fun lang(code: String): LanguageSettings = languages[code] ?: LanguageSettings()

    fun withLang(code: String, f: (LanguageSettings) -> LanguageSettings) = copy(languages = languages + (code to f(lang(code))))
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object K {
        const val NEW_PER_DAY = "new_per_day_"
        const val DIRECTION = "direction_"
        const val REMIND = "remind_"
        const val MODE = "review_mode_"
        fun mode(code: String) = stringPreferencesKey(MODE + code)
        val islandAnswers = booleanPreferencesKey("island_answers")
        val imageGeneration = booleanPreferencesKey("image_generation")
        val imageServerUrl = stringPreferencesKey("image_server_url")
        val imageSource = stringPreferencesKey("image_source")
        fun newPerDay(code: String) = intPreferencesKey(NEW_PER_DAY + code)
        fun direction(code: String) = stringPreferencesKey(DIRECTION + code)
        fun remind(code: String) = booleanPreferencesKey(REMIND + code)
        val dailyGoal = intPreferencesKey("daily_goal")
        val activeCourse = stringPreferencesKey("active_course")
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
        val theme = stringPreferencesKey("theme")
        val favoriteCreature = stringPreferencesKey("favorite_creature")
        val appIcon = stringPreferencesKey("app_icon")
        val listens = intPreferencesKey("listens")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val importUrl = stringPreferencesKey("import_url")

        /** Числовые ключи — для восстановления из резервной копии. */
        fun isInt(name: String) = name.startsWith(NEW_PER_DAY) || name == "daily_goal" || name == "listens"
    }

    val flow: Flow<AppSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::read)

    suspend fun current(): AppSettings = flow.first()

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        val names = p.asMap().keys.map { it.name }
        val courseIds = names.mapNotNull { n ->
            listOf(K.NEW_PER_DAY, K.DIRECTION, K.REMIND, K.MODE).firstOrNull { n.startsWith(it) }?.let { n.removePrefix(it) }
        }.toSet()
        return AppSettings(
            languages = courseIds.associateWith { id ->
                LanguageSettings(
                    newPerDay = p[K.newPerDay(id)] ?: 10,
                    direction = p[K.direction(id)]?.let { runCatching { Direction.valueOf(it) }.getOrNull() } ?: Direction.MIXED,
                    remindersEnabled = p[K.remind(id)] ?: true,
                    reviewMode = p[K.mode(id)]?.let { runCatching { ReviewMode.valueOf(it) }.getOrNull() } ?: ReviewMode.BUTTONS,
                )
            },
            dailyGoal = p[K.dailyGoal] ?: d.dailyGoal,
            activeCourseId = p[K.activeCourse] ?: "",
            autoSpeak = p[K.autoSpeak] ?: false,
            reminder = ReminderConfig(
                enabled = p[K.remindersEnabled] ?: true,
                times = p[K.reminderTimes]?.let(::parseTimes)?.takeIf { it.isNotEmpty() } ?: d.reminder.times,
                days = p[K.reminderDays]?.let(::parseDays) ?: d.reminder.days,
                quietHoursEnabled = p[K.quietEnabled] ?: false,
                quietStart = p[K.quietStart]?.let(::parseTime) ?: d.reminder.quietStart,
                quietEnd = p[K.quietEnd]?.let(::parseTime) ?: d.reminder.quietEnd,
            ),
            combineReminders = p[K.combine] ?: true,
            showWordInNotification = p[K.showWord] ?: false,
            hideOnLockScreen = p[K.hideLock] ?: true,
            sessionNotification = p[K.sessionNotification] ?: true,
            islandAnswers = p[K.islandAnswers] ?: true,
            imageGeneration = p[K.imageGeneration] ?: false,
            imageServerUrl = p[K.imageServerUrl] ?: "",
            imageSource = p[K.imageSource]?.let { runCatching { ImageSource.valueOf(it) }.getOrNull() } ?: ImageSource.WEB,
            capsuleEnabled = p[K.capsule] ?: false,
            capsuleContent = p[K.capsuleContent]?.let { runCatching { CapsuleContent.valueOf(it) }.getOrNull() } ?: CapsuleContent.SESSION,
            animations = p[K.animations] ?: true,
            motivation = p[K.motivation] ?: true,
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.LIGHT,
            favoriteCreature = p[K.favoriteCreature] ?: "",
            appIcon = p[K.appIcon] ?: AppIcons.DEFAULT,
            listens = p[K.listens] ?: 0,
            onboardingDone = p[K.onboarding] ?: false,
            importUrl = p[K.importUrl] ?: "",
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    suspend fun incrementListens() {
        context.dataStore.edit { it[K.listens] = (it[K.listens] ?: 0) + 1 }
    }

    /** Удаляет настройки курса (при удалении курса). */
    suspend fun removeCourse(id: String) {
        context.dataStore.edit {
            it.remove(K.newPerDay(id)); it.remove(K.direction(id)); it.remove(K.remind(id)); it.remove(K.mode(id))
        }
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        s.languages.forEach { (code, l) ->
            p[K.newPerDay(code)] = l.newPerDay
            p[K.direction(code)] = l.direction.name
            p[K.remind(code)] = l.remindersEnabled
            p[K.mode(code)] = l.reviewMode.name
        }
        p[K.dailyGoal] = s.dailyGoal
        p[K.activeCourse] = s.activeCourseId
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
        p[K.islandAnswers] = s.islandAnswers
        p[K.imageGeneration] = s.imageGeneration
        p[K.imageServerUrl] = s.imageServerUrl
        p[K.imageSource] = s.imageSource.name
        p[K.capsule] = s.capsuleEnabled
        p[K.capsuleContent] = s.capsuleContent.name
        p[K.animations] = s.animations
        p[K.motivation] = s.motivation
        p[K.theme] = s.theme.name
        p[K.favoriteCreature] = s.favoriteCreature
        p[K.appIcon] = s.appIcon
        p[K.listens] = s.listens
        p[K.onboarding] = s.onboardingDone
        p[K.importUrl] = s.importUrl
    }

    /** Плоское представление для резервной копии. */
    suspend fun exportMap(): Map<String, String> =
        context.dataStore.data.first().asMap().mapKeys { it.key.name }.mapValues { it.value.toString() }

    suspend fun importMap(values: Map<String, String>) {
        context.dataStore.edit { p ->
            values.forEach { (name, raw) ->
                when {
                    K.isInt(name) -> raw.toIntOrNull()?.let { p[intPreferencesKey(name)] = it }
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
