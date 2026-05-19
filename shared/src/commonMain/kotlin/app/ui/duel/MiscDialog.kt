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
import androidx.compose.ui.window.Dialog
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme

/**
 * Misc effects popover. Currently: per-player infinite-LP toggle (∞). When
 * on, the LP card renders ∞ instead of digits and the action region is
 * disabled for that side.
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
    val d = DuelTheme.dimens
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1140), RoundedCornerShape(d.radiusLg))
                .border(d.borderHairline, DuelColors.DuelGold.copy(alpha = 0.5f), RoundedCornerShape(d.radiusLg))
                .padding(d.s16),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(d.s12)) {
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

                Spacer(Modifier.height(d.s4))
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
    val d = DuelTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(d.touchMd)
            .background(Color(0xFF120A33), RoundedCornerShape(d.radiusMd))
            .border(d.borderHairline, DuelColors.DuelGold.copy(alpha = 0.4f), RoundedCornerShape(d.radiusMd))
            .clickable { onChange(!on) }
            .padding(horizontal = d.s12),
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
