package app.lingvo.ui.components

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Планета серии — узнаваемая Земля: материки в ортографической проекции по упрощённым
 * контурам (долгота/широта), океан с объёмным освещением, полярные шапки, облака, атмосфера.
 * Земля медленно вращается. Когда дневная цель выполнена — тёплое «рассветное» сияние
 * и искорки на орбите; если нет — просто спокойная планета, без мрачных красок и упрёков.
 */
object EarthPainter {
    /** Контур: долгота, широта, долгота, широта, … */
    private fun p(vararg v: Int): FloatArray = FloatArray(v.size) { v[it].toFloat() }

    private val NORTH_AMERICA = p(
        -168, 66, -162, 70, -152, 71, -141, 70, -128, 70, -115, 68, -95, 72, -82, 73, -78, 66, -86, 64, -94, 60, -92, 57, -85, 55,
        -80, 52, -77, 56, -78, 62, -72, 61, -65, 60, -60, 55, -56, 52, -60, 47, -66, 45, -70, 42, -74, 40, -76, 35,
        -81, 31, -80, 26, -82, 26, -84, 30, -90, 29, -97, 27, -97, 22, -94, 18, -88, 21, -87, 16, -84, 11, -80, 8,
        -78, 8, -83, 9, -86, 12, -92, 15, -96, 16, -105, 20, -106, 23, -112, 30, -110, 23, -115, 30, -117, 33,
        -121, 35, -124, 40, -124, 46, -125, 50, -130, 55, -136, 58, -145, 60, -152, 59, -158, 57, -165, 60, -164, 63, -168, 66,
    )
    private val SOUTH_AMERICA = p(
        -80, 8, -77, 8, -72, 12, -62, 11, -52, 5, -50, 0, -44, -2, -35, -6, -35, -9, -39, -14, -41, -22, -48, -26,
        -53, -34, -58, -38, -62, -41, -65, -45, -68, -50, -70, -54, -74, -52, -75, -45, -73, -37, -71, -30, -70, -18,
        -76, -14, -81, -6, -80, -1, -78, 2, -78, 7,
    )
    private val EURASIA = p(
        -9, 37, -9, 43, -2, 44, -5, 48, 2, 51, 5, 53, 8, 54, 8, 57, 10, 59, 5, 61, 5, 62, 14, 67, 20, 70, 28, 71, 40, 67,
        44, 68, 55, 68, 60, 70, 70, 73, 80, 73, 90, 76, 105, 78, 115, 74, 130, 72, 140, 72, 150, 71, 160, 70, 170, 70,
        180, 68, 178, 65, 170, 60, 163, 57, 162, 54, 156, 51, 155, 57, 150, 59, 142, 59, 137, 54, 141, 52, 140, 48,
        135, 43, 130, 42, 128, 39, 126, 35, 126, 38, 125, 40, 121, 40, 118, 38, 121, 37, 122, 31, 120, 27, 117, 23,
        110, 21, 108, 19, 106, 21, 106, 17, 109, 12, 105, 9, 103, 10, 100, 13, 100, 8, 104, 1, 101, 3, 98, 8, 98, 16,
        94, 17, 92, 22, 87, 22, 80, 15, 80, 10, 77, 8, 73, 17, 72, 21, 67, 25, 62, 25, 57, 26, 52, 27, 50, 30, 48, 30,
        50, 26, 51, 24, 56, 24, 59, 22, 55, 17, 52, 16, 45, 13, 43, 15, 39, 22, 35, 28, 34, 30, 32, 31, 35, 33, 36, 36,
        30, 36, 27, 37, 26, 40, 23, 40, 23, 37, 21, 38, 19, 41, 19, 42, 13, 46, 12, 44, 15, 41, 16, 38, 15, 40, 12, 42,
        8, 44, 3, 43, 3, 42, 0, 39, -1, 37, -5, 36, -9, 37,
    )
    private val AFRICA = p(
        -17, 21, -16, 26, -13, 28, -10, 30, -9, 33, -6, 36, 0, 36, 10, 37, 11, 33, 20, 31, 25, 32, 32, 31, 34, 28, 38, 22,
        39, 16, 43, 12, 51, 12, 51, 10, 48, 5, 42, -1, 40, -5, 39, -9, 40, -15, 35, -21, 35, -25, 33, -27, 30, -31,
        26, -34, 20, -35, 18, -32, 15, -27, 12, -18, 13, -12, 12, -5, 9, -1, 9, 4, 5, 6, 1, 6, -4, 5, -8, 4, -12, 7,
        -15, 11, -17, 15, -17, 21,
    )
    private val AUSTRALIA = p(
        114, -22, 114, -26, 115, -34, 118, -35, 124, -33, 129, -32, 134, -33, 138, -35, 140, -38, 146, -39, 150, -37,
        153, -31, 153, -25, 150, -22, 146, -19, 145, -15, 142, -11, 141, -17, 137, -16, 136, -12, 132, -11, 129, -15,
        125, -14, 122, -18, 114, -22,
    )
    private val GREENLAND = p(-73, 78, -60, 82, -30, 83, -20, 80, -18, 75, -22, 70, -30, 68, -40, 65, -43, 60, -48, 61, -52, 65, -54, 68, -60, 76, -73, 78)
    private val ISLANDS = listOf(
        p(-5, 50, 1, 51, 2, 53, 0, 54, -2, 56, -2, 58, -5, 58, -6, 56, -5, 54, -3, 54, -5, 52, -5, 50), // Великобритания
        p(-10, 52, -6, 52, -6, 55, -8, 55, -10, 54), // Ирландия
        p(-24, 65, -14, 66, -14, 64, -20, 63), // Исландия
        p(130, 31, 132, 34, 135, 34, 140, 35, 141, 38, 142, 41, 140, 41, 139, 38, 137, 37, 133, 35, 130, 33), // Япония
        p(141, 42, 145, 43, 145, 45, 142, 45), // Хоккайдо
        p(44, -25, 47, -25, 50, -15, 49, -12, 44, -16, 43, -22), // Мадагаскар
        p(95, 5, 98, 4, 104, -3, 106, -6, 102, -4, 95, 3), // Суматра
        p(106, -6, 114, -8, 114, -7, 106, -6), // Ява
        p(109, 1, 111, -3, 116, -4, 119, 1, 117, 7, 113, 4), // Калимантан
        p(131, -1, 141, -3, 150, -10, 143, -9, 138, -8, 132, -4), // Новая Гвинея
        p(120, 18, 122, 18, 124, 13, 126, 7, 122, 7, 121, 14), // Филиппины
        p(172, -35, 175, -37, 178, -38, 175, -41, 171, -46, 167, -46, 170, -42, 174, -40), // Новая Зеландия
        p(80, 10, 82, 7, 80, 6), // Шри-Ланка
        p(-85, 22, -78, 22, -74, 20, -78, 20, -84, 21), // Куба
    )
    /** Пустыни и сухие равнины — тёплым песочным цветом поверх суши. */
    private val DESERTS = listOf(
        p(-16, 19, -12, 27, -5, 30, 10, 31, 20, 29, 31, 29, 33, 22, 30, 16, 15, 15, 0, 16, -16, 17), // Сахара
        p(36, 29, 44, 30, 50, 26, 56, 22, 52, 17, 44, 16, 39, 21), // Аравия
        p(117, -22, 128, -20, 138, -22, 140, -28, 132, -30, 122, -30, 116, -26), // Австралия
        p(55, 40, 70, 44, 85, 42, 100, 42, 110, 42, 105, 38, 90, 38, 75, 38, 60, 37), // Центральная Азия
        p(-118, 38, -110, 40, -104, 36, -108, 31, -114, 32), // юго-запад США
    )
    private val ANTARCTICA: FloatArray = run {
        val list = ArrayList<Float>()
        var lon = -180
        while (lon <= 180) {
            list += lon.toFloat(); list += -70f + 4f * sin(lon / 25.0).toFloat() + if (lon in -75..-55) 6f else 0f
            lon += 15
        }
        list += 180f; list += -90f; list += -180f; list += -90f
        list.toFloatArray()
    }
    private val CLOUDS = listOf( // lon, lat, радиус (градусы)
        floatArrayOf(-30f, 45f, 9f), floatArrayOf(20f, -40f, 11f), floatArrayOf(100f, 10f, 8f), floatArrayOf(160f, 35f, 10f),
        floatArrayOf(-120f, -15f, 9f), floatArrayOf(60f, 55f, 7f), floatArrayOf(-80f, 5f, 6f), floatArrayOf(-150f, 50f, 8f),
        floatArrayOf(130f, -45f, 10f), floatArrayOf(-10f, 0f, 7f),
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()

    /**
     * @param rotation долгота центра (градусы), меняется со временем;
     * @param cloudShift сдвиг облаков относительно суши (градусы);
     * @param glow 0..1 — «дыхание» сияния; @param spin 0..1 — фаза искр.
     */
    @Synchronized
    fun draw(canvas: Canvas, size: Float, rotation: Float, cloudShift: Float, todayDone: Boolean, streak: Int, glow: Float, spin: Float) {
        val cx = size / 2f
        val cy = size / 2f
        val r = size * 0.34f
        val lat0 = 18.0 * PI / 180 // небольшой наклон оси — видно северное полушарие
        val lon0 = rotation * PI / 180

        // Атмосфера и (когда цель выполнена) тёплое сияние.
        val halo = if (todayDone) 0xFFFFC56B.toInt() else 0xFF8FD3FF.toInt()
        val haloA = if (todayDone) 0.55f + 0.25f * glow else 0.35f + 0.1f * glow
        fill.style = Paint.Style.FILL
        fill.shader = RadialGradient(cx, cy, r * 1.45f, intArrayOf(alpha(halo, haloA), alpha(halo, haloA * 0.5f), 0),
            floatArrayOf(0.66f, 0.8f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.45f, fill)

        // Океан: глубокий синий с объёмом (свет сверху слева).
        fill.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.5f,
            intArrayOf(0xFF4FB3F2.toInt(), 0xFF1E73C8.toInt(), 0xFF0E3F86.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null

        canvas.save()
        path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.clipPath(path)

        val project = Projector(cx, cy, r, lat0, lon0)
        val landA = 0xFF5DBB63.toInt()
        val landB = 0xFF2E8B57.toInt()
        fill.shader = LinearGradient(0f, cy - r, 0f, cy + r, landA, landB, Shader.TileMode.CLAMP)
        listOf(NORTH_AMERICA, SOUTH_AMERICA, EURASIA, AFRICA, AUSTRALIA).forEach { project.fill(canvas, it, fill) }
        ISLANDS.forEach { project.fill(canvas, it, fill) }
        fill.shader = null
        fill.color = 0xFFD9C08A.toInt()
        DESERTS.forEach { project.fill(canvas, it, fill) }
        fill.color = 0xFFF4F8FF.toInt()
        project.fill(canvas, GREENLAND, fill)
        project.fill(canvas, ANTARCTICA, fill)
        // Арктическая шапка.
        project.fill(canvas, FloatArray(50) { i -> if (i % 2 == 0) -180f + (i / 2) * 15f else 80f + 1.5f * sin(i.toFloat()) }, fill)

        // Облака дрейфуют чуть быстрее суши.
        fill.color = 0xB3FFFFFF.toInt()
        for (cl in CLOUDS) {
            for (k in 0 until 3) {
                val lon = cl[0] + cloudShift + k * cl[2] * 0.9f
                val lat = cl[1] + if (k == 1) cl[2] * 0.35f else 0f
                project.fill(canvas, circleIn(lon, lat, cl[2] * (if (k == 1) 0.8f else 0.6f)), fill)
            }
        }

        // Ночная сторона и мягкий свет: тень снизу справа, блик сверху слева.
        fill.shader = RadialGradient(cx - r * 0.45f, cy - r * 0.5f, r * 2.1f,
            intArrayOf(0x00000000, 0x00000000, 0x660A1A40), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = RadialGradient(cx - r * 0.4f, cy - r * 0.45f, r * 0.7f, intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null
        canvas.restore()

        // Тонкий светящийся край атмосферы.
        stroke.shader = null
        stroke.strokeWidth = r * 0.04f
        stroke.color = alpha(0xFFBFE8FF.toInt(), 0.7f)
        canvas.drawCircle(cx, cy, r * 1.01f, stroke)

        // Искры на орбите: по одной за день серии (до 14), ярче в выполненный день.
        val n = min(streak, 14)
        for (i in 0 until n) {
            val a = (spin * 2 * PI + i * 2 * PI / max(n, 1)).toFloat()
            val ox = cx + cos(a) * r * 1.3f
            val oy = cy + sin(a) * r * 1.3f * 0.42f
            // За планетой искры не видны.
            if (sin(a) < 0 && hypot(ox - cx, oy - cy) < r) continue
            fill.color = if (todayDone) 0xFFFFD166.toInt() else 0xCCFFFFFF.toInt()
            canvas.drawCircle(ox, oy, size * (0.011f + 0.006f * (if (todayDone) glow else 0.3f)), fill)
        }
        if (todayDone) {
            stroke.strokeWidth = size * 0.012f
            stroke.color = alpha(0xFFFFD166.toInt(), 0.85f)
            canvas.save()
            canvas.rotate(-18f, cx, cy)
            canvas.drawArc(android.graphics.RectF(cx - r * 1.32f, cy - r * 0.5f, cx + r * 1.32f, cy + r * 0.5f), 195f, 150f, false, stroke)
            canvas.restore()
        }
    }

    private fun alpha(color: Int, a: Float): Int = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    private fun circleIn(lon: Float, lat: Float, rad: Float): FloatArray = FloatArray(24) { i ->
        val k = i / 2
        val ang = k * 2 * PI / 12
        if (i % 2 == 0) lon + (rad * cos(ang) / max(0.3, cos(lat * PI / 180))).toFloat() else lat + (rad * sin(ang)).toFloat()
    }

    /** Ортографическая проекция; точки обратной стороны прижимаются к краю диска. */
    private class Projector(val cx: Float, val cy: Float, val r: Float, val lat0: Double, val lon0: Double) {
        private val sin0 = sin(lat0)
        private val cos0 = cos(lat0)
        private val out = FloatArray(2)

        /** Возвращает true, если точка на видимой стороне. */
        fun project(lonDeg: Double, latDeg: Double): Boolean {
            val lon = lonDeg * PI / 180
            val lat = latDeg * PI / 180
            val cosLat = cos(lat)
            var x = cosLat * sin(lon - lon0)
            var y = cos0 * sin(lat) - sin0 * cosLat * cos(lon - lon0)
            val visible = sin0 * sin(lat) + cos0 * cosLat * cos(lon - lon0) >= 0
            if (!visible) {
                val l = sqrt(x * x + y * y).coerceAtLeast(1e-6)
                x /= l; y /= l
            }
            out[0] = cx + (x * r).toFloat()
            out[1] = cy - (y * r).toFloat()
            return visible
        }

        fun fill(canvas: Canvas, poly: FloatArray, paint: Paint) {
            val p = Path()
            var anyVisible = false
            val n = poly.size / 2
            var first = true
            for (i in 0 until n) {
                val lon1 = poly[2 * i].toDouble(); val lat1 = poly[2 * i + 1].toDouble()
                val j = (i + 1) % n
                var lon2 = poly[2 * j].toDouble(); val lat2 = poly[2 * j + 1].toDouble()
                if (lon2 - lon1 > 180) lon2 -= 360 else if (lon1 - lon2 > 180) lon2 += 360
                // Дробим ребро, чтобы контур ложился по кривизне шара.
                val steps = max(1, (max(kotlin.math.abs(lon2 - lon1), kotlin.math.abs(lat2 - lat1)) / 3.0).toInt())
                for (s in 0 until steps) {
                    val t = s.toDouble() / steps
                    val vis = project(lon1 + (lon2 - lon1) * t, lat1 + (lat2 - lat1) * t)
                    anyVisible = anyVisible || vis
                    if (first) { p.moveTo(out[0], out[1]); first = false } else p.lineTo(out[0], out[1])
                }
            }
            p.close()
            if (anyVisible) canvas.drawPath(p, paint)
        }
    }
}
