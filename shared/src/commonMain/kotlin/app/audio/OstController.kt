package app.audio

import app.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the background-music [AudioEngine] (looping) and an [overlayEngine]
 * for one-shot stingers (Tribute Summon, Exodia) that play on top of the
 * looping track without disturbing it.
 *
 * Track changes go through a [Mutex] so a fast-fired sequence of
 * `play(A); play(B); play(C)` resolves cleanly to "we are now playing C"
 * without earlier loads racing the engine into an inconsistent state.
 */
class OstController(private val scope: CoroutineScope) {

    private val engine = AudioEngine().also {
        it.setLooping(true)
        it.setVolume(DEFAULT_VOLUME)
    }
    private val overlayEngine = AudioEngine().also {
        it.setLooping(false)
        it.setVolume(DEFAULT_OVERLAY_VOLUME)
    }
    private val mutex = Mutex()
    private var current: OstTrack? = null
    private var loadJob: Job? = null

    /** True between [pauseForBackground] and [resumeFromBackground]. */
    private var pausedForLifecycle = false

    fun play(track: OstTrack) {
        if (current?.cacheKey == track.cacheKey && !pausedForLifecycle) return
        loadJob?.cancel()
        loadJob = scope.launch {
            mutex.withLock {
                if (current?.cacheKey == track.cacheKey && !pausedForLifecycle) return@withLock
                val previous = current
                try {
                    val bytes = Res.readBytes(track.resourcePath)
                    engine.stop()
                    engine.load(bytes, track.cacheKey)
                    engine.setLooping(track.looping)
                    engine.setVolume(DEFAULT_VOLUME)
                    if (!pausedForLifecycle) engine.play()
                    current = track
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    current = previous
                    println("OstController.play(${track.cacheKey}) FAILED: ${e::class.simpleName}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /** Play a one-shot stinger over the looping track; non-looping. */
    fun playOverlay(track: OstTrack) {
        scope.launch {
            try {
                val bytes = Res.readBytes(track.resourcePath)
                overlayEngine.stop()
                overlayEngine.load(bytes, track.cacheKey)
                overlayEngine.setLooping(false)
                overlayEngine.setVolume(DEFAULT_OVERLAY_VOLUME)
                if (!pausedForLifecycle) overlayEngine.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("OstController.playOverlay(${track.cacheKey}) FAILED: ${e::class.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun stopOverlay() {
        overlayEngine.stop()
    }

    fun setVolume(volume: Float) { engine.setVolume(volume) }

    fun stop() {
        loadJob?.cancel()
        scope.launch {
            mutex.withLock {
                current = null
                engine.stop()
            }
        }
        overlayEngine.stop()
    }

    /** Pause everything when the app goes into the background. */
    fun pauseForBackground() {
        pausedForLifecycle = true
        engine.pause()
        overlayEngine.pause()
    }

    /** Resume the looping track when the app returns to the foreground. */
    fun resumeFromBackground() {
        pausedForLifecycle = false
        if (current != null) engine.play()
        // Overlay stays paused — one-shots aren't worth resuming mid-clip.
    }

    fun release() {
        loadJob?.cancel()
        engine.release()
        overlayEngine.release()
    }

    companion object {
        private const val DEFAULT_VOLUME = 0.55f
        private const val DEFAULT_OVERLAY_VOLUME = 0.85f
    }
}
