package app.audio

import androidx.compose.ui.graphics.Color

/**
 * In-duel music pack. Each pack ships a `dmp-<key>-<kind>.mp3` file for most
 * [DmpTrackKind]s. When a pack is missing a kind, the resolver in [OstTracks]
 * falls back to [Joey] (the reference pack — guaranteed complete).
 */
enum class MusicPack(
    val key: String,
    val displayName: String,
    val color: Color,
) {
    Joey("joey", "Joey", Color(0xFFE8B852)),
    Yugi("yugi", "Yugi", Color(0xFF7A1A1A)),
    Kaiba("kaiba", "Kaiba", Color(0xFF1F2E80)),
    ForbiddenMemories("forbiddenmemories", "Forbidden Memories", Color(0xFF1B5E66)),
    LegacyDuelist("legacyduelist", "Legacy Duelist", Color(0xFF555E6B)),
    MasterDuel("masterduel", "Master Duel", Color(0xFF6B1FB0)),
}

/**
 * Per-pack track variants. In Auto mode the duel screen drives music through
 * [OstEventScheduler] using [DuelAutoEvent] tracks instead — these per-pack
 * DMP tracks are the Manual-mode menu and the source for the Tribute Summon
 * one-shot stinger.
 */
enum class DmpTrackKind(
    val fileSuffix: String,
    val displayName: String,
    val isOneShot: Boolean = false,
) {
    NormalDuel("normal-duel", "Normal duel"),
    TournamentDuel("tournament-duel", "Tournament"),
    LosingTheme("losing-theme", "Losing"),
    WinningTheme("winning-theme", "Winning"),
    TributeSummon("tribute-summon", "Tribute summon", isOneShot = true),
}

/** How DuelScreen drives the OST. */
enum class MusicMode(val displayName: String) {
    Auto("Auto"),       // OstEventScheduler picks tracks based on LP state.
    Manual("Manual"),   // User-pinned (pack, kind) plays on loop.
    Disabled("Off"),    // Silent.
}

/** User-controllable selection from the Music popover. */
data class MusicSettings(
    val mode: MusicMode = MusicMode.Auto,
    val pack: MusicPack = MusicPack.Joey,
    val manualTrack: DmpTrackKind = DmpTrackKind.NormalDuel,
)

/** Resolved track — opaque (cacheKey, resourcePath) pair handed to the engine. */
data class OstTrack(
    val cacheKey: String,
    val resourcePath: String,
    val looping: Boolean,
)

/** App-default and miscellaneous tracks (always available, pack-agnostic). */
object OstTracks {
    val Main = OstTrack("ost-app-main", "files/sounds/lp-main-theme.mp3", looping = true)
    val Rps = OstTrack("ost-app-rps", "files/sounds/lp-rockpaperscissors.mp3", looping = true)
    val MatchEndWin = OstTrack("ost-app-match-end-win", "files/sounds/ingame-misc-matchend-win.mp3", looping = true)
    val MatchEndDraw = OstTrack("ost-app-match-end-draw", "files/sounds/ingame-misc-matchend-draw.mp3", looping = true)
    val Exodia = OstTrack("ost-misc-exodia", "files/ost/misc-exodia.mp3", looping = false)

    /**
     * Resolve the file for a (pack, kind) pair, falling back to [MusicPack.Joey]
     * when [pack] doesn't ship that kind.
     */
    fun dmp(pack: MusicPack, kind: DmpTrackKind): OstTrack {
        val effective = if (DmpAvailability.has(pack, kind)) pack else MusicPack.Joey
        return OstTrack(
            cacheKey = "ost-dmp-${effective.key}-${kind.fileSuffix}",
            resourcePath = "files/ost/dmp-${effective.key}-${kind.fileSuffix}.mp3",
            looping = !kind.isOneShot,
        )
    }
}

object DmpAvailability {
    fun has(pack: MusicPack, kind: DmpTrackKind): Boolean = when (pack) {
        MusicPack.ForbiddenMemories,
        MusicPack.LegacyDuelist -> kind != DmpTrackKind.TributeSummon
        else -> true
    }
}
