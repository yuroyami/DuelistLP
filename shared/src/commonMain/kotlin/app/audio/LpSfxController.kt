package app.audio

import app.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Generic game SFXs — fire-and-forget overlays that play on top of the OST
 * music (without ducking it). Each entry maps to one file under `files/sfx/`.
 *
 * The four louder one-shot stingers (Tribute / Fusion / Special / Exodia)
 * live in [OneShotTracks] and play through the OST overlay engine so the
 * background music ducks under them.
 */
enum class GameSfx(val resourcePath: String, val cacheKey: String) {
    /** Floating-delta phase of any damage action (DamageOpponent or DamageSelf). */
    InflictDamage("files/sfx/ingame_sfx_inflict_damage.wav", "sfx-inflict-damage"),

    /** Floating-delta phase of any heal action (HealSelf or HealOpponent). */
    GainHeal("files/sfx/ingame_sfx_gain_heal.mp3", "sfx-gain-heal"),

    /** A player's turn begins (after End Turn → next player's Draw Phase). */
    TurnStart("files/sfx/ingame_sfx_turn_start.wav", "sfx-turn-start"),

    /** Non-Battle phase advance (DP→SP, SP→MP1, BP→MP2, MP2→EP). */
    PhaseTransition("files/sfx/ingame_sfx_phase_transition.wav", "sfx-phase-transition"),

    /** Entering Battle Phase from MP1. */
    BattlePhase("files/sfx/ingame_sfx_battle_phase.wav", "sfx-battle-phase"),

    /** Match end — overlays the match-end music. */
    Applause("files/sfx/ingame_sfx_applause.wav", "sfx-applause"),

    /** Dice roll begins. */
    DiceThrow("files/sfx/ingame_sfx_dice_throw.wav", "sfx-dice-throw"),

    /** Coin flip begins. */
    CoinToss("files/sfx/ingame_sfx_coin_toss.wav", "sfx-coin-toss"),
}

/**
 * LP-change SFX with three engines so they can overlap cleanly:
 *
 *   - [loopEngine]    `ingame_sfx_lp_roll_loop.mp3` — ticking loop while LP is moving
 *   - [settleEngine]  `ingame_sfx_lp_roll_settle.mp3` — one-shot resolution chord
 *   - [sfxEngine]     generic [GameSfx] one-shots
 *
 * Two engines for loop+settle (instead of one with seek) means each has its
 * own clean playhead, no audible seek glitch, and they can briefly overlap
 * at the transition. The SFX engine is a third instance so its one-shots
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
    private val sfxEngine = AudioEngine().also {
        it.setLooping(false)
        it.setVolume(DEFAULT_SFX_VOLUME)
    }
    private var loaded = false
    private var loadJob: Job? = null
    private var changing = false
    private val sfxLoaded = mutableSetOf<String>()
    private var lastSfxKey: String? = null

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
     * Play a generic SFX one-shot. First call for a given [sfx] loads bytes
     * off the resources thread; subsequent calls with the same key just seek
     * to 0 and replay. Different SFX swap in (only one plays at a time on
     * this engine, which is fine for our event cadence).
     *
     * Failures are swallowed with a single-line log so a missing asset doesn't
     * abort the surrounding flow.
     */
    fun playSfx(sfx: GameSfx) {
        scope.launch {
            try {
                if (sfx.cacheKey !in sfxLoaded) {
                    val bytes = Res.readBytes(sfx.resourcePath)
                    sfxEngine.load(bytes, sfx.cacheKey)
                    sfxLoaded += sfx.cacheKey
                    lastSfxKey = sfx.cacheKey
                } else if (lastSfxKey != sfx.cacheKey) {
                    val bytes = Res.readBytes(sfx.resourcePath)
                    sfxEngine.load(bytes, sfx.cacheKey)
                    lastSfxKey = sfx.cacheKey
                }
                sfxEngine.seekTo(0L)
                sfxEngine.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("LpSfxController.playSfx(${sfx.cacheKey}) failed: ${e::class.simpleName}: ${e.message}")
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
        sfxEngine.release()
    }

    companion object {
        private const val LOOP_PATH = "files/sfx/ingame_sfx_lp_roll_loop.mp3"
        private const val LOOP_KEY = "sfx-lp-roll-loop"
        private const val SETTLE_PATH = "files/sfx/ingame_sfx_lp_roll_settle.mp3"
        private const val SETTLE_KEY = "sfx-lp-roll-settle"
        private const val DEFAULT_VOLUME = 0.85f
        private const val DEFAULT_SFX_VOLUME = 0.95f
    }
}
