package app.audio

/**
 * Importance tier for an automatic event. [OstEventScheduler] uses this to
 * decide whether an incoming event is allowed to preempt the active track.
 *
 *   - [Major] preempts everything immediately. Once playing, it locks Minor
 *     preemption for the configured Major-lock window so it has a chance to
 *     play through at least once.
 *   - [Minor] yields to any incoming Major. To another Minor it yields only
 *     after a time OR action-count threshold, whichever comes first.
 */
enum class EventTier { Major, Minor }

/**
 * Auto-mode OST events. [DuelEventDetector] is pure — given an LP transition,
 * it returns every event whose trigger condition fires. [OstEventScheduler]
 * then decides which actually plays based on tier + already-fired tracking.
 *
 * All events are one-timer per match unless [recurrent] is set true. Declaration
 * order matters: when two Major (or two Minor) events fire on the SAME LP
 * action, [OstEventScheduler.trigger] picks the first one in this enum.
 * Deeper-escalation events are listed last so they preempt the milder ones on
 * subsequent ticks (e.g. LostHalfHp can preempt LessThan75 once already-fired
 * one-timers free the slot).
 */
enum class DuelAutoEvent(
    val track: OstTrack,
    val tier: EventTier,
    val recurrent: Boolean = false,
) {
    /** (Major) Default duel theme. Fires immediately at match start. */
    Standard(
        track = OstTrack(
            cacheKey = "ost-ingame-standard",
            resourcePath = "files/ost/soundtrack_ingame_standard.mp3",
            looping = true,
        ),
        tier = EventTier.Major,
    ),

    /** (Minor) Same player's last 2 hits combined > 10% of starting LP. */
    ConsecutiveHits(
        track = OstTrack(
            cacheKey = "ost-ingame-consecutivehits",
            resourcePath = "files/ost/soundtrack_ingame_consecutivehits.mp3",
            looping = true,
        ),
        tier = EventTier.Minor,
    ),

    /** (Minor) Single heal action > 25% of starting LP. */
    TooMuchHeal(
        track = OstTrack(
            cacheKey = "ost-ingame-toomuchheal",
            resourcePath = "files/ost/soundtrack_ingame_toomuchheal.mp3",
            looping = true,
        ),
        tier = EventTier.Minor,
    ),

    /** (Minor) Either player at or below 15% of starting LP (state check). */
    AlmostLost(
        track = OstTrack(
            cacheKey = "ost-ingame-almostlost",
            resourcePath = "files/ost/soundtrack_ingame_almostlost.mp3",
            looping = true,
        ),
        tier = EventTier.Minor,
    ),

    /** (Major) A player crosses below 75% of starting LP for the first time. */
    LessThan75(
        track = OstTrack(
            cacheKey = "ost-ingame-lessthan75",
            resourcePath = "files/ost/soundtrack_ingame_lessthan75.mp3",
            looping = true,
        ),
        tier = EventTier.Major,
    ),

    /** (Major) Both players currently below 25% of starting LP. */
    HighStakesBelow25(
        track = OstTrack(
            cacheKey = "ost-ingame-highstakesbelow25",
            resourcePath = "files/ost/soundtrack_ingame_highstakesbelow25.mp3",
            looping = true,
        ),
        tier = EventTier.Major,
    ),

    /** (Major) A player crosses below 50% of starting LP for the first time. */
    LostHalfHp(
        track = OstTrack(
            cacheKey = "ost-ingame-losthalfhp",
            resourcePath = "files/ost/soundtrack_ingame_losthalfhp.mp3",
            looping = true,
        ),
        tier = EventTier.Major,
    ),
}
