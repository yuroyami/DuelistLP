package app.ui.duel

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import app.resources.Res
import app.resources.yugi_lp_bg
import app.ui.components.AnimatedLifePoints
import app.ui.theme.DuelColors
import app.ui.theme.lpDigitStyle
import org.jetbrains.compose.resources.painterResource

/**
 * The signature LP card: blurred-spiral webp background, golden border, and
 * two vertically-mirrored copies of the same LP value rendered inside the
 * same canvas — top rotated 180°, bottom upright — so two players sitting
 * across a table each read it head-on, like the mirror-printed lettering on
 * an ambulance hood.
 *
 * The digit font size is computed from the box's actual measured size at
 * layout time, so the LP fits perfectly on a tiny iPhone XS or a tall iPad
 * without hand-tuning.
 */
@Composable
fun LpBox(
    value: Int,
    modifier: Modifier = Modifier,
    minDigits: Int = 4,
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
        // Two stacked renderings: each gets ~half the box height after a small
        // vertical inset for the rounded border + padding.
        val perHalfHeightDp = (maxHeight - VERTICAL_INSET_DP.dp) / 2
        // ~70% of available half-height fits the glyph with descender room.
        val byHeight = (perHalfHeightDp.value * 0.70f)
        // 4 italic digits + ~25% slant padding ≈ fontSize × digits × 0.62.
        val byWidth = maxWidth.value / (minDigits * 0.62f)
        val digitSp = minOf(byHeight, byWidth).toInt().coerceIn(MIN_DIGIT_SP, MAX_DIGIT_SP)
        val strokeDp = (digitSp / 28f).coerceAtLeast(1f).dp

        Image(
            painter = painterResource(Res.drawable.yugi_lp_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Top reading: rotated 180° for the player across the table.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationZ = 180f },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedLifePoints(
                    value = value,
                    style = lpDigitStyle(digitSp),
                    strokeWidth = strokeDp,
                    minDigits = minDigits,
                )
            }
            // Bottom reading: upright, for the player on the near side.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedLifePoints(
                    value = value,
                    style = lpDigitStyle(digitSp),
                    strokeWidth = strokeDp,
                    minDigits = minDigits,
                )
            }
        }
    }
}

private const val MIN_DIGIT_SP = 24
private const val MAX_DIGIT_SP = 80
private const val VERTICAL_INSET_DP = 12   // border + column padding
