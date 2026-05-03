package app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.model.Match
import app.model.PlayerSlot
import app.persistence.DuelStore
import app.ui.theme.DuelColors
import app.util.formatDateTime
import app.util.formatDuration
import kotlinx.coroutines.launch

/**
 * Lazy list of saved matches (most-recent first), reactively driven from
 * [DuelStore.matches]. Tap a row → [MatchDetailScreen]. Clear button trims
 * the entire history (with confirm dialog).
 */
@Composable
fun HistoryScreen(
    store: DuelStore,
    onBack: () -> Unit,
    onOpen: (Match) -> Unit,
) {
    val matches by store.matches.collectAsState(initial = emptyList())
    var showClearConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuelColors.ArenaGradient)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = DuelColors.DuelGoldGlow) }
            Text(
                "Match history",
                style = MaterialTheme.typography.titleLarge,
                color = DuelColors.DuelGoldGlow,
                fontWeight = FontWeight.Bold,
            )
            TextButton(
                onClick = { showClearConfirm = true },
                enabled = matches.isNotEmpty(),
            ) { Text("Clear", color = if (matches.isEmpty()) Color.Gray else DuelColors.Crimson) }
        }

        if (matches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No duels yet.",
                    color = DuelColors.DuelGoldGlow.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(matches, key = { it.id }) { match -> MatchRow(match, onClick = { onOpen(match) }) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = Color(0xFF1A1140),
            title = { Text("Clear history?", color = DuelColors.DuelGoldGlow) },
            text = { Text("Deletes every saved match.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch { store.clearHistory() }
                }) { Text("Delete all", color = DuelColors.Crimson) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MatchRow(match: Match, onClick: () -> Unit) {
    val winnerName = when {
        match.isDraw -> "Draw"
        match.winner != null -> if (match.winner == PlayerSlot.P1) match.player1 else match.player2
        else -> "—"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1140), RoundedCornerShape(12.dp))
            .border(1.dp, DuelColors.DuelGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${match.player1}  vs  ${match.player2}",
                style = MaterialTheme.typography.titleMedium,
                color = DuelColors.DuelGoldGlow,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Winner: $winnerName · ${match.finalP1Lp} – ${match.finalP2Lp}",
                color = Color(0xFFEDE7FF),
            )
            Text(
                text = "${formatDateTime(match.startedAt)}  ·  ${formatDuration(match.durationMs)}",
                color = DuelColors.DuelGoldGlow.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
