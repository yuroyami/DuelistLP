package app.model

/**
 * Terminal state of a duel for in-memory branching. Persistence uses the flat
 * `winner + isDraw` form on [Match] for serialization stability — see
 * [Match.outcome] for the round-trip back to this sealed type.
 */
sealed interface Outcome {
    val isOver: Boolean

    data object InProgress : Outcome {
        override val isOver: Boolean get() = false
    }

    data class Win(val winner: PlayerSlot) : Outcome {
        override val isOver: Boolean get() = true
    }

    data object Draw : Outcome {
        override val isOver: Boolean get() = true
    }
}
