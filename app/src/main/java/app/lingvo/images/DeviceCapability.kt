package app.lingvo.images

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlin.math.roundToInt

/**
 * Хватит ли телефону сил рисовать картинки на самом устройстве.
 *
 * Модели вроде Stable Diffusion держат в памяти больше гигабайта весов и считают на GPU.
 * Поэтому проверяем объём оперативной памяти, разрядность и версию Android. На слабом телефоне
 * функция недоступна — вместо неё остаются поиск картинки в интернете и свой сервер.
 */
object DeviceCapability {
    /** Минимум оперативной памяти. Ниже этого генерация почти наверняка упрётся в память. */
    const val MIN_RAM_GB = 5

    data class Report(
        val ramGb: Double,
        val sixtyFourBit: Boolean,
        val androidOk: Boolean,
        val lowRamDevice: Boolean,
    ) {
        val available: Boolean get() = ramGb >= MIN_RAM_GB && sixtyFourBit && androidOk && !lowRamDevice

        /** Короткая подпись для настроек — почему функция недоступна или какой запас у телефона. */
        val reasonRu: String
            get() = when {
                available -> "Доступно: ${"%.1f".format(ramGb)} ГБ памяти"
                lowRamDevice -> "Генерация картинок доступна только на мощных смартфонах: телефон работает в режиме экономии памяти"
                !sixtyFourBit -> "Генерация картинок доступна только на мощных смартфонах: нужен 64-разрядный процессор"
                !androidOk -> "Генерация картинок доступна только на мощных смартфонах: нужен Android 12 или новее"
                else -> "Генерация картинок доступна только на мощных смартфонах: нужно не меньше $MIN_RAM_GB ГБ памяти, " +
                    "у этого — ${"%.1f".format(ramGb)} ГБ"
            }
    }

    fun check(context: Context): Report {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { runCatching { am.getMemoryInfo(it) } }
        val ramGb = if (info.totalMem > 0) info.totalMem / 1024.0 / 1024.0 / 1024.0 else 0.0
        return Report(
            // Округляем до десятых: производители указывают «6 ГБ» там, где системе видно 5,7 ГБ.
            ramGb = (ramGb * 10).roundToInt() / 10.0,
            sixtyFourBit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            androidOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            lowRamDevice = runCatching { am.isLowRamDevice }.getOrDefault(false),
        )
    }
}
