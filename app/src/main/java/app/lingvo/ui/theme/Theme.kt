package app.lingvo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lingvo.data.ThemeMode

/** Палитра Lingvo World: глубокий синий глобуса, тёплые акценты речевых пузырей из иконки. */
object Palette {
    val Ocean = Color(0xFF2F6BFF)
    val Sky = Color(0xFF4CC9F0)
    val Violet = Color(0xFF7B5CFF)
    val Coral = Color(0xFFFF6B5B)
    val Sun = Color(0xFFFFB627)
    val Mint = Color(0xFF22C3A6)
    val Leaf = Color(0xFF3DD68C)
    val Ink = Color(0xFF1B2140)
    val Cloud = Color(0xFFF6F8FF)

    val remembered = Color(0xFF22C38E)
    val forgot = Color(0xFFFF6B5B)

    private val courseColors = listOf(Coral, Sun, Violet, Mint, Ocean, Sky, Leaf, Color(0xFFFF8FB1))

    /** Стабильный цвет курса по его ID (не зависит от конкретных языков). */
    fun course(id: String): Color = when (id) {
        "es" -> Coral
        "de" -> Sun
        else -> courseColors[Math.floorMod(id.hashCode(), courseColors.size)]
    }
}

@Immutable
data class Gradients(
    val hero: Brush,
    val sunrise: Brush,
    val ocean: Brush,
    val violet: Brush,
    val mint: Brush,
    val fire: Brush,
    val surfaceGlow: Brush,
)

private val LightGradients = Gradients(
    hero = Brush.linearGradient(listOf(Color(0xFF3D7BFF), Color(0xFF7B5CFF), Color(0xFFB45CFF))),
    sunrise = Brush.linearGradient(listOf(Color(0xFFFFC94D), Color(0xFFFF8A5B))),
    ocean = Brush.linearGradient(listOf(Color(0xFF4CC9F0), Color(0xFF2F6BFF))),
    violet = Brush.linearGradient(listOf(Color(0xFFB79CFF), Color(0xFF7B5CFF))),
    mint = Brush.linearGradient(listOf(Color(0xFF7BE8C6), Color(0xFF22C3A6))),
    fire = Brush.linearGradient(listOf(Color(0xFFFFD166), Color(0xFFFF6B35))),
    surfaceGlow = Brush.verticalGradient(listOf(Color(0xFFEFF3FF), Color(0xFFFFFFFF))),
)

private val DarkGradients = LightGradients.copy(
    surfaceGlow = Brush.verticalGradient(listOf(Color(0xFF1C2140), Color(0xFF12152B))),
)

val LocalGradients = staticCompositionLocalOf { LightGradients }

private val Light = lightColorScheme(
    primary = Palette.Ocean,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF0B2A80),
    secondary = Palette.Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE3FF),
    onSecondaryContainer = Color(0xFF2E1A80),
    tertiary = Color(0xFFE8890C),
    tertiaryContainer = Color(0xFFFFEBC7),
    onTertiaryContainer = Color(0xFF5A3300),
    background = Palette.Cloud,
    onBackground = Palette.Ink,
    surface = Color.White,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFEDF0FA),
    onSurfaceVariant = Color(0xFF5A6182),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFF),
    surfaceContainer = Color(0xFFF2F5FF),
    surfaceContainerHigh = Color(0xFFECF0FD),
    surfaceContainerHighest = Color(0xFFE5EAFA),
    outline = Color(0xFFB4BBD6),
    outlineVariant = Color(0xFFDCE1F1),
    error = Color(0xFFE5484D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FB0FF),
    onPrimary = Color(0xFF0B2A80),
    primaryContainer = Color(0xFF2A3E8F),
    secondary = Color(0xFFC3B2FF),
    secondaryContainer = Color(0xFF3D2F80),
    tertiary = Color(0xFFFFC56E),
    tertiaryContainer = Color(0xFF5A3D10),
    background = Color(0xFF12152B),
    surface = Color(0xFF1A1E38),
    surfaceVariant = Color(0xFF272C4D),
    surfaceContainer = Color(0xFF1F2442),
    surfaceContainerHigh = Color(0xFF262B4C),
    surfaceContainerHighest = Color(0xFF2E3456),
)

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Крупный шрифт изучаемого слова на карточке. */
val CardTermStyle = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)

/** Совместимость со старым кодом: цвет курса. */
object LangColors {
    fun of(code: String) = Palette.course(code)
}

@Composable
fun LingvoTheme(mode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalGradients provides if (dark) DarkGradients else LightGradients) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
