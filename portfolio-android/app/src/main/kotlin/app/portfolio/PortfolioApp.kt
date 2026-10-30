package app.portfolio

import android.app.Application
import app.portfolio.data.Settings
import app.portfolio.work.Notifications
import app.portfolio.work.UpdateWorker
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PortfolioApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)

        // Периодическую проверку ставим при старте: интервал мог измениться
        // в настройках, а WorkManager сам не переставит задачу.
        val settings = Settings(this)
        CoroutineScope(Dispatchers.IO).launch {
            if (settings.notificationsEnabled.first()) {
                UpdateWorker.schedule(this@PortfolioApp, settings.checkIntervalHours.first())
            }
        }
    }

    /** Логотипы в репозитории данных — SVG, обычному загрузчику он не по зубам. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
}
