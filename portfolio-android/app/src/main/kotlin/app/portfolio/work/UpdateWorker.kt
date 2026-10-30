package app.portfolio.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.portfolio.core.NewEvents
import app.portfolio.data.Repository
import app.portfolio.data.Settings
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка репозитория данных.
 *
 * Запускается периодически (по умолчанию раз в 3 часа) только при наличии
 * сети. Постоянных служб и будильников приложение не держит — всё через
 * WorkManager, как и положено.
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        if (!settings.notificationsEnabled.first()) return Result.success()

        val repository = Repository(applicationContext.cacheDir)
        val data = try {
            repository.load(settings.repoConfigNow(), force = true)
        } catch (error: Exception) {
            // Сеть могла моргнуть — попробуем в следующий раз.
            return Result.retry()
        }

        val firstRun = settings.isFirstRun()
        val selection = NewEvents.select(
            events = data.allEvents,
            alreadySeen = settings.seenEvents.first(),
            mutedAssets = settings.mutedAssets.first(),
            firstRun = firstRun,
        )

        for (event in selection.toNotify) {
            Notifications.show(
                applicationContext,
                event,
                data.watchlist.find(event.assetId)?.second,
            )
        }

        settings.rememberSeen(selection.seen)
        settings.markChecked()
        if (firstRun) settings.markFirstRunDone()

        return Result.success()
    }

    companion object {
        private const val NAME = "portfolio-update"

        /** Ставит или переставляет периодическую проверку. */
        fun schedule(context: Context, intervalHours: Long) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(
                intervalHours.coerceIn(1L, 24L),
                TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
