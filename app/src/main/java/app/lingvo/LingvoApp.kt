package app.lingvo

import android.app.Application
import app.lingvo.data.AppDatabase
import app.lingvo.data.LingvoRepository
import app.lingvo.data.SettingsStore
import app.lingvo.island.IslandController
import app.lingvo.notify.Notifications
import app.lingvo.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** Ручное внедрение зависимостей: приложению хватает одного контейнера. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = AppDatabase.create(app)
    val settings = SettingsStore(app)
    val repository = LingvoRepository(app, database, settings)
    val island = IslandController(app, repository, scope)
    val speaker = Speaker(app)
}

class LingvoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.island.start()
        // Пересчитываем будильник при запуске и при каждом изменении настроек напоминаний.
        container.scope.launch {
            container.settings.flow
                .distinctUntilChangedBy { it.reminder }
                .collect { ReminderScheduler.reschedule(this@LingvoApp, it) }
        }
    }
}
