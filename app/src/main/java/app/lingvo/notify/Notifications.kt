package app.lingvo.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.lingvo.MainActivity
import app.lingvo.R
import app.lingvo.core.LanguageSpec
import app.lingvo.core.SessionText
import app.lingvo.data.AppSettings

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_SESSION = "session"

    private const val ID_REMINDER_COMBINED = 1000
    /** Стабильные ID уведомлений по ID курса (курсов может быть сколько угодно). */
    private fun reminderId(spec: LanguageSpec) = 10_000 + Math.floorMod(spec.code.hashCode(), 40_000)
    fun sessionId(spec: LanguageSpec) = 50_000 + Math.floorMod(spec.code.hashCode(), 40_000)
    private const val ACCENT = 0xFF5B3FD1.toInt()

    const val EXTRA_OPEN = "open"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Напоминания о повторении", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ежедневное напоминание, когда есть карточки к повторению"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSION, "Активная тренировка", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Компактный прогресс текущей сессии повторения"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun channelEnabled(context: Context, channel: String): Boolean {
        val ch = context.getSystemService(NotificationManager::class.java).getNotificationChannel(channel) ?: return true
        return ch.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Открывает приложение на нужном экране ("review/es", "home"). */
    fun openIntent(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN, route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun broadcast(context: Context, action: String, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(action).apply(extras)
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // ---------- Напоминания ----------

    @SuppressLint("MissingPermission")
    fun showReminders(context: Context, counts: List<Pair<LanguageSpec, Int>>, settings: AppSettings, text: app.lingvo.core.ReminderTexts.Text? = null) {
        if (!canPost(context)) return
        val nm = NotificationManagerCompat.from(context)
        val nonZero = counts.filter { it.second > 0 }.ifEmpty { counts.take(1) }
        if (nonZero.isEmpty()) { cancelReminders(context); return }
        val visibility = if (settings.hideOnLockScreen) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC

        fun build(group: List<Pair<LanguageSpec, Int>>, route: String, req: Int): Notification {
            val title = text?.title ?: SessionText.reminderTitle(group)
            val body = text?.body ?: SessionText.reminderBody(group)
            val public = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_lingvo)
                .setContentTitle("Время повторить слова")
                .build()
            return NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_lingvo)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setColor(ACCENT)
                .setAutoCancel(true)
                .setVisibility(visibility)
                .setPublicVersion(public)
                .setContentIntent(openIntent(context, route, req))
                .addAction(0, "Начать", openIntent(context, route, req + 50))
                .addAction(0, "Отложить на час", broadcast(context, NotificationActionReceiver.ACTION_SNOOZE_REMINDER, req + 100))
                .build()
        }

        if (settings.combineReminders || nonZero.size == 1) {
            val route = if (nonZero.size == 1 && nonZero[0].second > 0) "review/${nonZero[0].first.code}" else "home"
            counts.forEach { nm.cancel(reminderId(it.first)) }
            nm.notify(ID_REMINDER_COMBINED, build(nonZero, route, ID_REMINDER_COMBINED))
        } else {
            nm.cancel(ID_REMINDER_COMBINED)
            nonZero.forEach { pair -> nm.notify(reminderId(pair.first), build(listOf(pair), "review/${pair.first.code}", reminderId(pair.first))) }
        }
    }

    /** Убирает все напоминания (любых курсов). */
    fun cancelReminders(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.activeNotifications.filter { it.notification.channelId == CHANNEL_REMINDERS }.forEach { nm.cancel(it.id) }
    }

    // ---------- Компактное уведомление активной сессии ----------

    data class SessionInfo(
        val spec: LanguageSpec,
        val sessionId: Long,
        val done: Int,
        val total: Int,
        /** Текущее слово (без перевода) — только если пользователь разрешил. */
        val currentWord: String?,
        val otherQueues: String?,
        val streak: Int = 0,
        val creatureName: String? = null,
    )

    @SuppressLint("MissingPermission")
    fun showSession(context: Context, info: SessionInfo, settings: AppSettings) {
        if (!canPost(context)) return
        val title = SessionText.progress(info.spec, info.done, info.total) + if (info.streak > 0) "  🔥${info.streak}" else ""
        val remaining = (info.total - info.done).coerceAtLeast(0)
        val text = buildString {
            append("Осталось $remaining ${SessionText.plural(remaining, "карточка", "карточки", "карточек")}")
            if (info.currentWord != null) append(" · ${info.currentWord}")
            if (info.otherQueues != null) append(" · ${info.otherQueues}")
            if (info.creatureName != null) append(" · ${info.creatureName} болеет за вас")
        }
        val reqBase = sessionId(info.spec) * 10
        val content = openIntent(context, "review/${info.spec.code}", reqBase)
        val snooze = broadcast(context, NotificationActionReceiver.ACTION_SNOOZE_SESSION, reqBase + 1) {
            putExtra(NotificationActionReceiver.EXTRA_LANGUAGE, info.spec.code)
        }
        val end = broadcast(context, NotificationActionReceiver.ACTION_END_SESSION, reqBase + 2) {
            putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, info.sessionId)
            putExtra(NotificationActionReceiver.EXTRA_LANGUAGE, info.spec.code)
        }
        val notification = if (Build.VERSION.SDK_INT >= 36) {
            buildLiveUpdate(context, info, title, text, content, snooze, end, settings)
        } else {
            NotificationCompat.Builder(context, CHANNEL_SESSION)
                .setSmallIcon(R.drawable.ic_stat_lingvo)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(info.total.coerceAtLeast(1), info.done, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setColor(ACCENT)
                .setVisibility(if (settings.hideOnLockScreen) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
                .setPublicVersion(
                    NotificationCompat.Builder(context, CHANNEL_SESSION).setSmallIcon(R.drawable.ic_stat_lingvo)
                        .setContentTitle("${info.spec.flag} Повторение · ${info.done}/${info.total}").build()
                )
                .setContentIntent(content)
                .addAction(0, "Открыть", content)
                .addAction(0, "Отложить", snooze)
                .addAction(0, "Завершить", end)
                .build()
        }
        NotificationManagerCompat.from(context).notify(sessionId(info.spec), notification)
    }

    /**
     * Android 16+: ProgressStyle + запрос «Live Update» (promoted ongoing). Система сама решает,
     * показывать ли его как чип в строке состояния / на экране блокировки (аналог Dynamic Island).
     */
    @SuppressLint("NewApi")
    private fun buildLiveUpdate(
        context: Context, info: SessionInfo, title: String, text: String,
        content: PendingIntent, snooze: PendingIntent, end: PendingIntent, settings: AppSettings,
    ): Notification {
        val total = info.total.coerceAtLeast(1)
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(total)))
            .setProgress(info.done.coerceIn(0, total))
        val icon = Icon.createWithResource(context, R.drawable.ic_stat_lingvo)
        val publicVersion = Notification.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(icon)
            .setContentTitle("${info.spec.flag} Повторение · ${info.done}/${info.total}")
            .build()
        val builder = Notification.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setColor(ACCENT)
            .setVisibility(if (settings.hideOnLockScreen) Notification.VISIBILITY_PRIVATE else Notification.VISIBILITY_PUBLIC)
            .setPublicVersion(publicVersion)
            .setContentIntent(content)
            .addAction(Notification.Action.Builder(icon, "Открыть", content).build())
            .addAction(Notification.Action.Builder(icon, "Отложить", snooze).build())
            .addAction(Notification.Action.Builder(icon, "Завершить", end).build())
        // Запрос продвижения в Live Update (EXTRA_REQUEST_PROMOTED_ONGOING).
        builder.extras.putBoolean("android.requestPromotedOngoing", true)
        // Короткий текст для чипа в строке состояния, если метод доступен в прошивке.
        runCatching {
            Notification.Builder::class.java.getMethod("setShortCriticalText", String::class.java)
                .invoke(builder, "${info.done}/${info.total}")
        }
        return builder.build()
    }

    fun cancelSession(context: Context, spec: LanguageSpec) {
        NotificationManagerCompat.from(context).cancel(sessionId(spec))
    }

    /** Может ли система показать уведомление как Live Update (Android 16+). */
    fun canPromote(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 36) return null
        return runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            NotificationManager::class.java.getMethod("canPostPromotedNotifications").invoke(nm) as Boolean
        }.getOrNull()
    }
}
