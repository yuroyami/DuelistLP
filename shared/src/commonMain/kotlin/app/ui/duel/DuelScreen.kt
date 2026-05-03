package app.ui.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.audio.ActionSfx
import app.audio.DuelAutoEvent
import app.audio.DuelEventDetector
import app.audio.LocalLpSfx
import app.audio.LocalOst
import app.audio.MusicMode
import app.audio.MusicSettings
import app.audio.OstEventScheduler
import app.audio.OstTracks
import app.model.DuelState
import app.model.Match
import app.model.MatchEvent
import app.model.Outcome
import app.model.PlayerSlot
import app.model.RpsPick
import app.persistence.DuelStore
import app.ui.theme.DuelColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

/**
 * The duel surface — orchestrates LP state, animation locking, the action
 * buffer, music, and the victory overlay.
 *
 * Layout: only the active player's [PlayerHalf] renders in full; the inactive
 * player gets a slim [OpponentPeek]. End Turn flips the active half (gated by
 * [canEndTurn]: not animating, duel not over, active buffer empty).
 *
 * LP changes go through a stage-then-commit buffer:
 *   1. User stages 1+ actions via [stageAction] (panel tap → keypad, or
 *      long-press → triangular gauge).
 *   2. [commitBuffer] replays each entry sequentially: floating delta pop →
 *      LP roll → settle. [actionsLocked] disables both halves throughout.
 *   3. Outcome check is DEFERRED until the last entry settles, so a 0/0
 *      mid-batch resolves to a draw rather than locking in a winner.
 *
 * Music has three modes ([MusicMode]): Auto (driven by [OstEventScheduler]),
 * Manual (user-pinned track), Off. Match-end always plays Win/Draw track
 * regardless of mode.
 */
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
    // Cap one action at half the starting LP. 8000 LP → max 4000 per swing.
    val wheelMax = remember(startingLp) { (startingLp / 2).coerceAtLeast(50) }

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

    var showQuitConfirm by remember { mutableStateOf(false) }
    var showMusicDialog by remember { mutableStateOf(false) }
    var showMiscDialog by remember { mutableStateOf(false) }
    // Match-end Rematch/Save buttons. Revealed ~1.8 s after the duel resolves
    // so the victory pulse has a moment before action UI shows up.
    var actionsVisible by remember { mutableStateOf(false) }

    var musicSettings by remember { mutableStateOf(MusicSettings()) }
    var p1Infinite by remember { mutableStateOf(false) }
    var p2Infinite by remember { mutableStateOf(false) }

    // Action buffers. Invariant: at most one of these is non-empty at any
    // time, because only the active half renders input controls and the
    // active half is the only valid stager.
    var p1Buffer by remember { mutableStateOf<List<StagedAction>>(emptyList()) }
    var p2Buffer by remember { mutableStateOf<List<StagedAction>>(emptyList()) }

    // false = auto-commit each action immediately.
    // true  = explicit queue: stage many, commit on demand via the buffer strip.
    var bufferMode by remember { mutableStateOf(false) }

    // Set by single-tap on a panel; opens [ActionValueDialog] for typed magnitude.
    var keypadMode by remember { mutableStateOf<LpActionMode?>(null) }

    // True from the first floating-delta pop until the last settle of a commit
    // sequence. While true, both halves are disabled and End Turn is gated.
    var animationDurationMs by remember { mutableStateOf(MIN_ANIM_MS) }
    var animationStepMagnitude by remember { mutableStateOf(0) }
    var actionsLocked by remember { mutableStateOf(false) }
    var p1FloatingDelta by remember { mutableStateOf<Int?>(null) }
    var p2FloatingDelta by remember { mutableStateOf<Int?>(null) }
    // Triangular gauge state. Hosted at this level (not in PlayerHalf) so it
    // can render on the OPPOSITE half from the pressing finger.
    var wheelOverlay by remember { mutableStateOf<WheelOverlay?>(null) }

    val scope = rememberCoroutineScope()
    val ost = LocalOst.current
    val lpSfx = LocalLpSfx.current
    var stabilizeJob: kotlinx.coroutines.Job? by remember { mutableStateOf(null) }

    // Auto-mode music driver. Per-screen lifetime; reset on entry so prior
    // matches' one-timer events don't carry over.
    val eventScheduler = remember {
        OstEventScheduler(ost = ost, nowMs = { Clock.System.now().toEpochMilliseconds() })
    }

    /**
     * Apply ONE signed delta to one player. Plays the floating-delta pop +
     * action SFX, then the LP slot-machine roll, then schedules the settle.
     * Returns the new [DuelState] so the caller can chain multiple actions
     * without read-after-write hazards on the captured `state` snapshot.
     *
     * Suspends until the LP roll completes. `state` is updated mid-flight
     * (after the floating delta hangs) so the digit reel sees the new value.
     */
    suspend fun runOneAction(
        target: PlayerSlot,
        delta: Int,
        fromState: DuelState,
        actionSfx: ActionSfx,
    ): DuelState {
        val before = fromState.lpOf(target)
        val after = (before + delta).coerceAtLeast(0)
        if (after == before) return fromState

        val stepMag = kotlin.math.abs(delta)
        animationStepMagnitude = stepMag
        animationDurationMs = (MIN_ANIM_MS + stepMag * MS_PER_LP).coerceAtMost(MAX_ANIM_MS)

        // Phase A: floating delta pop + action SFX. LP doesn't move yet.
        when (target) {
            PlayerSlot.P1 -> p1FloatingDelta = delta
            PlayerSlot.P2 -> p2FloatingDelta = delta
        }
        lpSfx.playActionSfx(actionSfx)
        delay(FLOATING_DELTA_TOTAL_MS)

        // Phase B: flip LP, fire the roll loop, schedule the settle chord.
        val nowEvent = Clock.System.now().toEpochMilliseconds()
        val newP1 = if (target == PlayerSlot.P1) after else fromState.p1Lp
        val newP2 = if (target == PlayerSlot.P2) after else fromState.p2Lp
        val event = MatchEvent.LpChange(nowEvent, target, delta, before, after)
        val advanced = fromState.copy(
            p1Lp = newP1,
            p2Lp = newP2,
            events = fromState.events + event,
        )
        state = advanced

        lpSfx.onLpChange()
        stabilizeJob?.cancel()
        stabilizeJob = scope.launch {
            delay(animationDurationMs.toLong() + STABILIZE_DELAY_MS)
            lpSfx.stabilize()
        }
        delay(animationDurationMs.toLong())
        return advanced
    }

    /** 0/0 → draw, single 0 → win for the other side, else in progress. */
    fun evaluateOutcome(s: DuelState): Outcome = when {
        s.p1Lp == 0 && s.p2Lp == 0 -> Outcome.Draw
        s.p1Lp == 0 -> Outcome.Win(PlayerSlot.P2)
        s.p2Lp == 0 -> Outcome.Win(PlayerSlot.P1)
        else -> Outcome.InProgress
    }

    /** Pure detection. Callers accumulate, then [eventScheduler.trigger] once at sequence end. */
    fun detectFor(
        before: DuelState,
        after: DuelState,
        delta: Int,
        target: PlayerSlot,
    ): List<DuelAutoEvent> = DuelEventDetector.detect(
        startingLp = after.startingLp,
        p1Before = before.p1Lp,
        p2Before = before.p2Lp,
        p1After = after.p1Lp,
        p2After = after.p2Lp,
        target = target,
        delta = delta,
        lpHistory = after.events.filterIsInstance<MatchEvent.LpChange>(),
    )

    /** Auto-mode event dispatch after the post-settle wait. No-op if duel is over. */
    suspend fun maybeFireEvents(after: DuelState, candidates: List<DuelAutoEvent>) {
        if (after.outcome.isOver) return
        if (musicSettings.mode != MusicMode.Auto) return
        if (candidates.isEmpty()) return
        delay(POST_SETTLE_EVENT_DELAY_MS)
        eventScheduler.onLpAction()
        eventScheduler.trigger(candidates)
    }

    /**
     * Atomic commit: replays each staged entry through [runOneAction], then
     * evaluates outcome ONCE at the end. Deferring the outcome check is what
     * lets a 0/0 mid-batch resolve to a draw rather than locking in whoever
     * hit 0 first.
     */
    fun commitBuffer(actor: PlayerSlot) {
        if (state.outcome.isOver) return
        if (actionsLocked) return
        val buffer = if (actor == PlayerSlot.P1) p1Buffer else p2Buffer
        if (buffer.isEmpty()) return

        actionsLocked = true
        scope.launch {
            try {
                val opponent = actor.opposite()
                var working = state
                val accumulatedEvents = mutableListOf<DuelAutoEvent>()
                for (entry in buffer) {
                    // Translate StagedAction (mode + magnitude) → signed delta + target + SFX.
                    val target: PlayerSlot
                    val delta: Int
                    val sfx: ActionSfx
                    when (entry.mode) {
                        LpActionMode.Attack -> {
                            target = opponent; delta = -entry.value; sfx = ActionSfx.Damage
                        }
                        LpActionMode.Heal -> {
                            target = actor; delta = entry.value; sfx = ActionSfx.Heal
                        }
                        LpActionMode.Sacrifice -> {
                            target = actor; delta = -entry.value; sfx = ActionSfx.Blood
                        }
                    }
                    val before = working
                    val advanced = runOneAction(target, delta, working, actionSfx = sfx)
                    if (advanced !== before) {
                        accumulatedEvents += detectFor(before, advanced, delta, target)
                        working = advanced
                    }
                }
                // Drain the buffer regardless of outcome.
                // Drain regardless of outcome so the buffer never stays stale.
                if (actor == PlayerSlot.P1) p1Buffer = emptyList() else p2Buffer = emptyList()

                val outcome = evaluateOutcome(working)
                if (outcome != Outcome.InProgress) {
                    val now = Clock.System.now().toEpochMilliseconds()
                    val terminalEvent = if (outcome is Outcome.Win) {
                        MatchEvent.Victory(now, outcome.winner)
                    } else {
                        MatchEvent.Draw(now)
                    }
                    state = working.copy(outcome = outcome, events = working.events + terminalEvent)
                } else {
                    // distinct() preserves declaration order so the scheduler
                    // still sees Major candidates before Minor ones.
                    maybeFireEvents(working, accumulatedEvents.distinct())
                }
            } finally {
                actionsLocked = false
            }
        }
    }

    /**
     * Append to actor's buffer; if [bufferMode] is off, immediately commit
     * the new single-entry buffer so the action runs without an explicit
     * Commit press. Refuses defensively if the opponent's buffer is non-empty.
     */
    fun stageAction(actor: PlayerSlot, action: StagedAction) {
        if (state.outcome.isOver) return
        if (actionsLocked) return
        when (actor) {
            PlayerSlot.P1 -> {
                if (p2Buffer.isNotEmpty()) return
                p1Buffer = p1Buffer + action
            }
            PlayerSlot.P2 -> {
                if (p1Buffer.isNotEmpty()) return
                p2Buffer = p2Buffer + action
            }
        }
        if (!bufferMode) commitBuffer(actor)
    }

    fun undoBuffer(actor: PlayerSlot) {
        if (actionsLocked) return
        when (actor) {
            PlayerSlot.P1 -> if (p1Buffer.isNotEmpty()) p1Buffer = p1Buffer.dropLast(1)
            PlayerSlot.P2 -> if (p2Buffer.isNotEmpty()) p2Buffer = p2Buffer.dropLast(1)
        }
    }

    fun clearBuffer(actor: PlayerSlot) {
        if (actionsLocked) return
        when (actor) {
            PlayerSlot.P1 -> p1Buffer = emptyList()
            PlayerSlot.P2 -> p2Buffer = emptyList()
        }
    }

    // Reset scheduler + fire MatchStart if Auto. Runs once when the screen
    // composes (a Rematch destroys + recomposes this screen, so MatchStart
    // is one-timer per duel, not per app session).
    LaunchedEffect(Unit) {
        eventScheduler.reset()
        if (musicSettings.mode == MusicMode.Auto) {
            eventScheduler.trigger(listOf(DuelAutoEvent.MatchStart))
        }
    }

    // React to mode/track switches from the Music popover.
    // Auto→Auto re-trigger of MatchStart is a no-op (one-timer already fired).
    LaunchedEffect(musicSettings) {
        when (musicSettings.mode) {
            MusicMode.Disabled -> {
                eventScheduler.stop()
                ost.stop()
            }
            MusicMode.Manual -> {
                eventScheduler.stop()
                ost.play(OstTracks.dmp(musicSettings.pack, musicSettings.manualTrack))
            }
            MusicMode.Auto -> {
                // Kick off MatchStart if no event has fired yet; otherwise
                // the scheduler resumes naturally on the next LP action.
                eventScheduler.trigger(listOf(DuelAutoEvent.MatchStart))
            }
        }
    }

    // Match-end: silence event scheduler, play win/draw track, persist match,
    // reveal action buttons after a dramatic pause.
    LaunchedEffect(state.outcome) {
        if (!state.outcome.isOver) return@LaunchedEffect
        eventScheduler.stop()
        if (musicSettings.mode != MusicMode.Disabled) {
            val track = if (state.outcome is Outcome.Draw) OstTracks.MatchEndDraw else OstTracks.MatchEndWin
            ost.play(track)
        }
        lpSfx.stabilize()
        stabilizeJob?.cancel()
        delay(1800)
        actionsVisible = true
        scope.launch {
            store.saveMatch(state.toMatch(endedAtMs = Clock.System.now().toEpochMilliseconds()))
        }
    }

    fun activeBufferEmpty(): Boolean = when (state.currentTurn) {
        PlayerSlot.P1 -> p1Buffer.isEmpty()
        PlayerSlot.P2 -> p2Buffer.isEmpty()
    }

    fun canEndTurn(): Boolean =
        !actionsLocked && !state.outcome.isOver && activeBufferEmpty()

    fun endTurn() {
        if (!canEndTurn()) return
        // Defensive: never carry a hanging gauge into the next turn.
        wheelOverlay = null
        state = state.copy(
            currentTurn = state.currentTurn.opposite(),
            turnNumber = state.turnNumber + 1,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuelColors.ArenaGradient)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Upper area: P2 full half (rotated 180°) when their turn, else
            // P2 peek. Mirror logic in the lower area for P1. Only ONE
            // PlayerHalf renders at a time so input is unambiguous.
            if (state.currentTurn == PlayerSlot.P2) {
                PlayerHalf(
                    playerName = state.player2,
                    selfLp = state.p2Lp,
                    showFirstBadge = state.firstPlayer == PlayerSlot.P2 && state.turnNumber <= 2,
                    rotated = true,
                    onStageAction = { action -> stageAction(PlayerSlot.P2, action) },
                    onTapForValue = { mode -> keypadMode = mode },
                    bufferedActions = p2Buffer,
                    bufferEnabled = bufferMode,
                    onCommitBuffer = { commitBuffer(PlayerSlot.P2) },
                    onUndoBuffer = { undoBuffer(PlayerSlot.P2) },
                    onClearBuffer = { clearBuffer(PlayerSlot.P2) },
                    onWheelStateChange = { ws ->
                        wheelOverlay = ws?.let { WheelOverlay(PlayerSlot.P2, it.mode, it.value) }
                    },
                    onEndTurn = ::endTurn,
                    endTurnEnabled = canEndTurn(),
                    isInfinite = p2Infinite,
                    actionsEnabled = !actionsLocked && !state.outcome.isOver,
                    wheelMax = wheelMax,
                    animationDurationMs = animationDurationMs,
                    animationStepMagnitude = animationStepMagnitude,
                    pendingFloatingDelta = p2FloatingDelta,
                    onFloatingDeltaComplete = { p2FloatingDelta = null },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                OpponentPeek(
                    name = state.player2,
                    lp = state.p2Lp,
                    isInfinite = p2Infinite,
                    showFirstBadge = state.firstPlayer == PlayerSlot.P2 && state.turnNumber <= 2,
                    rotated = true,
                    animationDurationMs = animationDurationMs,
                    pendingFloatingDelta = p2FloatingDelta,
                    onFloatingDeltaComplete = { p2FloatingDelta = null },
                )
            }

            CenterBar(
                bufferMode = bufferMode,
                onBufferModeChange = { bufferMode = it },
                turnNumber = state.turnNumber,
                onMusic = { showMusicDialog = true },
                onMisc = { showMiscDialog = true },
                onQuit = { showQuitConfirm = true },
            )

            // Lower area: P1 full half when their turn, else P1 peek.
            if (state.currentTurn == PlayerSlot.P1) {
                PlayerHalf(
                    playerName = state.player1,
                    selfLp = state.p1Lp,
                    showFirstBadge = state.firstPlayer == PlayerSlot.P1 && state.turnNumber <= 2,
                    rotated = false,
                    onStageAction = { action -> stageAction(PlayerSlot.P1, action) },
                    onTapForValue = { mode -> keypadMode = mode },
                    bufferedActions = p1Buffer,
                    bufferEnabled = bufferMode,
                    onCommitBuffer = { commitBuffer(PlayerSlot.P1) },
                    onUndoBuffer = { undoBuffer(PlayerSlot.P1) },
                    onClearBuffer = { clearBuffer(PlayerSlot.P1) },
                    onWheelStateChange = { ws ->
                        wheelOverlay = ws?.let { WheelOverlay(PlayerSlot.P1, it.mode, it.value) }
                    },
                    onEndTurn = ::endTurn,
                    endTurnEnabled = canEndTurn(),
                    isInfinite = p1Infinite,
                    actionsEnabled = !actionsLocked && !state.outcome.isOver,
                    wheelMax = wheelMax,
                    animationDurationMs = animationDurationMs,
                    animationStepMagnitude = animationStepMagnitude,
                    pendingFloatingDelta = p1FloatingDelta,
                    onFloatingDeltaComplete = { p1FloatingDelta = null },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                OpponentPeek(
                    name = state.player1,
                    lp = state.p1Lp,
                    isInfinite = p1Infinite,
                    showFirstBadge = state.firstPlayer == PlayerSlot.P1 && state.turnNumber <= 2,
                    rotated = false,
                    animationDurationMs = animationDurationMs,
                    pendingFloatingDelta = p1FloatingDelta,
                    onFloatingDeltaComplete = { p1FloatingDelta = null },
                )
            }
        }

        // Gauge overlay: dim scrim + gauge on the OPPOSITE half from the
        // pressing player so they can read it past their finger. Rotated
        // 180° when P2 is pressing so the readout reads upright across the
        // table.
        wheelOverlay?.let { overlay ->
            val pressingIsP2 = overlay.pressingSlot == PlayerSlot.P2
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = if (pressingIsP2) Alignment.BottomCenter else Alignment.TopCenter,
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)) {
                    LpActionWheel(
                        mode = overlay.mode,
                        value = overlay.value,
                        maxValue = wheelMax,
                        modifier = if (pressingIsP2) Modifier.rotate(180f) else Modifier,
                    )
                }
            }
        }

        if (state.outcome.isOver) {
            VictoryOverlay(
                outcome = state.outcome,
                p1Name = state.player1,
                p2Name = state.player2,
                showActions = actionsVisible,
                onRematch = onRematch,
                onSaveAndExit = onExit,
            )
        }
    }

    if (showQuitConfirm) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text("End the duel?") },
            text = { Text("Returns to setup. Match is saved if a winner exists.") },
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

    if (showMusicDialog) {
        MusicSettingsDialog(
            settings = musicSettings,
            onSettingsChange = { musicSettings = it },
            onDismiss = { showMusicDialog = false },
        )
    }

    if (showMiscDialog) {
        MiscDialog(
            p1Name = state.player1,
            p2Name = state.player2,
            p1Infinite = p1Infinite,
            p2Infinite = p2Infinite,
            onP1InfiniteChange = { p1Infinite = it },
            onP2InfiniteChange = { p2Infinite = it },
            onDismiss = { showMiscDialog = false },
        )
    }

    keypadMode?.let { mode ->
        ActionValueDialog(
            mode = mode,
            maxValue = wheelMax,
            onConfirm = { value ->
                stageAction(state.currentTurn, StagedAction(mode, value))
            },
            onDismiss = { keypadMode = null },
        )
    }
}

