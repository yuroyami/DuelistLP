package app.ui.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.ui.theme.DuelColors

/**
 * Misc effects popover. Currently exposes a per-player infinite-LP toggle —
 * Sleepy 8 (∞), useful when a card effect grants unbeatable LP for a turn.
 * While on, the player's LP can't change and their card reads "∞".
 */
@Composable
fun MiscDialog(
    p1Name: String,
    p2Name: String,
    p1Infinite: Boolean,
    p2Infinite: Boolean,
    onP1InfiniteChange: (Boolean) -> Unit,
    onP2InfiniteChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1140), RoundedCornerShape(14.dp))
                .border(1.dp, DuelColors.DuelGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Misc",
                    style = MaterialTheme.typography.titleLarge,
                    color = DuelColors.DuelGoldGlow,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    "Infinite LP",
                    color = DuelColors.DuelGoldGlow.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )

                InfiniteRow(name = p1Name, on = p1Infinite, onChange = onP1InfiniteChange)
                InfiniteRow(name = p2Name, on = p2Infinite, onChange = onP2InfiniteChange)

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = DuelColors.DuelGoldGlow)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfiniteRow(name: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF120A33), RoundedCornerShape(10.dp))
            .border(1.dp, DuelColors.DuelGold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable { onChange(!on) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "$name  ∞",
            color = DuelColors.DuelGoldGlow,
            style = MaterialTheme.typography.titleMedium,
        )
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = DuelColors.DuelGold,
                uncheckedThumbColor = DuelColors.DuelGoldGlow,
                uncheckedTrackColor = Color(0xFF1A1140),
                uncheckedBorderColor = DuelColors.DuelGold.copy(alpha = 0.5f),
            ),
        )
    }
}
