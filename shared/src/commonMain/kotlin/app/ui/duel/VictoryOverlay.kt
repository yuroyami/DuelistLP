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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import app.model.Outcome
import app.model.PlayerSlot
import app.ui.components.StrokedText
import app.ui.theme.DuelColors
import app.ui.theme.lpDigitStyle

/**
 * Match-end splash. For [Outcome.Win], winner side pulses gold "VICTORY",
 * loser dims to dark-red "DEFEAT". For [Outcome.Draw], both halves get the
 * calmer "DRAW" banner. Action buttons (Rematch / Save & Exit) appear when
 * caller flips [showActions] (DuelScreen does this ~1.8 s after game-end).
 */
@Composable
fun VictoryOverlay(
    outcome: Outcome,
    p1Name: String,
    p2Name: String,
    showActions: Boolean,
    onRematch: () -> Unit,
    onSaveAndExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
    ) {
        OutcomeHalf(
            outcome = outcome,
            slot = PlayerSlot.P2,
            playerName = p2Name,
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

        OutcomeHalf(
            outcome = outcome,
            slot = PlayerSlot.P1,
            playerName = p1Name,
            rotated = false,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun OutcomeHalf(
    outcome: Outcome,
    slot: PlayerSlot,
    playerName: String,
    rotated: Boolean,
    modifier: Modifier = Modifier,
) {
    val (text, role) = when (outcome) {
        is Outcome.Win ->
            if (outcome.winner == slot) "VICTORY" to HalfRole.Winner
            else "DEFEAT" to HalfRole.Loser
        Outcome.Draw -> "DRAW" to HalfRole.Drawn
        Outcome.InProgress -> return
    }

    val infinite = rememberInfiniteTransition(label = "outcomePulse")
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

    val gradient = when (role) {
        HalfRole.Winner -> Brush.radialGradient(
            colors = listOf(DuelColors.DuelGold.copy(alpha = 0.55f), Color(0xFF120A33)),
            radius = 900f,
        )
        HalfRole.Loser -> Brush.verticalGradient(
            listOf(Color(0xFF170B17), Color.Black),
        )
        HalfRole.Drawn -> Brush.verticalGradient(
            listOf(Color(0xFF1F1A4A), Color(0xFF120A33)),
        )
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .background(gradient)
            .then(if (rotated) Modifier.rotate(180f) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val byHeight = maxHeight.value * 0.28f
        val widthDenom = 0.62f * text.length + 0.30f
        val byWidth = maxWidth.value / widthDenom
        val fitSp = minOf(byHeight, byWidth).coerceIn(20f, 96f)
        val isAnimated = role == HalfRole.Winner || role == HalfRole.Drawn
        val titleSp = (if (isAnimated) fitSp else fitSp * 0.78f).toInt()

        val fillColor = when (role) {
            HalfRole.Winner -> DuelColors.LpYellow
            HalfRole.Loser -> Color(0xFF8B0000)
            HalfRole.Drawn -> DuelColors.DuelGoldGlow
        }
        val nameColor = when (role) {
            HalfRole.Winner, HalfRole.Drawn -> DuelColors.DuelGoldGlow
            HalfRole.Loser -> Color(0xFFA68585)
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .scale(if (isAnimated) pulse else 1f)
                    .alpha(if (isAnimated) glow else 0.7f),
            ) {
                StrokedText(
                    text = text,
                    style = lpDigitStyle(titleSp),
                    fillColor = fillColor,
                    strokeColor = DuelColors.LpStroke,
                    strokeWidth = (titleSp / 28f).coerceAtLeast(1f).dp,
                )
            }
            Text(
                playerName,
                color = nameColor,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

private enum class HalfRole { Winner, Loser, Drawn }
