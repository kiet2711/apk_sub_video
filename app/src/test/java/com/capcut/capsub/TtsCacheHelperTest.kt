package com.capcut.capsub

import com.capcut.capsub.domain.tts.TtsCacheHelper
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsCacheHelperTest {

    @Test
    fun preferredVoiceDirectoryIsSelectedEvenWhenFirstSentenceIsMissing() {
        val root = createTempDirectory("tts_cache_test_").toFile()
        try {
            val voiceDir = File(root, "voice_a").apply { mkdirs() }
            File(voiceDir, "sub_2.mp3").writeBytes(ByteArray(256) { 1 })

            val selected = TtsCacheHelper.selectTargetDirectory(root, "voice_a")

            assertEquals(voiceDir.canonicalPath, selected.canonicalPath)
            assertFalse(File(voiceDir, "sub_1.mp3").exists())
            assertTrue(File(selected, "sub_2.mp3").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun preferredVoiceDoesNotFallBackToAnotherVoice() {
        val root = createTempDirectory("tts_cache_test_").toFile()
        try {
            val preferred = File(root, "preferred").apply { mkdirs() }
            val other = File(root, "other").apply { mkdirs() }
            File(other, "sub_1.mp3").writeBytes(ByteArray(256) { 1 })

            val selected = TtsCacheHelper.selectTargetDirectory(root, "preferred")

            assertEquals(preferred.canonicalPath, selected.canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun docKeyIsIdenticalBetweenRamDocumentAndSrtParsedDocument() {
        val ramDoc = com.capcut.capsub.data.model.SubtitleDocument(
            mutableListOf(
                com.capcut.capsub.data.model.SubtitleItem(
                    id = 1,
                    startMs = 1000,
                    endMs = 3000,
                    originalText = "哈喽大家好",
                    translatedText = "Xin chào các bạn"
                ),
                com.capcut.capsub.data.model.SubtitleItem(
                    id = 2,
                    startMs = 3200,
                    endMs = 5000,
                    originalText = "再见",
                    translatedText = "Tạm biệt nhé"
                )
            )
        )

        val srtContent = ramDoc.toSrtString(mode = "translated")
        val srtDoc = com.capcut.capsub.data.model.SubtitleDocument.parseSrt(srtContent)

        val keyFromRam = TtsCacheHelper.getDocKey(ramDoc)
        val keyFromSrt = TtsCacheHelper.getDocKey(srtDoc)

        assertEquals("Khóa docKey giữa RAM và khi nạp lại từ SRT phải trùng nhau!", keyFromRam, keyFromSrt)
    }
}
