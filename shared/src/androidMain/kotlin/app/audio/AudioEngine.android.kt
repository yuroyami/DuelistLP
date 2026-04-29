package app.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android AudioEngine — Media3 ExoPlayer (audio-only).
 *
 * Resource bytes are written once to the app cache directory under their
 * [cacheKey] filename, then handed to ExoPlayer as a `file://` MediaItem.
 * Subsequent loads with the same key skip the disk write.
 */
actual class AudioEngine actual constructor() {

    private val context: Context = AudioEngineContext.appContext
        ?: error("AudioEngine: AudioEngineContext.init(context) must be called first")
    private val player: ExoPlayer = ExoPlayer.Builder(context).build().also { p ->
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onCompleteCb?.invoke()
            }
        })
    }
    private var loadedKey: String? = null
    private var onCompleteCb: (() -> Unit)? = null

    actual suspend fun load(bytes: ByteArray, cacheKey: String) = withContext(Dispatchers.IO) {
        if (cacheKey == loadedKey) return@withContext
        val cacheDir = File(context.cacheDir, "audio").apply { mkdirs() }
        val file = File(cacheDir, cacheKey)
        if (!file.exists() || file.length() != bytes.size.toLong()) {
            file.writeBytes(bytes)
        }
        withContext(Dispatchers.Main) {
            player.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
            player.prepare()
            loadedKey = cacheKey
        }
    }

    actual fun play() { player.play() }
    actual fun pause() { player.pause() }
    actual fun stop() {
        player.pause()
        player.seekTo(0L)
    }

    actual fun setLooping(loop: Boolean) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    actual fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    actual fun setOnComplete(callback: (() -> Unit)?) { onCompleteCb = callback }

    actual fun seekTo(positionMs: Long) { player.seekTo(positionMs.coerceAtLeast(0L)) }

    actual val positionMs: Long get() = player.currentPosition.coerceAtLeast(0L)
    actual val durationMs: Long get() = player.duration.let { if (it <= 0L) 0L else it }

    actual fun release() {
        player.release()
        loadedKey = null
    }
}

/** Process-wide app context holder for [AudioEngine]. Set from [app.AppActivity]. */
object AudioEngineContext {
    @Volatile var appContext: Context? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
