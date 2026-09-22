package com.capcut.capsub

import com.capcut.capsub.domain.media.BilibiliResolver
import com.capcut.capsub.domain.media.BilibiliVideoCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliResolverTest {

    @Test
    fun testIsBilibiliUrl() {
        assertTrue(BilibiliResolver.isBilibiliUrl("https://www.bilibili.com/video/BV1kS8H6VERt"))
        assertTrue(BilibiliResolver.isBilibiliUrl("https://b23.tv/abcXYZ"))
        assertTrue(BilibiliResolver.isBilibiliUrl("https://upos-sz-mirrorcos.bilivideo.com/test.mp4"))
        assertTrue(BilibiliResolver.isBilibiliUrl("BV1kS8H6VERt"))
        assertTrue(BilibiliResolver.isBilibiliPageUrl("https://www.bilibili.com/video/BV1kS8H6VERt"))
        assertTrue(!BilibiliResolver.isBilibiliPageUrl("https://upos-sz-mirrorcos.bilivideo.com/test.m4s"))
    }

    @Test
    fun testResolveBvUrl() = runBlocking {
        val target = BilibiliResolver.resolveUrl("https://www.bilibili.com/video/BV1kS8H6VERt?p=2")
        assertEquals("BV1kS8H6VERt", target.bvid)
        assertEquals(2, target.pageIndex)
    }

    @Test
    fun testDashTrackSelectionPrefersSupportedAvc() {
        val candidates = listOf(
            BilibiliVideoCandidate(
                url = "https://cdn.example/4k-av1.m4s",
                qualityId = 120,
                codecId = 13,
                codecs = "av01.0.12M.10",
                bandwidth = 9_000_000L
            ),
            BilibiliVideoCandidate(
                url = "https://cdn.example/1080-avc.m4s",
                qualityId = 80,
                codecId = 7,
                codecs = "avc1.640032",
                bandwidth = 4_000_000L
            ),
            BilibiliVideoCandidate(
                url = "https://cdn.example/4k-avc.m4s",
                qualityId = 120,
                codecId = 7,
                codecs = "avc1.640033",
                bandwidth = 12_000_000L
            )
        )

        val selected = BilibiliResolver.selectBestVideoCandidate(candidates)

        assertNotNull(selected)
        assertEquals("https://cdn.example/1080-avc.m4s", selected?.url)
    }
}
