package app.investwatch.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.investwatch.core.Notify
import app.investwatch.core.QuietHours
import app.investwatch.data.AppRepository
import app.investwatch.data.SettingsStore
import app.investwatch.data.db.AppDatabase
import app.investwatch.data.toModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Фоновая синхронизация: забирает новые события из репозитория и показывает
 * уведомления по избранным бумагам. Постоянных служб и будильников
 * приложение не держит — только периодическая задача при наличии сети.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: AppRepository,
    private val database: AppDatabase,
    private val settings: SettingsStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            repository.sync(force = false)
        } catch (error: Exception) {
            // Сеть могла моргнуть или токен ещё не введён — попробуем позже.
            return Result.retry()
        }

        if (!settings.notificationsEnabled.first()) return Result.success()

        val events = database.eventDao().allNow().map { it.toModel() }
        val favorites = database.favoriteDao().allNow().map { it.assetId }.toSet()
        val firstRun = settings.isFirstRun()

        val selection = Notify.select(
            events = events,
            seen = settings.seenEvents.first(),
            rules = Notify.Rules(
                favorites = favorites,
                muted = settings.mutedAssets.first(),
                enabled = true,
                firstRun = firstRun,
            ),
        )

        val quiet = settings.quietHours.first()
        if (!quiet.covers(minutesNow())) {
            val assets = database.watchlistDao().assetsNow().associate { it.id to it.toModel() }
            for (event in selection.toNotify) {
                Notifications.show(applicationContext, event, assets[event.assetId])
            }
        }

        // В тихие часы события всё равно помечаем показанными: будить человека
        // утром пачкой ночных уведомлений — худшее из решений.
        settings.rememberSeen(selection.seen)
        if (firstRun) settings.markFirstRunDone()

        return Result.success()
    }

    private fun minutesNow(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    companion object {
        private const val NAME = "invest-watch-sync"

        /** Раз в 30 минут — минимальный разумный интервал для WorkManager. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

/** Тихие часы считаются от локального времени телефона. */
fun QuietHours.coversNow(): Boolean {
    val calendar = Calendar.getInstance()
    return covers(calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE))
}
