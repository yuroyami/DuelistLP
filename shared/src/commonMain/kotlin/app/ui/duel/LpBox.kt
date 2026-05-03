package app.ui.duel

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * LP card. Background webp + golden border + two stacked copies of the LP:
 *   - top: rotated 180°, half size — the inline mirror for the opponent
 *   - bottom: upright, full size — the player's main read
 *
 * Digit font sizes are computed from the measured box at layout so the card
 * fits any device without hand-tuning. When [isInfinite] is true, a single
 * ∞ glyph is drawn instead (rotating ∞ 180° is a no-op so the mirror is
 * redundant).
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
            // Each half gets exactly half the height. Mirror is half the
            // font size of main, sharing a common visual midline through
            // the card's geometric center.
            val perHalfHeight = (maxHeight - VERTICAL_INSET_DP.dp) / 2
            val byHeight = perHalfHeight.value * 0.78f
            val byWidth = maxWidth.value / (minDigits * 0.62f)
            val digitSpBottom = minOf(byHeight, byWidth).toInt().coerceIn(MIN_DIGIT_SP, MAX_DIGIT_SP)
            val strokeDpBottom = (digitSpBottom / 28f).coerceAtLeast(1f).dp
            val digitSpTop = (digitSpBottom / 2).coerceAtLeast(MIN_OPPONENT_SP)
            val strokeDpTop = (digitSpTop / 28f).coerceAtLeast(0.5f).dp

            // Heuristica italic overhangs past the natural advance on the
            // trailing edge. Without compensation, a centered Row of digits
            // paints visually shifted toward the slant direction. The
            // translationX below shifts the upright copy LEFT and the
            // 180°-rotated copy RIGHT (rotation flips the X direction inside
            // the graphics layer) by the same magnitude, so both visual
            // centers land on the card's vertical midline.
            val correctionFactor = ITALIC_VISUAL_OFFSET_FACTOR

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            rotationZ = 180f
                            translationX = (digitSpTop * correctionFactor).sp.toPx()
                        },
                    ) {
                        AnimatedLifePoints(
                            value = value,
                            style = lpDigitStyle(digitSpTop),
                            strokeWidth = strokeDpTop,
                            durationMs = durationMs,
                            stepMagnitude = stepMagnitude,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationX = -(digitSpBottom * correctionFactor).sp.toPx()
                        },
                    ) {
                        AnimatedLifePoints(
                            value = value,
                            style = lpDigitStyle(digitSpBottom),
                            strokeWidth = strokeDpBottom,
                            durationMs = durationMs,
                            stepMagnitude = stepMagnitude,
                        )
                    }
                }
            }
        }
    }
}

private const val MIN_DIGIT_SP = 24
private const val MAX_DIGIT_SP = 88
private const val MIN_OPPONENT_SP = 12
private const val INFINITY_MAX_SP = 130
private const val VERTICAL_INSET_DP = 12   // border + column padding

// Half of Heuristica italic's right-overhang as a fraction of font size.
// Empirically tuned: italic slant pushes the visual ~12% past the natural
// advance; halving that recenters the layout box on the visual glyph.
private const val ITALIC_VISUAL_OFFSET_FACTOR = 0.06f
