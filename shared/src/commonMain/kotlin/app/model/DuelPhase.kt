package app.model

/**
 * Yu-Gi-Oh!-style turn-phase progression. A turn begins in [DrawPhase] and
 * advances through the six phases in order; only the active player can advance.
 * From [EndPhase] there is no [next] — the player must use the End Turn slider
 * to commit the turn change (which resets the next turn to [DrawPhase]).
 *
 * The tracker is purely visual / audio cueing — no game-rule enforcement is
 * derived from this; the LP-action region remains enabled across all phases.
 */
enum class DuelPhase {
    DrawPhase,
    StandbyPhase,
    MainPhase1,
    BattlePhase,
    MainPhase2,
    EndPhase;

    /** The next phase forward, or null when on [EndPhase]. */
    fun next(): DuelPhase? = when (this) {
        DrawPhase -> StandbyPhase
        StandbyPhase -> MainPhase1
        MainPhase1 -> BattlePhase
        BattlePhase -> MainPhase2
        MainPhase2 -> EndPhase
        EndPhase -> null
    }

    /** Full readable label shown in the phase banner. */
    val displayName: String
        get() = when (this) {
            DrawPhase -> "DRAW PHASE"
            StandbyPhase -> "STANDBY PHASE"
            MainPhase1 -> "MAIN PHASE 1"
            BattlePhase -> "BATTLE PHASE"
            MainPhase2 -> "MAIN PHASE 2"
            EndPhase -> "END PHASE"
        }

    /** Short code shown in the always-visible phase tracker bar. */
    val shortCode: String
        get() = when (this) {
            DrawPhase -> "DP"
            StandbyPhase -> "SP"
            MainPhase1 -> "MP1"
            BattlePhase -> "BP"
            MainPhase2 -> "MP2"
            EndPhase -> "EP"
        }
}
