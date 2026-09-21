package com.capcut.capsub

import com.capcut.capsub.data.api.CapCutSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapCutSignerTest {

    @Test
    fun testMd5() {
        val input = "hello world"
        val expected = "5eb63bbbe01eeed093cb22bb8f5acdc3"
        assertEquals(expected, CapCutSigner.md5(input))
    }

    @Test
    fun testMakeSignHeader() {
        val url = "https://editor-api-sg.capcutapi.com/lv/v1/upload_sign?device_id=123"
        val appvr = "8.7.0"
        val deviceTime = "1720000000"
        val tdid = "7647145645564632872"

        val sign = CapCutSigner.makeSignHeader(url, appvr, deviceTime, tdid)
        // MD5 phải có độ dài đúng 32 ký tự hex
        assertEquals(32, sign.length)
        assertTrue(sign.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun testMakeSsStub() {
        val body = """{"biz":"cc_pc_text_recognize"}"""
        val stub = CapCutSigner.makeSsStub(body)
        assertEquals(32, stub.length)
        assertEquals(CapCutSigner.md5(body), stub)
    }

    @Test
    fun testMakeTraceId() {
        val traceId = CapCutSigner.makeTraceId()
        assertTrue(traceId.startsWith("00-"))
        assertTrue(traceId.endsWith("-01"))
    }

    @Test
    fun testAws4Authorization() {
        val auth = CapCutSigner.aws4Authorization(
            method = "GET",
            url = "https://example.com/top/v1?Action=ApplyUploadInner",
            body = ByteArray(0),
            accessKeyId = "TEST_KEY_ID",
            secretAccessKey = "TEST_SECRET_KEY",
            sessionToken = "TEST_SESSION_TOKEN",
            amzDate = "20260921T120000Z"
        )

        assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential=TEST_KEY_ID/20260921/sdwdmwlll/vod/aws4_request"))
        assertTrue(auth.contains("SignedHeaders=x-amz-date;x-amz-security-token"))
        assertTrue(auth.contains("Signature="))
    }

    @Test
    fun testMakeTtsPayloadSign() {
        val ssml = "<speak><voice name=\"BV074_streaming\"><prosody rate=\"1.0\">Xin chào</prosody></voice></speak>"
        val extraInfo = "{\"benefit_info\":{}}"
        val deviceId = "1234567890123456789"
        val appId = "359289"

        val sign = CapCutSigner.makeTtsPayloadSign(ssml, extraInfo, deviceId, appId)
        // Chữ ký RSA PKCS#1 v1.5 với khóa 2048-bit luôn có kích thước 256 bytes, mã hóa Base64 là 344 ký tự
        assertTrue(sign.isNotBlank())
        assertEquals(344, sign.length)
    }

    @Test
    fun testVoicePresets() {
        val voices = com.capcut.capsub.data.model.VoicePresets.VIETNAMESE_VOICES
        assertTrue(voices.isNotEmpty())
        assertTrue(voices.any { it.displayName.contains("Dịu Dàng") })
        assertTrue(voices.all { it.voiceType.isNotBlank() && it.resourceId.isNotBlank() })
    }
}
