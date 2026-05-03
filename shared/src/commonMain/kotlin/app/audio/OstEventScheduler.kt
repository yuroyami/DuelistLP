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
        val pct110 = startingLp * 110 / 100
        val pct25 = startingLp * 25 / 100

        // #3 — transition only (cross-down through threshold).
        val p1CrossedDown75 = p1Before >= pct75 && p1After < pct75
        val p2CrossedDown75 = p2Before >= pct75 && p2After < pct75
        if (p1CrossedDown75 || p2CrossedDown75) fired += DuelAutoEvent.LpBelow75

        // #4 — transition only (cross-up through threshold).
        val p1CrossedUp110 = p1Before <= pct110 && p1After > pct110
        val p2CrossedUp110 = p2Before <= pct110 && p2After > pct110
        if (p1CrossedUp110 || p2CrossedUp110) fired += DuelAutoEvent.LpAbove110

        // #5 — concurrent state (both currently below).
        if (p1After < pct25 && p2After < pct25) fired += DuelAutoEvent.BothBelow25

        // #6 — relative state. Avoid div-by-zero by checking opponent > 0.
        if (p2After > 0 && p1After * 2 < p2After) fired += DuelAutoEvent.HalfOfOpponent
        else if (p1After > 0 && p2After * 2 < p1After) fired += DuelAutoEvent.HalfOfOpponent

        // #7 / #8 — single-action magnitude.
        if (delta <= -BIG_HIT_THRESHOLD) fired += DuelAutoEvent.BigHit2000
        if (delta >= BIG_HEAL_THRESHOLD) fired += DuelAutoEvent.BigHeal1000

        // #2 — sliding-window: this action + the previous one against the same target.
        val lossStreakThreshold = startingLp / 10
        val recentForTarget = lpHistory.filter { it.target == target }.takeLast(2)
        if (recentForTarget.size == 2) {
            val totalLoss = recentForTarget.sumOf { (-it.delta).coerceAtLeast(0) }
            if (totalLoss > lossStreakThreshold) fired += DuelAutoEvent.LossStreak
        }

        return fired
    }

    private const val BIG_HIT_THRESHOLD = 2000
    private const val BIG_HEAL_THRESHOLD = 1000
}
