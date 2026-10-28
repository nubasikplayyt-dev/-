package app.lingvo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lingvo.core.CreatureDef
import app.lingvo.core.CreatureShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class Mood { CALM, HAPPY, CHEER, SLEEPY }

/**
 * Существо-полиглот, нарисованное векторно: силуэт по [CreatureDef.shape], цвета существа,
 * глаза и настроение. Уровень 2 добавляет сияние, уровень 3 — корону и искры.
 */
@Composable
fun CreatureArt(
    def: CreatureDef,
    level: Int,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    mood: Mood = Mood.CALM,
    locked: Boolean = false,
    animate: Boolean = true,
) {
    val t = rememberInfiniteTransition(label = "creature")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "breath")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val b = if (animate && !locked) breath else 0.5f
    val colorA = if (locked) Color(0xFFD8DCEA) else Color(def.colorA)
    val colorB = if (locked) Color(0xFFBFC5DA) else Color(def.colorB)
    val rarity = Color(def.rarity.color)
    Canvas(modifier.size(size).semantics { contentDescription = if (locked) "Неизвестное существо" else "${def.name}, уровень $level" }) {
        val w = this.size.width
        val c = Offset(w / 2, w / 2)
        if (!locked && level >= 2) {
            drawCircle(Brush.radialGradient(listOf(rarity.copy(alpha = 0.45f), Color.Transparent), c, w * 0.5f), w * 0.5f, c)
        }
        if (!locked && level >= 3) {
            for (i in 0 until 6) {
                val a = (spin + i * 60f) * PI.toFloat() / 180f
                val r = w * 0.44f
                drawCircle(Color.White.copy(alpha = 0.9f), w * 0.022f, Offset(c.x + cos(a) * r, c.y + sin(a) * r))
            }
        }
        val squash = 1f + (b - 0.5f) * 0.06f
        scale(1f / squash, squash, pivot = Offset(c.x, w * 0.9f)) {
            val body = bodyPath(def.shape, w)
            val fill = Brush.verticalGradient(listOf(colorA, colorB), startY = w * 0.12f, endY = w * 0.92f)
            drawPath(body, Color.Black.copy(alpha = 0.06f), style = Stroke(w * 0.035f))
            drawPath(body, fill)
            clipPath(body) {
                drawCircle(Color.White.copy(alpha = 0.28f), w * 0.22f, Offset(w * 0.36f, w * 0.34f))
            }
            if (def.shape == CreatureShape.ORB) {
                drawOval(Color.White.copy(alpha = 0.7f), Offset(w * 0.12f, w * 0.52f), Size(w * 0.76f, w * 0.12f), style = Stroke(w * 0.025f))
            }
            if (def.shape == CreatureShape.BOOK) {
                drawLine(Color.White.copy(alpha = 0.6f), Offset(w * 0.5f, w * 0.3f), Offset(w * 0.5f, w * 0.86f), w * 0.02f)
            }
            if (locked) {
                drawCircle(Color.White.copy(alpha = 0.8f), w * 0.12f, Offset(c.x, w * 0.58f))
                return@scale
            }
            drawFace(w, mood, b)
        }
        if (!locked && level >= 3) drawCrown(w)
    }
}

