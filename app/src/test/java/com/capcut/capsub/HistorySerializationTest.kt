package com.capcut.capsub

import com.capcut.capsub.data.model.HistoryItem
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.model.SubtitleItem
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
                ttsVoice = "ICL_uranus_vi_female_yuenan1",
                docKey = "abc123md5",
                createdAt = 123L
            )
        )

        val encoded = json.encodeToString(expected)
        val decoded = json.decodeFromString<List<HistoryItem>>(encoded)

        assertEquals(expected, decoded)
    }

    @Test
    fun legacyHistoryJsonWithoutTtsVoiceAndDocKeyCanBeLoaded() {
        val legacyJson = """
            [
              {
                "id": "history-legacy",
                "videoUri": "content://media/video/2",
                "videoName": "legacy.mp4",
                "durationMs": 5000,
                "srtFilePath": "/path/sub.srt",
                "sentenceCount": 5,
                "translationEngine": "gemini-1.5-flash",
                "sourceLanguage": "zh-CN",
                "createdAt": 1000
              }
            ]
        """.trimIndent()

        val decoded = json.decodeFromString<List<HistoryItem>>(legacyJson)
        assertEquals(1, decoded.size)
        assertEquals("history-legacy", decoded[0].id)
        assertEquals(null, decoded[0].ttsVoice)
        assertEquals(null, decoded[0].docKey)
    }

    @Test
    fun bilingualSubtitleDocumentCanBeSavedAndLoadedWithoutLosingEitherLanguage() {
        val expected = SubtitleDocument(
            mutableListOf(
                SubtitleItem(
                    id = 1,
                    startMs = 40L,
                    endMs = 2_560L,
                    originalText = "为了摆脱被囚禁的必死结局",
                    translatedText = "Để thoát khỏi cái kết chết chóc bị giam cầm."
                )
            )
        )

        val encoded = json.encodeToString(expected)
        val decoded = json.decodeFromString<SubtitleDocument>(encoded)

        assertEquals(expected, decoded)
    }
}
