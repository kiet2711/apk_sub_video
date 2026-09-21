package com.capcut.capsub.domain.tts

import android.media.MediaMetadataRetriever
import java.io.File

data class AudioValidationResult(
    val isValid: Boolean,
    val durationMs: Long = 0L,
    val reason: String = ""
)

/**
 * Kiểm tra file TTS bằng chính bộ giải mã media của Android. Kích thước file chỉ
 * là điều kiện sơ bộ; một file chỉ được dùng khi Android đọc được duration > 0.
 */
object AudioFileValidator {
    const val MIN_AUDIO_BYTES = 128L

    // Bộ nhớ đệm kết quả kiểm định để tránh gọi MediaMetadataRetriever liên tục trên Main Thread
    private val validationCache = java.util.concurrent.ConcurrentHashMap<String, AudioValidationResult>()

    fun validate(file: File): AudioValidationResult {
        if (!file.exists()) {
            return AudioValidationResult(false, reason = "Chưa nhận được file âm thanh")
        }
        val length = file.length()
        if (!file.isFile || length < MIN_AUDIO_BYTES) {
            return AudioValidationResult(false, reason = "File âm thanh rỗng hoặc tải chưa đủ")
        }

        val cacheKey = "${file.absolutePath}:$length:${file.lastModified()}"
        validationCache[cacheKey]?.let { return it }

        var retriever: MediaMetadataRetriever? = null
        val result = try {
            retriever = MediaMetadataRetriever().apply { setDataSource(file.absolutePath) }
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            if (durationMs > 0L) {
                AudioValidationResult(true, durationMs)
            } else {
                AudioValidationResult(false, reason = "File âm thanh có duration bằng 0")
            }
        } catch (error: Exception) {
            AudioValidationResult(
                isValid = false,
                reason = "Android không giải mã được MP3: ${error.message ?: error.javaClass.simpleName}"
            )
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {
            }
        }
        validationCache[cacheKey] = result
        return result
    }

    fun invalidate(file: File) {
        val prefix = file.absolutePath
        validationCache.keys.removeIf { it.startsWith(prefix) }
    }

    fun clearCache() {
        validationCache.clear()
    }
}
