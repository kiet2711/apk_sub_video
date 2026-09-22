package com.capcut.capsub.data.api

import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.model.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Động cơ dịch phụ đề chuyên nghiệp bằng Google Gemini API:
 * - Hỗ trợ dịch ĐA LUỒNG song song qua Coroutines + Semaphore khi có nhiều API Key.
 * - Hỗ trợ Custom Prompt (System Instruction tùy chỉnh từ người dùng).
 * - Hỗ trợ Ngôn ngữ đích tùy chọn (Tiếng Việt, Tiếng Anh, v.v.).
 * - Hỗ trợ xoay vòng API Key (Round-Robin & Auto-Failover).
 */
class GeminiTranslator(
    private val apiKeys: List<String>,
    private val modelId: String = "gemini-3.5-flash-lite",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
) {
    private val keyIndex = AtomicInteger(0)
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun getNextApiKey(): String {
        if (apiKeys.isEmpty()) throw IllegalStateException("Chưa cấu hình Gemini API Key! Vui lòng nhập ít nhất 1 Key trong Cài đặt.")
        val idx = Math.floorMod(keyIndex.getAndIncrement(), apiKeys.size)
        return apiKeys[idx].trim()
    }

    suspend fun translateSubtitles(
        document: SubtitleDocument,
        stylePreset: String = "Zhihu",
        customPrompt: String = "",
        targetLanguage: String = "Tiếng Việt",
        chunkSize: Int = 45,
        threadCount: Int = 2,
        progressCallback: ((Float, String) -> Unit)? = null
    ): SubtitleDocument = withContext(Dispatchers.IO) {
        if (document.isEmpty) return@withContext document
        if (apiKeys.isEmpty()) throw IllegalStateException("Chưa có Gemini API Key! Vui lòng vào Cài đặt để thêm Key.")

        val items = document.items
        val chunks = items.chunked(chunkSize)
        val totalChunks = chunks.size
        val systemPrompt = buildSystemPrompt(stylePreset, targetLanguage, customPrompt)

        // Tính toán số luồng tối đa dựa trên threadCount và số lượng API key hiện có
        val effectiveThreads = threadCount.coerceIn(1, 10).coerceAtMost(chunks.size)
        val semaphore = Semaphore(effectiveThreads)
        val completedCount = AtomicInteger(0)

        progressCallback?.invoke(
            0.05f,
            if (effectiveThreads > 1) "Đang khởi chạy $effectiveThreads luồng Gemini dịch $totalChunks khối phụ đề..."
            else "Đang chuẩn bị dịch $totalChunks khối phụ đề..."
        )

        coroutineScope {
            val deferredList = chunks.mapIndexed { chunkIdx, chunkItems ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val translatedTexts = translateChunkWithRetry(chunkItems, systemPrompt)
                        val done = completedCount.incrementAndGet()
                        val pct = 0.05f + (done.toFloat() / totalChunks) * 0.90f
                        progressCallback?.invoke(pct, "Gemini đã dịch xong $done/$totalChunks khối phụ đề...")
                        Pair(chunkIdx, translatedTexts)
                    }
                }
            }

            val results = deferredList.awaitAll().sortedBy { it.first }
            results.forEach { (chunkIdx, translatedTexts) ->
                val chunkItems = chunks[chunkIdx]
                chunkItems.forEachIndexed { itemIdx, originalItem ->
                    val trans = translatedTexts.getOrNull(itemIdx) ?: originalItem.originalText
                    originalItem.translatedText = trans
                    originalItem.normalizeTranslation()
                }
            }
        }

        progressCallback?.invoke(1.0f, "Dịch thuật hoàn tất!")
        return@withContext document
    }

    private suspend fun translateChunkWithRetry(
        items: List<SubtitleItem>,
        systemPrompt: String,
        maxRetries: Int = 3
    ): List<String> {
        val srtInput = StringBuilder()
        items.forEachIndexed { i, it ->
            sbFormatSrt(srtInput, i + 1, it.formatSrtTimecode(), it.originalText)
        }

        var lastError: Exception? = null
        val attempts = maxRetries.coerceAtLeast(apiKeys.size)
        for (attempt in 0 until attempts) {
            val key = getNextApiKey()
            try {
                val rawResponse = callGeminiRestApi(srtInput.toString(), systemPrompt, key)
                val parsedItems = SubtitleDocument.parseSrt(rawResponse).items

                if (parsedItems.size == items.size) {
                    return parsedItems.map { it.originalText }
                } else if (parsedItems.isNotEmpty()) {
                    val result = mutableListOf<String>()
                    for (i in items.indices) {
                        result.add(parsedItems.getOrNull(i)?.originalText ?: items[i].originalText)
                    }
                    return result
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw IOException("Dịch phụ đề thất bại sau nhiều lần thử với các API Key: ${lastError?.message}", lastError)
    }

    private fun sbFormatSrt(sb: StringBuilder, id: Int, timecode: String, text: String) {
        sb.append(id).append("\n")
        sb.append(timecode).append("\n")
        sb.append(text).append("\n\n")
    }

    private fun callGeminiRestApi(prompt: String, systemInstruction: String, apiKey: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"

        val requestJson = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", prompt) })
                    }
                })
            }
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", systemInstruction) })
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                put("maxOutputTokens", 8192)
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val bodyString = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw IOException("Gemini API Error HTTP ${response.code}: $bodyString")
        }

        val rootJson = jsonParser.parseToJsonElement(bodyString).jsonObject
        val candidates = rootJson["candidates"]?.jsonArray
        val firstCandidate = candidates?.getOrNull(0)?.jsonObject
        val parts = firstCandidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
        val text = parts?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""

        if (text.isBlank()) {
            throw IOException("Gemini không trả về nội dung hợp lệ.")
        }
        return text.trim()
    }

    private fun buildSystemPrompt(stylePreset: String, targetLanguage: String, customPrompt: String): String {
        val styleGuide = when (stylePreset.lowercase()) {
            "zhihu" -> "Phong cách phim ngắn Zhihu vả mặt kịch tính, nhịp điệu dồn dập, sắc bén, gãy gọn, gay cấn."
            "thuanviet" -> "Phong cách văn học trau chuốt, mượt mà, giàu cảm xúc, thoát ý tự nhiên."
            "cotrang" -> "Phong cách cổ trang tiên hiệp huyền huyễn, bảo lưu chuẩn mực danh xưng, đại từ xưng hô và pháp bảo môn phái."
            else -> "Tự động nhận diện thể loại câu chuyện để dịch thoát nghĩa, tự nhiên và lôi cuốn nhất."
        }

        val userCustomSection = if (customPrompt.isNotBlank()) {
            """
            HƯỚNG DẪN BỔ SUNG ĐẶC BIỆT TỪ NGƯỜI DÙNG:
            $customPrompt
            """.trimIndent()
        } else ""

        return """
            Bạn là chuyên gia dịch thuật phụ đề video chuyên nghiệp hàng đầu thế giới.
            NGÔN NGỮ ĐÍCH CẦN DỊCH: $targetLanguage.

            YÊU CẦU PHONG CÁCH:
            $styleGuide

            $userCustomSection

            QUY TẮC BẮT BUỘC VỀ TÊN NHÂN VẬT & TỪ NGỮ (KHI DỊCH SANG TIẾNG VIỆT):
            1. PHIÊN ÂM 100% SANG HÁN VIỆT: Toàn bộ họ tên nhân vật, tên riêng, biệt danh, chức vụ BẮT BUỘC phải chuyển sang âm Hán Việt chuẩn mực (Ví dụ: 余昭昭 -> Dư Chiêu Chiêu, 顾总 -> Cố tổng, 陆爷 -> Lục gia, 李特助 -> trợ lý Lý...).
            2. TUYỆT ĐỐI KHÔNG DỊCH NỬA VỜI: Nghiêm cấm tuyệt đối việc dịch một nửa tiếng Việt một nửa để lại chữ Hán (CẤM: 'Dư 昭昭', 'Cố 总'...).
            3. 100% THUẦN NGÔN NGỮ ĐÍCH: Không để sót bất kỳ chữ Hán nào trong kết quả dịch.

            QUY TẮC BẢO TOÀN CẤU TRÚC PHỤ ĐỀ SRT:
            1. Đầu vào có bao nhiêu khối phụ đề (ID từ 1 đến N), đầu ra BẮT BUỘC PHẢI CÓ ĐỦ CHÍNH XÁC bấy nhiêu khối.
            2. Giữ nguyên số thứ tự ID và dòng Timecode (00:00:00,000 --> 00:00:00,000).
            3. Dưới mỗi timecode CHỈ ĐƯỢC ghi bản dịch ở ngôn ngữ đích. TUYỆT ĐỐI KHÔNG lặp lại câu gốc, không xuất song ngữ.
            4. KHÔNG thêm lời chào, nhãn "bản gốc/bản dịch" hoặc giải thích ngoài định dạng SRT chuẩn.
        """.trimIndent()
    }
}
