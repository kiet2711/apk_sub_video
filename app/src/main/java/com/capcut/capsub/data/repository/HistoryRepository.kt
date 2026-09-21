package com.capcut.capsub.data.repository

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import com.capcut.capsub.data.model.HistoryItem
import com.capcut.capsub.data.model.SubtitleDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        document.saveToFile(srtFile, mode = "translated")

        val item = HistoryItem(
            id = id,
            videoUri = videoUri.toString(),
            videoName = if (videoName.isNotBlank()) videoName else existing?.videoName ?: "Video",
            durationMs = if (durationMs > 0) durationMs else existing?.durationMs ?: 0L,
            srtFilePath = srtFile.absolutePath,
            sentenceCount = document.size,
            translationEngine = translationEngine,
            sourceLanguage = sourceLanguage,
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
        try {
            File(item.srtFilePath).delete()
        } catch (e: Exception) {
            Log.w(TAG, "Không thể xoá file phụ đề ${item.srtFilePath}", e)
        }

        currentList.removeAll { it.id == id }
        saveList(currentList)
        true
    }

    fun loadSubtitleDocument(item: HistoryItem): SubtitleDocument {
        val srtFile = File(item.srtFilePath)
        if (!srtFile.exists()) return SubtitleDocument()
        val content = srtFile.readText(Charsets.UTF_8)
        val doc = SubtitleDocument.parseSrt(content)
        com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(context, doc)
        return doc
    }

    fun updateSubtitleForUri(videoUri: Uri, document: SubtitleDocument) = synchronized(fileLock) {
        val list = getHistoryList().toMutableList()
        val item = list.firstOrNull { it.videoUri == videoUri.toString() } ?: return@synchronized
        try {
            val srtFile = File(item.srtFilePath)
            document.saveToFile(srtFile, mode = "translated")
            val updated = item.copy(
                sentenceCount = document.size,
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

    companion object {
        private const val TAG = "HistoryRepository"
        private val fileLock = Any()
        private val _historyFlow = MutableStateFlow<List<HistoryItem>>(emptyList())
        val historyFlow: StateFlow<List<HistoryItem>> = _historyFlow.asStateFlow()
        private var isInitialized = false
    }
}
