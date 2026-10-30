package app.portfolio.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.portfolio.core.CriterionState
import app.portfolio.core.Verdict

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F6F4A),
    secondary = Color(0xFF3A6FA5),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6FD39B),
    secondary = Color(0xFF8CB8E8),
)

@Composable
fun PortfolioTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        // На Android 12+ берём цвета системной темы — приложение выглядит «родным».
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(colorScheme = scheme, content = content)
}

/** Цвета вердиктов и статусов критериев — одинаковые в обеих темах по смыслу. */
object StatusColors {

    @Composable
    fun verdict(verdict: Verdict): Color {
        val dark = isSystemInDarkTheme()
        return when (verdict) {
            Verdict.BUY_ZONE -> if (dark) Color(0xFF51CF66) else Color(0xFF2F9E44)
            Verdict.HOLD -> if (dark) Color(0xFF4DABF7) else Color(0xFF2F81D6)
            Verdict.WATCH -> if (dark) Color(0xFFFFD43B) else Color(0xFFC9A227)
            Verdict.DO_NOT_BUY -> if (dark) Color(0xFFADB5BD) else Color(0xFF868E96)
            Verdict.SELL_CANDIDATE -> if (dark) Color(0xFFFF922B) else Color(0xFFD9480F)
        }
    }

    @Composable
    fun criterion(state: CriterionState): Color {
        val dark = isSystemInDarkTheme()
        return when (state) {
            CriterionState.PASS -> if (dark) Color(0xFF51CF66) else Color(0xFF2F9E44)
            CriterionState.WARN -> if (dark) Color(0xFFFFD43B) else Color(0xFFC9A227)
            CriterionState.FAIL -> if (dark) Color(0xFFFF6B6B) else Color(0xFFD9480F)
            CriterionState.NOT_APPLICABLE -> if (dark) Color(0xFFADB5BD) else Color(0xFF868E96)
        }
    }

    /** Цвет заглушки логотипа по тикеру: у одной бумаги он всегда один. */
    fun logo(ticker: String): Color {
        var hash = 0
        for (char in ticker.uppercase()) hash = (hash shl 5) - hash + char.code
        val hue = ((hash % 360) + 360) % 360
        return Color.hsl(hue.toFloat(), 0.52f, 0.42f)
    }
}
