package app.lingvo.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import app.lingvo.core.CreatureDef
import app.lingvo.core.CreatureShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Настроение существа: реакция на то, что происходит в занятии. */
enum class Mood {
    /** Обычное состояние: смотрит, моргает. */
    CALM,
    /** Доволен (правильный ответ, главный экран). */
    HAPPY,
    /** Ликует: конец занятия, выполненная цель, достижение. */
    CHEER,
    /** Дремлет (давно не занимались) — без упрёков. */
    SLEEPY,
    /** Мягко поддерживает после ошибки: «ничего, вспомним». */
    SUPPORT,
    /** Гордится: новый уровень, новая поза. */
    PROUD,
}

/** Случайные «живые» движения в покое. */
enum class CreatureAction { NONE, HOP, WIGGLE, LOOK, WAVE }

/** Кадр анимации: дыхание, моргание, текущее движение и его прогресс 0..1. */
data class CreaturePose(
    val breath: Float = 0.5f,
    val blink: Float = 0f,
    val action: CreatureAction = CreatureAction.NONE,
    val actionT: Float = 0f,
    /** Фаза вращения искр (уровень 3, ликование). */
    val spin: Float = 0f,
)

/**
 * Рисует существ-полиглотов на обычном android.graphics.Canvas — одним кодом для экранов приложения,
 * значка в уведомлении-острове и плавающей капсулы.
 *
 * Стиль: пастельные цвета, мягкие округлые формы, крупные блестящие глаза и румянец. У каждого вида
 * свой силуэт: птенец с хохолком и крылышками, аксолотль с жабрами, лисёнок с хвостом-огоньком,
 * облачный зайчик с длинными ушами, белка с хвостом-молнией, черепашка с панцирем-глобусом,
 * совёнок в очках с книгой, хрустальный оленёнок с рожками, космическая кошка со звёздами.
 * Рисование идёт в единичных координатах (0..1), размер задаётся масштабом.
 */
