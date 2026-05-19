package app.ui.duel

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.resources.Res
import app.resources.yugi_lp_bg
import app.ui.components.AnimatedLifePoints
import app.ui.components.StrokedText
import app.ui.theme.DuelColors
import app.ui.theme.lpDigitStyle
import org.jetbrains.compose.resources.painterResource

/**
 * LP card. Background webp + golden border + a single centered LP read.
 *
 * The previous two-copy layout (mirrored mini-LP on top + main below) was
 * removed: each player now sees the opponent's LP as a separate row above
 * the LP card, so the card itself just shows their own value, centered and
 * large. The box also shrinks a bit so it doesn't dominate the half — there's
 * room above for the opponent peek and the phase tracker.
 *
 * Digit font size is computed from the measured box at layout so the card
 * fits any device without hand-tuning. When [isInfinite] is true, a single ∞
 * glyph is drawn instead.
 */
@Composable
fun LpBox(
    value: Int,
    modifier: Modifier = Modifier,
    minDigits: Int = 5,
    isInfinite: Boolean = false,
    durationMs: Int = 360,
    stepMagnitude: Int = 0,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 3.dp,
                color = DuelColors.DuelGold,
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        Image(
            painter = painterResource(Res.drawable.yugi_lp_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (isInfinite) {
            val byHeight = (maxHeight - VERTICAL_INSET_DP.dp).value * 0.85f
            val byWidth = maxWidth.value * 0.55f
            val infinitySp = minOf(byHeight, byWidth).toInt().coerceIn(MIN_DIGIT_SP, INFINITY_MAX_SP)
            val strokeDp = (infinitySp / 28f).coerceAtLeast(1f).dp
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                StrokedText(
                    text = "∞",
                    style = lpDigitStyle(infinitySp),
                    fillColor = DuelColors.LpYellow,
                    strokeColor = DuelColors.LpStroke,
                    strokeWidth = strokeDp,
                )
            }
        } else {
            // Single centred digit reel. Font size scales with the box so the
            // 5-digit max ("16000") still fits on small phones, while smaller
            // values use most of the available height.
            val byHeight = (maxHeight - VERTICAL_INSET_DP.dp).value * 0.78f
            val byWidth = maxWidth.value / (minDigits * 0.62f)
            val digitSp = minOf(byHeight, byWidth).toInt().coerceIn(MIN_DIGIT_SP, MAX_DIGIT_SP)
            val strokeDp = (digitSp / 28f).coerceAtLeast(1f).dp

            // Heuristica italic glyphs visually overhang past their layout
            // box. Shift LEFT by half the overhang so the visual centre lands
            // on the card's geometric centre (matches the previous mirrored
            // layout's compensation, just applied once for the single copy).
            val correctionPx = digitSp * ITALIC_VISUAL_OFFSET_FACTOR

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        translationX = -correctionPx.sp.toPx()
                    },
                ) {
                    AnimatedLifePoints(
                        value = value,
                        style = lpDigitStyle(digitSp),
                        strokeWidth = strokeDp,
                        durationMs = durationMs,
                        stepMagnitude = stepMagnitude,
                    )
                }
            }
        }
    }
}

private const val MIN_DIGIT_SP = 28
private const val MAX_DIGIT_SP = 100
private const val INFINITY_MAX_SP = 140
private const val VERTICAL_INSET_DP = 12

// Half of Heuristica italic's right-overhang as a fraction of font size.
private const val ITALIC_VISUAL_OFFSET_FACTOR = 0.06f
