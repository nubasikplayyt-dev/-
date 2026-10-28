package app.lingvo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lingvo.R

/** Иллюстрации из пользовательского набора иконок (res/drawable-nodpi/art_*.webp). */
@DrawableRes
fun artRes(id: String?): Int? = when (id) {
    "world" -> R.drawable.art_world
    "classic" -> R.drawable.art_classic
    "open_book" -> R.drawable.art_open_book
    "dialog_star" -> R.drawable.art_dialog_star
    "bullseye" -> R.drawable.art_bullseye
    "sniper" -> R.drawable.art_sniper
    "listener" -> R.drawable.art_listener
    "knowledge_cup" -> R.drawable.art_knowledge_cup
    "plan" -> R.drawable.art_plan
    "week7" -> R.drawable.art_week7
    "days14" -> R.drawable.art_days14
    "days30" -> R.drawable.art_days30
    "words100" -> R.drawable.art_words100
    "chronicle" -> R.drawable.art_chronicle
    "level_up" -> R.drawable.art_level_up
    "graduate" -> R.drawable.art_graduate
    "horizons" -> R.drawable.art_horizons
    "crown_world" -> R.drawable.art_crown_world
    "champion" -> R.drawable.art_champion
    else -> null
}

/** Карточка-плитка: белая, скруглённая, с мягкой тенью. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .shadow(6.dp, shape, ambientColor = Color(0x223D5AFE), spotColor = Color(0x223D5AFE))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/** Карточка с градиентным фоном и белым текстом — для главных акцентов. */
@Composable
fun GradientCard(
    brush: Brush,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .shadow(10.dp, shape, ambientColor = Color(0x333D5AFE), spotColor = Color(0x553D5AFE))
            .clip(shape)
            .background(brush)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/** Большая градиентная кнопка главного действия (удобна для большого пальца). */
@Composable
fun BigActionButton(
    text: String,
    brush: Brush,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val pressed by animateFloatAsState(if (enabled) 1f else 0.5f, label = "btn-alpha")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .shadow(if (enabled) 8.dp else 0.dp, shape, spotColor = Color(0x553D5AFE))
            .clip(shape)
            .background(if (enabled) brush else Brush.linearGradient(listOf(Color(0xFFCFD5E8), Color(0xFFBFC6DD))))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, color = Color.White.copy(alpha = pressed), style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.85f * pressed), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Кольцо прогресса (дневная цель и т. п.). */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 9.dp,
    brush: Brush = Brush.sweepGradient(listOf(Color(0xFF4CC9F0), Color(0xFF7B5CFF), Color(0xFF4CC9F0))),
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    animate: Boolean = true,
    center: @Composable () -> Unit = {},
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), if (animate) tween(700, easing = FastOutSlowInEasing) else tween(0), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
            drawArc(brush, -90f, 360f * p, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        center()
    }
}

/** Линейный прогресс с градиентом. */
@Composable
fun GradientBar(progress: Float, modifier: Modifier = Modifier, brush: Brush = Brush.horizontalGradient(listOf(Color(0xFF4CC9F0), Color(0xFF7B5CFF))), height: Dp = 10.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(p).height(height).clip(RoundedCornerShape(50)).background(brush))
    }
}

/** Эмблема достижения/предмета: иллюстрация или символ в градиентном круге. */
@Composable
fun Emblem(art: String?, symbol: String, unlocked: Boolean, size: Dp = 64.dp, brush: Brush? = null) {
    val res = artRes(art)
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        Modifier.size(size).clip(shape)
            .background(
                if (!unlocked) Brush.linearGradient(listOf(Color(0xFFE4E7F2), Color(0xFFD3D8EA)))
                else brush ?: Brush.linearGradient(listOf(Color(0xFFFFE7A3), Color(0xFFFFC2A8)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (res != null) {
            Image(
                painterResource(res), null,
                Modifier.size(size).let { if (!unlocked) it.scale(0.92f) else it },
                alpha = if (unlocked) 1f else 0.35f,
                colorFilter = if (unlocked) null else androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                ),
            )
        } else {
            Text(symbol, fontSize = (size.value * 0.45f).sp, modifier = Modifier.let { if (!unlocked) it.scale(0.9f) else it },
                color = if (unlocked) Color.Unspecified else Color.Gray.copy(alpha = 0.5f))
        }
        if (!unlocked) Text("🔒", fontSize = (size.value * 0.22f).sp, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}

/** Пилюля-метка. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Color.White) {
    Surface(modifier, shape = RoundedCornerShape(50), color = color) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = textColor, style = MaterialTheme.typography.labelMedium)
    }
}

/** Строка меню настроек/разделов: иконка в цветном кружке, заголовок, пояснение. */
@Composable
fun MenuRow(icon: ImageVector, color: Color, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** Небольшая плитка статистики с цветным акцентом. */
@Composable
fun StatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.12f)).padding(14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Заголовок экрана в стиле приложения (без панели). */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** Появление с «пружинкой» — для эмблем при разблокировке. */
@Composable
fun PopIn(key: Any?, enabled: Boolean = true, content: @Composable () -> Unit) {
    val scale = remember(key) { Animatable(if (enabled) 0.4f else 1f) }
    LaunchedEffect(key) {
        if (enabled) scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 220f))
    }
    Box(Modifier.scale(scale.value)) { content() }
}

@Composable
fun CenteredText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun RowGap(w: Dp = 12.dp) = Spacer(Modifier.width(w))

@Composable
fun SpacedColumn(modifier: Modifier = Modifier, gap: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap), content = content)
