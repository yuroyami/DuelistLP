package app.ui.history

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.model.Match
import app.model.MatchEvent
import app.model.PlayerSlot
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme
import app.util.formatClockTime
import app.util.formatDateTime
import app.util.formatDuration

/**
 * Replay view for one saved [Match]. Header shows participants + final LP +
 * duration; the lazy list under it walks every [MatchEvent] in chronological
 * order with per-event color (crimson for damage, emerald for heal, gold for
 * meta events).
 */
@Composable
fun MatchDetailScreen(match: Match, onBack: () -> Unit) {
    val d = DuelTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuelColors.ArenaGradient)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = d.s12, vertical = d.s8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = DuelColors.DuelGoldGlow) }
            Text(
                "Match detail",
                style = MaterialTheme.typography.titleLarge,
                color = DuelColors.DuelGoldGlow,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(d.touchLg))
        }

        Column(modifier = Modifier.padding(horizontal = d.s16)) {
            Header(match)
            Spacer(Modifier.height(d.s12))
            Text(
                "Event log",
                color = DuelColors.DuelGoldGlow,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(d.s8))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = d.s16),
            verticalArrangement = Arrangement.spacedBy(d.s6),
        ) {
            items(match.events) { event -> EventRow(event, match) }
            item { Spacer(Modifier.height(d.s20)) }
        }
    }
}

@Composable
private fun Header(match: Match) {
    val d = DuelTheme.dimens
    val winnerLabel = when {
        match.isDraw -> "Draw"
        match.winner != null -> if (match.winner == PlayerSlot.P1) match.player1 else match.player2
        else -> "—"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1140), RoundedCornerShape(d.radiusMd))
            .border(d.borderHairline, DuelColors.DuelGold.copy(alpha = 0.45f), RoundedCornerShape(d.radiusMd))
            .padding(d.s14),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(d.s4)) {
            Text(
                "${match.player1}  vs  ${match.player2}",
                style = MaterialTheme.typography.titleMedium,
                color = DuelColors.DuelGoldGlow,
                fontWeight = FontWeight.Bold,
            )
            Text("Starting LP: ${match.startingLp}", color = Color(0xFFEDE7FF))
            Text(
                "Final: ${match.finalP1Lp} – ${match.finalP2Lp}  · ${if (match.isDraw) "Result" else "Winner"}: $winnerLabel",
                color = DuelColors.DuelGoldGlow,
            )
            Text(
                "${formatDateTime(match.startedAt)}  · ${formatDuration(match.durationMs)}",
                color = DuelColors.DuelGoldGlow.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun EventRow(event: MatchEvent, match: Match) {
    val d = DuelTheme.dimens
    val (label, detail, color) = describeEvent(event, match)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF130C36), RoundedCornerShape(d.radiusSm))
            .padding(horizontal = d.s10, vertical = d.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatClockTime(event.timestamp),
            color = DuelColors.DuelGoldGlow.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = d.s10),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = color, fontWeight = FontWeight.Bold)
            Text(detail, color = Color(0xFFEDE7FF), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun describeEvent(event: MatchEvent, match: Match): Triple<String, String, Color> = when (event) {
    is MatchEvent.LpChange -> {
        val name = if (event.target == PlayerSlot.P1) match.player1 else match.player2
        val sign = if (event.delta < 0) "−" else "+"
        val verb = if (event.delta < 0) "loses" else "gains"
        Triple(
            "$name $verb ${kotlin.math.abs(event.delta)} LP",
            "$sign${kotlin.math.abs(event.delta)}  ·  ${event.before} → ${event.after}",
            if (event.delta < 0) DuelColors.Crimson else DuelColors.EmeraldHeal,
        )
    }
    is MatchEvent.FirstPlayerDecided -> {
        val first = if (event.firstPlayer == PlayerSlot.P1) match.player1 else match.player2
        Triple(
            "$first goes first",
            "RPS: ${event.p1Pick.name.lowercase()} vs ${event.p2Pick.name.lowercase()} (rounds: ${event.rounds})",
            DuelColors.DuelGoldGlow,
        )
    }
    is MatchEvent.Victory -> {
        val w = if (event.winner == PlayerSlot.P1) match.player1 else match.player2
        Triple("VICTORY: $w", "Match ends.", DuelColors.LpYellow)
    }
    is MatchEvent.Draw -> Triple(
        "DRAW",
        "Both players hit 0 LP — match ends in a draw.",
        DuelColors.DuelGoldGlow,
    )
}

