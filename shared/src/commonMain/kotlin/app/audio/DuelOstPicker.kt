package app.audio

import app.model.MatchEvent
import app.model.PlayerSlot

/**
 * Picks which [DmpTrackKind] should play during a duel given the current LP
 * and the most recent event. The returned kind is then resolved against the
 * user's selected [MusicPack] via [OstTracks.dmp].
 *
 * The "tense" threshold is **half the starting LP** — so for the standard
 * 8000 it kicks in at 4000, for 16000 at 8000, for 4000 at 2000, etc.
 *
 * Rules:
 *   - both LP < threshold → TournamentDuel
 *   - one LP < threshold (other ≥ threshold) → LosingTheme
 *   - the last event was a tournament-exiting comeback — both players were
 *     below threshold and one healed up across it → WinningTheme
 *   - otherwise → NormalDuel
 */
object DuelOstPicker {

    fun pick(
        p1Lp: Int,
        p2Lp: Int,
        startingLp: Int,
        lastEvent: MatchEvent?,
    ): DmpTrackKind {
        val threshold = startingLp / 2
        val bothBelow = p1Lp < threshold && p2Lp < threshold
        val anyBelow = p1Lp < threshold || p2Lp < threshold

        val crossedUpFromTournament = lastEvent is MatchEvent.LpChange &&
            lastEvent.delta > 0 &&
            lastEvent.before < threshold &&
            lastEvent.after >= threshold &&
            (if (lastEvent.target == PlayerSlot.P1) p2Lp else p1Lp) < threshold

        return when {
            crossedUpFromTournament -> DmpTrackKind.WinningTheme
            bothBelow -> DmpTrackKind.TournamentDuel
            anyBelow -> DmpTrackKind.LosingTheme
            else -> DmpTrackKind.NormalDuel
        }
    }
}
