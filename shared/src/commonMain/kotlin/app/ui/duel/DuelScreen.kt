package app.ui.duel

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.audio.DuelAutoEvent
import app.audio.DuelEventDetector
import app.audio.GameSfx
import app.audio.LocalLpSfx
import app.audio.LocalOst
import app.audio.OneShotTracks
import app.audio.OstEventScheduler
import app.audio.OstTracks
import app.model.DuelPhase
import app.model.DuelState
import app.model.Match
import app.model.MatchEvent
import app.model.Outcome
import app.model.PlayerSlot
import app.model.RpsPick
import app.persistence.DuelSettings
import app.persistence.DuelStore
import app.ui.theme.DuelColors
import app.ui.theme.DuelTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
 * Music is a single ON/OFF toggle persisted in [DuelStore]. When on, the
 * [OstEventScheduler] drives event-based track switches; when off, silence.
 * Match-end music still plays Winner/Draw when enabled, with an applause
 * SFX overlay regardless.
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
    var showMiscDialog by remember { mutableStateOf(false) }
    // Match-end Rematch/Save buttons. Revealed ~1.8 s after the duel resolves
    // so the victory pulse has a moment before action UI shows up.
    var actionsVisible by remember { mutableStateOf(false) }

    // Music is a persisted boolean — no in-duel mode/pack selection any more.
    val settings by store.settings.collectAsState(initial = DuelSettings(musicEnabled = true))
    val musicEnabled = settings.musicEnabled
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

    // One-shot wheel overlay. Holds which player opened it (for rotation);
    // null when closed.
    var oneShotWheelOpen by remember { mutableStateOf<PlayerSlot?>(null) }

    // Dice / coin overlays — neutral utilities, both faces designed
    // 180°-symmetric so seated players read them without rotation.
    var diceOverlayOpen by remember { mutableStateOf(false) }
    var coinOverlayOpen by remember { mutableStateOf(false) }

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
        sfx: GameSfx,
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
        lpSfx.playSfx(sfx)
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

    /** Event dispatch after the post-settle wait. No-op if duel is over or music off. */
    suspend fun maybeFireEvents(after: DuelState, candidates: List<DuelAutoEvent>) {
        if (after.outcome.isOver) return
        if (!musicEnabled) return
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
                    val sfx: GameSfx
                    when (entry.mode) {
                        LpActionMode.DamageOpponent -> {
                            target = opponent; delta = -entry.value; sfx = GameSfx.InflictDamage
                        }
                        LpActionMode.HealOpponent -> {
                            target = opponent; delta = entry.value; sfx = GameSfx.GainHeal
                        }
                        LpActionMode.HealSelf -> {
                            target = actor; delta = entry.value; sfx = GameSfx.GainHeal
                        }
                        LpActionMode.DamageSelf -> {
                            target = actor; delta = -entry.value; sfx = GameSfx.InflictDamage
                        }
                    }
                    val before = working
                    val advanced = runOneAction(target, delta, working, sfx = sfx)
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

    // Phase-banner trigger key — incremented on every phase change OR turn
    // change, so PhaseBanner re-animates regardless of whether the new phase
    // equals the previous one (e.g. DP → ... → EP → DP on a new turn).
    var phaseBannerKey by remember { mutableStateOf(0) }
    var bannerPhase by remember { mutableStateOf(state.currentPhase) }

    // Initial match-start sequence: reset scheduler, play Standard if music
    // is on, fire the first turn-start SFX, and show the first phase banner.
    LaunchedEffect(Unit) {
        eventScheduler.reset()
        if (musicEnabled) {
            eventScheduler.trigger(listOf(DuelAutoEvent.Standard))
        }
        lpSfx.playSfx(GameSfx.TurnStart)
        bannerPhase = state.currentPhase
        phaseBannerKey++
    }

    // React to the music toggle. On enable, replay Standard (fresh start);
    // on disable, silence both scheduler + engine. Skipped on first comp
    // because LaunchedEffect(Unit) already handled the initial state.
    var musicReactInit by remember { mutableStateOf(false) }
    LaunchedEffect(musicEnabled) {
        if (!musicReactInit) {
            musicReactInit = true
            return@LaunchedEffect
        }
        if (musicEnabled) {
            eventScheduler.trigger(listOf(DuelAutoEvent.Standard))
        } else {
            eventScheduler.stop()
            ost.stop()
        }
    }

    // Match-end: silence event scheduler, play winner/draw track + applause
    // SFX overlay, persist match, reveal action buttons after a dramatic pause.
    LaunchedEffect(state.outcome) {
        if (!state.outcome.isOver) return@LaunchedEffect
        eventScheduler.stop()
        if (musicEnabled) {
            val track = if (state.outcome is Outcome.Draw) OstTracks.MatchEndDraw else OstTracks.MatchEndWinner
            ost.play(track)
        }
        // Applause SFX plays regardless of music — it's an event SFX, not music.
        lpSfx.playSfx(GameSfx.Applause)
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
            currentPhase = DuelPhase.DrawPhase,
        )
        lpSfx.playSfx(GameSfx.TurnStart)
        bannerPhase = DuelPhase.DrawPhase
        phaseBannerKey++
    }

    /**
     * Advance one phase. Fires PhaseTransition SFX (or BattlePhase SFX when
     * entering Battle Phase). On End Phase, the button is disabled — the
     * player must use End Turn to commit the turn flip.
     */
    fun advancePhase() {
        if (actionsLocked || state.outcome.isOver) return
        val next = state.currentPhase.next() ?: return
        state = state.copy(currentPhase = next)
        val sfx = if (next == DuelPhase.BattlePhase) GameSfx.BattlePhase else GameSfx.PhaseTransition
        lpSfx.playSfx(sfx)
        bannerPhase = next
        phaseBannerKey++
    }

    /** Music toggle — flips the persisted boolean. */
    fun toggleMusic() {
        scope.launch { store.setMusicEnabled(!musicEnabled) }
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
                    opponentName = state.player1,
                    opponentLp = state.p1Lp,
                    opponentIsInfinite = p1Infinite,
                    showFirstBadge = state.firstPlayer == PlayerSlot.P2 && state.turnNumber <= 2,
                    rotated = true,
                    onStageAction = { action -> stageAction(PlayerSlot.P2, action) },
                    onTapForValue = { mode -> keypadMode = mode },
                    bufferedActions = p2Buffer,
                    bufferEnabled = bufferMode,
                    onBufferModeChange = { bufferMode = it },
                    onCommitBuffer = { commitBuffer(PlayerSlot.P2) },
                    onUndoBuffer = { undoBuffer(PlayerSlot.P2) },
                    onClearBuffer = { clearBuffer(PlayerSlot.P2) },
                    onWheelStateChange = { ws ->
                        wheelOverlay = ws?.let { WheelOverlay(PlayerSlot.P2, it.mode, it.value) }
                    },
                    onEndTurn = ::endTurn,
                    endTurnEnabled = canEndTurn(),
                    onOpenOneShotWheel = { oneShotWheelOpen = PlayerSlot.P2 },
                    currentPhase = state.currentPhase,
                    onAdvancePhase = ::advancePhase,
                    turnNumber = state.turnNumber,
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
                musicEnabled = musicEnabled,
                onMusicToggle = ::toggleMusic,
                onMisc = { showMiscDialog = true },
                onDice = { diceOverlayOpen = true },
                onCoin = { coinOverlayOpen = true },
                onQuit = { showQuitConfirm = true },
            )

            // Lower area: P1 full half when their turn, else P1 peek.
            if (state.currentTurn == PlayerSlot.P1) {
                PlayerHalf(
                    playerName = state.player1,
                    selfLp = state.p1Lp,
                    opponentName = state.player2,
                    opponentLp = state.p2Lp,
                    opponentIsInfinite = p2Infinite,
                    showFirstBadge = state.firstPlayer == PlayerSlot.P1 && state.turnNumber <= 2,
                    rotated = false,
                    onStageAction = { action -> stageAction(PlayerSlot.P1, action) },
                    onTapForValue = { mode -> keypadMode = mode },
                    bufferedActions = p1Buffer,
                    bufferEnabled = bufferMode,
                    onBufferModeChange = { bufferMode = it },
                    onCommitBuffer = { commitBuffer(PlayerSlot.P1) },
                    onUndoBuffer = { undoBuffer(PlayerSlot.P1) },
                    onClearBuffer = { clearBuffer(PlayerSlot.P1) },
                    onWheelStateChange = { ws ->
                        wheelOverlay = ws?.let { WheelOverlay(PlayerSlot.P1, it.mode, it.value) }
                    },
                    onEndTurn = ::endTurn,
                    endTurnEnabled = canEndTurn(),
                    onOpenOneShotWheel = { oneShotWheelOpen = PlayerSlot.P1 },
                    currentPhase = state.currentPhase,
                    onAdvancePhase = ::advancePhase,
                    turnNumber = state.turnNumber,
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
            val gd = DuelTheme.dimens
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = if (pressingIsP2) Alignment.BottomCenter else Alignment.TopCenter,
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = gd.s12)) {
                    LpActionWheel(
                        mode = overlay.mode,
                        value = overlay.value,
                        maxValue = wheelMax,
                        modifier = if (pressingIsP2) Modifier.rotate(180f) else Modifier,
                    )
                }
            }
        }

        oneShotWheelOpen?.let { slot ->
            val activeOverlay by ost.currentOverlay.collectAsState()
            val items = remember {
                listOf(
                    OneShotItem(
                        displayName = "Tribute",
                        track = OneShotTracks.Tribute,
                        accent = DuelColors.DuelGold,
                    ),
                    OneShotItem(
                        displayName = "Fusion",
                        track = OneShotTracks.Fusion,
                        accent = Color(0xFF8E6FD3),
                    ),
                    OneShotItem(
                        displayName = "Special",
                        track = OneShotTracks.Special,
                        accent = Color(0xFF5B8BE0),
                    ),
                    OneShotItem(
                        displayName = "Exodia",
                        track = OneShotTracks.Exodia,
                        accent = DuelColors.Crimson,
                    ),
                )
            }
            OneShotWheel(
                items = items,
                activeKey = activeOverlay?.cacheKey,
                onToggle = { item ->
                    if (activeOverlay?.cacheKey == item.track.cacheKey) ost.stopOverlay()
                    else ost.playOverlay(item.track)
                },
                onDismiss = { oneShotWheelOpen = null },
                rotated = slot == PlayerSlot.P2,
            )
        }

        if (diceOverlayOpen) {
            DiceRollOverlay(onDismiss = { diceOverlayOpen = false })
        }

        if (coinOverlayOpen) {
            CoinFlipOverlay(onDismiss = { coinOverlayOpen = false })
        }

        // Phase banner — rendered above the regular UI but below the overlays
        // so it doesn't fight the dice/coin/oneshot wheels. The banner gates
        // its own visibility via its internal scrim animatable.
        PhaseBanner(
            phase = bannerPhase,
            activeSlot = state.currentTurn,
            phaseChangeKey = phaseBannerKey,
        )

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

