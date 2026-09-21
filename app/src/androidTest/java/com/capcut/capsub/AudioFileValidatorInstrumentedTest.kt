package com.capcut.capsub

import androidx.test.core.app.ApplicationProvider
import com.capcut.capsub.domain.tts.AudioFileValidator
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFileValidatorInstrumentedTest {

    @Test
    fun rejectsMissingTinyAndCorruptAudioFiles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testDir = File(context.cacheDir, "audio_validator_test").apply { mkdirs() }
        val missing = File(testDir, "missing.mp3")
        val tiny = File(testDir, "tiny.mp3").apply { writeBytes(ByteArray(16)) }
        val corrupt = File(testDir, "corrupt.mp3").apply { writeBytes(ByteArray(512) { 0x41 }) }

        try {
            assertFalse(AudioFileValidator.validate(missing).isValid)
            assertFalse(AudioFileValidator.validate(tiny).isValid)
            val corruptResult = AudioFileValidator.validate(corrupt)
            assertFalse(corruptResult.isValid)
            assertTrue(corruptResult.reason.isNotBlank())
        } finally {
            tiny.delete()
            corrupt.delete()
            testDir.delete()
        }
    }
}
