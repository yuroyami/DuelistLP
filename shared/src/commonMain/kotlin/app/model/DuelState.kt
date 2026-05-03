package app.model

/**
 * In-memory duel snapshot held by `DuelScreen` for the lifetime of one match.
 * Mutated only via `state = state.copy(...)` so each LP change is a new value.
 *
 * Persisted as [Match] when the duel resolves; the two types are kept separate
 * because [Match] uses a flat `winner + isDraw` representation for forward-
 * compatible JSON, while [DuelState] uses [Outcome] for exhaustive branching.
 */
data class DuelState(
    val matchId: String,
    val startedAt: Long,
    val player1: String,
    val player2: String,
    val startingLp: Int,
    val firstPlayer: PlayerSlot,
    val p1Lp: Int,
    val p2Lp: Int,
    val events: List<MatchEvent> = emptyList(),
    val outcome: Outcome = Outcome.InProgress,
    /** Whose turn it is. Starts as [firstPlayer]; flipped on End Turn. */
    val currentTurn: PlayerSlot = firstPlayer,
    /** Increments on each turn flip. Drives the "Turn N" label only. */
    val turnNumber: Int = 1,
) {
    fun lpOf(slot: PlayerSlot): Int = when (slot) {
        PlayerSlot.P1 -> p1Lp
        PlayerSlot.P2 -> p2Lp
    }

    fun nameOf(slot: PlayerSlot): String = when (slot) {
        PlayerSlot.P1 -> player1
        PlayerSlot.P2 -> player2
    }

    val isOver: Boolean get() = outcome.isOver
    val winner: PlayerSlot? get() = (outcome as? Outcome.Win)?.winner
    val isDraw: Boolean get() = outcome is Outcome.Draw
}
