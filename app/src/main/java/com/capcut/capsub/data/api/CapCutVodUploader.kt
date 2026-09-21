package com.capcut.capsub.data.api

import com.capcut.capsub.data.model.DeviceConfig
import com.capcut.capsub.data.model.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Quản lý quy trình tải tệp âm thanh/video lên ByteDance VOD Space của CapCut bằng chữ ký AWS SigV4.
 * Port từ uploader.py của tool PC.
 */
class CapCutVodUploader(
    private val device: DeviceConfig = DeviceConfig(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
) {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    /**
     * Tải file media lên CapCut VOD theo từng chunk 5MB kèm mã kiểm tra CRC32.
     * Sử dụng luồng đọc tuần tự để đảm bảo RAM chỉ tốn ~5MB cho mọi kích thước file.
     */
    suspend fun uploadFile(
        file: File,
        progressCallback: ((Float, String) -> Unit)? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            throw IOException("Không tìm thấy tệp: ${file.absolutePath}")
        }

        val localMd5 = CapCutSigner.fileMd5(file)
        val fileSize = file.length()

        // 1. Upload Sign
        progressCallback?.invoke(0.05f, "Đang xác thực bảo mật VOD...")
        val (signUrl, signHeaders, signBody) = buildUploadSignRequest()
        val signRequest = Request.Builder()
            .url(signUrl)
            .post(signBody.toRequestBody("application/json".toMediaType()))
            .apply { signHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        val signResp = client.newCall(signRequest).execute()
        val signJsonStr = CapCutSigner.decompressResponseBody(signResp)
        val signJson = jsonParser.parseToJsonElement(signJsonStr.ifBlank { "{}" }).jsonObject
        val creds = signJson["data"]?.jsonObject ?: throw IOException("Lỗi upload_sign: $signJsonStr")

        val domain = creds["domain"]?.jsonPrimitive?.content ?: ""
        val accessKeyId = creds["access_key_id"]?.jsonPrimitive?.content ?: ""
        val secretAccessKey = creds["secret_access_key"]?.jsonPrimitive?.content ?: ""
        val sessionToken = creds["session_token"]?.jsonPrimitive?.content ?: ""
        val spaceName = creds["space_name"]?.jsonPrimitive?.content ?: ""

        // 2. Apply Upload Inner
        progressCallback?.invoke(0.15f, "Đang xin cấp phép đường truyền tải lên...")
        val applyUrl = "https://$domain/top/v1?Action=ApplyUploadInner&SpaceName=$spaceName&UseQuic=false&Version=2020-11-19&device_platform=mac"
        val (amzDate, httpDate) = CapCutSigner.getUtcDatesForVod()
        val applyAuth = CapCutSigner.aws4Authorization("GET", applyUrl, ByteArray(0), accessKeyId, secretAccessKey, sessionToken, amzDate)

        val applyRequest = Request.Builder()
            .url(applyUrl)
            .get()
            .addHeader("Authorization", applyAuth)
            .addHeader("Date", httpDate)
            .addHeader("X-Amz-Date", amzDate)
            .addHeader("X-Amz-Expires", "31536000")
            .addHeader("X-Amz-Security-Token", sessionToken)
            .addHeader("accept-encoding", "identity")
            .addHeader("store-country-code", device.loc.lowercase())
            .addHeader("tdid", device.tdid)
            .addHeader("pf", device.pf)
            .build()

        val applyResp = client.newCall(applyRequest).execute()
        val applyJsonStr = CapCutSigner.decompressResponseBody(applyResp)
        val applyJson = jsonParser.parseToJsonElement(applyJsonStr.ifBlank { "{}" }).jsonObject
        val resultObj = applyJson["Result"]?.jsonObject ?: throw IOException("Lỗi ApplyUploadInner: $applyJsonStr")
        val uploadNodes = resultObj["InnerUploadAddress"]?.jsonObject?.get("UploadNodes")?.jsonArray
        val firstNode = uploadNodes?.getOrNull(0)?.jsonObject ?: throw IOException("Không tìm thấy UploadNode")

        val storeInfos = firstNode["StoreInfos"]?.jsonArray?.getOrNull(0)?.jsonObject ?: throw IOException("Không tìm thấy StoreInfos")
        val uploadHost = firstNode["UploadHost"]?.jsonPrimitive?.content ?: ""
        val sessionKey = firstNode["SessionKey"]?.jsonPrimitive?.content ?: ""
        val storeUri = storeInfos["StoreUri"]?.jsonPrimitive?.content ?: ""
        val uploadId = storeInfos["UploadID"]?.jsonPrimitive?.content ?: ""
        val uploadAuth = storeInfos["Auth"]?.jsonPrimitive?.content ?: ""
        val defaultVid = firstNode["Vid"]?.jsonPrimitive?.content ?: ""

        // 3. Transfer binary in 5MB chunks
        val chunkSize = 5 * 1024 * 1024 // 5MB
        val partCrcs = mutableListOf<String>()
        val totalParts = ((fileSize + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)

        FileInputStream(file).use { fis ->
            val buffer = ByteArray(chunkSize)
            var partIndex = 0
            var bytesRead: Int

            while (fis.read(buffer).also { bytesRead = it } != -1) {
                val chunkData = if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead)
                val chunkCrc32 = CapCutSigner.crc32Hex(chunkData)
                partCrcs.add("$partIndex:$chunkCrc32")

                val transferUrl = "https://$uploadHost/upload/v1/$storeUri?uploadid=$uploadId&part_number=$partIndex&phase=transfer"
                val transferRequest = Request.Builder()
                    .url(transferUrl)
                    .post(chunkData.toRequestBody("application/octet-stream".toMediaType()))
                    .addHeader("Authorization", uploadAuth)
                    .addHeader("Date", CapCutSigner.getUtcDatesForVod().second)
                    .addHeader("X-Upload-Content-CRC32", chunkCrc32)
                    .addHeader("store-country-code", device.loc.lowercase())
                    .addHeader("tdid", device.tdid)
                    .addHeader("pf", device.pf)
                    .build()

                val transferResp = client.newCall(transferRequest).execute()
                if (!transferResp.isSuccessful) {
                    throw IOException("Lỗi tải phân đoạn $partIndex: HTTP ${transferResp.code}")
                }

                partIndex++
                val uploadPct = 0.20f + (partIndex.toFloat() / totalParts) * 0.60f
                progressCallback?.invoke(uploadPct, "Đang tải lên CapCut Cloud: ${(uploadPct * 100).toInt()}% ($partIndex/$totalParts)")
            }
        }

        // 4. Finish upload
        val finishUrl = "https://$uploadHost/upload/v1/$storeUri?uploadmode=part&phase=finish&uploadid=$uploadId"
        val finishBodyText = partCrcs.joinToString(",")
        val finishRequest = Request.Builder()
            .url(finishUrl)
            .post(finishBodyText.toRequestBody("text/plain".toMediaType()))
            .addHeader("Authorization", uploadAuth)
            .addHeader("Date", CapCutSigner.getUtcDatesForVod().second)
            .addHeader("store-country-code", device.loc.lowercase())
            .addHeader("tdid", device.tdid)
            .addHeader("pf", device.pf)
            .build()

        val finishResp = client.newCall(finishRequest).execute()
        if (!finishResp.isSuccessful) {
            throw IOException("Lỗi hoàn tất tải lên (finish): HTTP ${finishResp.code}")
        }

        // 5. Commit Upload Inner
        progressCallback?.invoke(0.85f, "Đang xác nhận lưu trữ tệp tin...")
        val commitUrl = "https://$domain/top/v1?Action=CommitUploadInner&SpaceName=$spaceName&Version=2020-11-19&device_platform=mac"
        val commitBody = """{"Functions":[{"Input":{"SnapshotTime":0.0},"Name":"Snapshot"}],"SessionKey":"$sessionKey"}"""
        val (commitAmzDate, commitHttpDate) = CapCutSigner.getUtcDatesForVod()
        val commitAuth = CapCutSigner.aws4Authorization("POST", commitUrl, commitBody.toByteArray(), accessKeyId, secretAccessKey, sessionToken, commitAmzDate)

        val commitRequest = Request.Builder()
            .url(commitUrl)
            .post(commitBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", commitAuth)
            .addHeader("Date", commitHttpDate)
            .addHeader("X-Amz-Date", commitAmzDate)
            .addHeader("X-Amz-Expires", "31536000")
            .addHeader("X-Amz-Security-Token", sessionToken)
            .addHeader("store-country-code", device.loc.lowercase())
            .addHeader("tdid", device.tdid)
            .addHeader("pf", device.pf)
            .build()

        val commitResp = client.newCall(commitRequest).execute()
        val commitJsonStr = CapCutSigner.decompressResponseBody(commitResp)
        val commitJson = jsonParser.parseToJsonElement(commitJsonStr.ifBlank { "{}" }).jsonObject
        val commitResult = commitJson["Result"]?.jsonObject?.get("Results")?.jsonArray?.getOrNull(0)?.jsonObject

        val finalVid = commitResult?.get("Vid")?.jsonPrimitive?.content ?: defaultVid
        val videoMeta = commitResult?.get("VideoMeta")?.jsonObject
        val durationSec = videoMeta?.get("Duration")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val finalMd5 = videoMeta?.get("Md5")?.jsonPrimitive?.content ?: localMd5

        progressCallback?.invoke(0.95f, "Tải lên thành công!")

        return@withContext UploadResult(
            vid = finalVid,
            md5 = finalMd5,
            durationMs = (durationSec * 1000).toLong(),
            size = fileSize,
            storeUri = storeUri
        )
    }

    private fun buildUploadSignRequest(): Triple<String, Map<String, String>, String> {
        val body = """{"biz":"cc_pc_text_recognize","key_version":"v5"}"""
        val path = "/lv/v1/upload_sign"
        val queryParams = device.toQueryMap(includeRegion = false)
            .map { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
            .joinToString("&")
        val fullUrl = "${CapCutSigner.BASE_URL}$path?$queryParams"

        val headers = CapCutSigner.buildBaseHeaders(device, body, appid = true)
        val sign = CapCutSigner.makeSignHeader(fullUrl, device.appvr, headers["device-time"] ?: "", device.tdid)
        headers["sign"] = sign

        return Triple(fullUrl, headers, body)
    }
}
