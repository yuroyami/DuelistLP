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
 * Background music + one-shot stinger orchestration.
 *
 * Two engines:
 *   - [engine] — the looping track (currently playing background music)
 *   - [overlayEngine] — one-shot stingers (Tribute Summon, Exodia)
 *
 * Track changes go through [mutex] so a fast `play(A); play(B); play(C)`
 * collapses cleanly to "we're playing C" without earlier loads racing the
 * engine into an inconsistent state.
 *
 * When an overlay plays, the loop pauses immediately on the calling thread
 * (no audible delay) and resumes on overlay completion or cancellation.
 *
 * [pausedForLifecycle] and [pausedForOverlay] track the two reasons playback
 * might be silenced; the loop only resumes when BOTH are false.
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
    /** What overlay (if any) is currently playing. UI reads this to highlight buttons. */
    val currentOverlay: StateFlow<OstTrack?> = _currentOverlay.asStateFlow()

    private var pausedForLifecycle = false
    private var pausedForOverlay = false

    /**
     * Switch the looping track. Idempotent if already on [track]. Cancels any
     * prior in-flight load so the latest call wins.
     */
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
     * Play a one-shot over the looping track. The loop pauses immediately on
     * the calling thread and resumes when the overlay ends, is cancelled, or
     * is replaced by another [playOverlay] call.
     */
    fun playOverlay(track: OstTrack) {
        overlayJob?.cancel()
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

    /** Called from App.kt on Lifecycle.ON_PAUSE / ON_STOP. */
    fun pauseForBackground() {
        pausedForLifecycle = true
        engine.pause()
        overlayEngine.pause()
    }

    /** Called from App.kt on Lifecycle.ON_RESUME. */
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
