package app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Theme is dark-only. App identity is gold + purple; LP digits are yellow over
// near-black stroke. Action panels in DuelScreen tint themselves crimson
// (ATTACK), blood-red (SACRIFICE), or emerald (HEAL).
object DuelColors {
    val LpYellow = Color(0xFFFFE34C)
    val LpYellowDeep = Color(0xFFFFC400)
    val LpStroke = Color(0xFF1B1206)
    val DuelPurple = Color(0xFF2A1F62)
    val DuelPurpleDeep = Color(0xFF120A33)
    val DuelGold = Color(0xFFE8B852)
    val DuelGoldGlow = Color(0xFFFFE07C)
    val Crimson = Color(0xFFD7263D)
    val EmeraldHeal = Color(0xFF14C38E)
    val BloodRed = Color(0xFFA8121E)
    val BloodGlow = Color(0xFFE84260)

    val ArenaGradient = Brush.verticalGradient(
        listOf(Color(0xFF120A33), Color(0xFF231548), Color(0xFF0B0620))
    )
}

@Composable
fun DuelTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = DuelColors.DuelGold,
        onPrimary = Color.Black,
        secondary = DuelColors.LpYellow,
        background = DuelColors.DuelPurpleDeep,
        onBackground = Color(0xFFEDE7FF),
        surface = Color(0xFF1A1140),
        onSurface = Color(0xFFEDE7FF),
        error = DuelColors.Crimson,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = duelTypography(),
        content = content,
    )
}
