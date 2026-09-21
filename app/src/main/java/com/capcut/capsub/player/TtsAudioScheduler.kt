package com.capcut.capsub.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.model.SubtitleItem
import com.capcut.capsub.domain.tts.AudioFileValidator
import java.io.File

/**
 * Phát track TTS độc lập và trộn với audio video. Hai player luân phiên để giảm
 * độ trễ, nhưng trạng thái câu chỉ được xác nhận sau khi ExoPlayer thực sự chạy.
 */
class TtsAudioScheduler(private val context: Context) {

    private val players = arrayOfNulls<ExoPlayer>(2)
    private var nextPlayerIndex = 0
    private var currentPlayerIndex = -1

    private var subtitleDoc: SubtitleDocument? = null
    private var lastPlayedItemId = -1
    @Volatile private var pendingItemId = -1
    @Volatile private var currentItemId = -1
    private var isMuted = false
    private var aiVolume = 1.0f
    private val playbackRetries = mutableMapOf<Int, Int>()
    private val retryAfterElapsedMs = mutableMapOf<Int, Long>()

    init {
        initializePlayers()
    }

    fun initializePlayers() {
        releasePlayers()
        players.indices.forEach { index -> players[index] = buildPlayer(index) }
        resetPlaybackState()
    }

    private fun buildPlayer(index: Int): ExoPlayer {
        val speechAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        return ExoPlayer.Builder(context).build().apply {
            // Không giành audio focus: giọng AI phải được mix song song với video gốc.
            setAudioAttributes(speechAttributes, false)
            volume = effectiveVolume()
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying && index == currentPlayerIndex && pendingItemId != -1) {
                        currentItemId = pendingItemId
                        lastPlayedItemId = pendingItemId
                        pendingItemId = -1
                        playbackRetries.remove(currentItemId)
                        retryAfterElapsedMs.remove(currentItemId)
                        Log.d(TAG, "Audio AI đã bắt đầu phát câu #$currentItemId bằng player $index")
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (index != currentPlayerIndex) return
                    if (playbackState == Player.STATE_ENDED) {
                        currentItemId = -1
                        pendingItemId = -1
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (index != currentPlayerIndex) return
                    val failedId = if (pendingItemId != -1) pendingItemId else currentItemId
                    Log.e(TAG, "Lỗi phát audio AI câu #$failedId: ${error.message}", error)
                    if (failedId != -1) {
                        val retryCount = (playbackRetries[failedId] ?: 0) + 1
                        playbackRetries[failedId] = retryCount
                        if (retryCount <= MAX_PLAYBACK_RETRIES) {
                            retryAfterElapsedMs[failedId] = SystemClock.elapsedRealtime() + 500L * retryCount
                            lastPlayedItemId = -1
                        } else {
                            lastPlayedItemId = failedId
                        }
                    }
                    pendingItemId = -1
                    currentItemId = -1
                }
            })
        }
    }

    fun setSubtitleDocument(doc: SubtitleDocument?) {
        subtitleDoc = doc
        stop()
        resetPlaybackState()
        val countWithAudio = doc?.items?.count { !it.audioFilePath.isNullOrBlank() } ?: 0
        Log.d(TAG, "setSubtitleDocument: total ${doc?.size ?: 0}, audio hợp lệ: $countWithAudio")
    }

    fun setAiVolume(volume: Float) {
        aiVolume = volume.coerceIn(0f, 1f)
        players.forEach { it?.volume = effectiveVolume() }
        Log.d(TAG, "Âm lượng AI: ${(aiVolume * 100).toInt()}%${if (isMuted) " (đang tắt)" else ""}")
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        players.forEach { it?.volume = effectiveVolume() }
        return isMuted
    }

    fun onVideoPositionUpdate(positionMs: Long, isVideoPlaying: Boolean) {
        if (!isVideoPlaying) {
            pause()
            return
        }

        val doc = subtitleDoc ?: return
        val item = doc.getActiveItem(positionMs)
        if (item == null) {
            if (currentItemId != -1 || pendingItemId != -1) stopCurrentAudio()
            return
        }

        if (currentItemId == item.id) {
            val player = players.getOrNull(currentPlayerIndex)
            if (player?.playbackState == Player.STATE_READY && !player.isPlaying) player.play()
            return
        }
        if (pendingItemId == item.id || lastPlayedItemId == item.id) return

        val retryAt = retryAfterElapsedMs[item.id] ?: 0L
        if (SystemClock.elapsedRealtime() < retryAt) return

        val path = item.audioFilePath ?: return
        val file = File(path)
        if (!file.exists()) {
            item.audioFilePath = null
            return
        }

        val durationMs = if (item.audioDurationMs > 0L) {
            item.audioDurationMs
        } else {
            val validation = AudioFileValidator.validate(file)
            if (!validation.isValid) {
                Log.w(TAG, "Bỏ qua câu #${item.id}: ${validation.reason}")
                item.audioFilePath = null
                return
            }
            item.audioDurationMs = validation.durationMs
            validation.durationMs
        }

        val timelineOffsetMs = (positionMs - item.startMs).coerceAtLeast(0L)
        val sourceOffsetMs = (timelineOffsetMs * item.playbackSpeed).toLong()
        if (sourceOffsetMs >= durationMs) {
            lastPlayedItemId = item.id
            return
        }
        playItem(item, sourceOffsetMs)
    }

    private fun playItem(item: SubtitleItem, sourceOffsetMs: Long) {
        val file = item.audioFilePath?.let(::File) ?: return
        if (!AudioFileValidator.validate(file).isValid) return
        if (players.any { it == null }) initializePlayers()

        val selectedIndex = nextPlayerIndex
        val oldIndex = currentPlayerIndex
        nextPlayerIndex = 1 - selectedIndex
        currentPlayerIndex = selectedIndex
        pendingItemId = item.id
        currentItemId = -1

        if (oldIndex >= 0 && oldIndex != selectedIndex) players[oldIndex]?.stop()
        players[selectedIndex]?.apply {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            playbackParameters = PlaybackParameters(item.playbackSpeed.coerceIn(0.5f, 2.5f))
            volume = effectiveVolume()
            prepare()
            if (sourceOffsetMs > 0L) seekTo(sourceOffsetMs)
            playWhenReady = true
        }

        Log.d(
            TAG,
            "Chuẩn bị câu #${item.id} [${item.startMs}-${item.endMs}] ${file.name}, " +
                "offset=${sourceOffsetMs}ms, speed=${item.playbackSpeed}x, volume=$aiVolume"
        )
    }

    fun onSeek(targetPositionMs: Long) {
        stopCurrentAudio()
        lastPlayedItemId = -1
        pendingItemId = -1
        currentItemId = -1
        val currentItem = subtitleDoc?.getActiveItem(targetPositionMs)
        if (currentItem == null) return
        // Ticker kế tiếp sẽ phát từ đúng offset của câu thay vì bỏ qua khi tua vào giữa.
        retryAfterElapsedMs.remove(currentItem.id)
    }

    fun pause() {
        if (currentPlayerIndex >= 0) players[currentPlayerIndex]?.pause()
    }

    fun resume() {
        if (currentPlayerIndex >= 0) players[currentPlayerIndex]?.play()
    }

    fun stop() {
        players.forEach { it?.stop() }
    }

    private fun stopCurrentAudio() {
        if (currentPlayerIndex >= 0) players[currentPlayerIndex]?.stop()
        pendingItemId = -1
        currentItemId = -1
    }

    fun release() {
        releasePlayers()
        resetPlaybackState()
    }

    private fun releasePlayers() {
        players.indices.forEach { index ->
            players[index]?.release()
            players[index] = null
        }
    }

    private fun resetPlaybackState() {
        nextPlayerIndex = 0
        currentPlayerIndex = -1
        lastPlayedItemId = -1
        pendingItemId = -1
        currentItemId = -1
        playbackRetries.clear()
        retryAfterElapsedMs.clear()
    }

    private fun effectiveVolume(): Float = if (isMuted) 0f else aiVolume

    internal fun hasStartedItem(itemId: Int): Boolean = currentItemId == itemId

    companion object {
        private const val TAG = "TtsAudioScheduler"
        private const val MAX_PLAYBACK_RETRIES = 2
    }
}
