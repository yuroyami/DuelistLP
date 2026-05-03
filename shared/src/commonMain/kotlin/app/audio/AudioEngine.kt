package app.audio

/**
 * Cross-platform audio primitive. Each instance owns one native player; the
 * higher-level controllers ([OstController], [LpSfxController]) stack multiple
 * instances to overlay loops and one-shots.
 *
 * Files come in as raw bytes (read from compose-resources). The actual writes
 * those bytes once to a per-process scratch file in the cache dir and loads
 * the file URI — repeated [load] calls with the same [cacheKey] are no-ops.
 *
 * Actuals:
 *   - Android: Media3 ExoPlayer (audio-only)
 *   - iOS:     AVFoundation AVPlayer with manual loop via end-of-item observer
 *
 * Must be [release]d when no longer needed (holds native resources).
 */
expect class AudioEngine() {

    /** Load media from raw bytes. Idempotent for the same [cacheKey]. */
    suspend fun load(bytes: ByteArray, cacheKey: String)

    /** Begin or resume playback. No-op if no media is loaded. */
    fun play()

    /** Pause at the current position. */
    fun pause()

    /** Stop and rewind to position 0. */
    fun stop()

    /** Whole-file looping. Combines with [setOnComplete] (which only fires when looping is OFF). */
    fun setLooping(loop: Boolean)

    /** Linear volume in [0f, 1f]. */
    fun setVolume(volume: Float)

    /** End-of-media callback. Pass null to clear. Only fires when looping is OFF. */
    fun setOnComplete(callback: (() -> Unit)?)

    fun seekTo(positionMs: Long)

    /** Current playhead in ms; 0 when stopped. */
    val positionMs: Long

    /** Loaded media duration in ms; 0 if no media is loaded yet. */
    val durationMs: Long

    /** Free native resources. Instance is unusable after this. */
    fun release()
}
