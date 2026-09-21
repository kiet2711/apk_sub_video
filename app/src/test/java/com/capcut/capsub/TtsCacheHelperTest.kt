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
}