// Center bar between the two halves. The leading ♪ button is now a music
// ON/OFF toggle (no popover); ✦ opens the Misc dialog (infinite-LP toggles);
// the dice/coin buttons trigger their 3D overlays.
@Composable
private fun CenterBar(
    musicEnabled: Boolean,
    onMusicToggle: () -> Unit,
    onMisc: () -> Unit,
    onDice: () -> Unit,
    onCoin: () -> Unit,
    onQuit: () -> Unit,
) {
    val d = DuelTheme.dimens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(d.centerBarHeight)
            .background(Color(0xFF120A33))
            .border(d.borderHairline, DuelColors.DuelGold.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = d.s8, vertical = d.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.s6),
        ) {
            MusicToggleButton(enabled = musicEnabled, onClick = onMusicToggle)
            CenterIconButton(label = "✦", onClick = onMisc)
            CenterCanvasButton(onClick = onDice) { drawDiceMini() }
            CenterCanvasButton(onClick = onCoin) { drawCoinMini() }
            Box(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(Color(0xFF1A1140), RoundedCornerShape(d.radiusMd))
                    .border(d.borderHairline, DuelColors.Crimson.copy(alpha = 0.7f), RoundedCornerShape(d.radiusMd)),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(
                    onClick = onQuit,
                    contentPadding = PaddingValues(horizontal = d.s12, vertical = 0.dp),
                ) {
                    Text(
                        "Quit",
                        color = DuelColors.Crimson,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textBody,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterIconButton(label: String, onClick: () -> Unit) {
    val d = DuelTheme.dimens
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color(0xFF1A1140), RoundedCornerShape(d.radiusMd))
            .border(d.borderHairline, DuelColors.DuelGold.copy(alpha = 0.7f), RoundedCornerShape(d.radiusMd)),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = d.s12, vertical = 0.dp),
        ) {
            Text(
                label,
                color = DuelColors.DuelGoldGlow,
                fontWeight = FontWeight.Bold,
                fontSize = d.textIcon,
            )
        }
    }
}

