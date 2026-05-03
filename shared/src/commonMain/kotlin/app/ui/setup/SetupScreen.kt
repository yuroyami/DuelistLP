package app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.persistence.DuelSettings
import app.persistence.DuelStore
import app.ui.components.StrokedText
import app.ui.theme.DuelColors
import app.ui.theme.lpDigitStyle
import kotlinx.coroutines.launch

/**
 * Pre-duel screen: two name fields + starting-LP entry with preset chips.
 * Hydrates names + LP from [store] on first composition (default "Player 1"
 * / "Player 2" if persisted entries are blank). Persists on Start.
 *
 * Custom LP is allowed; the Start button is gated to LP ≥ 100 + non-blank
 * names. Picking a preset chip just sets the text field — no separate state.
 */
@Composable
fun SetupScreen(
    store: DuelStore,
    onStartDuel: (player1: String, player2: String, startingLp: Int) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val settings by store.settings.collectAsState(initial = DuelSettings())

    var player1 by remember { mutableStateOf("") }
    var player2 by remember { mutableStateOf("") }
    var lpText by remember { mutableStateOf("") }
    var hydrated by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        if (!hydrated) {
            player1 = settings.player1.ifEmpty { "Player 1" }
            player2 = settings.player2.ifEmpty { "Player 2" }
            lpText = settings.startingLp.toString()
            hydrated = true
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DuelColors.ArenaGradient)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Title size + vertical rhythm scaled to viewport. Coerce keeps it
        // legible on small iPhone XS and not dwarfed on large Pro Max.
        val titleSp = (maxWidth.value * 0.18f).toInt().coerceIn(48, 88)
        val verticalGap = (maxHeight.value * 0.025f).toInt().coerceIn(12, 22).dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(verticalGap),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        ) {
            Spacer(Modifier.height(4.dp))

            StrokedText(
                text = "DUEL",
                style = lpDigitStyle(titleSp),
                fillColor = DuelColors.LpYellow,
                strokeColor = DuelColors.LpStroke,
                strokeWidth = (titleSp / 28f).coerceAtLeast(1f).dp,
            )
            Text(
                text = "Time to duel.",
                color = DuelColors.DuelGoldGlow,
                style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            DuelField(
                value = player1,
                onValueChange = { player1 = it },
                label = "Player 1 name",
            )
            DuelField(
                value = player2,
                onValueChange = { player2 = it },
                label = "Player 2 name",
            )
            DuelField(
                value = lpText,
                onValueChange = { new -> lpText = new.filter { it.isDigit() }.take(6) },
                label = "Starting LP",
                keyboardType = KeyboardType.Number,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(2000, 4000, 8000, 16000).forEach { preset ->
                    LpPresetChip(
                        label = "$preset",
                        selected = lpText.toIntOrNull() == preset,
                        onClick = { lpText = preset.toString() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val scope = rememberCoroutineScope()
            val canStart = player1.isNotBlank() && player2.isNotBlank() && (lpText.toIntOrNull() ?: 0) >= 100
            Button(
                onClick = {
                    val lp = lpText.toIntOrNull() ?: DuelStore.DEFAULT_LP
                    scope.launch {
                        store.saveSettings(DuelSettings(lp, player1.trim(), player2.trim()))
                    }
                    onStartDuel(player1.trim(), player2.trim(), lp)
                },
                enabled = canStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DuelColors.DuelGold,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("START", style = MaterialTheme.typography.titleMedium)
            }

            TextButton(onClick = onOpenHistory) {
                Text("History", color = DuelColors.DuelGoldGlow)
            }
        }
    }
}

@Composable
private fun DuelField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DuelColors.DuelGold,
            unfocusedBorderColor = DuelColors.DuelGold.copy(alpha = 0.45f),
            focusedLabelColor = DuelColors.DuelGoldGlow,
            cursorColor = DuelColors.DuelGoldGlow,
        ),
    )
}

@Composable
private fun LpPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(
                color = if (selected) DuelColors.DuelGold else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = 1.dp,
                color = DuelColors.DuelGold,
                shape = RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Text(
                label,
                color = if (selected) Color.Black else DuelColors.DuelGoldGlow,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
