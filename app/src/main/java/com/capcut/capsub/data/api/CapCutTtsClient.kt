package com.capcut.capsub.data.api

import com.capcut.capsub.data.model.DeviceConfig
import com.capcut.capsub.domain.tts.AudioFileValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class TtsErrorKind {
    PARAMETERS_MODIFIED,
    TASK_FAILED,
    TIMEOUT,
    NETWORK,
    INVALID_AUDIO,
    INVALID_RESPONSE
}

class CapCutTtsException(
    val kind: TtsErrorKind,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Client kết nối và gọi API Text-to-Speech (TTS) của CapCut Cloud.
 * Hỗ trợ tạo giọng đọc đa luồng siêu tốc, bảo mật RSA PKCS#1 v1.5 và tải file âm thanh trực tiếp.
 */
class CapCutTtsClient(
    val device: DeviceConfig = DeviceConfig(),
    private val client: OkHttpClient = sharedClient
) {
    companion object {
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .connectionPool(okhttp3.ConnectionPool(128, 5, TimeUnit.MINUTES))
                .dispatcher(okhttp3.Dispatcher().apply {
                    maxRequests = 200
                    maxRequestsPerHost = 100
                })
                .build()
        }
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }

    /**
     * Tổng hợp giọng nói cho một câu văn bản và lưu trực tiếp ra file đích (MP3).
     * Tự động xoay vòng Device ID và thử lại nếu bị CapCut giới hạn tần suất ("shark block only").
     */
    suspend fun generateSpeechToFile(
        text: String,
        voiceType: String,
        resourceId: String,
        rate: String = "1.0",
        destFile: File,
        timeoutMs: Long = 20_000L,
        maxRetries: Int = 3
    ): File = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            throw IllegalArgumentException("Nội dung câu thoại không được để trống")
        }

        destFile.parentFile?.mkdirs()
        val partFile = File(destFile.parentFile, "${destFile.name}.part")
        if (partFile.exists()) partFile.delete()

        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                executeGenerateSpeech(cleanText, voiceType, resourceId, rate, partFile, timeoutMs)
                val validation = AudioFileValidator.validate(partFile)
                if (!validation.isValid) {
                    throw CapCutTtsException(TtsErrorKind.INVALID_AUDIO, validation.reason)
                }

                if (destFile.exists() && !destFile.delete()) {
                    throw CapCutTtsException(
                        TtsErrorKind.INVALID_AUDIO,
                        "Không thể thay thế file audio cũ: ${destFile.name}"
                    )
                }
                if (!partFile.renameTo(destFile)) {
                    partFile.copyTo(destFile, overwrite = true)
                    partFile.delete()
                }
                AudioFileValidator.invalidate(destFile)
                return@withContext destFile
            } catch (e: Exception) {
                lastException = e
                if (partFile.exists()) partFile.delete()
                android.util.Log.w("CapCutTtsClient", "Lần thử $attempt/$maxRetries cho câu '${cleanText.take(20)}...' thất bại: ${e.message}")
                if (attempt < maxRetries) {
                    device.randomize()
                    val extraDelay = if (e is CapCutTtsException && e.kind == TtsErrorKind.PARAMETERS_MODIFIED) 600L else 0L
                    delay(700L * attempt + extraDelay)
                }
            }
        }
        throw lastException ?: CapCutTtsException(
            TtsErrorKind.INVALID_RESPONSE,
            "Tạo TTS thất bại sau $maxRetries lần thử"
        )
    }

    private suspend fun executeGenerateSpeech(
        cleanText: String,
        voiceType: String,
        resourceId: String,
        rate: String,
        destFile: File,
        timeoutMs: Long
    ): File {
        val bindId = UUID.randomUUID().toString()
        val (createUrl, createHeaders, createBody) = buildCreateTtsRequest(cleanText, voiceType, resourceId, rate, bindId)

        val createReq = Request.Builder()
            .url(createUrl)
            .post(createBody.toRequestBody("application/json".toMediaType()))
            .apply { createHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        val createJsonStr = client.newCall(createReq).execute().use { response ->
            if (!response.isSuccessful) {
                throw CapCutTtsException(TtsErrorKind.NETWORK, "Tạo task TTS lỗi HTTP ${response.code}")
            }
            CapCutSigner.decompressResponseBody(response)
        }
        val createJson = jsonParser.parseToJsonElement(createJsonStr.ifBlank { "{}" }).jsonObject
        throwIfApiRejected(createJsonStr, createJson)
        val tasks = createJson["data"]?.jsonObject?.get("tasks")?.jsonArray
        val taskItem = tasks?.getOrNull(0)?.jsonObject
            ?: throw CapCutTtsException(TtsErrorKind.INVALID_RESPONSE, "CapCut không trả task TTS: $createJsonStr")

        val taskId = taskItem["id"]?.jsonPrimitive?.content
            ?: throw CapCutTtsException(TtsErrorKind.INVALID_RESPONSE, "Thiếu task id: $createJsonStr")
        val token = taskItem["token"]?.jsonPrimitive?.content
            ?: throw CapCutTtsException(TtsErrorKind.INVALID_RESPONSE, "Thiếu task token: $createJsonStr")

        // Polling nhận kết quả
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            delay(350)

            val (queryUrl, queryHeaders, queryBody) = buildQueryTtsRequest(taskId, token, bindId)
            val queryReq = Request.Builder()
                .url(queryUrl)
                .post(queryBody.toRequestBody("application/json".toMediaType()))
                .apply { queryHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val queryJsonStr = client.newCall(queryReq).execute().use { response ->
                if (!response.isSuccessful) {
                    throw CapCutTtsException(TtsErrorKind.NETWORK, "Truy vấn TTS lỗi HTTP ${response.code}")
                }
                CapCutSigner.decompressResponseBody(response)
            }
            val queryJson = jsonParser.parseToJsonElement(queryJsonStr.ifBlank { "{}" }).jsonObject
            throwIfApiRejected(queryJsonStr, queryJson)
            val queryTasks = queryJson["data"]?.jsonObject?.get("tasks")?.jsonArray
            val currentTask = queryTasks?.getOrNull(0)?.jsonObject ?: continue

            val status = currentTask["status"]?.jsonPrimitive?.content ?: ""
            if (status.equals("success", ignoreCase = true) || status.equals("succeed", ignoreCase = true)) {
                downloadAudioFromTask(currentTask, destFile)
                return destFile
            } else if (status.equals("failed", ignoreCase = true)) {
                val errMsg = currentTask["message"]?.jsonPrimitive?.content
                    ?: queryJson["message"]?.jsonPrimitive?.content
                    ?: "CapCut báo lỗi tổng hợp âm thanh"
                throw CapCutTtsException(TtsErrorKind.TASK_FAILED, "TTS Task failed: $errMsg")
            }
        }

        throw CapCutTtsException(TtsErrorKind.TIMEOUT, "TTS Task timeout sau ${timeoutMs / 1000}s")
    }

    private fun throwIfApiRejected(
        rawJson: String,
        json: kotlinx.serialization.json.JsonObject
    ) {
        val ret = json["ret"]?.jsonPrimitive?.content
        if (!ret.isNullOrBlank() && ret != "0") {
            val message = json["errmsg"]?.jsonPrimitive?.content
                ?: json["message"]?.jsonPrimitive?.content
                ?: "CapCut từ chối yêu cầu"
            val kind = if (ret == "5001" || message.contains("parameters modified", ignoreCase = true)) {
                TtsErrorKind.PARAMETERS_MODIFIED
            } else {
                TtsErrorKind.INVALID_RESPONSE
            }
            throw CapCutTtsException(kind, "CapCut ret=$ret: $message. Phản hồi: ${rawJson.take(300)}")
        }
    }

    private fun downloadAudioFromTask(taskObj: kotlinx.serialization.json.JsonObject, destFile: File) {
        var audioUrl: String? = null
        var audioBase64: String? = null

        val payloadStr = taskObj["payload"]?.jsonPrimitive?.content
        if (!payloadStr.isNullOrBlank()) {
            try {
                val payloadJson = jsonParser.parseToJsonElement(payloadStr).jsonObject
                val audioSubtitles = payloadJson["audio_subtitles"]?.jsonArray
                if (audioSubtitles != null && audioSubtitles.isNotEmpty()) {
                    audioUrl = audioSubtitles[0].jsonObject["speech_url"]?.jsonPrimitive?.content
                }
                if (audioUrl.isNullOrBlank()) {
                    audioUrl = payloadJson["speech_url"]?.jsonPrimitive?.content
                        ?: payloadJson["video_url"]?.jsonPrimitive?.content
                }
                if (audioBase64.isNullOrBlank()) {
                    audioBase64 = payloadJson["audio"]?.jsonPrimitive?.content
                }
                val capJsonElement = payloadJson["cap_json"]
                if (capJsonElement != null) {
                    val capJson = try {
                        capJsonElement.jsonObject
                    } catch (_: Exception) {
                        jsonParser.parseToJsonElement(capJsonElement.jsonPrimitive.content).jsonObject
                    }
                    if (audioUrl.isNullOrBlank()) {
                        audioUrl = capJson["speech_url"]?.jsonPrimitive?.content
                            ?: capJson["video_url"]?.jsonPrimitive?.content
                    }
                    if (audioBase64.isNullOrBlank()) {
                        audioBase64 = capJson["audio"]?.jsonPrimitive?.content
                    }
                }
            } catch (_: Exception) {}
        }

        if (audioUrl.isNullOrBlank()) {
            audioUrl = taskObj["video_url"]?.jsonPrimitive?.content
        }
        if (audioUrl.isNullOrBlank()) {
            audioBase64 = taskObj["audio"]?.jsonPrimitive?.content
        }

        if (!audioUrl.isNullOrBlank()) {
            val downloadReq = Request.Builder().url(audioUrl).get().build()
            client.newCall(downloadReq).execute().use { downloadResp ->
                if (!downloadResp.isSuccessful) {
                    throw CapCutTtsException(TtsErrorKind.NETWORK, "Tải file audio thất bại HTTP ${downloadResp.code}")
                }
                val body = downloadResp.body
                    ?: throw CapCutTtsException(TtsErrorKind.INVALID_AUDIO, "CapCut trả file audio rỗng")
                destFile.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } else if (!audioBase64.isNullOrBlank()) {
            val cleanBase64 = audioBase64.substringAfter("base64,", audioBase64)
            val bytes = CapCutSigner.base64Decode(cleanBase64)
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(bytes)
        } else {
            throw CapCutTtsException(
                TtsErrorKind.INVALID_RESPONSE,
                "Không tìm thấy đường link audio hoặc base64 trong dữ liệu trả về"
            )
        }
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildCreateTtsRequest(
        text: String,
        voiceType: String,
        resourceId: String,
        rate: String,
        bindId: String
    ): Triple<String, Map<String, String>, String> {
        val babi = """{"feature_entrance":"editor","feature_entrance_detail":"editor-feature-text_to_speech","feature_key":"text_to_speech","scenario":"video_editor"}"""

        val ssml = """
            <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="en-US">
                <voice name="$voiceType" mock_tone_info="" platform="sami" resource_id="$resourceId" emotion="" emotion_scale="0" style="" role="" moyin_emotion="" is_clone_tone="false" need_subtitle_timestamp="false">
                    <prosody rate="$rate">${escapeXml(text)}</prosody>
                </voice>
            </speak>
        """.trimIndent()

        val extraInfo = """{"benefit_info":{}}"""
        val payloadSign = CapCutSigner.makeTtsPayloadSign(ssml, extraInfo, device.deviceId, device.aid)

        val payloadObj = buildJsonObject {
            put("audio_format", "mp3")
            put("babi_param", babi)
            put("credit_disable", false)
            put("extra_info", extraInfo)
            put("need_merge_voice", false)
            put("need_subtitle_timestamp", false)
            put("scene", "text_to_speech")
            put("ssml", ssml)
            put("sign", payloadSign)
        }

        val body = buildJsonObject {
            put("bind_id", bindId)
            put("can_queue", true)
            put("enter_from", "text_to_speech")
            putJsonArray("tasks") {
                add(buildJsonObject {
                    put("context", UUID.randomUUID().toString())
                    put("payload", payloadObj.toString())
                    put("req_key", "sami_text_to_speech")
                    put("task_version", "v3")
                })
            }
        }.toString()

        val path = "/lv/v1/common_task/new"
        val queryMap = device.toQueryMap(includeRegion = true).toMutableMap().apply {
            put("babi_param", babi)
        }
        val queryParams = queryMap.map { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")
        val fullUrl = "${CapCutSigner.BASE_URL}$path?$queryParams"

        val headers = CapCutSigner.buildBaseHeaders(device, body, appid = true)
        val sign = CapCutSigner.makeSignHeader(fullUrl, device.appvr, headers["device-time"] ?: "", device.tdid)
        headers["sign"] = sign

        return Triple(fullUrl, headers, body)
    }

    private fun buildQueryTtsRequest(taskId: String, token: String, bindId: String): Triple<String, Map<String, String>, String> {
        val body = buildJsonObject {
            putJsonArray("tasks") {
                add(buildJsonObject {
                    put("bind_id", bindId)
                    put("id", taskId)
                    put("req_key", "sami_text_to_speech")
                    put("task_version", "v3")
                    put("token", token)
                })
            }
        }.toString()

        val path = "/lv/v1/common_task/query"
        val queryParams = device.toQueryMap(includeRegion = true)
            .map { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
            .joinToString("&")
        val fullUrl = "${CapCutSigner.BASE_URL}$path?$queryParams"

        val headers = CapCutSigner.buildBaseHeaders(device, body, appid = true)
        val sign = CapCutSigner.makeSignHeader(fullUrl, device.appvr, headers["device-time"] ?: "", device.tdid)
        headers["sign"] = sign

        return Triple(fullUrl, headers, body)
    }
}
