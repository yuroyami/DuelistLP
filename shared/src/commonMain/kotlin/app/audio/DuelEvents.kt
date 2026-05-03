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
 * All events are one-timer per match unless [recurrent] is set true.
 */
enum class DuelAutoEvent(
    val track: OstTrack,
    val tier: EventTier,
    val recurrent: Boolean = false,
) {
    /** #1 Match start — fires immediately, before any LP change. */
    MatchStart(
        track = OstTrack("event-match-start", "files/sounds/ingame-joey-normal-duel.mp3", looping = true),
        tier = EventTier.Major,
    ),

    /** #2 Same player lost > 10% of starting LP across their last 2 hits. */
    LossStreak(
        track = OstTrack("event-loss-streak", "files/sounds/ingame-formem-normal-duel.mp3", looping = true),
        tier = EventTier.Minor,
    ),

    /** #3 A player crosses below 75% of starting LP for the first time. */
    LpBelow75(
        track = OstTrack("event-lp-below-75", "files/sounds/ingame-masterduel-normal-duel.mp3", looping = true),
        tier = EventTier.Major,
    ),

    /** #4 A player goes above 110% of starting LP. */
    LpAbove110(
        track = OstTrack("event-lp-above-110", "files/sounds/ingame-legacyduelist-normal-duel.mp3", looping = true),
        tier = EventTier.Minor,
    ),

    /** #5 Both players sitting below 25% of starting LP. */
    BothBelow25(
        track = OstTrack("event-both-below-25", "files/sounds/ingame-masterduel-tournament-duel.mp3", looping = true),
        tier = EventTier.Major,
    ),

    /** #6 A player drops below half of the opponent's CURRENT LP. */
    HalfOfOpponent(
        track = OstTrack("event-half-of-opponent", "files/sounds/ingame-legacyduelist-tournament-duel.mp3", looping = true),
        tier = EventTier.Major,
    ),

    /** #7 Single hit ≥ 2000 damage inflicted on a player. */
    BigHit2000(
        track = OstTrack("event-big-hit-2000", "files/sounds/ingame-joey-losing-theme.mp3", looping = true),
        tier = EventTier.Minor,
    ),

    /** #8 Single heal ≥ 1000 LP gained by a player. */
    BigHeal1000(
        track = OstTrack("event-big-heal-1000", "files/sounds/ingame-masterduel-winning-theme.mp3", looping = true),
        tier = EventTier.Minor,
    ),
}
