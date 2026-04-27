package app.audio

/**
 * Minimal cross-platform audio engine for short SFX and looping background music.
 *
 *   - Android: Media3 ExoPlayer (audio-only build)
 *   - iOS:     AVFoundation AVPlayer
 *
 * The engine takes the file as a byte array (extracted from compose-resources).
 * Each platform writes those bytes once to a per-process scratch file in the
 * cache directory and loads it from there.
 *
 * Created instances must be released with [release] when they are no longer
 * needed; they hold native player resources.
 */
expect class AudioEngine() {

    /** Load a file from raw bytes. Safe to call repeatedly with the same [cacheKey]. */
    suspend fun load(bytes: ByteArray, cacheKey: String)

    /** Begin or resume playback. No-op if no media is loaded. */
    fun play()

    /** Pause playback at the current position. */
    fun pause()

    /** Stop and rewind to the beginning. */
    fun stop()

    /** Whether to repeat the whole file once playback reaches the end. */
    fun setLooping(loop: Boolean)

    /** Linear volume in [0f, 1f]. */
    fun setVolume(volume: Float)

    /** Seek to [positionMs] from the start. */
    fun seekTo(positionMs: Long)

    /** Current playhead in ms; 0 when stopped. */
    val positionMs: Long

    /** Loaded media duration in ms; 0 if no media is loaded yet. */
    val durationMs: Long

    /** Free native resources. The instance is unusable after this. */
    fun release()
}