private fun bodyPath(shape: CreatureShape, w: Float): Path = Path().apply {
    when (shape) {
        CreatureShape.BLOB -> {
            moveTo(w * 0.5f, w * 0.2f)
            cubicTo(w * 0.85f, w * 0.18f, w * 0.92f, w * 0.6f, w * 0.82f, w * 0.78f)
            cubicTo(w * 0.72f, w * 0.94f, w * 0.28f, w * 0.94f, w * 0.18f, w * 0.78f)
            cubicTo(w * 0.08f, w * 0.6f, w * 0.15f, w * 0.18f, w * 0.5f, w * 0.2f)
            close()
        }
        CreatureShape.DROP -> {
            moveTo(w * 0.5f, w * 0.1f)
            cubicTo(w * 0.62f, w * 0.32f, w * 0.86f, w * 0.48f, w * 0.84f, w * 0.68f)
            cubicTo(w * 0.82f, w * 0.88f, w * 0.18f, w * 0.88f, w * 0.16f, w * 0.68f)
            cubicTo(w * 0.14f, w * 0.48f, w * 0.38f, w * 0.32f, w * 0.5f, w * 0.1f)
            close()
        }
        CreatureShape.FLAME -> {
            moveTo(w * 0.5f, w * 0.08f)
            cubicTo(w * 0.58f, w * 0.26f, w * 0.72f, w * 0.24f, w * 0.7f, w * 0.12f)
            cubicTo(w * 0.9f, w * 0.34f, w * 0.88f, w * 0.62f, w * 0.8f, w * 0.76f)
            cubicTo(w * 0.7f, w * 0.94f, w * 0.3f, w * 0.94f, w * 0.2f, w * 0.76f)
            cubicTo(w * 0.1f, w * 0.58f, w * 0.18f, w * 0.38f, w * 0.3f, w * 0.26f)
            cubicTo(w * 0.32f, w * 0.38f, w * 0.4f, w * 0.4f, w * 0.42f, w * 0.34f)
            cubicTo(w * 0.42f, w * 0.24f, w * 0.44f, w * 0.16f, w * 0.5f, w * 0.08f)
            close()
        }
        CreatureShape.ORB -> addOval(Rect(w * 0.16f, w * 0.18f, w * 0.84f, w * 0.86f))
        CreatureShape.STAR -> {
            val cx = w * 0.5f; val cy = w * 0.54f
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) w * 0.4f else w * 0.22f
                val a = -PI / 2 + i * PI / 5
                val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        CreatureShape.CLOUD -> {
            addOval(Rect(w * 0.12f, w * 0.38f, w * 0.52f, w * 0.82f))
            addOval(Rect(w * 0.48f, w * 0.38f, w * 0.88f, w * 0.82f))
            addOval(Rect(w * 0.24f, w * 0.18f, w * 0.76f, w * 0.7f))
            addRect(Rect(w * 0.3f, w * 0.6f, w * 0.7f, w * 0.82f))
        }
        CreatureShape.BOOK -> addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.18f, w * 0.22f, w * 0.82f, w * 0.88f, CornerRadius(w * 0.1f)))
        CreatureShape.CRYSTAL -> {
            moveTo(w * 0.5f, w * 0.1f)
            lineTo(w * 0.82f, w * 0.38f)
            lineTo(w * 0.7f, w * 0.88f)
            lineTo(w * 0.3f, w * 0.88f)
            lineTo(w * 0.18f, w * 0.38f)
            close()
        }
    }
}

private fun DrawScope.drawFace(w: Float, mood: Mood, b: Float) {
    val eyeY = w * 0.56f
    val dx = w * 0.12f
    val ink = Color(0xFF1B2140)
    for (s in listOf(-1f, 1f)) {
        val ex = w * 0.5f + s * dx
        when (mood) {
            Mood.HAPPY, Mood.CHEER -> drawArc(ink, 200f, 140f, false, Offset(ex - w * 0.05f, eyeY - w * 0.03f), Size(w * 0.1f, w * 0.08f),
                style = Stroke(w * 0.025f, cap = StrokeCap.Round))
            Mood.SLEEPY -> drawLine(ink, Offset(ex - w * 0.04f, eyeY), Offset(ex + w * 0.04f, eyeY), w * 0.022f, StrokeCap.Round)
            Mood.CALM -> {
                drawOval(Color.White, Offset(ex - w * 0.05f, eyeY - w * 0.06f), Size(w * 0.1f, w * 0.12f))
                drawCircle(ink, w * 0.032f, Offset(ex + w * 0.008f, eyeY + w * 0.008f))
                drawCircle(Color.White, w * 0.01f, Offset(ex + w * 0.018f, eyeY - w * 0.012f))
            }
        }
        drawCircle(Color(0x66FF7AA8), w * 0.035f, Offset(ex + s * w * 0.05f, eyeY + w * 0.08f))
    }
    val mouthW = if (mood == Mood.CHEER) w * 0.14f else w * 0.09f
    when (mood) {
        Mood.SLEEPY -> drawCircle(ink, w * 0.018f, Offset(w * 0.5f, eyeY + w * 0.12f))
        else -> drawArc(
            ink, 20f, 140f, mood == Mood.CHEER, Offset(w * 0.5f - mouthW / 2, eyeY + w * 0.04f),
            Size(mouthW, mouthW * 0.8f), style = if (mood == Mood.CHEER) androidx.compose.ui.graphics.drawscope.Fill else Stroke(w * 0.022f, cap = StrokeCap.Round),
        )
    }
    if (mood == Mood.SLEEPY) {
        drawContextZ(w, b)
    }
}

