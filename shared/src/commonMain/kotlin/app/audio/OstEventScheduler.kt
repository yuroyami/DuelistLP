package app.audio

import app.model.MatchEvent
import app.model.PlayerSlot

/**
 * Decides which [DuelAutoEvent] track plays after each LP change.
 *
 * Rules:
 *   - Major preempts everything immediately. Once a Major starts, Minor
 *     preemption is locked for [MAJOR_LOCK_MS] (so the Major has time to
 *     play through at least once).
 *   - Minor preempts a previous Minor only after [MINOR_LOCK_MS] OR
 *     [MINOR_LOCK_ACTIONS] LP-change actions, whichever comes first.
 *   - Most events are one-timer per match (tracked in [firedOneTimers]).
 *
 * Receives candidate events from [DuelEventDetector]; doesn't read LP itself.
 * That keeps preemption + lifecycle here, detection rules cleanly testable.
 */
class OstEventScheduler(
    private val ost: OstController,
    private val nowMs: () -> Long,
) {
    private var current: DuelAutoEvent? = null
    private var currentStartedAtMs: Long = 0L
    private var actionsSinceCurrentStarted: Int = 0
    private val firedOneTimers: MutableSet<DuelAutoEvent> = mutableSetOf()

    /** Reset for a new match. Stops any playing event track. */
    fun reset() {
        current = null
        currentStartedAtMs = 0L
        actionsSinceCurrentStarted = 0
        firedOneTimers.clear()
        ost.stop()
    }

    /** Stop the active event track and clear the "currently playing" pointer. */
    fun stop() {
        current = null
        ost.stop()
    }

    /** Tally one LP-change action against the active track for the action-count rule. */
    fun onLpAction() {
        if (current != null) actionsSinceCurrentStarted++
    }

    /**
     * Try to fire one of the [candidates]. Sorted Major-first then declaration
     * order; plays the first one that hasn't already fired (or is recurrent)
     * AND is allowed to preempt the current track.
     */
    fun trigger(candidates: List<DuelAutoEvent>) {
        if (candidates.isEmpty()) return
        val ordered = candidates.sortedBy { if (it.tier == EventTier.Major) 0 else 1 }
        for (event in ordered) {
            if (!event.recurrent && event in firedOneTimers) continue
            if (!canPreempt(event)) continue
            play(event)
            return
        }
    }

    private fun canPreempt(incoming: DuelAutoEvent): Boolean {
        val cur = current ?: return true
        if (incoming.tier == EventTier.Major) return true
        return when (cur.tier) {
            EventTier.Major -> nowMs() - currentStartedAtMs >= MAJOR_LOCK_MS
            EventTier.Minor -> {
                val elapsed = nowMs() - currentStartedAtMs
                elapsed >= MINOR_LOCK_MS || actionsSinceCurrentStarted >= MINOR_LOCK_ACTIONS
            }
        }
    }

    private fun play(event: DuelAutoEvent) {
        firedOneTimers += event
        current = event
        currentStartedAtMs = nowMs()
        actionsSinceCurrentStarted = 0
        ost.play(event.track)
    }

    companion object {
        // Approximate length of one play-through of a Major track. Tuned so
        // back-to-back Majors during a normal duel feel intentional rather
        // than thrashy.
        const val MAJOR_LOCK_MS = 90_000L

        const val MINOR_LOCK_MS = 60_000L
        const val MINOR_LOCK_ACTIONS = 4
    }
}

/**
 * Pure detection of which [DuelAutoEvent]s a single LP transition fires.
 * Returns ALL satisfied conditions; [OstEventScheduler.trigger] picks at
 * most one to actually play.
 *
 * All thresholds scale with `startingLp` so the rules feel the same on
 * 4000 / 8000 / 16000-LP duels.
 */
object DuelEventDetector {

    fun detect(
        startingLp: Int,
        p1Before: Int,
        p2Before: Int,
        p1After: Int,
        p2After: Int,
        target: PlayerSlot,
        delta: Int,
        lpHistory: List<MatchEvent.LpChange>,
    ): List<DuelAutoEvent> {
        val fired = mutableListOf<DuelAutoEvent>()

        val pct75 = startingLp * 75 / 100
        val pct50 = startingLp / 2
        val pct25 = startingLp * 25 / 100
        val pct15 = startingLp * 15 / 100
        val tooMuchHealThreshold = startingLp / 4   // > 25%
        val lossStreakThreshold = startingLp / 10   // > 10%

        // Majors are added in DESCENDING severity so the most-dramatic event
        // wins same-tick precedence (trigger() stable-sorts within tier).
        //
        // HighStakesBelow25 > LostHalfHp > LessThan75.

        // HighStakesBelow25 — both currently below 25% (endgame).
        if (p1After < pct25 && p2After < pct25) fired += DuelAutoEvent.HighStakesBelow25

        // LostHalfHp — cross-down through 50% (mid-duel turning point).
        val p1CrossedDown50 = p1Before >= pct50 && p1After < pct50
        val p2CrossedDown50 = p2Before >= pct50 && p2After < pct50
        if (p1CrossedDown50 || p2CrossedDown50) fired += DuelAutoEvent.LostHalfHp

        // LessThan75 — cross-down through 75% (early warning).
        val p1CrossedDown75 = p1Before >= pct75 && p1After < pct75
        val p2CrossedDown75 = p2Before >= pct75 && p2After < pct75
        if (p1CrossedDown75 || p2CrossedDown75) fired += DuelAutoEvent.LessThan75

        // Minor events — order among themselves doesn't matter for severity,
        // but kept stable for determinism.

        // AlmostLost — either player at or below 15% of startingLp.
        if (p1After <= pct15 || p2After <= pct15) fired += DuelAutoEvent.AlmostLost

        // TooMuchHeal — single positive delta exceeds 25% of startingLp.
        if (delta > tooMuchHealThreshold) fired += DuelAutoEvent.TooMuchHeal

        // ConsecutiveHits — sliding window: this hit + the previous on same target.
        val recentForTarget = lpHistory.filter { it.target == target }.takeLast(2)
        if (recentForTarget.size == 2) {
            val totalLoss = recentForTarget.sumOf { (-it.delta).coerceAtLeast(0) }
            if (totalLoss > lossStreakThreshold) fired += DuelAutoEvent.ConsecutiveHits
        }

        return fired
    }
}
