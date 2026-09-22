package com.capcut.capsub.domain.media

import android.content.Context
import android.net.Uri
import java.io.File
import kotlin.math.ceil

data class AudioChunkInfo(
    val index: Int,
    val startMs: Long,
    val durationMs: Long,
    val file: File
)

/**
 * Tiện ích phân đoạn âm thanh thành các lát cắt (mặc định 10 phút/đoạn)
 * giúp xử lý các video dài 1-3 tiếng trên CapCut Cloud mà không bị quá tải hay timeout.
 */
object AudioChunker {

    const val DEFAULT_CHUNK_DURATION_SEC = 600L // 10 phút

    fun sliceMedia(
        context: Context,
        videoUri: Uri,
        totalDurationMs: Long,
        tempDir: File,
        chunkDurationSec: Long = DEFAULT_CHUNK_DURATION_SEC,
        progressCallback: ((Float, String) -> Unit)? = null
    ): List<AudioChunkInfo> {
        val chunkDurationMs = chunkDurationSec * 1000L
        tempDir.mkdirs()

        val isRemote = NetworkHeaderHelper.isRemoteUri(videoUri)

        // Trường hợp 1: File ngắn (< 10 phút) hoặc Video từ xa (trích xuất audio 1 lần)
        if (isRemote || totalDurationMs <= chunkDurationMs) {
            val singleFile = File(tempDir, "audio_full.m4a")
            if (isRemote) {
                RemoteAudioFetcher.extractAudioFromRemoteUrl(context, videoUri, singleFile) { pct, msg ->
                    progressCallback?.invoke(pct * 0.2f, msg)
                }
            } else {
                progressCallback?.invoke(0.05f, "Đang trích xuất luồng âm thanh...")
                AudioExtractor.extractAudio(context, videoUri, singleFile) { pct ->
                    progressCallback?.invoke(pct * 0.2f, "Đang trích xuất âm thanh: ${(pct * 100).toInt()}%")
                }
            }

            if (totalDurationMs <= chunkDurationMs) {
                return listOf(
                    AudioChunkInfo(
                        index = 0,
                        startMs = 0L,
                        durationMs = totalDurationMs,
                        file = singleFile
                    )
                )
            }

            // Với video từ xa dài hơn 10 phút: cắt thành N phân đoạn từ file audio_full.m4a cục bộ
            val localAudioUri = Uri.fromFile(singleFile)
            val numChunks = ceil(totalDurationMs.toDouble() / chunkDurationMs).toInt()
            val chunkList = mutableListOf<AudioChunkInfo>()

            for (i in 0 until numChunks) {
                val startMs = i * chunkDurationMs
                val durMs = minOf(chunkDurationMs, totalDurationMs - startMs)
                val chunkFile = File(tempDir, "chunk_%03d.m4a".format(i))

                val chunkMsg = "Đang cắt phân đoạn ${i + 1}/$numChunks (${durMs / 1000}s)..."
                val baseProgress = 0.2f + (i.toFloat() / numChunks) * 0.05f
                progressCallback?.invoke(baseProgress, chunkMsg)

                AudioExtractor.extractAudio(context, localAudioUri, chunkFile, startMs, durMs)

                chunkList.add(
                    AudioChunkInfo(
                        index = i,
                        startMs = startMs,
                        durationMs = durMs,
                        file = chunkFile
                    )
                )
            }
            return chunkList
        }

        // Trường hợp 2: File dài -> Cắt thành N phân đoạn
        val numChunks = ceil(totalDurationMs.toDouble() / chunkDurationMs).toInt()
        val chunkList = mutableListOf<AudioChunkInfo>()

        for (i in 0 until numChunks) {
            val startMs = i * chunkDurationMs
            val durMs = minOf(chunkDurationMs, totalDurationMs - startMs)
            val chunkFile = File(tempDir, "chunk_%03d.m4a".format(i))

            val chunkMsg = "Đang cắt phân đoạn ${i + 1}/$numChunks (${durMs / 1000}s)..."
            val baseProgress = (i.toFloat() / numChunks) * 0.2f
            progressCallback?.invoke(baseProgress, chunkMsg)

            AudioExtractor.extractAudio(context, videoUri, chunkFile, startMs, durMs)

            chunkList.add(
                AudioChunkInfo(
                    index = i,
                    startMs = startMs,
                    durationMs = durMs,
                    file = chunkFile
                )
            )
        }

        return chunkList
    }
}
