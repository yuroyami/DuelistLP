package app.model

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
    val winner: PlayerSlot? = null,
) {
    fun lpOf(slot: PlayerSlot): Int = when (slot) {
        PlayerSlot.P1 -> p1Lp
        PlayerSlot.P2 -> p2Lp
    }

    fun nameOf(slot: PlayerSlot): String = when (slot) {
        PlayerSlot.P1 -> player1
        PlayerSlot.P2 -> player2
    }

    val isOver: Boolean get() = winner != null
}
