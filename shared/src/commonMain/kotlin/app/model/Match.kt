package app.model

import kotlinx.serialization.Serializable

@Serializable
data class Match(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val player1: String,
    val player2: String,
    val startingLp: Int,
    val firstPlayer: PlayerSlot,
    val finalP1Lp: Int,
    val finalP2Lp: Int,
    val winner: PlayerSlot? = null,
    val events: List<MatchEvent> = emptyList(),
) {
    val durationMs: Long get() = (endedAt ?: startedAt) - startedAt
}

@Serializable
enum class PlayerSlot { P1, P2 }
