package com.capcut.capsub

import com.capcut.capsub.domain.media.BilibiliResolver
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
    }

    @Test
    fun testResolveBvUrl() = runBlocking {
        val target = BilibiliResolver.resolveUrl("https://www.bilibili.com/video/BV1kS8H6VERt?p=2")
        assertEquals("BV1kS8H6VERt", target.bvid)
        assertEquals(2, target.pageIndex)
    }
}
