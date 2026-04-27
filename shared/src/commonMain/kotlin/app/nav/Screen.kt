package app.nav

import app.model.Match

sealed interface Screen {
    data object Setup : Screen

    data class Rps(
        val player1: String,
        val player2: String,
        val startingLp: Int,
    ) : Screen

    data class Duel(
        val player1: String,
        val player2: String,
        val startingLp: Int,
        val firstPlayer: app.model.PlayerSlot,
        val rpsRounds: Int,
        val rpsP1Pick: app.model.RpsPick,
        val rpsP2Pick: app.model.RpsPick,
    ) : Screen

    data object History : Screen

    data class MatchDetail(val match: Match) : Screen
}
