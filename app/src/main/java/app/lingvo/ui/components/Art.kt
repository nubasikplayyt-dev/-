package app.lingvo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lingvo.core.CreatureDef
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Живое существо-полиглот: дышит, моргает, иногда само подпрыгивает, виляет хвостом,
 * машет или оглядывается. Настроение [mood] — реакция на занятие (радость, поддержка после ошибки,
 * ликование, гордость при новом уровне). Нажатие — существо машет в ответ.
 * Рисует [CreaturePainter] — тот же код, что в уведомлении и капсуле.
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
    val live = animate && !locked
    val t = rememberInfiniteTransition(label = "creature")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "breath")
    val spin by t.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "spin")
    val blink = remember { Animatable(0f) }
    val actionT = remember { Animatable(0f) }
    var action by remember { mutableStateOf(CreatureAction.NONE) }
    var tapped by remember { mutableStateOf(0) }

    if (live) {
        // Моргание раз в 2–5 секунд, иногда двойное.
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(2000, 5200))
                repeat(if (Random.nextInt(5) == 0) 2 else 1) {
                    blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(110)); delay(90)
                }
            }
        }
        // Случайные движения в покое.
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(5000, 11000))
                action = listOf(CreatureAction.HOP, CreatureAction.WIGGLE, CreatureAction.LOOK, CreatureAction.WAVE).random()
                actionT.snapTo(0f); actionT.animateTo(1f, tween(900, easing = LinearEasing))
                action = CreatureAction.NONE
            }
        }
        // Реакция на смену настроения и на нажатие.
        LaunchedEffect(mood, tapped) {
            if (tapped == 0 && mood == Mood.CALM) return@LaunchedEffect
            action = when {
                tapped > 0 && mood == Mood.CALM -> CreatureAction.WAVE
                mood == Mood.SUPPORT -> CreatureAction.LOOK
                else -> CreatureAction.HOP
            }
            actionT.snapTo(0f); actionT.animateTo(1f, tween(800, easing = LinearEasing))
            action = CreatureAction.NONE
        }
    }

    val pose = if (live) CreaturePose(breath, blink.value, action, actionT.value, spin) else CreaturePose(spin = 0.1f)
    val tap = remember { MutableInteractionSource() }
    Canvas(
        modifier.size(size)
            .let { if (live) it.clickable(tap, indication = null) { tapped++ } else it }
            .semantics { contentDescription = if (locked) "Неизвестное существо" else "${def.name}, уровень $level" },
    ) {
        drawIntoCanvas { CreaturePainter.draw(it.nativeCanvas, this.size.minDimension, def, level, mood, pose, locked) }
    }
}

/**
 * Земля серии: медленно вращается, облака дрейфуют. Выполненная цель дня — тёплое сияние и искры;
 * невыполненная — просто спокойная планета, без упрёков.
 */
@Composable
fun StreakEarth(streak: Int, todayDone: Boolean, modifier: Modifier = Modifier, size: Dp = 120.dp, animate: Boolean = true) {
    val t = rememberInfiniteTransition(label = "earth")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "rot")
    val clouds by t.animateFloat(0f, 360f, infiniteRepeatable(tween(150000, easing = LinearEasing)), label = "clouds")
    val glow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow")
    val spin by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
    Canvas(
        modifier.size(size).semantics {
            contentDescription = "Земля серии: $streak дн." + if (todayDone) ", цель на сегодня выполнена" else ""
        },
    ) {
        val r = if (animate) -rot else 20f
        drawIntoCanvas {
            EarthPainter.draw(it.nativeCanvas, this.size.minDimension, r, if (animate) clouds else 30f, todayDone, streak,
                if (animate) glow else 0.5f, if (animate) spin else 0.1f)
        }
    }
}
