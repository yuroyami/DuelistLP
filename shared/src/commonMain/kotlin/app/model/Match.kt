package app.model

import kotlinx.serialization.Serializable

/**
 * Persisted form of a finished (or in-progress) duel. Stored as a JSON string
 * inside a `List<Match>` under the `matches_json` DataStore key.
 *
 * Outcome is split into `winner: PlayerSlot?` + `isDraw: Boolean` rather than
 * a sealed type so old serialized payloads stay compatible after model edits.
 * [outcome] reconstructs the sealed [Outcome] from those fields.
 */
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
    val isDraw: Boolean = false,
    val events: List<MatchEvent> = emptyList(),
) {
    val durationMs: Long get() = (endedAt ?: startedAt) - startedAt

    val outcome: Outcome get() = when {
        isDraw -> Outcome.Draw
        winner != null -> Outcome.Win(winner)
        else -> Outcome.InProgress
    }
}

@Serializable
enum class PlayerSlot { P1, P2 }
