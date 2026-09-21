package com.capcut.capsub.data.api

import com.capcut.capsub.data.model.DeviceConfig
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Module bảo mật, mã hóa chữ ký HTTP và AWS SigV4 cho CapCut Cloud & ByteDance VOD API.
 * Port trực tiếp từ signer.py của tool PC.
 */
object CapCutSigner {

    const val VOD_REGION = "sdwdmwlll"
    const val VOD_SERVICE = "vod"
    const val BASE_URL = "https://editor-api-sg.capcutapi.com"

    fun md5(input: String): String = md5(input.toByteArray(StandardCharsets.UTF_8))

    fun md5(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun makeSignHeader(url: String, appvr: String, deviceTime: String, tdid: String): String {
        val path = url.split("?")[0]
        val pathSuffix = if (path.length >= 7) path.takeLast(7) else path
        val signStr = "9e2c|$pathSuffix|3|$appvr|$deviceTime|$tdid|11ac"
        return md5(signStr)
    }

    fun makeSsStub(bodyText: String): String {
        return md5(bodyText)
    }

    fun makeTraceId(): String {
        val seed = UUID.randomUUID().toString().replace("-", "").take(32)
        return "00-$seed-${seed.take(16)}-01"
    }

    fun crc32Hex(data: ByteArray): String {
        val crc = CRC32()
        crc.update(data)
        return String.format("%08x", crc.value)
    }

    fun fileMd5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(1024 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun getUtcDatesForVod(): Pair<String, String> {
        val now = Date()
        val isoFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val httpFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return Pair(isoFormat.format(now), httpFormat.format(now))
    }

    private fun aws4SigningKey(
        secretAccessKey: String,
        dateStamp: String,
        region: String = VOD_REGION,
        service: String = VOD_SERVICE
    ): ByteArray {
        val kDate = hmacSha256(("AWS4$secretAccessKey").toByteArray(StandardCharsets.UTF_8), dateStamp.toByteArray(StandardCharsets.UTF_8))
        val kRegion = hmacSha256(kDate, region.toByteArray(StandardCharsets.UTF_8))
        val kService = hmacSha256(kRegion, service.toByteArray(StandardCharsets.UTF_8))
        return hmacSha256(kService, "aws4_request".toByteArray(StandardCharsets.UTF_8))
    }

    private fun canonicalQuery(urlStr: String): String {
        val uri = URI(urlStr)
        val query = uri.rawQuery ?: return ""
        val pairs = query.split("&").mapNotNull {
            val idx = it.indexOf("=")
            if (idx != -1) {
                Pair(it.substring(0, idx), it.substring(idx + 1))
            } else null
        }.sortedBy { it.first }

        return pairs.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
    }

    /**
     * Tạo header Authorization chuẩn AWS SigV4 cho ByteDance VOD
     */
    fun aws4Authorization(
        method: String,
        url: String,
        body: ByteArray,
        accessKeyId: String,
        secretAccessKey: String,
        sessionToken: String,
        amzDate: String
    ): String {
        val dateStamp = amzDate.take(8)
        val scope = "$dateStamp/$VOD_REGION/$VOD_SERVICE/aws4_request"
        val signedHeaders = "x-amz-date;x-amz-security-token"
        val canonicalHeaders = "x-amz-date:$amzDate\nx-amz-security-token:$sessionToken\n"
        val uri = URI(url)
        val path = uri.rawPath

        val canonicalRequest = listOf(
            method,
            path,
            canonicalQuery(url),
            canonicalHeaders,
            signedHeaders,
            sha256Hex(body)
        ).joinToString("\n")

        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            scope,
            sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
        ).joinToString("\n")

        val signingKey = aws4SigningKey(secretAccessKey, dateStamp)
        val signatureBytes = hmacSha256(signingKey, stringToSign.toByteArray(StandardCharsets.UTF_8))
        val signature = signatureBytes.joinToString("") { "%02x".format(it) }

        return "AWS4-HMAC-SHA256 Credential=$accessKeyId/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
    }

    /**
     * Header cơ bản bắt buộc khi gửi request lên CapCut Cloud API
     */
    fun buildBaseHeaders(device: DeviceConfig, bodyText: String, appid: Boolean = false): MutableMap<String, String> {
        val now = (System.currentTimeMillis() / 1000).toString()
        val headers = mutableMapOf(
            "content-type" to "application/json",
            "appvr" to device.appvr,
            "ch" to device.channel,
            "device-time" to now,
            "lan" to device.lan,
            "loc" to device.loc,
            "pf" to device.pf,
            "sign-ver" to "1",
            "tdid" to device.tdid,
            "x-ss-stub" to makeSsStub(bodyText),
            "x-ss-dp" to device.aid,
            "x-khronos" to now,
            "x-tt-trace-id" to makeTraceId(),
            "user-agent" to "Cronet/TTNetVersion:1d7cc3b1 2025-07-16 QuicVersion:52c2b40d 2025-04-03",
            "store-country-code" to device.loc.lowercase(),
            "store-country-code-src" to "did",
            "is-dispatch-us-ttp" to "0",
            "is-app-region-us-ttp" to "0"
        )
        if (appid) {
            headers["app-sdk-version"] = device.appvr
            headers["appid"] = device.aid
        }
        return headers
    }

    /**
     * Đọc và tự động giải nén GZIP nếu response trả về dữ liệu nén
     */
    fun decompressResponseBody(response: okhttp3.Response): String {
        val body = response.body ?: return ""
        val bytes = body.bytes()
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(bytes)).use { gis ->
                return gis.bufferedReader(StandardCharsets.UTF_8).readText()
            }
        }
        return String(bytes, StandardCharsets.UTF_8)
    }
}
