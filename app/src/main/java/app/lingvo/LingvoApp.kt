package app.lingvo

import android.app.Application
import android.util.Log
import app.lingvo.data.AppDatabase
import app.lingvo.data.LingvoRepository
import app.lingvo.data.SettingsStore
import app.lingvo.images.WordImages
import app.lingvo.island.IslandController
import app.lingvo.notify.Notifications
import app.lingvo.notify.ReminderScheduler
import app.lingvo.ui.AppState
import app.lingvo.ui.appStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/** Ручное внедрение зависимостей: приложению хватает одного контейнера. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = AppDatabase.create(app)
    val settings = SettingsStore(app)
    val repository = LingvoRepository(app, database, settings)
    val island = IslandController(app, repository, scope)
    val speaker = Speaker(app) { scope.launch { settings.incrementListens() } }
    val wordImages = WordImages(app)
    val cloud = app.lingvo.cloud.Cloud(app)
    val friends = app.lingvo.cloud.FriendsRepository(cloud, database.friends(), settings, repository)

    init {
        // Новые слова пользователя — в очередь на картинку (если генерация включена в настройках).
        repository.onWordsAdded = { added, spec ->
            scope.launch {
                val st = settings.current()
                added.forEach { runCatching { wordImages.request(it, spec, st) } }
            }
        }
    }

    /** Общее состояние для всех экранов (считается один раз, пока на него кто-то подписан). */
    val appState: SharedFlow<AppState> = appStateFlow(this).shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
}

class LingvoApp : Application() {
    lateinit var container: AppContainer
        private set

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.island.start()
        container.scope.launch {
            container.settings.flow
                .distinctUntilChangedBy { it.reminder }
                .collect { ReminderScheduler.reschedule(this@LingvoApp, it) }
        }
        // Обмен прогрессом: сводка уходит друзьям при запуске и после занятий (не чаще раза в минуту).
        container.scope.launch {
            container.appState
                .map { it.snapshot.answers }
                .distinctUntilChanged()
                .debounce(60_000)
                .collect {
                    val s = container.settings.current()
                    if (!s.friendsEnabled) return@collect
                    runCatching { container.friends.refreshAll() }
                    if (s.friendNotifications) runCatching { notifyOvertaken() }
                }
        }
        // Достижения, области карты, существа и иконки пересчитываются при любом изменении реальных показателей.
        container.scope.launch {
            runCatching { container.repository.evaluateWorld() }.onFailure { Log.e("LingvoApp", "evaluate", it) }
            container.appState
                .map { it.snapshot.copy(regions = 0, creatures = 0, achievements = 0) }
                .distinctUntilChanged()
                .debounce(700)
                .collect { runCatching { container.repository.evaluateWorld() }.onFailure { e -> Log.e("LingvoApp", "evaluate", e) } }
        }
    }
    /** Сообщает, если друг обошёл вас за эту неделю (только если уведомления включены). */
    private suspend fun notifyOvertaken() {
        val me = container.friends.mySummary()
        val friends = container.friends.observeFriends().first().associate { it.code to it.progress }
        val ahead = app.lingvo.core.Competition.overtookMe(me.weekPoints, friends, notifiedThisWeek, me.weekKey)
        if (notifiedWeekKey != me.weekKey) { notifiedThisWeek.clear(); notifiedWeekKey = me.weekKey }
        val first = ahead.firstOrNull() ?: return
        notifiedThisWeek += first.nick
        app.lingvo.notify.Notifications.showFriendAhead(this, first.nick, first.weekPoints, me.weekPoints)
    }

    private val notifiedThisWeek = mutableSetOf<String>()
    private var notifiedWeekKey = 0
}
