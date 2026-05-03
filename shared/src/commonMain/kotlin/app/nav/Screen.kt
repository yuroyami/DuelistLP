package app.nav

import app.model.Match

/**
 * Navigation target. `App.kt` holds a single `mutableStateOf<Screen>` and
 * switches the rendered composable on it — there is no nav library. Each
 * branch carries the data it needs to construct its screen, so back-navigation
 * is just `screen = Screen.Setup` with no stack restoration required.
 */
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
