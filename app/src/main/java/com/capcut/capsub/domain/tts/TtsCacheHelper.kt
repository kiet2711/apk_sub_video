package com.capcut.capsub.domain.tts

import android.content.Context
import android.util.Log
import com.capcut.capsub.data.api.CapCutSigner
import com.capcut.capsub.data.model.SubtitleDocument
import java.io.File

object TtsCacheHelper {

    private const val TAG = "TtsCacheHelper"

    data class CacheIssue(
        val itemId: Int,
        val text: String,
        val reason: String,
        val filePath: String
    )

    data class CacheAudit(
        val linkedCount: Int,
        val issues: List<CacheIssue>,
        val targetDir: File?
    )

    fun getDocKey(doc: SubtitleDocument): String {
        if (doc.items.isEmpty()) return "empty"
        val first = doc.items.first()
        val last = doc.items.last()
        // Tạo khóa định danh ổn định không phụ thuộc vào trạng thái audioFilePath
        val signature = "${doc.items.size}_${first.startMs}_${first.originalText.take(15)}_${last.endMs}_${last.originalText.take(15)}"
        return CapCutSigner.md5(signature)
    }

    fun getCacheDir(context: Context, doc: SubtitleDocument, voiceType: String? = null): File {
        val key = getDocKey(doc)
        val dir = if (!voiceType.isNullOrBlank()) {
            File(context.filesDir, "tts_cache/$key/$voiceType")
        } else {
            File(context.filesDir, "tts_cache/$key")
        }
        dir.mkdirs()
        return dir
    }

    fun clearCache(context: Context, doc: SubtitleDocument, voiceType: String? = null) {
        val dir = getCacheDir(context, doc, voiceType)
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * Tự động quét và liên kết toàn bộ file audio .mp3 có sẵn trong cache với danh sách SubtitleItem.
     * Chỉ quét bên trong thư mục định danh của tài liệu phụ đề này, tránh tuyệt đối xung đột file ngoại lai.
     */
    fun linkAudioFiles(context: Context, doc: SubtitleDocument, preferredVoiceType: String? = null): Int {
        return auditAndLinkAudioFiles(context, doc, preferredVoiceType).linkedCount
    }

    /**
     * Quét từng câu giống tool desktop: file phải tồn tại, giải mã được và có
     * duration > 0. Không dùng sự tồn tại của sub_1.mp3 để quyết định cả cache.
     */
    fun auditAndLinkAudioFiles(
        context: Context,
        doc: SubtitleDocument,
        preferredVoiceType: String? = null
    ): CacheAudit {
        if (doc.items.isEmpty()) return CacheAudit(0, emptyList(), null)
        doc.reindex()

        val docKey = getDocKey(doc)
        val docDir = File(context.filesDir, "tts_cache/$docKey")
        if (!docDir.exists()) {
            doc.items.forEach { it.audioFilePath = null; it.audioDurationMs = 0L }
            return CacheAudit(
                linkedCount = 0,
                issues = doc.items.map { item ->
                    CacheIssue(item.id, item.getDisplayText(), "Chưa có file âm thanh", "")
                },
                targetDir = null
            )
        }

        // Xác định thư mục audio đích chính xác
        val targetDir = selectTargetDirectory(docDir, preferredVoiceType)

        var linkedCount = 0
        val issues = mutableListOf<CacheIssue>()
        doc.items.forEach { item ->
            val file = File(targetDir, "sub_${item.id}.mp3")
            val validation = AudioFileValidator.validate(file)
            if (validation.isValid) {
                item.audioFilePath = file.absolutePath
                item.audioDurationMs = validation.durationMs
                val srtDurMs = (item.endMs - item.startMs).coerceAtLeast(200L)
                if (item.audioDurationMs > srtDurMs) {
                    val factor = (item.audioDurationMs.toFloat() / srtDurMs.toFloat()).coerceIn(1.0f, 2.2f)
                    item.playbackSpeed = Math.round(factor * 10f) / 10f
                } else {
                    item.playbackSpeed = 1.0f
                }
                linkedCount++
            } else {
                item.audioFilePath = null
                item.audioDurationMs = 0L
                item.playbackSpeed = 1.0f
                issues += CacheIssue(
                    itemId = item.id,
                    text = item.getDisplayText(),
                    reason = validation.reason,
                    filePath = file.absolutePath
                )
            }
        }
        if (linkedCount > 0) {
            Log.d(TAG, "Đã liên kết thành công $linkedCount câu audio từ: ${targetDir.name}")
        }

        return CacheAudit(linkedCount, issues, targetDir)
    }

    internal fun selectTargetDirectory(docDir: File, preferredVoiceType: String?): File {
        return when {
            !preferredVoiceType.isNullOrBlank() && File(docDir, preferredVoiceType).isDirectory -> {
                File(docDir, preferredVoiceType)
            }
            docDir.listFiles()?.any { it.isFile && it.name.matches(Regex("sub_\\d+\\.mp3")) } == true -> {
                docDir
            }
            else -> {
                docDir.listFiles()
                    ?.filter { dir ->
                        dir.isDirectory && dir.listFiles()?.any { file ->
                            file.isFile && file.name.matches(Regex("sub_\\d+\\.mp3"))
                        } == true
                    }
                    ?.maxByOrNull { it.lastModified() }
                    ?: docDir
            }
        }
    }
}