object CreaturePainter {
    private const val INK = 0xFF2A2440.toInt()
    private const val BLUSH = 0x66FF8FB1
    private const val WHITE = 0xFFFFFFFF.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /** Готовая картинка — для уведомлений и капсулы. */
    fun bitmap(def: CreatureDef, level: Int, mood: Mood, sizePx: Int, pose: CreaturePose = CreaturePose()): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), sizePx.toFloat(), def, level, mood, pose, locked = false)
        return bmp
    }

    @Synchronized
    fun draw(canvas: Canvas, size: Float, def: CreatureDef, level: Int, mood: Mood, pose: CreaturePose, locked: Boolean) {
        canvas.save()
        canvas.scale(size, size)
        val a = if (locked) 0xFFD9DCEA.toInt() else def.colorA.toInt()
        val b = if (locked) 0xFFC3C8DA.toInt() else def.colorB.toInt()
        val accent = if (locked) 0xFFB8BED3.toInt() else accentOf(def.shape)

        if (!locked && level >= 2) glow(canvas, def.rarity.color.toInt(), 0.5f, 0.55f, 0.5f, 0.4f)
        // Тень на земле.
        fill.shader = null; fill.color = 0x22000000
        val hop = if (pose.action == CreatureAction.HOP) sin(pose.actionT * PI).toFloat() * 0.07f else 0f
        val cheerHop = if (mood == Mood.CHEER) ((sin(pose.spin * 4 * PI).toFloat() + 1f) / 2f) * 0.035f else 0f
        val lift = hop + cheerHop
        canvas.drawOval(RectF(0.26f + lift, 0.905f, 0.74f - lift, 0.955f), fill)

        canvas.save()
        canvas.translate(0f, -lift)
        // Дыхание: лёгкое сжатие-растяжение от земли.
        val squash = 1f + (pose.breath - 0.5f) * 0.05f
        canvas.scale(1f / squash, squash, 0.5f, 0.92f)
        if (mood == Mood.PROUD) canvas.rotate(-4f, 0.5f, 0.92f)

        val wig = if (pose.action == CreatureAction.WIGGLE) sin(pose.actionT * 4 * PI).toFloat() * 14f else 0f
        val wave = if (pose.action == CreatureAction.WAVE || mood == Mood.CHEER) sin(pose.actionT * 6 * PI + pose.spin * 8 * PI).toFloat() else 0f
        val look = if (pose.action == CreatureAction.LOOK) sin(pose.actionT * 2 * PI).toFloat() * 0.025f else 0f
        val face = Face(look = look, blink = if (locked) 0f else pose.blink, mood = mood, hidden = locked)

        when (def.shape) {
            CreatureShape.CHICK -> chick(canvas, a, b, accent, face, wig, wave)
            CreatureShape.AXOLOTL -> axolotl(canvas, a, b, accent, face, wig, pose.breath)
            CreatureShape.FOX -> fox(canvas, a, b, accent, face, wig, pose.breath, locked)
            CreatureShape.BUNNY -> bunny(canvas, a, b, accent, face, wig)
            CreatureShape.SQUIRREL -> squirrel(canvas, a, b, accent, face, wig)
            CreatureShape.TURTLE -> turtle(canvas, a, b, accent, face, wig, locked)
            CreatureShape.OWL -> owl(canvas, a, b, accent, face, wave)
            CreatureShape.DEER -> deer(canvas, a, b, accent, face, wig, locked)
            CreatureShape.CAT -> cat(canvas, a, b, accent, face, wig, pose.spin, locked)
        }
        if (locked) question(canvas)
        canvas.restore()

        if (!locked && level >= 3) crown(canvas, def.shape)
        if (!locked && (level >= 3 || mood == Mood.CHEER || mood == Mood.PROUD)) sparkles(canvas, pose.spin, if (mood == Mood.CHEER) 0xFFFFD166.toInt() else WHITE)
        if (!locked && mood == Mood.SUPPORT) heart(canvas, 0.8f, 0.3f - pose.breath * 0.03f, 0.06f)
        if (!locked && mood == Mood.SLEEPY) zz(canvas, pose.breath)
        canvas.restore()
    }

    private data class Face(val look: Float, val blink: Float, val mood: Mood, val hidden: Boolean = false)

    private fun accentOf(shape: CreatureShape): Int = when (shape) {
        CreatureShape.CHICK -> 0xFFFFA24C.toInt()
        CreatureShape.AXOLOTL -> 0xFFFF7FAF.toInt()
        CreatureShape.FOX -> 0xFFFFD166.toInt()
        CreatureShape.BUNNY -> 0xFFFFB8D2.toInt()
        CreatureShape.SQUIRREL -> 0xFFFFF3B0.toInt()
        CreatureShape.TURTLE -> 0xFF7CC6FF.toInt()
        CreatureShape.OWL -> 0xFF8A6BBE.toInt()
        CreatureShape.DEER -> 0xFFB8F1FF.toInt()
        CreatureShape.CAT -> 0xFFFFF1A8.toInt()
    }

    // ---------- Общие детали ----------

    private fun gradient(top: Int, bottom: Int, y0: Float, y1: Float) =
        LinearGradient(0f, y0, 0f, y1, top, bottom, Shader.TileMode.CLAMP)

    private fun body(c: Canvas, path: Path, a: Int, b: Int, y0: Float, y1: Float) {
        fill.shader = gradient(a, b, y0, y1)
        c.drawPath(path, fill)
        fill.shader = null
        stroke.shader = null; stroke.color = 0x14000000; stroke.strokeWidth = 0.012f
        c.drawPath(path, stroke)
    }

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, btm: Float, color: Int) {
        fill.shader = null; fill.color = color
        c.drawOval(RectF(l, t, r, btm), fill)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.shader = null; fill.color = color
        c.drawCircle(x, y, r, fill)
    }

    private fun shine(c: Canvas, x: Float, y: Float, r: Float) = circle(c, x, y, r, 0x40FFFFFF)

    private fun glow(c: Canvas, color: Int, x: Float, y: Float, r: Float, alpha: Float) {
        val col = (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
        fill.shader = RadialGradient(x, y, r, col, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, fill)
        fill.shader = null
    }

    private fun blob(cx: Float, cy: Float, rx: Float, ry: Float, bottomFlat: Float = 0.12f) = Path().apply {
        // Мягкое «бобовое» тело: снизу чуть шире и площе.
        moveTo(cx, cy - ry)
        cubicTo(cx + rx * 0.75f, cy - ry, cx + rx * 1.05f, cy - ry * 0.2f, cx + rx, cy + ry * 0.35f)
        cubicTo(cx + rx * 0.95f, cy + ry * (0.9f + bottomFlat), cx + rx * 0.4f, cy + ry, cx, cy + ry)
        cubicTo(cx - rx * 0.4f, cy + ry, cx - rx * 0.95f, cy + ry * (0.9f + bottomFlat), cx - rx, cy + ry * 0.35f)
        cubicTo(cx - rx * 1.05f, cy - ry * 0.2f, cx - rx * 0.75f, cy - ry, cx, cy - ry)
        close()
    }

    /** Глаза, румянец и рот. [mouth]: 0 — обычный рот, 1 — клювик, 2 — кошачья «w». */
    private fun face(c: Canvas, f: Face, cx: Float, eyeY: Float, dx: Float, r: Float, mouth: Int = 0, mouthDy: Float = 0.085f) {
        if (f.hidden) return
        stroke.shader = null
        for (s in listOf(-1f, 1f)) {
            val ex = cx + s * dx
            when (f.mood) {
                Mood.HAPPY, Mood.CHEER, Mood.PROUD -> {
                    stroke.color = INK; stroke.strokeWidth = r * 0.42f
                    c.drawArc(RectF(ex - r, eyeY - r * 0.7f, ex + r, eyeY + r * 1.0f), 200f, 140f, false, stroke)
                }
                Mood.SLEEPY -> {
                    stroke.color = INK; stroke.strokeWidth = r * 0.36f
                    c.drawArc(RectF(ex - r, eyeY - r * 0.9f, ex + r, eyeY + r * 0.5f), 20f, 140f, false, stroke)
                }
                Mood.CALM, Mood.SUPPORT -> {
                    val open = (1f - f.blink).coerceIn(0f, 1f)
                    if (open < 0.18f) {
                        stroke.color = INK; stroke.strokeWidth = r * 0.36f
                        c.drawArc(RectF(ex - r, eyeY - r * 0.6f, ex + r, eyeY + r * 0.6f), 20f, 140f, false, stroke)
                    } else {
                        val ry = r * 1.18f * open
                        oval(c, ex - r + f.look * 0.3f, eyeY - ry, ex + r + f.look * 0.3f, eyeY + ry, INK)
                        circle(c, ex - r * 0.32f + f.look, eyeY - ry * 0.42f, r * 0.38f, WHITE)
                        circle(c, ex + r * 0.38f + f.look, eyeY + ry * 0.35f, r * 0.17f, 0xCCFFFFFF.toInt())
                    }
                    if (f.mood == Mood.SUPPORT) {
                        // Мягкие «домиком» бровки — сочувствие, а не упрёк.
                        stroke.color = INK; stroke.strokeWidth = r * 0.26f
                        c.drawLine(ex - s * r * 0.9f, eyeY - r * 2.2f, ex + s * r * 0.6f, eyeY - r * 1.75f, stroke)
                    }
                }
            }
            oval(c, ex + s * r * 0.9f - r * 0.9f, eyeY + r * 1.25f, ex + s * r * 0.9f + r * 0.9f, eyeY + r * 1.85f, BLUSH)
        }
        val my = eyeY + mouthDy
        stroke.color = INK; stroke.strokeWidth = r * 0.3f
        when (mouth) {
            1 -> { // клювик
                fill.shader = null; fill.color = 0xFFFF9F43.toInt()
                val p = Path().apply { moveTo(cx - r * 0.75f, my - r * 0.6f); lineTo(cx + r * 0.75f, my - r * 0.6f); lineTo(cx, my + r * 0.55f); close() }
                c.drawPath(p, fill)
                if (f.mood == Mood.CHEER) {
                    fill.color = 0xFFE8743B.toInt()
                    c.drawPath(Path().apply { moveTo(cx - r * 0.5f, my); lineTo(cx + r * 0.5f, my); lineTo(cx, my + r * 0.9f); close() }, fill)
                }
            }
            2 -> { // кошачий ротик
                if (f.mood == Mood.CHEER) openMouth(c, cx, my, r)
                else {
                    c.drawArc(RectF(cx - r * 0.9f, my - r * 0.5f, cx, my + r * 0.45f), 0f, 160f, false, stroke)
                    c.drawArc(RectF(cx, my - r * 0.5f, cx + r * 0.9f, my + r * 0.45f), 20f, 160f, false, stroke)
                }
            }
            else -> when (f.mood) {
                Mood.CHEER -> openMouth(c, cx, my, r)
                Mood.SLEEPY -> circle(c, cx, my + r * 0.1f, r * 0.28f, INK)
                Mood.SUPPORT -> c.drawArc(RectF(cx - r * 0.6f, my - r * 0.35f, cx + r * 0.6f, my + r * 0.35f), 20f, 140f, false, stroke)
                else -> c.drawArc(RectF(cx - r * 0.85f, my - r * 0.6f, cx + r * 0.85f, my + r * 0.5f), 15f, 150f, false, stroke)
            }
        }
    }

    private fun openMouth(c: Canvas, cx: Float, my: Float, r: Float) {
        val p = Path().apply {
            moveTo(cx - r * 0.95f, my - r * 0.25f)
            quadTo(cx, my - r * 0.05f, cx + r * 0.95f, my - r * 0.25f)
            quadTo(cx + r * 0.7f, my + r * 1.3f, cx, my + r * 1.3f)
            quadTo(cx - r * 0.7f, my + r * 1.3f, cx - r * 0.95f, my - r * 0.25f)
            close()
        }
        fill.shader = null; fill.color = 0xFF5B2333.toInt(); c.drawPath(p, fill)
        circle(c, cx, my + r * 0.95f, r * 0.45f, 0xFFFF8FA3.toInt())
    }

    // ---------- Виды ----------

    private fun chick(c: Canvas, a: Int, b: Int, acc: Int, f: Face, wig: Float, wave: Float) {
        // Хохолок из трёх пёрышек.
        for ((i, ang) in listOf(-28f, 0f, 28f).withIndex()) {
            c.save(); c.rotate(ang + wig * 0.6f, 0.5f, 0.3f)
            oval(c, 0.47f, 0.13f + if (i == 1) -0.02f else 0f, 0.53f, 0.3f, b)
            c.restore()
        }
        // Лапки.
        for (s in listOf(-1f, 1f)) oval(c, 0.5f + s * 0.11f - 0.055f, 0.86f, 0.5f + s * 0.11f + 0.055f, 0.92f, acc)
        body(c, blob(0.5f, 0.58f, 0.3f, 0.31f), a, b, 0.27f, 0.9f)
        // Пушистые щёчки-перья по бокам.
        for (s in listOf(-1f, 1f)) {
            c.save(); c.rotate(s * (20f + wave * 18f), 0.5f + s * 0.27f, 0.6f)
            oval(c, 0.5f + s * 0.27f - 0.075f, 0.56f, 0.5f + s * 0.27f + 0.075f, 0.76f, b)
            c.restore()
        }
        oval(c, 0.36f, 0.66f, 0.64f, 0.87f, 0x55FFFFFF)
        shine(c, 0.38f, 0.4f, 0.08f)
        face(c, f, 0.5f, 0.52f, 0.11f, 0.042f, mouth = 1, mouthDy = 0.07f)
    }

    private fun axolotl(c: Canvas, a: Int, b: Int, acc: Int, f: Face, wig: Float, breath: Float) {
        // Хвост-плавник справа.
        c.save(); c.rotate(wig * 0.8f, 0.72f, 0.78f)
        body(c, Path().apply {
            moveTo(0.68f, 0.7f); cubicTo(0.86f, 0.62f, 0.98f, 0.7f, 0.96f, 0.82f); cubicTo(0.88f, 0.86f, 0.78f, 0.88f, 0.68f, 0.86f); close()
        }, a, b, 0.62f, 0.88f)
        c.restore()
        // Жабры: по три пушистые веточки с каждой стороны, «дышат».
        val sway = (breath - 0.5f) * 10f + wig * 0.5f
        for (s in listOf(-1f, 1f)) for ((i, ang) in listOf(-35f, -5f, 25f).withIndex()) {
            c.save(); c.rotate(s * (ang + sway), 0.5f + s * 0.24f, 0.46f)
            val x = 0.5f + s * 0.24f
            stroke.shader = null; stroke.color = acc; stroke.strokeWidth = 0.035f
            c.drawLine(x, 0.46f, x + s * 0.13f, 0.46f, stroke)
            for (k in 1..3) circle(c, x + s * 0.035f * k + s * 0.02f, 0.46f - 0.022f, 0.016f + i * 0.001f, acc)
            c.restore()
        }
        body(c, blob(0.5f, 0.6f, 0.29f, 0.29f), a, b, 0.3f, 0.9f)
        // Пятнышки и светлое брюшко.
        oval(c, 0.38f, 0.7f, 0.62f, 0.88f, 0x55FFFFFF)
        for ((x, y) in listOf(0.33f to 0.73f, 0.68f to 0.69f, 0.62f to 0.36f)) circle(c, x, y, 0.017f, 0x55FF7FAF)
        shine(c, 0.38f, 0.42f, 0.08f)
        face(c, f, 0.5f, 0.55f, 0.15f, 0.04f, mouthDy = 0.075f)
    }

    private fun fox(c: Canvas, a: Int, b: Int, acc: Int, f: Face, wig: Float, breath: Float, locked: Boolean) {
        // Пушистый хвост с огоньком на кончике.
        c.save(); c.rotate(-10f + wig, 0.7f, 0.82f)
        val tail = Path().apply {
            moveTo(0.66f, 0.84f); cubicTo(0.86f, 0.86f, 1.0f, 0.62f, 0.9f, 0.4f)
            cubicTo(0.84f, 0.5f, 0.76f, 0.56f, 0.7f, 0.66f); close()
        }
        body(c, tail, b, a, 0.4f, 0.86f)
        if (!locked) {
            val flick = (breath - 0.5f) * 0.03f
            fill.shader = LinearGradient(0f, 0.3f, 0f, 0.5f, 0xFFFFF3B0.toInt(), acc, Shader.TileMode.CLAMP)
            c.drawPath(Path().apply {
                moveTo(0.9f, 0.3f - flick); cubicTo(0.97f, 0.4f, 0.95f, 0.48f, 0.88f, 0.5f)
                cubicTo(0.83f, 0.47f, 0.82f, 0.42f, 0.86f, 0.38f); cubicTo(0.86f, 0.35f, 0.88f, 0.33f, 0.9f, 0.3f - flick); close()
            }, fill)
            fill.shader = null
        }
        c.restore()
        // Острые уши с тёмными кончиками.
        for (s in listOf(-1f, 1f)) {
            c.save(); c.rotate(s * wig * 0.4f, 0.5f + s * 0.17f, 0.36f)
            val ear = Path().apply { moveTo(0.5f + s * 0.08f, 0.36f); lineTo(0.5f + s * 0.22f, 0.13f); lineTo(0.5f + s * 0.29f, 0.42f); close() }
            body(c, ear, a, b, 0.13f, 0.42f)
            fill.shader = null; fill.color = if (locked) 0x22000000 else 0xFF8A4B3A.toInt()
            c.drawPath(Path().apply { moveTo(0.5f + s * 0.18f, 0.2f); lineTo(0.5f + s * 0.22f, 0.13f); lineTo(0.5f + s * 0.245f, 0.22f); close() }, fill)
            fill.color = 0x66FFFFFF
            c.drawPath(Path().apply { moveTo(0.5f + s * 0.13f, 0.36f); lineTo(0.5f + s * 0.21f, 0.22f); lineTo(0.5f + s * 0.24f, 0.39f); close() }, fill)
            c.restore()
        }
        body(c, blob(0.5f, 0.6f, 0.28f, 0.29f), a, b, 0.3f, 0.9f)
        // Белая мордочка и грудка.
        oval(c, 0.36f, 0.56f, 0.64f, 0.76f, 0xEEFFF8F0.toInt())
        oval(c, 0.4f, 0.72f, 0.6f, 0.88f, 0xBBFFF8F0.toInt())
        circle(c, 0.5f, 0.615f, 0.022f, INK)
        shine(c, 0.37f, 0.42f, 0.07f)
        face(c, f, 0.5f, 0.53f, 0.12f, 0.04f, mouthDy = 0.12f)
    }

    private fun bunny(c: Canvas, a: Int, b: Int, acc: Int, f: Face, wig: Float) {
        // Длинные уши.
        for (s in listOf(-1f, 1f)) {
            c.save(); c.rotate(s * (8f + wig * 0.7f) + if (s > 0) wig * 0.5f else 0f, 0.5f + s * 0.1f, 0.4f)
            val l = 0.5f + s * 0.1f - 0.065f
            body(c, Path().apply { addOval(RectF(l, 0.04f, l + 0.13f, 0.44f), Path.Direction.CW) }, a, b, 0.04f, 0.44f)
            oval(c, l + 0.035f, 0.1f, l + 0.095f, 0.38f, (acc and 0x00FFFFFF) or 0xAA000000.toInt())
            c.restore()
        }
        body(c, blob(0.5f, 0.6f, 0.27f, 0.27f), a, b, 0.32f, 0.88f)
        // Облачко у ног.
        for ((x, r) in listOf(0.28f to 0.09f, 0.42f to 0.1f, 0.58f to 0.1f, 0.72f to 0.09f)) circle(c, x, 0.87f, r, 0xF2FFFFFF.toInt())
        circle(c, 0.5f, 0.82f, 0.08f, 0xF2FFFFFF.toInt())
        shine(c, 0.39f, 0.45f, 0.07f)
        face(c, f, 0.5f, 0.58f, 0.1f, 0.04f, mouthDy = 0.085f)
        fill.color = acc
        c.drawPath(Path().apply { moveTo(0.475f, 0.625f); lineTo(0.525f, 0.625f); lineTo(0.5f, 0.65f); close() }, fill)
    }

    private fun squirrel(c: Canvas, a: Int, b: Int, acc: Int, f: Face, wig: Float) {
        // Большой закрученный хвост с полоской-молнией.
        c.save(); c.rotate(wig * 0.7f, 0.7f, 0.85f)
        val tail = Path().apply {
            moveTo(0.64f, 0.88f); cubicTo(0.98f, 0.9f, 1.02f, 0.4f, 0.82f, 0.2f)
            cubicTo(0.7f, 0.1f, 0.56f, 0.2f, 0.66f, 0.3f); cubicTo(0.84f, 0.42f, 0.84f, 0.7f, 0.66f, 0.72f); close()
        }
        body(c, tail, b, a, 0.15f, 0.9f)
        fill.shader = null; fill.color = acc
        c.drawPath(Path().apply {
            moveTo(0.84f, 0.3f); lineTo(0.78f, 0.47f); lineTo(0.85f, 0.47f); lineTo(0.79f, 0.66f); lineTo(0.92f, 0.43f); lineTo(0.85f, 0.43f); lineTo(0.9f, 0.3f); close()
        }, fill)
        c.restore()
        for (s in listOf(-1f, 1f)) {
            val ear = Path().apply { moveTo(0.5f + s * 0.1f, 0.38f); quadTo(0.5f + s * 0.17f, 0.16f, 0.5f + s * 0.25f, 0.36f); close() }
            body(c, ear, a, b, 0.2f, 0.4f)
        }
        body(c, blob(0.5f, 0.61f, 0.25f, 0.28f), a, b, 0.33f, 0.89f)
        oval(c, 0.39f, 0.64f, 0.61f, 0.88f, 0x66FFFFFF)
        shine(c, 0.4f, 0.45f, 0.06f)
        face(c, f, 0.5f, 0.54f, 0.1f, 0.04f, mouthDy = 0.07f)
        if (f.mood != Mood.CHEER) {
            fill.color = WHITE; c.drawRect(0.487f, 0.625f, 0.513f, 0.66f, fill)
            stroke.color = 0x33000000; stroke.strokeWidth = 0.004f; c.drawLine(0.5f, 0.625f, 0.5f, 0.66f, stroke)
        }
    }

    private fun turtle(c: Canvas, a: Int, b: Int, acc: Int, f: Face, wig: Float, locked: Boolean) {
        // Лапки и хвостик.
        for ((x, y) in listOf(0.24f to 0.84f, 0.76f to 0.84f, 0.34f to 0.88f, 0.66f to 0.88f)) oval(c, x - 0.06f, y - 0.04f, x + 0.06f, y + 0.04f, b)
        // Голова выглядывает сверху (чуть покачивается).
        c.save(); c.rotate(wig * 0.4f, 0.5f, 0.55f)
        oval(c, 0.42f, 0.42f, 0.58f, 0.62f, b)
        body(c, Path().apply { addOval(RectF(0.32f, 0.2f, 0.68f, 0.54f), Path.Direction.CW) }, a, b, 0.2f, 0.54f)
        shine(c, 0.42f, 0.29f, 0.05f)
        face(c, f, 0.5f, 0.36f, 0.08f, 0.035f, mouthDy = 0.07f)
        c.restore()
        // Панцирь-глобус: океан, материки, блик.
        val shell = Path().apply {
            moveTo(0.14f, 0.82f); cubicTo(0.12f, 0.46f, 0.88f, 0.46f, 0.86f, 0.82f); close()
        }
        val ocean = if (locked) 0xFFC9CEDE.toInt() else acc
        body(c, shell, ocean, if (locked) 0xFFB4BAD0.toInt() else 0xFF4C9BE8.toInt(), 0.5f, 0.82f)
        c.save(); c.clipPath(shell)
        val land = if (locked) 0xFFDDE1EC.toInt() else 0xFF8EDB8E.toInt()
        oval(c, 0.22f, 0.6f, 0.4f, 0.74f, land); oval(c, 0.46f, 0.56f, 0.6f, 0.68f, land); oval(c, 0.58f, 0.68f, 0.78f, 0.8f, land)
        stroke.color = 0x55FFFFFF; stroke.strokeWidth = 0.01f
        c.drawArc(RectF(0.2f, 0.5f, 0.8f, 0.96f), 200f, 140f, false, stroke)
        c.restore()
        stroke.color = (b and 0x00FFFFFF) or 0xFF000000.toInt(); stroke.strokeWidth = 0.03f
        c.drawLine(0.15f, 0.82f, 0.85f, 0.82f, stroke)
    }

    private fun owl(c: Canvas, a: Int, b: Int, acc: Int, f: Face, wave: Float) {
        for (s in listOf(-1f, 1f)) {
            body(c, Path().apply { moveTo(0.5f + s * 0.12f, 0.3f); lineTo(0.5f + s * 0.25f, 0.15f); lineTo(0.5f + s * 0.27f, 0.36f); close() }, b, a, 0.15f, 0.36f)
        }
        body(c, blob(0.5f, 0.58f, 0.29f, 0.32f), a, b, 0.26f, 0.9f)
        // Крылышки (машут при ликовании).
        for (s in listOf(-1f, 1f)) {
            c.save(); c.rotate(s * (10f + wave * 15f), 0.5f + s * 0.26f, 0.52f)
            oval(c, 0.5f + s * 0.26f - 0.07f, 0.5f, 0.5f + s * 0.26f + 0.07f, 0.78f, b)
            c.restore()
        }
        // Пёрышки на груди.
        stroke.color = 0x33FFFFFF; stroke.strokeWidth = 0.012f
        for ((x, y) in listOf(0.44f to 0.68f, 0.56f to 0.68f, 0.5f to 0.73f)) c.drawArc(RectF(x - 0.04f, y - 0.03f, x + 0.04f, y + 0.03f), 20f, 140f, false, stroke)
        // Светлые круги вокруг глаз и очки.
        for (s in listOf(-1f, 1f)) circle(c, 0.5f + s * 0.12f, 0.47f, 0.095f, 0xEEFFF8EE.toInt())
        face(c, f, 0.5f, 0.47f, 0.12f, 0.042f, mouth = 1, mouthDy = 0.075f)
        stroke.color = acc; stroke.strokeWidth = 0.016f
        for (s in listOf(-1f, 1f)) c.drawCircle(0.5f + s * 0.12f, 0.47f, 0.085f, stroke)
        c.drawLine(0.465f, 0.46f, 0.535f, 0.46f, stroke)
        // Книга в лапках.
        fill.color = acc
        c.drawRoundRect(RectF(0.34f, 0.74f, 0.66f, 0.88f), 0.02f, 0.02f, fill)
        fill.color = 0xFFFFFBF0.toInt()
        c.drawRect(0.36f, 0.755f, 0.495f, 0.865f, fill); c.drawRect(0.505f, 0.755f, 0.64f, 0.865f, fill)
        stroke.color = 0x33000000; stroke.strokeWidth = 0.006f
        for (y in listOf(0.78f, 0.81f, 0.84f)) { c.drawLine(0.38f, y, 0.475f, y, stroke); c.drawLine(0.525f, y, 0.62f, y, stroke) }
    }

    private fun deer(c: Canvas, a: Int, b: Int, acc: Int, f: Face, wig: Float, locked: Boolean) {
        // Хрустальные рожки.
        for (s in listOf(-1f, 1f)) {
            c.save(); c.rotate(s * wig * 0.3f, 0.5f + s * 0.12f, 0.3f)
            val horn = Path().apply {
                moveTo(0.5f + s * 0.09f, 0.32f); lineTo(0.5f + s * 0.13f, 0.08f); lineTo(0.5f + s * 0.17f, 0.32f); close()
                moveTo(0.5f + s * 0.135f, 0.2f); lineTo(0.5f + s * 0.24f, 0.12f); lineTo(0.5f + s * 0.17f, 0.25f); close()
            }
            fill.shader = LinearGradient(0f, 0.08f, 0f, 0.32f, WHITE, if (locked) 0xFFD0D4E2.toInt() else acc, Shader.TileMode.CLAMP)
            c.drawPath(horn, fill); fill.shader = null
            stroke.color = 0x55FFFFFF; stroke.strokeWidth = 0.008f; c.drawPath(horn, stroke)
            c.restore()
            // Ушки в стороны.
            c.save(); c.rotate(s * (25f + wig * 0.6f), 0.5f + s * 0.22f, 0.38f)
            body(c, Path().apply { addOval(RectF(0.5f + s * 0.22f - 0.09f, 0.34f, 0.5f + s * 0.22f + 0.09f, 0.42f), Path.Direction.CW) }, a, b, 0.34f, 0.42f)
            c.restore()
        }
        body(c, blob(0.5f, 0.61f, 0.26f, 0.28f), a, b, 0.33f, 0.89f)
        for ((x, y) in listOf(0.34f to 0.72f, 0.4f to 0.79f, 0.64f to 0.74f, 0.6f to 0.81f)) circle(c, x, y, 0.016f, 0x88FFFFFF.toInt())
        oval(c, 0.4f, 0.6f, 0.6f, 0.72f, 0x66FFFFFF)
        circle(c, 0.5f, 0.635f, 0.018f, 0xFF7A4E73.toInt())
        shine(c, 0.4f, 0.45f, 0.06f)
        face(c, f, 0.5f, 0.55f, 0.1f, 0.04f, mouthDy = 0.12f)
    }

    private fun cat(c: Canvas, a: Int, b: Int, acc: Int, f: Face, wig: Float, spin: Float, locked: Boolean) {
        // Хвост-завиток.
        c.save(); c.rotate(wig, 0.72f, 0.84f)
        stroke.shader = null; stroke.color = b; stroke.strokeWidth = 0.06f
        val tail = Path().apply { moveTo(0.7f, 0.84f); cubicTo(0.95f, 0.84f, 0.95f, 0.5f, 0.82f, 0.5f) }
        c.drawPath(tail, stroke)
        c.restore()
        for (s in listOf(-1f, 1f)) {
            c.save(); c.rotate(s * wig * 0.4f, 0.5f + s * 0.16f, 0.38f)
            body(c, Path().apply { moveTo(0.5f + s * 0.06f, 0.36f); lineTo(0.5f + s * 0.2f, 0.16f); lineTo(0.5f + s * 0.27f, 0.42f); close() }, a, b, 0.16f, 0.42f)
            fill.color = 0x55FFB8D2
            c.drawPath(Path().apply { moveTo(0.5f + s * 0.11f, 0.36f); lineTo(0.5f + s * 0.195f, 0.23f); lineTo(0.5f + s * 0.235f, 0.39f); close() }, fill)
            c.restore()
        }
        body(c, blob(0.5f, 0.61f, 0.27f, 0.28f), a, b, 0.33f, 0.89f)
        if (!locked) {
            // Звёзды в шёрстке мерцают.
            for ((i, p) in listOf(0.33f to 0.72f, 0.66f to 0.78f, 0.6f to 0.42f, 0.4f to 0.83f).withIndex()) {
                val tw = 0.6f + 0.4f * sin((spin * 6 + i) * PI).toFloat()
                star(c, p.first, p.second, 0.022f * tw, acc)
            }
            // Месяц на лбу.
            circle(c, 0.5f, 0.42f, 0.03f, acc)
            circle(c, 0.512f, 0.413f, 0.026f, b)
        }
        shine(c, 0.39f, 0.46f, 0.06f)
        face(c, f, 0.5f, 0.56f, 0.11f, 0.04f, mouth = 2, mouthDy = 0.075f)
        // Усы.
        stroke.color = 0x88FFFFFF.toInt(); stroke.strokeWidth = 0.006f
        for (s in listOf(-1f, 1f)) for (dy in listOf(-0.01f, 0.015f)) c.drawLine(0.5f + s * 0.17f, 0.635f + dy, 0.5f + s * 0.27f, 0.62f + dy * 1.6f, stroke)
    }

    // ---------- Украшения ----------

    private fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        val p = Path()
        for (i in 0 until 8) {
            val rr = if (i % 2 == 0) r else r * 0.4f
            val ang = -PI / 2 + i * PI / 4
            val px = x + (rr * cos(ang)).toFloat(); val py = y + (rr * sin(ang)).toFloat()
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close()
        fill.shader = null; fill.color = color; c.drawPath(p, fill)
    }

    private fun sparkles(c: Canvas, spin: Float, color: Int) {
        for (i in 0 until 5) {
            val ang = (spin * 2 * PI + i * 2 * PI / 5).toFloat()
            val r = 0.44f
            val tw = 0.5f + 0.5f * sin(spin * 10 * PI + i).toFloat()
            star(c, 0.5f + cos(ang) * r, 0.52f + sin(ang) * r * 0.8f, 0.018f + 0.016f * tw, color)
        }
    }

    private fun heart(c: Canvas, x: Float, y: Float, s: Float) {
        val p = Path().apply {
            moveTo(x, y + s * 0.9f)
            cubicTo(x - s * 1.2f, y + s * 0.1f, x - s * 0.6f, y - s * 0.8f, x, y - s * 0.2f)
            cubicTo(x + s * 0.6f, y - s * 0.8f, x + s * 1.2f, y + s * 0.1f, x, y + s * 0.9f)
            close()
        }
        fill.shader = null; fill.color = 0xFFFF7A9C.toInt(); c.drawPath(p, fill)
    }

    private fun zz(c: Canvas, breath: Float) {
        stroke.color = 0xAA7B5CFF.toInt(); stroke.strokeWidth = 0.014f
        val x = 0.76f; val y = 0.2f - breath * 0.03f
        c.drawPath(Path().apply { moveTo(x, y); lineTo(x + 0.06f, y); lineTo(x, y + 0.06f); lineTo(x + 0.06f, y + 0.06f) }, stroke)
    }

    private fun crown(c: Canvas, shape: CreatureShape) {
        val top = when (shape) { CreatureShape.TURTLE -> 0.08f; CreatureShape.BUNNY, CreatureShape.DEER -> 0.0f; else -> 0.1f }
        val p = Path().apply {
            moveTo(0.4f, top + 0.12f); lineTo(0.41f, top + 0.03f); lineTo(0.455f, top + 0.08f); lineTo(0.5f, top)
            lineTo(0.545f, top + 0.08f); lineTo(0.59f, top + 0.03f); lineTo(0.6f, top + 0.12f); close()
        }
        fill.shader = LinearGradient(0f, top, 0f, top + 0.12f, 0xFFFFE066.toInt(), 0xFFFFA62B.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(p, fill); fill.shader = null
    }

    private fun question(c: Canvas) {
        // Текст рисуется в масштабе 1:100 — у крошечного кегля портится сглаживание.
        c.save(); c.scale(0.01f, 0.01f)
        fill.shader = null; fill.color = 0xCCFFFFFF.toInt(); fill.textAlign = Paint.Align.CENTER; fill.textSize = 22f
        fill.isFakeBoldText = true
        c.drawText("?", 50f, 72f, fill)
        fill.isFakeBoldText = false
        c.restore()
    }
}
