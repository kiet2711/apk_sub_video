package com.capcut.capsub

import com.capcut.capsub.domain.media.NetworkHeaderHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkHeaderHelperTest {

    @Test
    fun testIsRemoteUrl() {
        assertTrue(NetworkHeaderHelper.isRemoteUrl("https://upos-sz-mirrorcos.bilivideo.com/test.mp4"))
        assertTrue(NetworkHeaderHelper.isRemoteUrl("http://example.com/video.mp4"))
        assertFalse(NetworkHeaderHelper.isRemoteUrl("content://media/external/video/123"))
        assertFalse(NetworkHeaderHelper.isRemoteUrl("/storage/emulated/0/Download/video.mp4"))
        assertFalse(NetworkHeaderHelper.isRemoteUrl(null))
        assertFalse(NetworkHeaderHelper.isRemoteUrl(""))
    }

    @Test
    fun testBilibiliHeaders() {
        val biliUrl = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/64/18/41962701864/41962701864-1-192.mp4?e=123"
        val headers = NetworkHeaderHelper.getHeadersForUrl(biliUrl)

        assertTrue(headers.containsKey("Referer"))
        assertEquals("https://www.bilibili.com/", headers["Referer"])
        assertEquals("https://www.bilibili.com", headers["Origin"])
        assertTrue(headers.containsKey("User-Agent"))
    }

    @Test
    fun testDouyinHeaders() {
        val douyinUrl = "https://www.douyin.com/video/123456"
        val headers = NetworkHeaderHelper.getHeadersForUrl(douyinUrl)

        assertTrue(headers.containsKey("Referer"))
        assertEquals("https://www.douyin.com/", headers["Referer"])
    }

    @Test
    fun testDefaultHeaders() {
        val normalUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        val headers = NetworkHeaderHelper.getHeadersForUrl(normalUrl)

        assertFalse(headers.containsKey("Referer"))
        assertTrue(headers.containsKey("User-Agent"))
    }

    @Test
    fun testExtractCleanUrl() {
        val shareText = "【免费观看【借月之女扮男装都藏不住万人迷】傅筱意外进入一款乙女游戏】 https://b23.tv/abc1234"
        val clean = NetworkHeaderHelper.extractCleanUrl(shareText)
        assertEquals("https://b23.tv/abc1234", clean)

        val bvText = "Check this video BV1kS8H6VERt now"
        assertEquals("https://www.bilibili.com/video/BV1kS8H6VERt", NetworkHeaderHelper.extractCleanUrl(bvText))

        val shortB23 = "b23.tv/xyz789"
        assertEquals("https://b23.tv/xyz789", NetworkHeaderHelper.extractCleanUrl(shortB23))
    }
}
