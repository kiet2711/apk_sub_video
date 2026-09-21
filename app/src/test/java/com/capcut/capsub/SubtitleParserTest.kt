package com.capcut.capsub

import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.model.SubtitleItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {

    @Test
    fun testParseAndExportSrt() {
        val srtSample = """
            1
            00:00:01,500 --> 00:00:04,200
            Xin chào thế giới

            2
            00:00:05,000 --> 00:00:08,800
            Đây là ứng dụng CapSub Studio
        """.trimIndent()

        val doc = SubtitleDocument.parseSrt(srtSample)
        assertEquals(2, doc.size)

        assertEquals(1500L, doc.items[0].startMs)
        assertEquals(4200L, doc.items[0].endMs)
        assertEquals("Xin chào thế giới", doc.items[0].originalText)

        assertEquals(5000L, doc.items[1].startMs)
        assertEquals(8800L, doc.items[1].endMs)
        assertEquals("Đây là ứng dụng CapSub Studio", doc.items[1].originalText)

        val exported = doc.toSrtString("original")
        assertTrue(exported.contains("00:00:01,500 --> 00:00:04,200"))
        assertTrue(exported.contains("Xin chào thế giới"))
    }

    @Test
    fun testBlackBoxAssExport() {
        val doc = SubtitleDocument(
            mutableListOf(
                SubtitleItem(
                    id = 1,
                    startMs = 1500L,
                    endMs = 4200L,
                    originalText = "你好世界",
                    translatedText = "Xin chào thế giới"
                )
            )
        )

        val ass = doc.toAssString("translated")
        assertTrue(ass.contains("Style: BlackBox"))
        assertTrue(ass.contains("Dialogue: 0,0:00:01.50,0:00:04.20,BlackBox,,0,0,0,,Xin chào thế giới"))
    }

    @Test
    fun testActiveSubtitleLookup() {
        val doc = SubtitleDocument(
            mutableListOf(
                SubtitleItem(1, 1000L, 3000L, "Câu 1"),
                SubtitleItem(2, 4000L, 7000L, "Câu 2")
            )
        )

        assertEquals("Câu 1", doc.getActiveItem(2000L)?.originalText)
        assertEquals(null, doc.getActiveItem(3500L))
        assertEquals("Câu 2", doc.getActiveItem(5500L)?.originalText)
    }
}
