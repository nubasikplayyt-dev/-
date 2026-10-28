package app.lingvo.core

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

data class ReminderConfig(
    val enabled: Boolean = true,
    /** Время напоминаний в течение дня (1–3). По умолчанию 19:00. */
    val times: List<LocalTime> = listOf(LocalTime.of(19, 0)),
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val quietHoursEnabled: Boolean = false,
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(8, 0),
)

data class ReminderDecision(val notify: Boolean, val reason: String)

object ReminderPlanner {
    /** Не напоминаем, если пользователь занимался менее часа назад. */
    const val RECENT_ACTIVITY_MS = 60 * 60 * 1000L

    fun isQuiet(config: ReminderConfig, time: LocalTime): Boolean {
        if (!config.quietHoursEnabled || config.quietStart == config.quietEnd) return false
        return if (config.quietStart < config.quietEnd) {
            time >= config.quietStart && time < config.quietEnd
        } else {
            // Тихие часы через полночь, например 22:00–08:00.
            time >= config.quietStart || time < config.quietEnd
        }
    }

    /**
     * Следующий момент напоминания строго после [now] в часовом поясе [now].
     * Пересчитывается после перезагрузки, смены времени или часового пояса.
     */
    fun nextTrigger(config: ReminderConfig, now: ZonedDateTime): ZonedDateTime? {
        if (!config.enabled || config.times.isEmpty() || config.days.isEmpty()) return null
        val times = config.times.distinct().sorted().filterNot { isQuiet(config, it) }
        if (times.isEmpty()) return null
        for (dayOffset in 0..7) {
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            if (date.dayOfWeek !in config.days) continue
            for (t in times) {
                val candidate = date.atTime(t).atZone(now.zone)
                if (candidate.isAfter(now)) return candidate
            }
        }
        return null
    }

    fun decide(
        config: ReminderConfig,
        now: ZonedDateTime,
        dueTotal: Int,
        lastActivity: Long?,
        notificationsAllowed: Boolean,
    ): ReminderDecision = when {
        !config.enabled -> ReminderDecision(false, "Напоминания выключены")
        !notificationsAllowed -> ReminderDecision(false, "Нет разрешения на уведомления")
        now.dayOfWeek !in config.days -> ReminderDecision(false, "Сегодня напоминания не нужны")
        isQuiet(config, now.toLocalTime()) -> ReminderDecision(false, "Тихие часы")
        dueTotal <= 0 -> ReminderDecision(false, "Нет карточек к повторению")
        lastActivity != null && now.toInstant().toEpochMilli() - lastActivity in 0 until RECENT_ACTIVITY_MS ->
            ReminderDecision(false, "Вы недавно занимались")
        else -> ReminderDecision(true, "Есть карточки к повторению")
    }
}
