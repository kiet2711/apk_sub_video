package com.capcut.capsub.domain.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * Tiện ích bóc tách luồng âm thanh từ video sang file M4A bằng Android Native MediaExtractor & MediaMuxer.
 * Tốc độ cực nhanh (1 - 2 giây cho video 30 phút) do chỉ sao chép packet thô (Direct Stream Copy),
 * không giải mã/mã hóa lại, không gây nóng máy và không tốn pin.
 */
object AudioExtractor {

    /**
     * Bóc tách track âm thanh từ Video Uri ra file M4A
     */
    fun extractAudio(
        context: Context,
        videoUri: Uri,
        outputFile: File,
        startMs: Long = 0,
        durationMs: Long = -1,
        progressCallback: ((Float) -> Unit)? = null
    ): Long {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            val headers = if (NetworkHeaderHelper.isRemoteUri(videoUri)) {
                NetworkHeaderHelper.getHeadersForUri(videoUri)
            } else null
            extractor.setDataSource(context, videoUri, headers)
            val trackCount = extractor.trackCount
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                throw IllegalStateException("Không tìm thấy luồng âm thanh (Audio Track) trong tệp video này.")
            }

            extractor.selectTrack(audioTrackIndex)

            val totalDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            val startUs = startMs * 1000L
            val endUs = if (durationMs > 0) startUs + (durationMs * 1000L) else Long.MAX_VALUE

            if (startUs > 0) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            outputFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerAudioTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
            } else {
                256 * 1024
            }

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var lastProgressTime = 0L

            while (true) {
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                bufferInfo.presentationTimeUs = sampleTimeUs
                bufferInfo.flags = if (
                    extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                ) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)

                if (totalDurationUs > 0 && progressCallback != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastProgressTime > 150) {
                        lastProgressTime = now
                        val pct = ((sampleTimeUs - startUs).toFloat() / (totalDurationUs - startUs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        progressCallback(pct)
                    }
                }

                extractor.advance()
            }

            progressCallback?.invoke(1.0f)
            return (totalDurationUs / 1000L).coerceAtLeast(0L)
        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                // Ignore stop error on empty streams
            }
            try {
                extractor.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
