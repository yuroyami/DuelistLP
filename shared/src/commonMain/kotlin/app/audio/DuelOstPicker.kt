package app.audio

import app.model.MatchEvent
import app.model.PlayerSlot

/**
 * Picks which [OstController.Track] should play during a duel given the
 * current LP and the most recent event.
 *
 * The "tense" threshold is **half the starting LP** — so for the standard
 * 8000 it kicks in at 4000, for 16000 at 8000, for 4000 at 2000, etc.
 *
 * Rules:
 *   - both LP < threshold → Tournament
 *   - one LP < threshold (other ≥ threshold) → Losing
 *   - the last event was a tournament-exiting comeback — both players were
 *     below threshold and one healed up across it → Winning
 *   - otherwise → Duel
 */
object DuelOstPicker {

    fun pick(
        p1Lp: Int,
        p2Lp: Int,
        startingLp: Int,
        lastEvent: MatchEvent?,
    ): OstController.Track {
        val threshold = startingLp / 2
        val bothBelow = p1Lp < threshold && p2Lp < threshold
        val anyBelow = p1Lp < threshold || p2Lp < threshold

        // Comeback only counts when the OTHER player is still below threshold,
        // i.e. we were in Tournament before this event and just broke out of it.
        // The opponent's LP didn't change in this event, so their current value
        // is also their pre-event value.
        val crossedUpFromTournament = lastEvent is MatchEvent.LpChange &&
            lastEvent.delta > 0 &&
            lastEvent.before < threshold &&
            lastEvent.after >= threshold &&
            (if (lastEvent.target == PlayerSlot.P1) p2Lp else p1Lp) < threshold

        return when {
            crossedUpFromTournament -> OstController.Track.Winning
            bothBelow -> OstController.Track.Tournament
            anyBelow -> OstController.Track.Losing
            else -> OstController.Track.Duel
        }
    }
}
