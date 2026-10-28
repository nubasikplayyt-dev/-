package app.lingvo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    secondary = Color(0xFF5C6BC0),
    tertiary = Color(0xFFE65100),
    background = Color(0xFFFBFAFF),
    surface = Color(0xFFFBFAFF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9FA8FF),
    secondary = Color(0xFFB0B8F0),
    tertiary = Color(0xFFFFB074),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
)

/** Акцентные цвета языков. Навигация не опирается только на цвет — везде есть подписи. */
object LangColors {
    val es = Color(0xFFE53935)
    val de = Color(0xFFFFB300)
    fun of(code: String) = if (code == "de") de else es
}

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Крупный шрифт для изучаемого слова на карточке. */
val CardTermStyle = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun LingvoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
