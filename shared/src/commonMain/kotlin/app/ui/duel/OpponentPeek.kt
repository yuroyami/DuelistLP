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
import app.ui.components.AnimatedLifePoints
import app.ui.components.StrokedText
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme
import app.ui.theme.lpDigitStyle

/**
 * Slim strip rendered in place of the full [PlayerHalf] while the other
 * player has the turn. Name + FIRST badge on one side, animated LP on the
 * other. Floating-delta still pops over this strip so the active player sees
 * their attack land on the opponent's peek with the same big +/− pop.
 *
 * Height scales with viewport via [DuelTheme.dimens.opponentPeekHeight].
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
    val d = DuelTheme.dimens
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(d.opponentPeekHeight)
            .background(Color(0xFF120A33))
            .border(d.borderHairline, DuelColors.DuelGold.copy(alpha = 0.45f))
            .then(if (rotated) Modifier.rotate(180f) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = d.s16, vertical = d.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(d.s8),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = DuelColors.DuelGoldGlow.copy(alpha = 0.85f),
                )
                if (showFirstBadge) {
                    Box(
                        modifier = Modifier
                            .background(DuelColors.DuelGold.copy(alpha = 0.85f), RoundedCornerShape(d.radiusPill))
                            .padding(horizontal = d.s8, vertical = d.s2),
                    ) {
                        Text(
                            "FIRST",
                            color = Color.Black,
                            fontSize = d.textMicro,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            val lpDigitSp = (d.textPeekLp.value).toInt().coerceAtLeast(20)
            if (isInfinite) {
                StrokedText(
                    text = "∞",
                    style = lpDigitStyle(lpDigitSp + 6),
                    fillColor = DuelColors.LpYellow,
                    strokeColor = DuelColors.LpStroke,
                    strokeWidth = d.borderEmphasized,
                )
            } else {
                AnimatedLifePoints(
                    value = lp,
                    style = lpDigitStyle(lpDigitSp),
                    strokeWidth = d.borderStandard,
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
