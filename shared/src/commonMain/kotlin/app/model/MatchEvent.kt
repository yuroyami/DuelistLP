package app.model

import kotlinx.serialization.Serializable

/**
 * One entry in a match's event log. Persisted as part of [Match] so the full
 * timeline (every LP delta, the RPS rounds, the terminal outcome) replays
 * from history.
 *
 * `LpChange` always stores both `before` and `after` so the log is self-
 * contained — replays don't need to re-derive state. `target` is the player
 * whose LP moved, regardless of who triggered the change.
 */
@Serializable
sealed interface MatchEvent {
    val timestamp: Long

    @Serializable
    data class LpChange(
        override val timestamp: Long,
        val target: PlayerSlot,
        val delta: Int,
        val before: Int,
        val after: Int,
    ) : MatchEvent

    @Serializable
    data class FirstPlayerDecided(
        override val timestamp: Long,
        val firstPlayer: PlayerSlot,
        val p1Pick: RpsPick,
        val p2Pick: RpsPick,
        val rounds: Int,
    ) : MatchEvent

    @Serializable
    data class Victory(
        override val timestamp: Long,
        val winner: PlayerSlot,
    ) : MatchEvent

    @Serializable
    data class Draw(
        override val timestamp: Long,
    ) : MatchEvent
}

@Serializable
enum class RpsPick { ROCK, PAPER, SCISSORS }

enum class RpsOutcome { P1, P2, TIE }

/** Pure RPS truth table. TIE means the caller must re-pick. */
fun resolveRps(p1: RpsPick, p2: RpsPick): RpsOutcome = when {
    p1 == p2 -> RpsOutcome.TIE
    p1 == RpsPick.ROCK && p2 == RpsPick.SCISSORS -> RpsOutcome.P1
    p1 == RpsPick.PAPER && p2 == RpsPick.ROCK -> RpsOutcome.P1
    p1 == RpsPick.SCISSORS && p2 == RpsPick.PAPER -> RpsOutcome.P1
    else -> RpsOutcome.P2
}
