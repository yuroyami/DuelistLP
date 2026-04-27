package app.audio

import app.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Plays the anime LP-change SFX with a two-clip design:
 *
 *   - `life-points-change-loop.mp3` — the ticking loop, played on its own
 *     engine with [AudioEngine.setLooping] enabled. Starts on the first LP
 *     change and keeps looping while LP keeps changing.
 *   - `life-points-settle.mp3` — the resolution chord, played once on its
 *     own engine when the LP has been stable for [stabilize].
 *
 * Two engines (instead of one with mid-file seek) means the loop and the
 * settle each have their own clean playhead, no audible seek glitch, and
 * they can briefly overlap (settle starting before loop fully fades) for a
 * more natural transition.
 */
class LpSfxController(private val scope: CoroutineScope) {

    private val loopEngine = AudioEngine().also {
        it.setLooping(true)
        it.setVolume(DEFAULT_VOLUME)
    }
    private val settleEngine = AudioEngine().also {
        it.setLooping(false)
        it.setVolume(DEFAULT_VOLUME)
    }
    private var loaded = false
    private var loadJob: Job? = null
    private var changing = false

    /** Eagerly load both clips so the first LP change has zero startup latency. */
    fun preload() {
        if (loaded || loadJob?.isActive == true) return
        loadJob = scope.launch {
            try {
                val loopBytes = Res.readBytes(LOOP_PATH)
                val settleBytes = Res.readBytes(SETTLE_PATH)
                loopEngine.load(loopBytes, LOOP_KEY)
                loopEngine.setLooping(true)
                settleEngine.load(settleBytes, SETTLE_KEY)
                settleEngine.setLooping(false)
                loaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("LpSfxController preload FAILED: ${e::class.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /** Called whenever the LP value changes. Starts the loop if not already running. */
    fun onLpChange() {
        if (!loaded) {
            preload()
            scope.launch {
                loadJob?.join()
                if (loaded) onLpChange()
            }
            return
        }
        if (!changing) {
            changing = true
            settleEngine.stop()       // cancel any pending settle
            loopEngine.seekTo(0L)     // restart loop from the head
            loopEngine.play()
        }
        // Subsequent rapid changes: leave the loop running for a seamless ticking.
    }

    /**
     * LP has been stable long enough — stop the loop and play the resolution
     * chord once.
     */
    fun stabilize() {
        if (!changing) return
        changing = false
        loopEngine.stop()
        settleEngine.seekTo(0L)
        settleEngine.play()
    }

    fun setVolume(v: Float) {
        loopEngine.setVolume(v)
        settleEngine.setVolume(v)
    }

    fun release() {
        loadJob?.cancel()
        loopEngine.release()
        settleEngine.release()
    }

    companion object {
        private const val LOOP_PATH = "files/sfx/life-points-change-loop.mp3"
        private const val LOOP_KEY = "sfx-life-points-change-loop.mp3"
        private const val SETTLE_PATH = "files/sfx/life-points-settle.mp3"
        private const val SETTLE_KEY = "sfx-life-points-settle.mp3"
        private const val DEFAULT_VOLUME = 0.85f
    }
}
