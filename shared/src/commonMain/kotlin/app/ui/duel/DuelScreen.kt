package app.ui.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.audio.DuelOstPicker
import app.audio.LocalLpSfx
import app.audio.LocalOst
import app.audio.OstController
import app.model.DuelState
import app.model.Match
import app.model.MatchEvent
import app.model.PlayerSlot
import app.model.RpsPick
import app.persistence.DuelStore
import app.ui.theme.DuelColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

@Composable
fun DuelScreen(
    player1: String,
    player2: String,
    startingLp: Int,
    firstPlayer: PlayerSlot,
    rpsRounds: Int,
    rpsP1Pick: RpsPick,
    rpsP2Pick: RpsPick,
    store: DuelStore,
    onExit: () -> Unit,
    onRematch: () -> Unit,
) {
    val matchId = remember { newMatchId() }
    val nowMs = remember { Clock.System.now().toEpochMilliseconds() }

    var state by remember {
        mutableStateOf(
            DuelState(
                matchId = matchId,
                startedAt = nowMs,
                player1 = player1,
                player2 = player2,
                startingLp = startingLp,
                firstPlayer = firstPlayer,
                p1Lp = startingLp,
                p2Lp = startingLp,
                events = listOf(
                    MatchEvent.FirstPlayerDecided(
                        timestamp = nowMs,
                        firstPlayer = firstPlayer,
                        p1Pick = rpsP1Pick,
                        p2Pick = rpsP2Pick,
                        rounds = rpsRounds,
                    )
                ),
            )
        )
    }

    var showCustomFor by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    var showQuitConfirm by remember { mutableStateOf(false) }
    var actionsVisible by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val ost = LocalOst.current
    val lpSfx = LocalLpSfx.current
    var stabilizeJob: kotlinx.coroutines.Job? by remember { mutableStateOf(null) }

    fun applyDelta(target: PlayerSlot, delta: Int) {
        if (state.isOver) return
        val before = state.lpOf(target)
        val after = (before + delta).coerceAtLeast(0)
        if (after == before) return
        val now = Clock.System.now().toEpochMilliseconds()
        val event = MatchEvent.LpChange(now, target, delta, before, after)
        val newP1 = if (target == PlayerSlot.P1) after else state.p1Lp
        val newP2 = if (target == PlayerSlot.P2) after else state.p2Lp
        val winner = when {
            newP1 == 0 && newP2 == 0 -> target.opposite() // tie-breaker: last surviving
            newP1 == 0 -> PlayerSlot.P2
            newP2 == 0 -> PlayerSlot.P1
            else -> null
        }
        val withChange = state.copy(
            p1Lp = newP1,
            p2Lp = newP2,
            events = state.events + event,
        )
        state = if (winner != null) {
            withChange.copy(
                winner = winner,
                events = withChange.events + MatchEvent.Victory(now, winner),
            )
        } else {
            withChange
        }

        // SFX: keep the loop alive while LP is rapidly changing, fall through to
        // the resolution chord once it has been stable for a moment.
        lpSfx.onLpChange()
        stabilizeJob?.cancel()
        stabilizeJob = scope.launch {
            delay(STABILIZE_DELAY_MS)
            lpSfx.stabilize()
        }
    }

    // OST switching driven by the picker.
    val lastEvent = state.events.lastOrNull()
    LaunchedEffect(state.p1Lp, state.p2Lp, state.winner, lastEvent) {
        if (state.winner == null) {
            ost.play(DuelOstPicker.pick(state.p1Lp, state.p2Lp, state.startingLp, lastEvent))
        }
    }

    LaunchedEffect(state.winner) {
        val winner = state.winner ?: return@LaunchedEffect
        println("DuelScreen: winner=$winner — switching OST → Winning")
        // On victory, hand off to the winning theme regardless of LP totals
        // and stop the SFX loop immediately.
        ost.play(OstController.Track.Winning)
        lpSfx.stabilize()
        stabilizeJob?.cancel()
        delay(1800)
        actionsVisible = true
        // persist match
        scope.launch {
            store.saveMatch(state.toMatch(endedAtMs = Clock.System.now().toEpochMilliseconds()))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuelColors.ArenaGradient)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlayerHalf(
                playerName = state.player2,
                selfLp = state.p2Lp,
                opponentLp = state.p1Lp,
                isFirstPlayer = state.firstPlayer == PlayerSlot.P2,
                rotated = true,
                onLpChange = ::applyDelta,
                selfSlot = PlayerSlot.P2,
                opponentSlot = PlayerSlot.P1,
                onRequestCustomAmount = { commit -> showCustomFor = commit },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            CenterBar(onQuit = { showQuitConfirm = true })

            PlayerHalf(
                playerName = state.player1,
                selfLp = state.p1Lp,
                opponentLp = state.p2Lp,
                isFirstPlayer = state.firstPlayer == PlayerSlot.P1,
                rotated = false,
                onLpChange = ::applyDelta,
                selfSlot = PlayerSlot.P1,
                opponentSlot = PlayerSlot.P2,
                onRequestCustomAmount = { commit -> showCustomFor = commit },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        if (state.isOver) {
            VictoryOverlay(
                winner = state.winner!!,
                winnerName = state.nameOf(state.winner!!),
                loserName = state.nameOf(state.winner!!.opposite()),
                showActions = actionsVisible,
                onRematch = onRematch,
                onSaveAndExit = onExit,
            )
        }
    }

    showCustomFor?.let { commit ->
        CustomAmountDialog(
            onDismiss = { showCustomFor = null },
            onConfirm = { value ->
                commit(value)
                showCustomFor = null
            },
        )
    }

    if (showQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text("End the duel?") },
            text = { Text("Returns to setup. Current match will be saved if a winner has been declared.") },
            confirmButton = {
                TextButton(onClick = {
                    showQuitConfirm = false
                    onExit()
                }) { Text("End duel", color = DuelColors.Crimson) }
            },
            dismissButton = {
                TextButton(onClick = { showQuitConfirm = false }) { Text("Keep playing") }
            },
            containerColor = Color(0xFF1A1140),
        )
    }
}

@Composable
private fun CenterBar(onQuit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0xFF120A33))
            .border(1.dp, DuelColors.DuelGold.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onQuit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = DuelColors.Crimson,
                ),
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) { Text("End duel", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CustomAmountDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1140),
        title = { Text("Custom step", color = DuelColors.DuelGoldGlow) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("e.g. 250") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { (text.toIntOrNull() ?: 0).takeIf { it > 0 }?.let(onConfirm) ?: onDismiss() },
            ) { Text("Set", color = DuelColors.DuelGoldGlow) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** How long the LP must be stable before the SFX falls through to its resolution. */
private const val STABILIZE_DELAY_MS = 320L

private fun PlayerSlot.opposite(): PlayerSlot = if (this == PlayerSlot.P1) PlayerSlot.P2 else PlayerSlot.P1

private fun newMatchId(): String = buildString {
    append(Clock.System.now().toEpochMilliseconds().toString(36))
    append('-')
    repeat(4) { append(Random.nextInt(36).toString(36)) }
}

private fun DuelState.toMatch(endedAtMs: Long): Match = Match(
    id = matchId,
    startedAt = startedAt,
    endedAt = endedAtMs,
    player1 = player1,
    player2 = player2,
    startingLp = startingLp,
    firstPlayer = firstPlayer,
    finalP1Lp = p1Lp,
    finalP2Lp = p2Lp,
    winner = winner,
    events = events,
)
