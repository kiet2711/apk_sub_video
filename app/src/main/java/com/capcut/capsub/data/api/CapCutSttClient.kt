package com.capcut.capsub.data.api

import com.capcut.capsub.data.model.DeviceConfig
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.model.SubtitleItem
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
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Client gọi API Nhận Diện Giọng Nói (STT - Speech to Text) chính thức của CapCut Cloud.
 * Port từ client.py của tool PC.
 */
class CapCutSttClient(
    private val device: DeviceConfig = DeviceConfig(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    /**
     * Khởi tạo tác vụ STT và lặp Polling nhận danh sách phụ đề có mốc thời gian
     */
    suspend fun transcribeAudio(
        audioVid: String,
        audioMd5: String,
        durationMs: Long,
        language: String = "zh-CN",
        useTranslation: Boolean = false,
        translationLanguage: String = "vi-VN",
        timeOffsetMs: Long = 0L,
        progressCallback: ((Float, String) -> Unit)? = null
    ): SubtitleDocument = withContext(Dispatchers.IO) {

        // 1. Gửi request tạo Task STT
        progressCallback?.invoke(0.10f, if (useTranslation) "Đang gửi tác vụ STT & Dịch thuật CapCut..." else "Đang gửi tác vụ nhận diện giọng nói...")
        val (newUrl, newHeaders, newBody) = buildCreateSttRequest(audioVid, audioMd5, durationMs, language, useTranslation, translationLanguage)
        val newReq = Request.Builder()
            .url(newUrl)
            .post(newBody.toRequestBody("application/json".toMediaType()))
            .apply { newHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        val newResp = client.newCall(newReq).execute()
        val newJsonStr = CapCutSigner.decompressResponseBody(newResp)
        val newJson = jsonParser.parseToJsonElement(newJsonStr.ifBlank { "{}" }).jsonObject
        val tasks = newJson["data"]?.jsonObject?.get("tasks")?.jsonArray
        val taskItem = tasks?.getOrNull(0)?.jsonObject ?: throw IOException("Không nhận được STT task: $newJsonStr")

        val taskId = taskItem["id"]?.jsonPrimitive?.content ?: throw IOException("Thiếu task id: $newJsonStr")
        val token = taskItem["token"]?.jsonPrimitive?.content ?: throw IOException("Thiếu token: $newJsonStr")

        // 2. Polling kiểm tra kết quả (tối đa 5 phút)
        val startTime = System.currentTimeMillis()
        var pollAttempt = 0

        while (System.currentTimeMillis() - startTime < 300_000) {
            pollAttempt++
            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            val progressPct = minOf(0.95f, 0.20f + (elapsedSec / 60f) * 0.70f)
            progressCallback?.invoke(progressPct, "CapCut Cloud đang xử lý... (${elapsedSec}s)")

            delay(2000)

            val (queryUrl, queryHeaders, queryBody) = buildQuerySttRequest(taskId, token)
            val queryReq = Request.Builder()
                .url(queryUrl)
                .post(queryBody.toRequestBody("application/json".toMediaType()))
                .apply { queryHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val queryResp = client.newCall(queryReq).execute()
            val queryJsonStr = CapCutSigner.decompressResponseBody(queryResp)
            val queryJson = jsonParser.parseToJsonElement(queryJsonStr.ifBlank { "{}" }).jsonObject
            val queryTasks = queryJson["data"]?.jsonObject?.get("tasks")?.jsonArray
            val currentTask = queryTasks?.getOrNull(0)?.jsonObject ?: continue

            val status = currentTask["status"]?.jsonPrimitive?.content ?: ""
            if (status.equals("success", ignoreCase = true) || status.equals("succeed", ignoreCase = true)) {
                progressCallback?.invoke(1.0f, "Nhận diện giọng nói thành công!")
                return@withContext extractSubtitlesFromPayload(currentTask, timeOffsetMs)
            } else if (status.equals("failed", ignoreCase = true)) {
                throw IOException("CapCut báo lỗi xử lý thất bại (file không có âm thanh hoặc định dạng không hợp lệ).")
            }
        }

        throw IOException("Quá thời gian chờ phản hồi STT (Timeout 5 phút).")
    }

    private fun extractSubtitlesFromPayload(taskJson: kotlinx.serialization.json.JsonObject, timeOffsetMs: Long): SubtitleDocument {
        val payloadElement = taskJson["payload"] ?: return SubtitleDocument()
        val payloadObj = if (payloadElement.jsonPrimitive.isString) {
            jsonParser.parseToJsonElement(payloadElement.jsonPrimitive.content).jsonObject
        } else {
            payloadElement.jsonObject
        }

        val rawUtterances = payloadObj["utterances"]?.jsonArray ?: return SubtitleDocument()
        val list = mutableListOf<SubtitleItem>()

        rawUtterances.forEachIndexed { index, element ->
            val item = element.jsonObject
            val originalText = item["text"]?.jsonPrimitive?.content ?: ""
            val translationText = item["translation_text"]?.jsonPrimitive?.content ?: ""

            val startMs = (item["start_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) + timeOffsetMs
            val endMs = (item["end_time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) + timeOffsetMs

            val mainText = originalText.ifBlank { translationText }
            if (mainText.isNotBlank()) {
                list.add(
                    SubtitleItem(
                        id = index + 1,
                        startMs = startMs,
                        endMs = endMs,
                        originalText = mainText,
                        translatedText = translationText
                    )
                )
            }
        }

        return SubtitleDocument(list)
    }

    private fun buildCreateSttRequest(
        audioVid: String,
        audioMd5: String,
        durationMs: Long,
        language: String,
        useTranslation: Boolean,
        translationLanguage: String
    ): Triple<String, Map<String, String>, String> {
        val clientReqId = UUID.randomUUID().toString()
        val bindId = UUID.randomUUID().toString().uppercase()
        val contextId = UUID.randomUUID().toString()

        val capJsonObj = buildJsonObject {
            put("adjust_endtime", 200)
            put("audio", audioVid)
            put("audio_type", "vid")
            put("caption_type", 0)
            put("client_request_id", clientReqId)
            put("duration", durationMs)
            put("enable_cache", true)
            put("enter_from", "asr")
            put("language", language)
            put("max_lines", 1)
            put("md5", audioMd5)
            putJsonObject("pack_options") {
                put("need_attribute", true)
            }
            putJsonArray("songs_info") {
                add(buildJsonObject {
                    put("end_time", (durationMs - 10).coerceAtLeast(0))
                    put("id", "")
                    put("start_time", 0)
                })
            }
            put("translation_language", translationLanguage)
            put("use_translation", useTranslation)
            put("words_per_line", 15)
        }

        val payloadObj = buildJsonObject {
            put("cap_json", capJsonObj)
        }

        val body = buildJsonObject {
            put("bind_id", bindId)
            put("can_queue", true)
            put("enter_from", "asr")
            putJsonArray("tasks") {
                add(buildJsonObject {
                    put("context", contextId)
                    put("payload", payloadObj.toString())
                    put("req_key", "cc_audio_subtitle_asr")
                    put("task_version", "v3")
                })
            }
        }.toString()

        val path = "/lv/v1/common_task/new"
        val babi = """{"feature_entrance":"editor","feature_entrance_detail":"editor-elements-captions-subtitle_recognition","feature_key":"subtitle_recognition","scenario":"video_editor"}"""

        val queryMap = device.toQueryMap(includeRegion = true).toMutableMap().apply {
            put("babi_param", babi)
        }
        val queryParams = queryMap.map { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")
        val fullUrl = "${CapCutSigner.BASE_URL}$path?$queryParams"

        val headers = CapCutSigner.buildBaseHeaders(device, body, appid = false)
        val sign = CapCutSigner.makeSignHeader(fullUrl, device.appvr, headers["device-time"] ?: "", device.tdid)
        headers["sign"] = sign

        return Triple(fullUrl, headers, body)
    }

    private fun buildQuerySttRequest(taskId: String, token: String): Triple<String, Map<String, String>, String> {
        val body = buildJsonObject {
            putJsonArray("tasks") {
                add(buildJsonObject {
                    put("bind_id", "")
                    put("id", taskId)
                    put("req_key", "cc_audio_subtitle_asr")
                    put("task_version", "v3")
                    put("token", token)
                })
            }
        }.toString()

        val path = "/lv/v1/common_task/query"
        val queryParams = device.toQueryMap(includeRegion = false)
            .map { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
            .joinToString("&")
        val fullUrl = "${CapCutSigner.BASE_URL}$path?$queryParams"

        val headers = CapCutSigner.buildBaseHeaders(device, body, appid = false)
        val sign = CapCutSigner.makeSignHeader(fullUrl, device.appvr, headers["device-time"] ?: "", device.tdid)
        headers["sign"] = sign

        return Triple(fullUrl, headers, body)
    }
}
