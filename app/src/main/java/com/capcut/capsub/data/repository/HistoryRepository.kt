package com.capcut.capsub.data.repository

import android.content.Context
import android.net.Uri
import com.capcut.capsub.data.model.HistoryItem
import com.capcut.capsub.data.model.SubtitleDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class HistoryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val historyFile = File(context.filesDir, "history_records.json")
    private val subtitlesDir = File(context.filesDir, "saved_subtitles").apply { mkdirs() }

    init {
        if (!isInitialized) {
            _historyFlow.value = getHistoryList()
            isInitialized = true
        }
    }

    fun refreshHistory() {
        _historyFlow.value = getHistoryList()
    }

    @Synchronized
    fun getHistoryList(): List<HistoryItem> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val content = historyFile.readText(Charsets.UTF_8)
            json.decodeFromString<List<HistoryItem>>(content).sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveHistory(
        videoUri: Uri,
        videoName: String,
        durationMs: Long,
        document: SubtitleDocument,
        translationEngine: String,
        sourceLanguage: String
    ): HistoryItem {
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
        return item
    }

    @Synchronized
    fun deleteHistory(id: String): Boolean {
        val currentList = getHistoryList().toMutableList()
        val item = currentList.firstOrNull { it.id == id } ?: return false
        try {
            File(item.srtFilePath).delete()
        } catch (ignored: Exception) {}

        currentList.removeAll { it.id == id }
        saveList(currentList)
        return true
    }

    fun loadSubtitleDocument(item: HistoryItem): SubtitleDocument {
        val srtFile = File(item.srtFilePath)
        if (!srtFile.exists()) return SubtitleDocument()
        val content = srtFile.readText(Charsets.UTF_8)
        return SubtitleDocument.parseSrt(content)
    }

    @Synchronized
    fun updateSubtitleForUri(videoUri: Uri, document: SubtitleDocument) {
        val list = getHistoryList().toMutableList()
        val item = list.firstOrNull { it.videoUri == videoUri.toString() } ?: return
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
        } catch (ignored: Exception) {}
    }

    private fun saveList(list: List<HistoryItem>) {
        try {
            val content = json.encodeToString(list)
            historyFile.writeText(content, Charsets.UTF_8)
            _historyFlow.value = list
        } catch (ignored: Exception) {}
    }

    companion object {
        private val _historyFlow = MutableStateFlow<List<HistoryItem>>(emptyList())
        val historyFlow: StateFlow<List<HistoryItem>> = _historyFlow.asStateFlow()
        private var isInitialized = false
    }
}
