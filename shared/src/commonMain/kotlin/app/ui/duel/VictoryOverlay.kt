package app.ui.duel

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.model.PlayerSlot
import app.ui.components.StrokedText
import app.ui.theme.DuelColors
import app.ui.theme.lpDigitStyle

/**
 * Half-screen victory / defeat splash. Winner side glows gold and pulses with
 * "VICTORY", loser side dims and reads "DEFEAT". Buttons appear after a short
 * dramatic pause (handled by caller via [showActions]).
 */
@Composable
fun VictoryOverlay(
    winner: PlayerSlot,
    winnerName: String,
    loserName: String,
    showActions: Boolean,
    onRematch: () -> Unit,
    onSaveAndExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
    ) {
        // Top half = P2 side; bottom half = P1 side. Rotate the P2 panel 180°
        // so each player reads their result naturally when phone is between them.
        VictoryHalf(
            text = if (winner == PlayerSlot.P2) "VICTORY" else "DEFEAT",
            playerName = if (winner == PlayerSlot.P2) winnerName else loserName,
            isWinner = winner == PlayerSlot.P2,
            rotated = true,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showActions) 96.dp else 0.dp)
                .background(Color(0xFF120A33)),
            contentAlignment = Alignment.Center,
        ) {
            if (showActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onRematch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DuelColors.DuelGold,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Rematch", style = MaterialTheme.typography.titleMedium) }

                    Button(
                        onClick = onSaveAndExit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A1140),
                            contentColor = DuelColors.DuelGoldGlow,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Save & Exit", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }

        VictoryHalf(
            text = if (winner == PlayerSlot.P1) "VICTORY" else "DEFEAT",
            playerName = if (winner == PlayerSlot.P1) winnerName else loserName,
            isWinner = winner == PlayerSlot.P1,
            rotated = false,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun VictoryHalf(
    text: String,
    playerName: String,
    isWinner: Boolean,
    rotated: Boolean,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "victoryPulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "pulse",
    )
    val glow by infinite.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), repeatMode = RepeatMode.Reverse),
        label = "glow",
    )

    val gradient = if (isWinner) {
        Brush.radialGradient(
            colors = listOf(DuelColors.DuelGold.copy(alpha = 0.55f), Color(0xFF120A33)),
            radius = 900f,
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFF170B17), Color.Black),
        )
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .background(gradient)
            .then(if (rotated) Modifier.rotate(180f) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Scale the banner text to fit the half. Height target is ~28% of the
        // half's height; width target accounts for Heuristica bold-italic's
        // glyph advance (~0.62 of fontSize) plus StrokedText's ~25% horizontal
        // padding plus the stroke contribution on each side.
        val byHeight = maxHeight.value * 0.28f
        val widthDenom = 0.62f * text.length + 0.30f
        val byWidth = maxWidth.value / widthDenom
        val fitSp = minOf(byHeight, byWidth).coerceIn(20f, 96f)
        val titleSp = (if (isWinner) fitSp else fitSp * 0.78f).toInt()

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.scale(if (isWinner) pulse else 1f).alpha(if (isWinner) glow else 0.7f)) {
                StrokedText(
                    text = text,
                    style = lpDigitStyle(titleSp),
                    fillColor = if (isWinner) DuelColors.LpYellow else Color(0xFF8B0000),
                    strokeColor = DuelColors.LpStroke,
                    strokeWidth = (titleSp / 28f).coerceAtLeast(1f).dp,
                )
            }
            Text(
                playerName,
                color = if (isWinner) DuelColors.DuelGoldGlow else Color(0xFFA68585),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
