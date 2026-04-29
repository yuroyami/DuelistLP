package app.audio

import androidx.compose.ui.graphics.Color

/**
 * In-duel music pack. Each pack has its own set of `dmp-<key>-<kind>.mp3`
 * files; if a pack is missing a particular [DmpTrackKind] entry, the resolver
 * falls back to [Joey] (the reference pack — every kind is guaranteed there).
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
 * Kind of in-duel music. The first four are looping background tracks picked
 * automatically by [DuelOstPicker]; [TributeSummon] is a one-shot stinger
 * triggered manually from the music button.
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

/** How the duel screen drives the OST. */
enum class MusicMode(val displayName: String) {
    Auto("Auto"),
    Manual("Manual"),
    Disabled("Off"),
}

/** User-controllable music selection for the duel screen. */
data class MusicSettings(
    val mode: MusicMode = MusicMode.Auto,
    val pack: MusicPack = MusicPack.Joey,
    val manualTrack: DmpTrackKind = DmpTrackKind.NormalDuel,
)

/**
 * A resolved audio track — an opaque pair of (cache key, resource path) that
 * [OstController] can hand straight to the engine.
 */
data class OstTrack(
    val cacheKey: String,
    val resourcePath: String,
    val looping: Boolean,
)

/** App-default and miscellaneous tracks (always available, pack-agnostic). */
object OstTracks {
    val Main = OstTrack("ost-app-main", "files/ost/lp-main-theme.mp3", looping = true)
    val Rps = OstTrack("ost-app-rps", "files/ost/lp-rockpaperscissors.mp3", looping = true)
    val MatchEnd = OstTrack("ost-app-match-end", "files/ost/lp-match-end.mp3", looping = true)
    val Exodia = OstTrack("ost-misc-exodia", "files/ost/misc-exodia.mp3", looping = false)

    /**
     * Resolve the file for a (pack, kind) pair. Falls back to [MusicPack.Joey]
     * when the requested pack does not ship that particular kind (currently
     * only [DmpTrackKind.TributeSummon] is partial).
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