private fun DrawScope.drawContextZ(w: Float, b: Float) {
    val ink = Color(0xFF7B5CFF).copy(alpha = 0.6f + 0.4f * b)
    val x = w * 0.74f; val y = w * 0.22f - b * w * 0.04f
    val p = Path().apply { moveTo(x, y); lineTo(x + w * 0.08f, y); lineTo(x, y + w * 0.08f); lineTo(x + w * 0.08f, y + w * 0.08f) }
    drawPath(p, ink, style = Stroke(w * 0.018f, cap = StrokeCap.Round))
}

private fun DrawScope.drawCrown(w: Float) {
    val p = Path().apply {
        moveTo(w * 0.36f, w * 0.2f); lineTo(w * 0.38f, w * 0.08f); lineTo(w * 0.44f, w * 0.15f)
        lineTo(w * 0.5f, w * 0.05f); lineTo(w * 0.56f, w * 0.15f); lineTo(w * 0.62f, w * 0.08f); lineTo(w * 0.64f, w * 0.2f); close()
    }
    drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFE066), Color(0xFFFFA62B))))
}

/**
 * «Оживающий глобус» серии: чем длиннее серия, тем ярче свечение и больше орбитальных искр.
 * При нулевой серии глобус приглушён и неподвижен.
 */
@Composable
fun StreakGlobe(streak: Int, todayDone: Boolean, modifier: Modifier = Modifier, size: Dp = 120.dp, animate: Boolean = true) {
    val t = rememberInfiniteTransition(label = "globe")
    val rot by t.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "pulse")
    val intensity = if (streak == 0) 0f else min(1f, 0.25f + streak / 40f)
    val phase = if (animate && streak > 0) rot else 0.15f
    val glowPulse = if (animate) pulse else 0.5f
    Canvas(modifier.size(size).semantics { contentDescription = "Серия $streak дней" + if (todayDone) ", сегодня выполнено" else "" }) {
        val w = this.size.width
        val c = Offset(w / 2, w / 2)
        val r = w * 0.32f
        if (intensity > 0f) {
            val glow = Color(0xFFFFB627).copy(alpha = 0.18f + 0.35f * intensity * (0.7f + 0.3f * glowPulse))
            drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), c, w * 0.5f), w * 0.5f, c)
        }
        val ocean = if (streak == 0) listOf(Color(0xFFB7C3E0), Color(0xFF8F9BC0)) else listOf(Color(0xFF6FD3FF), Color(0xFF2F6BFF))
        drawCircle(Brush.linearGradient(ocean, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r, c)
        val land = if (streak == 0) Color(0xFFDDE3F2) else Color(0xFF8BE38B)
        val globe = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
        clipPath(globe) {
            // Материки «проплывают» по кругу: два набора пятен со сдвигом фазы.
            for (k in 0..1) {
                val off = ((phase + k) % 2f) * 2 * r * 2 - 2 * r
                val bx = c.x - r + off
                drawOval(land, Offset(bx + r * 0.2f, c.y - r * 0.55f), Size(r * 0.7f, r * 0.45f))
                drawOval(land, Offset(bx + r * 0.6f, c.y - r * 0.05f), Size(r * 0.55f, r * 0.75f))
                drawOval(land, Offset(bx - r * 0.5f, c.y + r * 0.1f), Size(r * 0.5f, r * 0.4f))
            }
            for (i in 0 until 3) {
                val x = ((phase * 2 + i / 3f) % 1f)
                val ew = r * 2 * sin(x * PI).toFloat()
                drawOval(Color.White.copy(alpha = 0.25f), Offset(c.x - ew / 2, c.y - r), Size(ew, 2 * r), style = Stroke(w * 0.008f))
            }
            drawCircle(Color.White.copy(alpha = 0.22f), r * 0.55f, Offset(c.x - r * 0.35f, c.y - r * 0.4f))
        }
        val sparks = min(streak, 14)
        for (i in 0 until sparks) {
            val a = (phase * 360f + i * 360f / sparks.coerceAtLeast(1)) * PI.toFloat() / 180f
            val rr = r * 1.32f
            val s = w * (0.012f + 0.012f * intensity)
            drawCircle(Color(0xFFFFD166), s, Offset(c.x + cos(a) * rr, c.y + sin(a) * rr * 0.55f))
        }
        if (todayDone) {
            rotate(-20f, c) {
                drawArc(Color(0xFFFFD166), 200f, 140f, false, Offset(c.x - r * 1.3f, c.y - r * 0.5f), Size(r * 2.6f, r * 1.0f),
                    style = Stroke(w * 0.02f, cap = StrokeCap.Round))
            }
        }
    }
}
