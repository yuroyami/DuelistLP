package app.ui.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.components.AnimatedLifePoints
import app.ui.components.StrokedText
import app.ui.theme.DuelColors
import app.ui.theme.lpDigitStyle

/**
 * Slim 72-dp strip rendered in place of the full [PlayerHalf] while the
 * other player has the turn. Name + FIRST badge on one side, animated LP on
 * the other. Floating-delta still pops over this strip so the active player
 * sees their attack land on the opponent's peek with the same big +/− pop.
 *
 * Rotated 180° for P2 so it reads upright across the table.
 */
@Composable
fun OpponentPeek(
    name: String,
    lp: Int,
    isInfinite: Boolean,
    showFirstBadge: Boolean,
    rotated: Boolean,
    animationDurationMs: Int,
    pendingFloatingDelta: Int?,
    onFloatingDeltaComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PEEK_HEIGHT_DP.dp)
            .background(Color(0xFF120A33))
            .border(1.dp, DuelColors.DuelGold.copy(alpha = 0.45f))
            .then(if (rotated) Modifier.rotate(180f) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = DuelColors.DuelGoldGlow.copy(alpha = 0.85f),
                )
                if (showFirstBadge) {
                    Box(
                        modifier = Modifier
                            .background(DuelColors.DuelGold.copy(alpha = 0.85f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("FIRST", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isInfinite) {
                StrokedText(
                    text = "∞",
                    style = lpDigitStyle(40),
                    fillColor = DuelColors.LpYellow,
                    strokeColor = DuelColors.LpStroke,
                    strokeWidth = 2.dp,
                )
            } else {
                AnimatedLifePoints(
                    value = lp,
                    style = lpDigitStyle(34),
                    strokeWidth = 1.5.dp,
                    durationMs = animationDurationMs,
                )
            }
        }

        // Floating-delta still pops here for hits during the other turn.
        pendingFloatingDelta?.let { delta ->
            LpFloatingDelta(
                delta = delta,
                onComplete = onFloatingDeltaComplete,
            )
        }
    }
}

private const val PEEK_HEIGHT_DP = 72