/**
 * Music ON/OFF toggle button. ♪ when enabled (gold), ♪̸ (note with slash)
 * when muted — the strike-through is drawn explicitly so it renders on any
 * platform that's missing the U+266A combining variants.
 */
@Composable
private fun MusicToggleButton(enabled: Boolean, onClick: () -> Unit) {
    val d = DuelTheme.dimens
    val borderColor = if (enabled) DuelColors.DuelGold.copy(alpha = 0.7f)
    else DuelColors.DuelGold.copy(alpha = 0.30f)
    val labelColor = if (enabled) DuelColors.DuelGoldGlow else DuelColors.DuelGoldGlow.copy(alpha = 0.40f)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color(0xFF1A1140), RoundedCornerShape(d.radiusMd))
            .border(d.borderHairline, borderColor, RoundedCornerShape(d.radiusMd))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "♪",
                color = labelColor,
                fontWeight = FontWeight.Bold,
                fontSize = d.textIcon,
                modifier = Modifier.padding(horizontal = d.s12),
            )
            if (!enabled) {
                Canvas(modifier = Modifier.size(width = d.s32, height = d.s32 - d.s2)) {
                    drawLine(
                        color = DuelColors.Crimson,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.10f, size.height * 0.85f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.90f, size.height * 0.15f),
                        strokeWidth = 3f,
                    )
                }
            }
        }
    }
}

