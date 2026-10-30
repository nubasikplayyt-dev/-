package app.portfolio.work

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.portfolio.MainActivity
import app.portfolio.R
import app.portfolio.core.Asset
import app.portfolio.core.EventTypes
import app.portfolio.core.MonitorEvent

/** Уведомления о новых событиях. Нажатие открывает экран нужной бумаги. */
object Notifications {

    const val CHANNEL_ID = "events"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    // Разрешение проверяется строкой ниже, но lint этого не видит.
    @SuppressLint("MissingPermission")
    fun show(context: Context, event: MonitorEvent, asset: Asset?) {
        if (!canPost(context)) return

        val title = buildString {
            append(event.importance.mark)
            append(' ')
            append(asset?.let { "${it.displayTicker} · ${it.name}" } ?: event.assetId)
        }
        val text = buildString {
            append(event.title)
            if (event.summary.isNotBlank()) {
                append("\n")
                append(event.summary)
            }
        }

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(event.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(EventTypes.label(event.type))
            .setContentIntent(openAsset(context, event.assetId))
            .setAutoCancel(true)
            .setGroup(GROUP)
            .build()

        NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
    }

    private fun openAsset(context: Context, assetId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ASSET_ID, assetId)
        }
        return PendingIntent.getActivity(
            context,
            assetId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val GROUP = "app.portfolio.events"
}
