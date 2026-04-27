package app.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.NSNotificationCenter
import platform.Foundation.writeToFile

/**
 * iOS AudioEngine — AVFoundation AVPlayer.
 *
 * Looping is implemented via an `AVPlayerItemDidPlayToEndTime` notification
 * observer that seeks back to the start when looping is enabled.
 */
@OptIn(ExperimentalForeignApi::class)
actual class AudioEngine actual constructor() {

    private val player: AVPlayer = AVPlayer().also { it.volume = 1f }
    private var item: AVPlayerItem? = null
    private var endObserver: Any? = null
    private var loopEnabled: Boolean = false
    private var loadedKey: String? = null
    private var pendingVolume: Float = 1f

    init {
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)
        }
    }

    actual suspend fun load(bytes: ByteArray, cacheKey: String) {
        if (cacheKey == loadedKey) return
        val filePath = withContext(Dispatchers.Default) {
            val cacheRoot = (NSSearchPathForDirectoriesInDomains(
                NSCachesDirectory, NSUserDomainMask, true
            ).firstOrNull() as? String) ?: error("Could not resolve caches directory")
            val audioDir = "$cacheRoot/audio"
            NSFileManager.defaultManager.createDirectoryAtPath(audioDir, true, null, null)
            val file = "$audioDir/$cacheKey"
            val existing = NSFileManager.defaultManager.contentsAtPath(file)
            if (existing == null || existing.length.toLong() != bytes.size.toLong()) {
                bytes.toNSData().writeToFile(file, atomically = true)
            }
            file
        }
        val url = NSURL.fileURLWithPath(filePath)
        // Remove the previous end-observer (it was scoped to the old item).
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null

        val newItem = AVPlayerItem(uRL = url)
        item = newItem
        player.replaceCurrentItemWithPlayerItem(newItem)
        player.volume = pendingVolume

        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = newItem,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            if (loopEnabled) {
                player.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
                player.play()
            }
        }
        loadedKey = cacheKey
    }

    actual fun play() { player.play() }
    actual fun pause() { player.pause() }
    actual fun stop() {
        player.pause()
        player.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
    }

    actual fun setLooping(loop: Boolean) { loopEnabled = loop }

    actual fun setVolume(volume: Float) {
        pendingVolume = volume.coerceIn(0f, 1f)
        player.volume = pendingVolume
    }

    actual fun seekTo(positionMs: Long) {
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000))
    }

    actual val positionMs: Long
        get() {
            val sec = CMTimeGetSeconds(player.currentTime())
            return if (sec.isNaN() || sec.isInfinite()) 0L else (sec * 1000.0).toLong().coerceAtLeast(0L)
        }

    actual val durationMs: Long
        get() {
            val it = item ?: return 0L
            val sec = CMTimeGetSeconds(it.duration)
            return if (sec.isNaN() || sec.isInfinite()) 0L else (sec * 1000.0).toLong().coerceAtLeast(0L)
        }

    actual fun release() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        item = null
        loadedKey = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
}
