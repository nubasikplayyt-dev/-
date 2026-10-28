package app.lingvo.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.lingvo.core.Answer
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lingvo.LingvoApp
import app.lingvo.core.Creatures
import app.lingvo.core.ReminderTexts
import app.lingvo.core.ReminderPlanner
import app.lingvo.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Планирование ежедневных напоминаний.
 * AlarmManager.setAndAllowWhileIdle — неточный будильник без особых разрешений: Android может
 * сдвинуть срабатывание на несколько минут ради экономии батареи. Постоянного сервиса нет.
 */
object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    private const val REQ_DAILY = 1
    private const val REQ_SNOOZE = 2

    private fun pending(context: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, req,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun reschedule(context: Context, settings: AppSettings, now: ZonedDateTime = ZonedDateTime.now()) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, ReminderReceiver.ACTION_REMIND, REQ_DAILY)
        am.cancel(pi)
        val next = ReminderPlanner.nextTrigger(settings.reminder, now) ?: run {
            Log.i(TAG, "Напоминания отключены")
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi)
        Log.i(TAG, "Следующее напоминание: $next")
    }

    fun snooze(context: Context, minutes: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + minutes * 60_000L
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, ReminderReceiver.ACTION_SNOOZED, REQ_SNOOZE))
    }
}

/** Будильник, перезагрузка, смена времени/часового пояса, обновление приложения. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LingvoApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = app.container.repository.settings.current()
                when (intent.action) {
                    ACTION_REMIND, ACTION_SNOOZED -> enqueueCheck(context, snoozed = intent.action == ACTION_SNOOZED)
                    else -> Unit // BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED
                }
                if (intent.action != ACTION_SNOOZED) ReminderScheduler.reschedule(context, settings)
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Ошибка планирования напоминаний", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "app.lingvo.action.REMIND"
        const val ACTION_SNOOZED = "app.lingvo.action.REMIND_SNOOZED"

        fun enqueueCheck(context: Context, snoozed: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(androidx.work.workDataOf(ReminderWorker.KEY_SNOOZED to snoozed))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("reminder-check", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Проверяет очередь и показывает напоминание, только если действительно есть что повторять. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LingvoApp
        val repo = app.container.repository
        return try {
            val settings = repo.settings.current()
            val courses = repo.courses().map { it.spec }.filter { settings.lang(it.code).remindersEnabled }
            val counts = courses.map { it to repo.dueCount(it.code) }
            val newAvailable = courses.sumOf { repo.newCardsForToday(it).second.coerceAtMost(repo.counts(it.code).newCount) }
            val streak = repo.streak()
            val now = ZonedDateTime.now()
            val snoozed = inputData.getBoolean(KEY_SNOOZED, false)
            val config = if (snoozed) settings.reminder.copy(days = java.time.DayOfWeek.entries.toSet()) else settings.reminder
            val decision = ReminderPlanner.decide(
                config, now, counts.sumOf { it.second },
                lastActivity = if (snoozed) null else repo.lastActivity(),
                notificationsAllowed = Notifications.canPost(applicationContext),
                goalCompleted = streak.todayCompleted,
                newAvailable = newAvailable,
            )
            Log.i("ReminderWorker", "Решение: ${decision.reason}")
            if (decision.notify) {
                val creature = Creatures.byId(settings.favoriteCreature)?.name
                val text = ReminderTexts.pick(
                    ReminderTexts.Context(counts, streak.current, streak.atRisk, streak.todayAnswers, streak.todayGoal, creature),
                    now.toLocalDate().toEpochDay(),
                )
                Notifications.showReminders(applicationContext, counts, settings, text)
            } else if (streak.todayCompleted || counts.sumOf { it.second } == 0) {
                Notifications.cancelReminders(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("ReminderWorker", "Ошибка проверки напоминания", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_SNOOZED = "snoozed"
    }
}

/** Действия из уведомлений: «Отложить», «Завершить сессию». */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LingvoApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_SNOOZE_REMINDER -> {
                        Notifications.cancelReminders(context)
                        ReminderScheduler.snooze(context, 60)
                    }
                    ACTION_SNOOZE_SESSION -> {
                        // Сессия сохраняется; уведомление скрывается и вернётся напоминанием через 30 минут.
                        val id = intent.getLongExtra(EXTRA_SESSION_ID, -1)
                        if (id > 0) app.container.island.snooze(id)
                        ReminderScheduler.snooze(context, 30)
                    }
                    ACTION_ANSWER -> {
                        // Ответ из острова: засчитывается, только если это всё ещё текущая карточка занятия
                        // (повторное нажатие или ответ, уже данный в приложении, ничего не меняют).
                        val id = intent.getLongExtra(EXTRA_SESSION_ID, -1)
                        val entry = intent.getStringExtra(EXTRA_ENTRY_ID)
                        if (id > 0 && entry != null) {
                            val answer = if (intent.getBooleanExtra(EXTRA_REMEMBERED, false)) Answer.REMEMBERED else Answer.FORGOT
                            app.container.repository.answer(id, entry, answer)
                        }
                    }
                    ACTION_REVEAL -> {
                        val id = intent.getLongExtra(EXTRA_SESSION_ID, -1)
                        val entry = intent.getStringExtra(EXTRA_ENTRY_ID)
                        if (id > 0 && entry != null) app.container.island.reveal(id, entry)
                    }
                    ACTION_END_SESSION -> {
                        val id = intent.getLongExtra(EXTRA_SESSION_ID, -1)
                        // Ответы уже сохранены — завершение только закрывает очередь сессии.
                        if (id > 0) app.container.repository.endSession(id)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE_REMINDER = "app.lingvo.action.SNOOZE_REMINDER"
        const val ACTION_SNOOZE_SESSION = "app.lingvo.action.SNOOZE_SESSION"
        const val ACTION_END_SESSION = "app.lingvo.action.END_SESSION"
        const val ACTION_ANSWER = "app.lingvo.action.ANSWER"
        const val ACTION_REVEAL = "app.lingvo.action.REVEAL"
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_REMEMBERED = "remembered"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_LANGUAGE = "language"
    }
}