/** Active gauge: pressing player + mode + currently-snapped magnitude. */
private data class WheelOverlay(
    val pressingSlot: PlayerSlot,
    val mode: LpActionMode,
    val value: Int,
)

// Center bar between the two halves. Houses the popovers (Music, Misc),
// the global Auto/Queue toggle, and Quit. End Turn moved into each player's
// own half (see [PlayerHalf]) so the action lives next to the actor.
@Composable
private fun CenterBar(
    bufferMode: Boolean,
    onBufferModeChange: (Boolean) -> Unit,
    turnNumber: Int,
    onMusic: () -> Unit,
    onMisc: () -> Unit,
    onQuit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF120A33))
            .border(1.dp, DuelColors.DuelGold.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CenterIconButton(label = "♪", onClick = onMusic)
            CenterIconButton(label = "✦", onClick = onMisc)
            BufferModeToggle(
                enabled = bufferMode,
                turnNumber = turnNumber,
                onChange = onBufferModeChange,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(Color(0xFF1A1140), RoundedCornerShape(10.dp))
                    .border(1.dp, DuelColors.Crimson.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(
                    onClick = onQuit,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text(
                        "Quit",
                        color = DuelColors.Crimson,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

// Pill that swaps between AUTO COMMIT and QUEUE MODE. When QUEUE is on,
// staged actions stack in the per-player buffer strip until the player
// presses Commit; when AUTO is on, each staged action runs immediately.
// Subtext shows "Turn N" so the bar still surfaces the turn counter.
@Composable
private fun BufferModeToggle(
    enabled: Boolean,
    turnNumber: Int,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (enabled) DuelColors.DuelGoldGlow else DuelColors.DuelGold.copy(alpha = 0.6f)
    val bg = if (enabled) DuelColors.DuelGold.copy(alpha = 0.30f) else Color(0xFF1A1140)
    val label = if (enabled) "QUEUE MODE" else "AUTO COMMIT"
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(10.dp))
            .border(if (enabled) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onChange(!enabled) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                color = DuelColors.DuelGoldGlow,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                letterSpacing = 1.5.sp,
                maxLines = 1,
            )
            Text(
                text = "Turn $turnNumber",
                color = DuelColors.DuelGoldGlow.copy(alpha = 0.55f),
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CenterIconButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .background(Color(0xFF1A1140), RoundedCornerShape(10.dp))
            .border(1.dp, DuelColors.DuelGold.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            Text(
                label,
                color = DuelColors.DuelGoldGlow,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
        }
    }
}

private const val STABILIZE_DELAY_MS = 320L

// LP-roll duration scales with delta magnitude:
//   Δ100  ≈ 480 ms   Δ500  ≈ 1280 ms   Δ1000 ≈ 2280 ms   ≥Δ2360 caps at 5 s
private const val MIN_ANIM_MS = 280
private const val MAX_ANIM_MS = 5000
private const val MS_PER_LP = 2

/** Pause after LP settles before evaluating Auto-mode music events. */
private const val POST_SETTLE_EVENT_DELAY_MS = 1000L

private fun PlayerSlot.opposite(): PlayerSlot =
    if (this == PlayerSlot.P1) PlayerSlot.P2 else PlayerSlot.P1

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
    isDraw = isDraw,
    events = events,
)
