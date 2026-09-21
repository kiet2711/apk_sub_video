package com.capcut.capsub

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.model.SubtitleItem
import com.capcut.capsub.domain.tts.AudioFileValidator
import com.capcut.capsub.player.TtsAudioScheduler
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TtsAudioSchedulerInstrumentedTest {

    @Test
    fun originalAndAiAudioCanPlayAtTheSameTime() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wavFile = File(context.cacheDir, "scheduler_test.wav")
        writeTestWav(wavFile)
        assertTrue(AudioFileValidator.validate(wavFile).isValid)

        val item = SubtitleItem(
            id = 1,
            startMs = 0L,
            endMs = 2_000L,
            originalText = "test",
            translatedText = "kiểm tra",
            audioFilePath = wavFile.absolutePath,
            audioDurationMs = 1_000L
        )
        lateinit var scheduler: TtsAudioScheduler
        lateinit var originalPlayer: ExoPlayer
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            originalPlayer = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(wavFile)))
                prepare()
                play()
            }
            scheduler = TtsAudioScheduler(context)
            scheduler.setSubtitleDocument(SubtitleDocument(mutableListOf(item)))
            scheduler.setAiVolume(1f)
            scheduler.onVideoPositionUpdate(0L, true)
        }

        var started = false
        var originalStarted = false
        repeat(40) {
            if (scheduler.hasStartedItem(1)) {
                started = true
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    originalStarted = originalPlayer.isPlaying
                }
                return@repeat
            }
            Thread.sleep(50L)
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            scheduler.release()
            originalPlayer.release()
        }
        wavFile.delete()
        assertTrue("TTS ExoPlayer không chuyển sang trạng thái phát", started)
        assertTrue("Audio gốc bị dừng khi audio AI bắt đầu", originalStarted)
    }

    private fun writeTestWav(file: File) {
        val sampleRate = 24_000
        val sampleCount = sampleRate
        val pcmSize = sampleCount * 2
        FileOutputStream(file).use { output ->
            output.write("RIFF".toByteArray())
            writeLeInt(output, 36 + pcmSize)
            output.write("WAVEfmt ".toByteArray())
            writeLeInt(output, 16)
            writeLeShort(output, 1)
            writeLeShort(output, 1)
            writeLeInt(output, sampleRate)
            writeLeInt(output, sampleRate * 2)
            writeLeShort(output, 2)
            writeLeShort(output, 16)
            output.write("data".toByteArray())
            writeLeInt(output, pcmSize)
            repeat(sampleCount) { index ->
                val sample = (sin(2.0 * PI * 440.0 * index / sampleRate) * 8_000).toInt()
                writeLeShort(output, sample)
            }
        }
    }

    private fun writeLeInt(output: FileOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write(value shr 8 and 0xFF)
        output.write(value shr 16 and 0xFF)
        output.write(value shr 24 and 0xFF)
    }

    private fun writeLeShort(output: FileOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write(value shr 8 and 0xFF)
    }
}
