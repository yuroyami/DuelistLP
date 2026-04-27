package app.model

import kotlinx.serialization.Serializable

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
}

@Serializable
enum class RpsPick { ROCK, PAPER, SCISSORS }

enum class RpsOutcome { P1, P2, TIE }

fun resolveRps(p1: RpsPick, p2: RpsPick): RpsOutcome = when {
    p1 == p2 -> RpsOutcome.TIE
    p1 == RpsPick.ROCK && p2 == RpsPick.SCISSORS -> RpsOutcome.P1
    p1 == RpsPick.PAPER && p2 == RpsPick.ROCK -> RpsOutcome.P1
    p1 == RpsPick.SCISSORS && p2 == RpsPick.PAPER -> RpsOutcome.P1
    else -> RpsOutcome.P2
}
