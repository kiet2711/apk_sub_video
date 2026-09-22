package com.capcut.capsub.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.capcut.capsub.domain.media.NetworkHeaderHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Quản lý vòng đời và điều khiển trình phát video Media3 ExoPlayer.
 */
class PlayerManager(private val context: Context) {

    var exoPlayer: ExoPlayer? = null
        private set

    val ttsAudioScheduler = TtsAudioScheduler(context)

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    fun initialize(videoUri: Uri, originalVolume: Float = 1.0f) {
        tickerJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        ttsAudioScheduler.initializePlayers()

        val isRemote = NetworkHeaderHelper.isRemoteUri(videoUri)
        val mediaSourceFactory = if (isRemote) {
            val settings = com.capcut.capsub.data.repository.SettingsRepository(context)
            val headers = NetworkHeaderHelper.getHeadersForUri(videoUri, settings.bilibiliSessData)
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(headers["User-Agent"] ?: NetworkHeaderHelper.DEFAULT_USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)
            val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            DefaultMediaSourceFactory(upstreamFactory)
        } else {
            DefaultMediaSourceFactory(context)
        }

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                setMediaItem(MediaItem.fromUri(videoUri))
                volume = originalVolume.coerceIn(0f, 1f)
                prepare()
                playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _durationMs.value = duration.coerceAtLeast(0L)
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (!playing) {
                        ttsAudioScheduler.pause()
                    }
                }
            })
        }

        exoPlayer = player
        startPositionTicker()
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
                delay(50) // Cập nhật vị trí mỗi 50ms để đồng bộ phụ đề và audio mượt mà
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
        exoPlayer?.let {
            if (it.isPlaying) pause() else play()
        }
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        exoPlayer?.seekTo(safePos)
        _currentPositionMs.value = safePos
        ttsAudioScheduler.onSeek(safePos)
    }

    fun forward10Seconds() {
        exoPlayer?.let {
            seekTo(it.currentPosition + 10_000L)
        }
    }

    fun rewind10Seconds() {
        exoPlayer?.let {
            seekTo(it.currentPosition - 10_000L)
        }
    }

    fun release() {
        tickerJob?.cancel()
        ttsAudioScheduler.release()
        exoPlayer?.release()
        exoPlayer = null
    }
}
