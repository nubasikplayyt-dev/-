package app.investwatch.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.investwatch.MainActivity
import app.investwatch.R
import app.investwatch.core.Asset
import app.investwatch.core.EventTypes
import app.investwatch.core.Importance
import app.investwatch.core.MonitorEvent

/**
 * Уведомления о новых событиях по избранным бумагам.
 *
 * Каналы для critical и high разные: в системных настройках их можно
 * настроить по-разному, вплоть до полного отключения одного из них.
 */
object Notifications {

    const val CHANNEL_CRITICAL = "events_critical"
    const val CHANNEL_HIGH = "events_high"

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CRITICAL,
                context.getString(R.string.channel_critical_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_critical_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HIGH,
                context.getString(R.string.channel_high_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_high_description) },
        )
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
        val body = buildString {
            append(event.title)
            if (event.summary.isNotBlank()) {
                append("\n")
                append(shorten(event.summary))
            }
        }

        val notification = NotificationCompat.Builder(context, channelOf(event.importance))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(event.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(EventTypes.label(event.type))
            .setContentIntent(openAsset(context, event.assetId))
            .setAutoCancel(true)
            .setGroup(GROUP)
            .build()

        NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
    }

    private fun channelOf(importance: Importance): String = when (importance) {
        Importance.CRITICAL -> CHANNEL_CRITICAL
        else -> CHANNEL_HIGH
    }

    /** В уведомлении хватает одной-двух строк сводки. */
    private fun shorten(text: String, limit: Int = 180): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        return if (clean.length <= limit) clean else clean.take(limit - 1).trimEnd() + "…"
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

    private const val GROUP = "app.investwatch.events"
}
