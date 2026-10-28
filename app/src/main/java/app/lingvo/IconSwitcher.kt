package app.lingvo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.lingvo.core.AppIcons

/**
 * Смена иконки приложения через activity-alias: в манифесте у каждой иконки свой псевдоним
 * MainActivity, включён ровно один. Работает на всех поддерживаемых версиях Android (8.0+).
 *
 * Ограничения платформы:
 *  - лаунчер обновляет значок не мгновенно (от секунд до перезапуска лаунчера);
 *  - некоторые лаунчеры убирают ярлык с рабочего стола при смене — его нужно вернуть из списка приложений;
 *  - иконка меняется только по выбору пользователя.
 */
object IconSwitcher {
    /** Псевдоним ".Icon_<id>" из манифеста (имя относительно namespace app.lingvo). */
    private fun component(context: Context, iconId: String) = ComponentName(context, "app.lingvo.Icon_$iconId")

    /** Иконка, которая сейчас включена в системе. */
    fun current(context: Context): String {
        val pm = context.packageManager
        return AppIcons.all.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(component(context, icon.id))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon.id == AppIcons.DEFAULT
                else -> false
            }
        }?.id ?: AppIcons.DEFAULT
    }

    /** Сначала включает новую иконку, потом выключает остальные — приложение не остаётся без значка. */
    fun apply(context: Context, iconId: String) {
        if (AppIcons.byId(iconId) == null) return
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            component(context, iconId), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
        )
        AppIcons.all.filter { it.id != iconId }.forEach {
            pm.setComponentEnabledSetting(
                component(context, it.id), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
            )
        }
    }
}
