package com.capcut.capsub.data.repository

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import com.capcut.capsub.data.model.HistoryItem
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.domain.media.BilibiliResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class HistoryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val historyFile = File(context.filesDir, "history_records.json")
    private val atomicHistoryFile = AtomicFile(historyFile)
    private val subtitlesDir = File(context.filesDir, "saved_subtitles").apply { mkdirs() }

    init {
        synchronized(fileLock) {
            if (!isInitialized) {
                _historyFlow.value = readHistoryListLocked()
                isInitialized = true
            }
        }
    }

    fun refreshHistory() = synchronized(fileLock) {
        _historyFlow.value = readHistoryListLocked()
    }

    fun getHistoryList(): List<HistoryItem> = synchronized(fileLock) {
        readHistoryListLocked()
    }

    private fun readHistoryListLocked(): List<HistoryItem> {
        if (!historyFile.exists() && !File("${historyFile.path}.bak").exists()) return emptyList()
        return try {
            val content = atomicHistoryFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (content.isBlank()) return emptyList()
            json.decodeFromString<List<HistoryItem>>(content).sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            Log.e(TAG, "Không thể đọc lịch sử tại ${historyFile.absolutePath}", e)
            emptyList()
        }
    }

    fun saveHistory(
        videoUri: Uri,
        videoName: String,
        durationMs: Long,
        document: SubtitleDocument,
        translationEngine: String,
        sourceLanguage: String
    ): HistoryItem = synchronized(fileLock) {
        val currentList = getHistoryList().toMutableList()
        val existing = currentList.firstOrNull { it.videoUri == videoUri.toString() }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val srtFile = if (existing != null) File(existing.srtFilePath) else File(subtitlesDir, "sub_$id.srt")
        persistSubtitleDocument(srtFile, document)

        val docKey = com.capcut.capsub.domain.tts.TtsCacheHelper.getDocKey(document)
        val item = HistoryItem(
            id = id,
            videoUri = videoUri.toString(),
            videoName = if (videoName.isNotBlank()) videoName else existing?.videoName ?: "Video",
            durationMs = if (durationMs > 0) durationMs else existing?.durationMs ?: 0L,
            srtFilePath = srtFile.absolutePath,
            sentenceCount = document.size,
            translationEngine = translationEngine,
            sourceLanguage = sourceLanguage,
            ttsVoice = existing?.ttsVoice,
            docKey = docKey,
            createdAt = System.currentTimeMillis()
        )

        currentList.removeAll { it.videoUri == videoUri.toString() }
        currentList.add(0, item)

        saveList(currentList)
        item
    }

    fun deleteHistory(id: String): Boolean = synchronized(fileLock) {
        val currentList = getHistoryList().toMutableList()
        val item = currentList.firstOrNull { it.id == id } ?: return@synchronized false

        // 1. Dọn dẹp cache audio MP3 tương ứng
        try {
            val srtFile = File(item.srtFilePath)
            val doc = loadSubtitleDocumentData(item)
            com.capcut.capsub.domain.tts.TtsCacheHelper.deleteDocCache(context, doc, explicitDocKey = item.docKey)
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi xoá audio cache của video ${item.videoName}", e)
        }

        // 2. Xoá file phụ đề SRT
        try {
            File(item.srtFilePath).delete()
            documentFileFor(File(item.srtFilePath)).delete()
        } catch (e: Exception) {
            Log.w(TAG, "Không thể xoá file phụ đề ${item.srtFilePath}", e)
        }

        currentList.removeAll { it.id == id }
        saveList(currentList)
        true
    }

    fun loadSubtitleDocument(item: HistoryItem): SubtitleDocument {
        val doc = loadSubtitleDocumentData(item)
        com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(context, doc, item.ttsVoice)
        return doc
    }

    /**
     * Nạp lịch sử và nâng cấp file cũ. Với video Bilibili, bản cũ chỉ lưu
     * SRT dịch nên hàm sẽ tải lại sub gốc và ghép theo timeline một lần.
     */
    suspend fun loadSubtitleDocumentWithRecovery(item: HistoryItem): SubtitleDocument = withContext(Dispatchers.IO) {
        val srtFile = File(item.srtFilePath)
        val documentFile = documentFileFor(srtFile)
        val needsMigration = !documentFile.exists()
        val doc = loadSubtitleDocumentData(item)

        if (needsMigration && BilibiliResolver.isBilibiliPageUrl(item.videoUri)) {
            try {
                val settings = com.capcut.capsub.data.repository.SettingsRepository(context)
                val target = BilibiliResolver.resolveUrl(item.videoUri)
                val details = BilibiliResolver.getVideoDetails(target, settings.bilibiliSessData)
                val subtitles = BilibiliResolver.getSubtitles(details.bvid, details.cid, settings.bilibiliSessData)
                val bestSubtitle = subtitles.firstOrNull { !it.isAi } ?: subtitles.firstOrNull()
                val originalDoc = bestSubtitle?.let {
                    BilibiliResolver.downloadSubtitleAsDocument(it.subtitleUrl)
                }
                if (originalDoc != null && mergeOriginalTextByTimeline(doc, originalDoc)) {
                    persistSubtitleDocument(srtFile, doc)
                }
            } catch (e: Exception) {
                // Không ghi sidecar thiếu bản gốc; lần mở sau có thể thử phục hồi lại.
                Log.w(TAG, "Chưa thể phục hồi sub gốc Bilibili cho ${item.videoName}", e)
            }
        } else if (needsMigration) {
            persistSubtitleDocument(srtFile, doc)
        }

        com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(context, doc, item.ttsVoice)
        doc
    }

    fun updateSubtitleForUri(
        videoUri: Uri,
        document: SubtitleDocument,
        voiceType: String? = null
    ) = synchronized(fileLock) {
        val list = getHistoryList().toMutableList()
        val item = list.firstOrNull { it.videoUri == videoUri.toString() } ?: return@synchronized
        try {
            val srtFile = File(item.srtFilePath)
            persistSubtitleDocument(srtFile, document)
            val computedDocKey = com.capcut.capsub.domain.tts.TtsCacheHelper.getDocKey(document)
            val updated = item.copy(
                sentenceCount = document.size,
                ttsVoice = voiceType ?: item.ttsVoice,
                docKey = computedDocKey,
                createdAt = System.currentTimeMillis()
            )
            val idx = list.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                list[idx] = updated
                saveList(list)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Không thể cập nhật phụ đề cho $videoUri", e)
        }
    }

    private fun saveList(list: List<HistoryItem>) {
        var output: FileOutputStream? = null
        try {
            val content = json.encodeToString(list)
            output = atomicHistoryFile.startWrite()
            output!!.write(content.toByteArray(Charsets.UTF_8))
            atomicHistoryFile.finishWrite(output)
            output = null
            _historyFlow.value = list
        } catch (e: Exception) {
            output?.let { atomicHistoryFile.failWrite(it) }
            Log.e(TAG, "Không thể lưu lịch sử tại ${historyFile.absolutePath}", e)
            throw e
        }
    }

    private fun loadSubtitleDocumentData(item: HistoryItem): SubtitleDocument {
        val srtFile = File(item.srtFilePath)
        val documentFile = documentFileFor(srtFile)

        if (documentFile.exists()) {
            try {
                return json.decodeFromString<SubtitleDocument>(documentFile.readText(Charsets.UTF_8)).apply {
                    normalizeTranslations()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Không thể đọc sidecar song ngữ ${documentFile.absolutePath}, thử khôi phục từ SRT", e)
            }
        }

        if (!srtFile.exists()) return SubtitleDocument()
        return try {
            SubtitleDocument.parseSrt(srtFile.readText(Charsets.UTF_8)).apply {
                recoverLegacyBilingualText(item.sourceLanguage)
                normalizeTranslations()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Không thể đọc phụ đề ${srtFile.absolutePath}", e)
            SubtitleDocument()
        }
    }

    private fun persistSubtitleDocument(srtFile: File, document: SubtitleDocument) {
        document.normalizeTranslations()
        document.saveToFile(srtFile, mode = "translated")
        writeDocumentSidecar(documentFileFor(srtFile), document)
    }

    private fun writeDocumentSidecar(file: File, document: SubtitleDocument) {
        file.parentFile?.mkdirs()
        var output: FileOutputStream? = null
        val atomicFile = AtomicFile(file)
        try {
            output = atomicFile.startWrite()
            output.write(json.encodeToString(document).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
            output = null
        } catch (e: Exception) {
            output?.let { atomicFile.failWrite(it) }
            throw e
        }
    }

    private fun documentFileFor(srtFile: File): File =
        File(srtFile.parentFile, "${srtFile.nameWithoutExtension}.document.json")

    private fun mergeOriginalTextByTimeline(
        translatedDoc: SubtitleDocument,
        originalDoc: SubtitleDocument
    ): Boolean {
        if (translatedDoc.isEmpty || originalDoc.isEmpty) return false
        var matched = 0

        translatedDoc.items.forEachIndexed { index, translated ->
            val original = originalDoc.items.getOrNull(index)?.takeIf {
                kotlin.math.abs(it.startMs - translated.startMs) <= 500L
            } ?: originalDoc.items.minByOrNull { kotlin.math.abs(it.startMs - translated.startMs) }?.takeIf {
                kotlin.math.abs(it.startMs - translated.startMs) <= 500L
            }

            if (original != null) {
                translated.originalText = original.originalText
                translated.normalizeTranslation()
                matched++
            }
        }
        // Chỉ chấp nhận khi phục hồi được gần như toàn bộ, tránh ghép nhầm track.
        return matched >= (translatedDoc.size * 0.95f).toInt()
    }

    companion object {
        private const val TAG = "HistoryRepository"
        private val fileLock = Any()
        private val _historyFlow = MutableStateFlow<List<HistoryItem>>(emptyList())
        val historyFlow: StateFlow<List<HistoryItem>> = _historyFlow.asStateFlow()
        private var isInitialized = false
    }
}
