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

    fun validate(file: File): AudioValidationResult {
        if (!file.exists()) {
            return AudioValidationResult(false, reason = "Chưa nhận được file âm thanh")
        }
        if (!file.isFile || file.length() < MIN_AUDIO_BYTES) {
            return AudioValidationResult(false, reason = "File âm thanh rỗng hoặc tải chưa đủ")
        }

        var retriever: MediaMetadataRetriever? = null
        return try {
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
    }
}
