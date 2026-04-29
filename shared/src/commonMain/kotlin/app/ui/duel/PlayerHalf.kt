package app.ui.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.model.PlayerSlot
import app.ui.components.RepeatingButton
import app.ui.theme.DuelColors

/**
 * One side of the dueling field. Contains:
 *  - Player name
 *  - LP box (yugi_lp_bg + animated digits, or "∞" if infinite)
 *  - Target toggle (Self / Opponent)
 *  - Step selector (10 / 100 / 500 / 1000 / Custom)
 *  - Big − / + buttons (long-press to repeat)
 */
@Composable
fun PlayerHalf(
    playerName: String,
    selfLp: Int,
    opponentLp: Int,
    isFirstPlayer: Boolean,
    rotated: Boolean,
    onLpChange: (target: PlayerSlot, delta: Int) -> Unit,
    selfSlot: PlayerSlot,
    opponentSlot: PlayerSlot,
    onRequestCustomAmount: (commit: (Int) -> Unit) -> Unit,
    isInfinite: Boolean,
    modifier: Modifier = Modifier,
) {
    var targetSelf by remember { mutableStateOf(true) }
    var step by remember { mutableStateOf(100) }

    Box(
        modifier = modifier.then(if (rotated) Modifier.rotate(180f) else Modifier),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = playerName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = DuelColors.DuelGoldGlow,
                    modifier = Modifier.padding(start = 4.dp),
                )
                if (isFirstPlayer) {
                    Box(
                        modifier = Modifier
                            .background(DuelColors.DuelGold, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text("FIRST", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            LpBox(
                value = selfLp,
                isInfinite = isInfinite,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            )

            TargetToggle(
                targetSelf = targetSelf,
                onChange = { targetSelf = it },
            )

            StepSelector(
                step = step,
                onStepChange = { step = it },
                onCustomRequest = {
                    onRequestCustomAmount { newAmount -> step = newAmount.coerceIn(1, 100_000) }
                },
            )

            ActionRow(
                onMinus = {
                    val target = if (targetSelf) selfSlot else opponentSlot
                    onLpChange(target, -step)
                },
                onPlus = {
                    val target = if (targetSelf) selfSlot else opponentSlot
                    onLpChange(target, +step)
                },
            )
        }
    }
}

@Composable
private fun TargetToggle(targetSelf: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(1.dp, DuelColors.DuelGold, RoundedCornerShape(10.dp)),
    ) {
        ToggleSegment(label = "Self", selected = targetSelf, onClick = { onChange(true) }, modifier = Modifier.weight(1f))
        ToggleSegment(label = "Opponent", selected = !targetSelf, onClick = { onChange(false) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ToggleSegment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (selected) DuelColors.DuelGold else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp)) {
            Text(
                label,
                color = if (selected) Color.Black else DuelColors.DuelGoldGlow,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun StepSelector(step: Int, onStepChange: (Int) -> Unit, onCustomRequest: () -> Unit) {
    val options = listOf(10, 100, 500, 1000)
    val isCustom = step !in options
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { value ->
            StepChip(
                text = value.toString(),
                selected = step == value,
                onClick = { onStepChange(value) },
                modifier = Modifier.weight(1f),
            )
        }
        StepChip(
            text = if (isCustom) "$step" else "…",
            selected = isCustom,
            onClick = onCustomRequest,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun StepChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                color = if (selected) DuelColors.DuelGold else Color(0xFF1A1140),
                shape = RoundedCornerShape(8.dp),
            )
            .border(1.dp, DuelColors.DuelGold.copy(alpha = if (selected) 1f else 0.45f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp)) {
            Text(
                text,
                color = if (selected) Color.Black else DuelColors.DuelGoldGlow,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ActionRow(onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BigActionButton(
            label = "−",
            background = DuelColors.Crimson,
            onTick = onMinus,
            modifier = Modifier.weight(1f),
        )
        BigActionButton(
            label = "+",
            background = DuelColors.EmeraldHeal,
            onTick = onPlus,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BigActionButton(
    label: String,
    background: Color,
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RepeatingButton(
        onTick = onTick,
        modifier = modifier
            .fillMaxHeight()
            .background(background, RoundedCornerShape(12.dp))
            .border(2.dp, DuelColors.DuelGold, RoundedCornerShape(12.dp)),
        contentColor = Color.Black,
    ) {
        Text(label, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}
