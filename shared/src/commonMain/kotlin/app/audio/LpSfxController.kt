package app.audio

import app.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Per-action one-shot played during the floating-delta phase of a commit,
 * BEFORE the LP roll begins. Picked by [DuelScreen] based on which combat
 * action is resolving.
 */
enum class ActionSfx(val resourcePath: String, val cacheKey: String) {
    Damage("files/sounds/ingame-misc-dmg.mp3", "sfx-action-dmg"),
    Heal("files/sounds/ingame-misc-heal.mp3", "sfx-action-heal"),
    Blood("files/sounds/ingame-misc-blood.mp3", "sfx-action-blood"),
}

/**
 * LP-change SFX with three engines so they can overlap cleanly:
 *
 *   - [loopEngine]    `lp-roll-loop.mp3` — ticking loop while LP is moving
 *   - [settleEngine]  `lp-roll-settle.mp3` — one-shot resolution chord
 *   - [actionEngine]  per-action one-shots ([ActionSfx])
 *
 * Two engines for loop+settle (instead of one with seek) means each has its
 * own clean playhead, no audible seek glitch, and they can briefly overlap
 * at the transition. The action engine is a third instance so its one-shots
 * overlay both the loop and any music.
 *
 * [preload] is called from App.kt so the first LP change has zero latency.
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
    private val actionEngine = AudioEngine().also {
        it.setLooping(false)
        it.setVolume(DEFAULT_ACTION_VOLUME)
    }
    private var loaded = false
    private var loadJob: Job? = null
    private var changing = false
    private val actionLoaded = mutableSetOf<String>()
    private var lastActionKey: String? = null

    /** Eagerly load the loop+settle clips. Idempotent. */
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

    /**
     * Signal that LP has started or is still moving. First call cancels any
     * pending settle and (re)starts the loop from the head; later calls during
     * the same change-burst are no-ops so the loop ticks seamlessly.
     */
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
            settleEngine.stop()
            loopEngine.seekTo(0L)
            loopEngine.play()
        }
    }

    /** Stop the loop and play the resolution chord. No-op if not currently changing. */
    fun stabilize() {
        if (!changing) return
        changing = false
        loopEngine.stop()
        settleEngine.seekTo(0L)
        settleEngine.play()
    }

    /**
     * Play the per-action one-shot. First call for a given [sfx] loads bytes
     * off the resources thread; subsequent calls just seek to 0 and replay.
     * Failures are swallowed with a single-line log so a missing asset doesn't
     * abort the surrounding commit sequence.
     */
    fun playActionSfx(sfx: ActionSfx) {
        scope.launch {
            try {
                if (sfx.cacheKey !in actionLoaded) {
                    val bytes = Res.readBytes(sfx.resourcePath)
                    actionEngine.load(bytes, sfx.cacheKey)
                    actionLoaded += sfx.cacheKey
                    lastActionKey = sfx.cacheKey
                } else if (lastActionKey != sfx.cacheKey) {
                    val bytes = Res.readBytes(sfx.resourcePath)
                    actionEngine.load(bytes, sfx.cacheKey)
                    lastActionKey = sfx.cacheKey
                }
                actionEngine.seekTo(0L)
                actionEngine.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("LpSfxController.playActionSfx(${sfx.cacheKey}) failed: ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    fun setVolume(v: Float) {
        loopEngine.setVolume(v)
        settleEngine.setVolume(v)
    }

    fun release() {
        loadJob?.cancel()
        loopEngine.release()
        settleEngine.release()
        actionEngine.release()
    }

    companion object {
        private const val LOOP_PATH = "files/sounds/lp-roll-loop.mp3"
        private const val LOOP_KEY = "sfx-lp-roll-loop"
        private const val SETTLE_PATH = "files/sounds/lp-roll-settle.mp3"
        private const val SETTLE_KEY = "sfx-lp-roll-settle"
        private const val DEFAULT_VOLUME = 0.85f
        private const val DEFAULT_ACTION_VOLUME = 0.95f
    }
}