// Icon button that draws its glyph via Canvas — no font dependency, so the
// dice and coin glyphs render identically across platforms.
@Composable
private fun CenterCanvasButton(
    onClick: () -> Unit,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    val d = DuelTheme.dimens
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color(0xFF1A1140), RoundedCornerShape(d.radiusMd))
            .border(d.borderHairline, DuelColors.DuelGold.copy(alpha = 0.7f), RoundedCornerShape(d.radiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = d.s12),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(d.textIcon.value.dp), onDraw = draw)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDiceMini() {
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawRoundRect(
        color = DuelColors.DuelGoldGlow,
        topLeft = Offset(0f, 0f),
        size = size,
        cornerRadius = CornerRadius(r * 0.32f),
    )
    drawRoundRect(
        color = Color(0xFF6B4F1A),
        topLeft = Offset(0f, 0f),
        size = size,
        cornerRadius = CornerRadius(r * 0.32f),
        style = Stroke(width = r * 0.16f),
    )
    val pipR = r * 0.13f
    val inset = r * 0.45f
    drawCircle(color = Color(0xFF120A33), radius = pipR, center = Offset(cx - inset, cy - inset))
    drawCircle(color = Color(0xFF120A33), radius = pipR, center = Offset(cx + inset, cy - inset))
    drawCircle(color = Color(0xFF120A33), radius = pipR, center = Offset(cx, cy))
    drawCircle(color = Color(0xFF120A33), radius = pipR, center = Offset(cx - inset, cy + inset))
    drawCircle(color = Color(0xFF120A33), radius = pipR, center = Offset(cx + inset, cy + inset))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoinMini() {
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(DuelColors.DuelGoldGlow, DuelColors.DuelGold, Color(0xFFB8893A)),
            center = Offset(cx - r * 0.30f, cy - r * 0.30f),
            radius = r,
        ),
        radius = r,
        center = Offset(cx, cy),
    )
    drawCircle(
        color = Color(0xFF6B4F1A),
        radius = r * 0.96f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.14f),
    )
    drawCircle(color = Color(0xFF120A33), radius = r * 0.18f, center = Offset(cx, cy))
    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = r * 0.06f, center = Offset(cx - r * 0.05f, cy - r * 0.05f))
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
