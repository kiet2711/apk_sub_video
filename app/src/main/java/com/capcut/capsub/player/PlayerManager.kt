package com.capcut.capsub.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import com.capcut.capsub.data.repository.SettingsRepository
import com.capcut.capsub.domain.media.BilibiliResolver
import com.capcut.capsub.domain.media.BilibiliStreamInfo
import com.capcut.capsub.domain.media.NetworkHeaderHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlayerLoadState {
    IDLE,
    RESOLVING,
    BUFFERING,
    READY,
    ERROR
}

/**
 * Quản lý vòng đời và điều khiển trình phát video Media3 ExoPlayer.
 *
 * URL trang Bilibili không phải URL media. Trước khi phát, manager resolve URL
 * trang thành hai luồng DASH video/audio và ghép chúng trên cùng timeline.
 */
class PlayerManager(private val context: Context) {

    var exoPlayer: ExoPlayer? = null
        private set

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player

    val ttsAudioScheduler = TtsAudioScheduler(context)

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var tickerJob: Job? = null
    private var resolverJob: Job? = null

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _loadState = MutableStateFlow(PlayerLoadState.IDLE)
    val loadState: StateFlow<PlayerLoadState> = _loadState

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError

    fun initialize(videoUri: Uri, originalVolume: Float = 1.0f) {
        resolverJob?.cancel()
        tickerJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        _player.value = null
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _isPlaying.value = false
        _playbackError.value = null
        ttsAudioScheduler.initializePlayers()

        // Tạo player ngay để PlayerView luôn có thể attach, kể cả trong lúc
        // URL Bilibili đang được resolve ở background.
        val player = ExoPlayer.Builder(context).build().apply {
            volume = originalVolume.coerceIn(0f, 1f)
            addListener(createPlayerListener())
        }
        exoPlayer = player
        _player.value = player
        startPositionTicker()

        val uriText = videoUri.toString()
        if (NetworkHeaderHelper.isRemoteUri(videoUri) && BilibiliResolver.isBilibiliPageUrl(uriText)) {
            _loadState.value = PlayerLoadState.RESOLVING
            resolverJob = scope.launch {
                try {
                    val settings = SettingsRepository(context)
                    val stream = withContext(Dispatchers.IO) {
                        val target = BilibiliResolver.resolveUrl(uriText)
                        if (target.bvid.isNullOrBlank() && target.aid.isNullOrBlank()) {
                            throw IllegalArgumentException("Không tìm thấy mã BV/av trong link Bilibili")
                        }
                        val details = BilibiliResolver.getVideoDetails(target, settings.bilibiliSessData)
                        BilibiliResolver.getPlayStream(details.bvid, details.cid, settings.bilibiliSessData)
                    }
                    prepareBilibiliStream(player, stream, settings.bilibiliSessData)
                } catch (error: Exception) {
                    if (resolverJob?.isCancelled == true) return@launch
                    fail("Không mở được video Bilibili: ${error.message ?: "lỗi không xác định"}")
                }
            }
        } else {
            try {
                val source = createMediaSource(videoUri)
                prepare(player, source)
            } catch (error: Exception) {
                fail("Không mở được video: ${error.message ?: "nguồn phát không hợp lệ"}")
            }
        }
    }

    private fun prepareBilibiliStream(
        player: ExoPlayer,
        stream: BilibiliStreamInfo,
        sessData: String
    ) {
        val videoUrl = stream.videoUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Bilibili không trả về luồng hình")
        val audioUrl = stream.audioUrl?.takeIf { it.isNotBlank() }

        val videoSource = createMediaSource(
            uri = Uri.parse(videoUrl),
            sessData = sessData,
            mimeType = if (stream.isDash) MimeTypes.VIDEO_MP4 else null,
            forceBilibiliHeaders = true
        )

        val mediaSource = if (stream.isDash && audioUrl != null && audioUrl != videoUrl) {
            val audioSource = createMediaSource(
                uri = Uri.parse(audioUrl),
                sessData = sessData,
                mimeType = MimeTypes.AUDIO_MP4,
                forceBilibiliHeaders = true
            )
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }
        prepare(player, mediaSource)
    }

    private fun prepare(player: ExoPlayer, mediaSource: MediaSource) {
        if (exoPlayer !== player) return
        _loadState.value = PlayerLoadState.BUFFERING
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    private fun createMediaSource(
        uri: Uri,
        sessData: String = SettingsRepository(context).bilibiliSessData,
        mimeType: String? = null,
        forceBilibiliHeaders: Boolean = false
    ): MediaSource {
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .apply { if (mimeType != null) setMimeType(mimeType) }
            .build()

        if (!NetworkHeaderHelper.isRemoteUri(uri)) {
            return DefaultMediaSourceFactory(context).createMediaSource(mediaItem)
        }

        val headers = NetworkHeaderHelper.getHeadersForUri(uri, sessData).toMutableMap()
        if (forceBilibiliHeaders) {
            headers.putIfAbsent("Referer", "https://www.bilibili.com/")
            headers.putIfAbsent("Origin", "https://www.bilibili.com")
            if (sessData.isNotBlank()) {
                headers.putIfAbsent(
                    "Cookie",
                    if (sessData.contains("=")) sessData else "SESSDATA=$sessData"
                )
            }
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"] ?: NetworkHeaderHelper.DEFAULT_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
    }

    private fun createPlayerListener() = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _loadState.value = PlayerLoadState.BUFFERING
                Player.STATE_READY -> {
                    _durationMs.value = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                    _loadState.value = PlayerLoadState.READY
                }
                Player.STATE_ENDED -> _isPlaying.value = false
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (!playing) ttsAudioScheduler.pause()
        }

        override fun onPlayerError(error: PlaybackException) {
            fail("Lỗi phát video: ${error.errorCodeName}")
        }
    }

    private fun fail(message: String) {
        _playbackError.value = message
        _loadState.value = PlayerLoadState.ERROR
        _isPlaying.value = false
    }

    fun setOriginalVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    _currentPositionMs.value = pos
                    ttsAudioScheduler.onVideoPositionUpdate(pos, player.isPlaying)
                }
                delay(50)
            }
        }
    }

    fun play() {
        exoPlayer?.play()
        ttsAudioScheduler.resume()
    }

    fun pause() {
        exoPlayer?.pause()
        ttsAudioScheduler.pause()
    }

    fun togglePlayPause() {
        exoPlayer?.let { if (it.isPlaying) pause() else play() }
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        exoPlayer?.seekTo(safePos)
        _currentPositionMs.value = safePos
        ttsAudioScheduler.onSeek(safePos)
    }

    fun forward10Seconds() {
        exoPlayer?.let { seekTo(it.currentPosition + 10_000L) }
    }

    fun rewind10Seconds() {
        exoPlayer?.let { seekTo(it.currentPosition - 10_000L) }
    }

    fun release() {
        resolverJob?.cancel()
        tickerJob?.cancel()
        ttsAudioScheduler.release()
        exoPlayer?.release()
        exoPlayer = null
        _player.value = null
        _loadState.value = PlayerLoadState.IDLE
    }
}
