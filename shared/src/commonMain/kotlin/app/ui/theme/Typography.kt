package app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.resources.Res
import app.resources.heuristica_bold
import app.resources.heuristica_italic
import app.resources.heuristica_regular
import org.jetbrains.compose.resources.Font

// Typography is the Heuristica family bundled in composeResources/font/.
// LP digits use the italic-bold cut via [lpDigitStyle] for the inked-card-game
// look. Body text uses the regular cut.

@Composable
fun heuristicaFamily(): FontFamily = FontFamily(
    Font(Res.font.heuristica_regular, FontWeight.Normal, FontStyle.Normal),
    Font(Res.font.heuristica_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.heuristica_bold, FontWeight.Bold, FontStyle.Normal),
)

@Composable
fun heuristicaItalicFamily(): FontFamily = FontFamily(
    Font(Res.font.heuristica_italic, FontWeight.Normal, FontStyle.Italic),
)

@Composable
fun duelTypography(): Typography {
    val family = heuristicaFamily()
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family, fontSize = 64.sp, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

/** The italic-bold cut used for LP digits, RPS reveal banners, and titles. */
@Composable
fun lpDigitStyle(fontSizeSp: Int): TextStyle = TextStyle(
    fontFamily = heuristicaItalicFamily(),
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Bold,
    fontSize = fontSizeSp.sp,
)
