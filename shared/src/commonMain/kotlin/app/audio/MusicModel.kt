package app.audio

/**
 * Resolved track — opaque (cacheKey, resourcePath) pair handed to the engine.
 * Music tracks loop; one-shots overlay (loaded by [OstController.playOverlay]).
 */
data class OstTrack(
    val cacheKey: String,
    val resourcePath: String,
    val looping: Boolean,
)

/** App-wide background music. All live under `files/ost/`. */
object OstTracks {
    val Main = OstTrack(
        cacheKey = "ost-mainscreen",
        resourcePath = "files/ost/soundtrack_mainscreen.mp3",
        looping = true,
    )
    val Rps = OstTrack(
        cacheKey = "ost-rps",
        resourcePath = "files/ost/soundtrack_rockpaperscissors.mp3",
        looping = true,
    )
    val MatchEndWinner = OstTrack(
        cacheKey = "ost-matchend-winner",
        resourcePath = "files/ost/soundtrack_matchend_winner.mp3",
        looping = true,
    )
    val MatchEndDraw = OstTrack(
        cacheKey = "ost-matchend-draw",
        resourcePath = "files/ost/soundtrack_matchend_draw.mp3",
        looping = true,
    )
}

/**
 * One-shot stinger tracks. Loaded as OST overlays — the music ducks under
 * them while they play. Files live under `files/sfx/` but conceptually they're
 * mid-volume "events" rather than quick SFX, hence the overlay engine.
 */
object OneShotTracks {
    val Tribute = OstTrack(
        cacheKey = "oneshot-tribute",
        resourcePath = "files/sfx/ingame_sfx_tribute.wav",
        looping = false,
    )
    val Fusion = OstTrack(
        cacheKey = "oneshot-fusion",
        resourcePath = "files/sfx/ingame_sfx_fusion_summon.wav",
        looping = false,
    )
    val Special = OstTrack(
        cacheKey = "oneshot-special",
        resourcePath = "files/sfx/ingame_sfx_special_summon.wav",
        looping = false,
    )
    val Exodia = OstTrack(
        cacheKey = "oneshot-exodia",
        resourcePath = "files/sfx/ingame_sfx_exodia.mp3",
        looping = false,
    )
}
