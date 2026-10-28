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
import android.graphics.Bitmap
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
    /** Канал острова. Важность DEFAULT (без звука и вибрации) — иначе система прячет его в «Беззвучные». */
    const val CHANNEL_SESSION = "session_live"
    /** Старый канал с низкой важностью — удаляется при запуске. */
    private const val LEGACY_CHANNEL_SESSION = "session"
    private const val ISLAND_ID = 4242
    private const val FRIEND_ID = 4343

    private const val ID_REMINDER_COMBINED = 1000
    /** Стабильные ID уведомлений по ID курса (курсов может быть сколько угодно). */
    private fun reminderId(spec: LanguageSpec) = 10_000 + Math.floorMod(spec.code.hashCode(), 40_000)
    private const val ACCENT = 0xFF5B3FD1.toInt()

    const val EXTRA_OPEN = "open"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Напоминания о повторении", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ежедневное напоминание, когда есть карточки к повторению"
            }
        )
        nm.deleteNotificationChannel(LEGACY_CHANNEL_SESSION)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSION, "Остров занятия", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Текущее слово и кнопки «Помню» / «Не помню» во время занятия. Без звука."
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

    // ---------- Остров: уведомление активного занятия ----------

    /** Что показать в острове-уведомлении. */
    data class IslandInfo(
        val spec: LanguageSpec,
        val sessionId: Long,
        val entryId: String,
        val word: String,
        val translation: String,
        val revealed: Boolean,
        val done: Int,
        val total: Int,
        val streak: Int,
        val creature: Bitmap?,
        val creatureName: String?,
        val answers: Boolean,
    )

    private fun answerIntent(context: Context, info: IslandInfo, remembered: Boolean, req: Int) =
        broadcast(context, NotificationActionReceiver.ACTION_ANSWER, req) {
            putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, info.sessionId)
            putExtra(NotificationActionReceiver.EXTRA_ENTRY_ID, info.entryId)
            putExtra(NotificationActionReceiver.EXTRA_REMEMBERED, remembered)
            putExtra(NotificationActionReceiver.EXTRA_LANGUAGE, info.spec.code)
        }

    /**
     * Компактный остров: существо слева, текущее слово, прогресс и кнопки «Не помню» / «Помню».
     * Перевод скрыт, пока пользователь не нажмёт «Перевод» (как «Показать ответ» в приложении).
     * Ответы обрабатывает тот же код, что и в приложении: засчитывается только текущая карточка.
     */
    @SuppressLint("MissingPermission")
    fun showIsland(context: Context, info: IslandInfo, settings: AppSettings) {
        if (!canPost(context)) return
        val title = "${info.spec.flag} ${info.word}"
        val progress = "${info.done}/${info.total}" + if (info.streak > 0) " · серия ${info.streak}" else ""
        val text = if (info.revealed) "${info.translation} · $progress" else "Вспомни перевод · $progress"
        val reqBase = (Math.floorMod(info.sessionId, 100_000L).toInt()) * 10 + 1_000_000
        val content = openIntent(context, "review/${info.spec.code}", reqBase)
        val forgot = answerIntent(context, info, false, reqBase + 3)
        val remembered = answerIntent(context, info, true, reqBase + 4)
        val reveal = broadcast(context, NotificationActionReceiver.ACTION_REVEAL, reqBase + 5) {
            putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, info.sessionId)
            putExtra(NotificationActionReceiver.EXTRA_ENTRY_ID, info.entryId)
        }
        val end = broadcast(context, NotificationActionReceiver.ACTION_END_SESSION, reqBase + 2) {
            putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, info.sessionId)
            putExtra(NotificationActionReceiver.EXTRA_LANGUAGE, info.spec.code)
        }
        val visibility = if (settings.hideOnLockScreen) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC
        val publicTitle = "${info.spec.flag} Занятие · ${info.done}/${info.total}"

        val notification = if (Build.VERSION.SDK_INT >= 36) {
            buildLiveUpdate(context, info, title, text, publicTitle, content, forgot, remembered, reveal, end, settings)
        } else {
            val b = NotificationCompat.Builder(context, CHANNEL_SESSION)
                .setSmallIcon(R.drawable.ic_stat_lingvo)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(info.spec.nameRu)
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    if (info.revealed) "${info.translation}\n$progress" else "Вспомни перевод и ответь.\n$progress"))
                .setProgress(info.total.coerceAtLeast(1), info.done, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(ACCENT)
                .setVisibility(visibility)
                .setPublicVersion(
                    NotificationCompat.Builder(context, CHANNEL_SESSION).setSmallIcon(R.drawable.ic_stat_lingvo)
                        .setContentTitle(publicTitle).build()
                )
                .setContentIntent(content)
            info.creature?.let { b.setLargeIcon(it) }
            if (info.answers) {
                b.addAction(0, "Не помню", forgot)
                b.addAction(0, "Помню", remembered)
                b.addAction(0, if (info.revealed) "Завершить" else "Перевод", if (info.revealed) end else reveal)
            } else {
                b.addAction(0, "Открыть", content)
                b.addAction(0, "Завершить", end)
            }
            b.build()
        }
        NotificationManagerCompat.from(context).notify(ISLAND_ID, notification)
    }

    /** Состояние завершения: короткое поздравление, само исчезает через 10 секунд. */
    @SuppressLint("MissingPermission")
    fun showIslandFinished(context: Context, spec: LanguageSpec, done: Int, correct: Int, creature: Bitmap?, creatureName: String?) {
        if (!canPost(context)) return
        val pct = if (done == 0) 0 else correct * 100 / done
        val b = NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_stat_lingvo)
            .setContentTitle("${spec.flag} Занятие завершено! 🎉")
            .setContentText("$done ${SessionText.plural(done, "ответ", "ответа", "ответов")} · помню $pct%" +
                (creatureName?.let { " · $it радуется" } ?: ""))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000)
            .setColor(ACCENT)
            .setContentIntent(openIntent(context, "home", 999_999))
        creature?.let { b.setLargeIcon(it) }
        NotificationManagerCompat.from(context).notify(ISLAND_ID, b.build())
    }

    /**
     * Android 16+: ProgressStyle + запрос «Live Update» (promoted ongoing). Система сама решает,
     * показывать ли его как чип в строке состояния и на экране блокировки.
     */
    @SuppressLint("NewApi")
    private fun buildLiveUpdate(
        context: Context, info: IslandInfo, title: String, text: String, publicTitle: String,
        content: PendingIntent, forgot: PendingIntent, remembered: PendingIntent, reveal: PendingIntent, end: PendingIntent,
        settings: AppSettings,
    ): Notification {
        val total = info.total.coerceAtLeast(1)
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(total)))
            .setProgress(info.done.coerceIn(0, total))
        val icon = Icon.createWithResource(context, R.drawable.ic_stat_lingvo)
        val publicVersion = Notification.Builder(context, CHANNEL_SESSION).setSmallIcon(icon).setContentTitle(publicTitle).build()
        val builder = Notification.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setColor(ACCENT)
            .setVisibility(if (settings.hideOnLockScreen) Notification.VISIBILITY_PRIVATE else Notification.VISIBILITY_PUBLIC)
            .setPublicVersion(publicVersion)
            .setContentIntent(content)
        info.creature?.let { builder.setLargeIcon(Icon.createWithBitmap(it)) }
        if (info.answers) {
            builder.addAction(Notification.Action.Builder(icon, "Не помню", forgot).build())
            builder.addAction(Notification.Action.Builder(icon, "Помню", remembered).build())
            builder.addAction(Notification.Action.Builder(icon, if (info.revealed) "Завершить" else "Перевод", if (info.revealed) end else reveal).build())
        } else {
            builder.addAction(Notification.Action.Builder(icon, "Открыть", content).build())
            builder.addAction(Notification.Action.Builder(icon, "Завершить", end).build())
        }
        // Запрос продвижения в Live Update (EXTRA_REQUEST_PROMOTED_ONGOING).
        builder.extras.putBoolean("android.requestPromotedOngoing", true)
        // Короткий текст для чипа в строке состояния, если метод доступен в прошивке.
        runCatching {
            Notification.Builder::class.java.getMethod("setShortCriticalText", String::class.java)
                .invoke(builder, "${info.done}/${info.total}")
        }
        return builder.build()
    }

    /** Друг обошёл вас за неделю. Показывается только при включённых уведомлениях о друзьях. */
    @SuppressLint("MissingPermission")
    fun showFriendAhead(context: Context, nick: String, theirPoints: Int, myPoints: Int) {
        if (!canPost(context)) return
        val b = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_lingvo)
            .setContentTitle("$nick впереди на этой неделе")
            .setContentText("$theirPoints очков против ваших $myPoints. Пара занятий — и догоните!")
            .setAutoCancel(true)
            .setColor(ACCENT)
            .setContentIntent(openIntent(context, "friends", 777_001))
        NotificationManagerCompat.from(context).notify(FRIEND_ID, b.build())
    }

    fun cancelIsland(context: Context) {
        NotificationManagerCompat.from(context).cancel(ISLAND_ID)
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
