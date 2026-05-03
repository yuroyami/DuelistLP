package app.ui.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.ui.components.StrokedText
import app.ui.theme.DuelColors

/**
 * Numeric keypad opened by a single tap on an action panel. Long-press still
 * opens the gauge — this is the typed-magnitude alternative.
 *
 * Mode-tinted (crimson ATTACK, blood SACRIFICE, emerald HEAL) so the staged
 * action is unambiguous. Confirms with value > 0; OK is disabled at 0.
 */
@Composable
fun ActionValueDialog(
    mode: LpActionMode,
    maxValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(0) }

    val accent = panelAccent(mode)
    val accentGlow = panelAccentGlow(mode)
    val accentDeep = panelAccentDeep(mode)

    fun appendDigit(d: Int) {
        val next = value * 10 + d
        if (next <= maxValue) value = next
    }

    fun backspace() {
        value /= 10
    }

    fun confirm() {
        if (value > 0) {
            onConfirm(value)
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1140), RoundedCornerShape(14.dp))
                .border(1.5.dp, accentGlow.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header — mode label tinted to match the action.
                Text(
                    text = modeTitle(mode),
                    style = TextStyle(
                        color = accentGlow,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.5.sp,
                    ),
                )

                // Big value readout.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(accentDeep.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    StrokedText(
                        text = value.toString(),
                        style = TextStyle(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Black,
                            fontSize = 56.sp,
                        ),
                        fillColor = if (value > 0) accentGlow else accentGlow.copy(alpha = 0.35f),
                        strokeColor = DuelColors.LpStroke,
                        strokeWidth = 2.5.dp,
                    )
                }

                // Keypad. 3×3 digit grid, then a Backspace / 0 / Confirm row.
                KeypadRow(
                    listOf(7, 8, 9),
                    onTap = ::appendDigit,
                    accent = accent,
                    accentGlow = accentGlow,
                )
                KeypadRow(
                    listOf(4, 5, 6),
                    onTap = ::appendDigit,
                    accent = accent,
                    accentGlow = accentGlow,
                )
                KeypadRow(
                    listOf(1, 2, 3),
                    onTap = ::appendDigit,
                    accent = accent,
                    accentGlow = accentGlow,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KeypadButton(
                        label = "⌫",
                        onClick = ::backspace,
                        accent = accent,
                        accentGlow = accentGlow,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                    KeypadButton(
                        label = "0",
                        onClick = { appendDigit(0) },
                        accent = accent,
                        accentGlow = accentGlow,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                    KeypadButton(
                        label = "OK",
                        onClick = ::confirm,
                        accent = accent,
                        accentGlow = accentGlow,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        emphasized = true,
                        enabled = value > 0,
                    )
                }

                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { value = 0 }) {
                        Text("Clear", color = DuelColors.DuelGoldGlow.copy(alpha = 0.7f))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = DuelColors.Crimson)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadRow(
    digits: List<Int>,
    onTap: (Int) -> Unit,
    accent: Color,
    accentGlow: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        digits.forEach { d ->
            KeypadButton(
                label = d.toString(),
                onClick = { onTap(d) },
                accent = accent,
                accentGlow = accentGlow,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    accent: Color,
    accentGlow: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val border = when {
        !enabled -> accent.copy(alpha = 0.25f)
        emphasized -> accentGlow
        else -> accent.copy(alpha = 0.6f)
    }
    val bg = if (emphasized && enabled) accent.copy(alpha = 0.25f) else Color(0xFF120A33)
    val color = when {
        !enabled -> Color(0xFF8A85A8)
        emphasized -> accentGlow
        else -> DuelColors.DuelGoldGlow
    }
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(10.dp))
            .border(if (emphasized && enabled) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = if (emphasized) FontWeight.ExtraBold else FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

private fun modeTitle(mode: LpActionMode): String = when (mode) {
    LpActionMode.Attack -> "ATTACK"
    LpActionMode.Heal -> "HEAL"
    LpActionMode.Sacrifice -> "SACRIFICE"
}
