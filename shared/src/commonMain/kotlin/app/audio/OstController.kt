package app.audio

import app.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the background-music [AudioEngine] (looping) and an [overlayEngine]
 * for one-shot stingers (Tribute Summon, Exodia). When an overlay plays the
 * looping track is paused; when the overlay ends or is cancelled, the
 * looping track resumes from where it left off.
 *
 * Track changes go through a [Mutex] so a fast-fired sequence of
 * `play(A); play(B); play(C)` resolves cleanly to "we are now playing C"
 * without earlier loads racing the engine into an inconsistent state.
 *
 * [currentOverlay] mirrors which overlay (if any) is currently playing —
 * the music settings dialog reads it to highlight the active stinger button.
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
    private var overlayJob: Job? = null

    private val _currentOverlay = MutableStateFlow<OstTrack?>(null)
    val currentOverlay: StateFlow<OstTrack?> = _currentOverlay.asStateFlow()

    private var pausedForLifecycle = false
    private var pausedForOverlay = false

    fun play(track: OstTrack) {
        if (current?.cacheKey == track.cacheKey) return
        loadJob?.cancel()
        loadJob = scope.launch {
            mutex.withLock {
                if (current?.cacheKey == track.cacheKey) return@withLock
                val previous = current
                try {
                    val bytes = Res.readBytes(track.resourcePath)
                    engine.stop()
                    engine.load(bytes, track.cacheKey)
                    engine.setLooping(track.looping)
                    engine.setVolume(DEFAULT_VOLUME)
                    if (!pausedForLifecycle && !pausedForOverlay) engine.play()
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

    /**
     * Play a one-shot stinger over the looping track. The looping track is
     * paused for the duration and resumes when the stinger ends (or is
     * stopped/replaced via [stopOverlay] or another [playOverlay] call).
     */
    fun playOverlay(track: OstTrack) {
        overlayJob?.cancel()
        // Pause the looping engine immediately on the calling thread so the
        // bg goes silent right when the user taps.
        if (!pausedForOverlay) {
            pausedForOverlay = true
            engine.pause()
        }
        _currentOverlay.value = track

        overlayJob = scope.launch {
            val completion = CompletableDeferred<Unit>()
            try {
                val bytes = Res.readBytes(track.resourcePath)
                overlayEngine.stop()
                overlayEngine.load(bytes, track.cacheKey)
                overlayEngine.setLooping(false)
                overlayEngine.setVolume(DEFAULT_OVERLAY_VOLUME)
                overlayEngine.setOnComplete {
                    if (!completion.isCompleted) completion.complete(Unit)
                }
                if (!pausedForLifecycle) overlayEngine.play()
                completion.await()
                overlayEngine.setOnComplete(null)
                onOverlayEnded()
            } catch (e: CancellationException) {
                overlayEngine.setOnComplete(null)
                throw e
            } catch (e: Throwable) {
                overlayEngine.setOnComplete(null)
                onOverlayEnded()
                println("OstController.playOverlay(${track.cacheKey}) FAILED: ${e::class.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun stopOverlay() {
        overlayJob?.cancel()
        overlayEngine.setOnComplete(null)
        overlayEngine.stop()
        onOverlayEnded()
    }

    private fun onOverlayEnded() {
        _currentOverlay.value = null
        pausedForOverlay = false
        if (current != null && !pausedForLifecycle) engine.play()
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
        overlayJob?.cancel()
        overlayEngine.stop()
        _currentOverlay.value = null
        pausedForOverlay = false
    }

    /** Pause everything when the app goes into the background. */
    fun pauseForBackground() {
        pausedForLifecycle = true
        engine.pause()
        overlayEngine.pause()
    }

    /** Resume the looping (or overlay) track when the app returns to the foreground. */
    fun resumeFromBackground() {
        pausedForLifecycle = false
        if (_currentOverlay.value != null) {
            overlayEngine.play()
        } else if (current != null && !pausedForOverlay) {
            engine.play()
        }
    }

    fun release() {
        loadJob?.cancel()
        overlayJob?.cancel()
        engine.release()
        overlayEngine.release()
    }

    companion object {
        private const val DEFAULT_VOLUME = 0.55f
        private const val DEFAULT_OVERLAY_VOLUME = 0.85f
    }
}
