package com.capcut.capsub

import com.capcut.capsub.data.model.HistoryItem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun historyItemListCanBeSavedAndLoaded() {
        val expected = listOf(
            HistoryItem(
                id = "history-1",
                videoUri = "content://media/video/1",
                videoName = "video.mp4",
                durationMs = 12_345L,
                srtFilePath = "/data/user/0/com.capcut.capsub/files/sub.srt",
                sentenceCount = 10,
                translationEngine = "capcut",
                sourceLanguage = "zh-CN",
                createdAt = 123L
            )
        )

        val encoded = json.encodeToString(expected)
        val decoded = json.decodeFromString<List<HistoryItem>>(encoded)

        assertEquals(expected, decoded)
    }
}
