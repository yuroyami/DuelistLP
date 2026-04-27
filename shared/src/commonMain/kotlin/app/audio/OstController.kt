package app.audio

import app.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns a single background-music [AudioEngine] instance and switches the
 * track whenever [play] is called with a different [Track]. Tracks loop
 * indefinitely.
 *
 * Track changes go through a [Mutex] so a fast-fired sequence of
 * `play(A); play(B); play(C)` resolves cleanly to "we are now playing C"
 * without earlier loads racing the engine into an inconsistent state.
 */
class OstController(private val scope: CoroutineScope) {

    enum class Track(val cacheKey: String, val resourcePath: String) {
        Main("ost-main.mp3", "files/ost/yugioh-main-theme.mp3"),
        Rps("ost-rps.mp3", "files/ost/yugioh-rockpaperscissors.mp3"),
        Duel("ost-duel.mp3", "files/ost/yugioh-single-duel.mp3"),
        Tournament("ost-tournament.mp3", "files/ost/yugioh-tournament-duel.mp3"),
        Losing("ost-losing.mp3", "files/ost/yugioh-losing-theme.mp3"),
        Winning("ost-winning.mp3", "files/ost/yugioh-winning-theme.mp3"),
    }

    private val engine = AudioEngine().also {
        it.setLooping(true)
        it.setVolume(DEFAULT_VOLUME)
    }
    private val mutex = Mutex()
    private var current: Track? = null
    private var loadJob: Job? = null

    fun play(track: Track) {
        if (current == track) return
        loadJob?.cancel()
        loadJob = scope.launch {
            mutex.withLock {
                if (current == track) return@withLock // someone else got there first
                val previous = current
                try {
                    val bytes = Res.readBytes(track.resourcePath)
                    engine.stop()
                    engine.load(bytes, track.cacheKey)
                    engine.setLooping(true)
                    engine.setVolume(DEFAULT_VOLUME)
                    engine.play()
                    current = track
                } catch (e: CancellationException) {
                    throw e // propagate; do not reset current
                } catch (e: Throwable) {
                    // Reset current so a retry can attempt the same track again,
                    // and surface the failure so we can see it in logs.
                    current = previous
                    println("OstController.play($track) FAILED: ${e::class.simpleName}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
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
    }

    fun release() {
        loadJob?.cancel()
        engine.release()
    }

    companion object {
        private const val DEFAULT_VOLUME = 0.55f
    }
}
