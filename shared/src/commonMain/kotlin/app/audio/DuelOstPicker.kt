package app.audio

import app.model.MatchEvent

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
 *   - last event was a positive LP change that crossed UP through threshold
 *     (was ≤ threshold before, > threshold after) → Winning
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
        val crossedUp = lastEvent is MatchEvent.LpChange &&
            lastEvent.delta > 0 &&
            lastEvent.before <= threshold &&
            lastEvent.after > threshold

        return when {
            crossedUp && !bothBelow -> OstController.Track.Winning
            bothBelow -> OstController.Track.Tournament
            anyBelow -> OstController.Track.Losing
            else -> OstController.Track.Duel
        }
    }
}
