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

    /**
     * Dịch danh sách các câu lỗi/câu chỉ định bằng Gemini AI đa luồng:
     * - Chia các câu thành từng khối nhỏ (10-15 câu).
     * - Đánh số định danh [ID] rõ ràng để Gemini trả về đúng từng câu theo ID.
     * - Chạy song song qua Semaphore theo số luồng người dùng cấu hình.
     * - Tự động xoay vòng Gemini API Key và thử lại khi gặp lỗi.
     *
     * @return Map ánh xạ giữa itemId và nội dung đã dịch sang tiếng Việt.
     */
    suspend fun translateItems(
        items: List<Pair<Int, String>>,
        stylePreset: String = "Zhihu",
        customPrompt: String = "",
        targetLanguage: String = "Tiếng Việt",
        chunkSize: Int = 15,
        threadCount: Int = 2,
        progressCallback: ((Float, String) -> Unit)? = null
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext emptyMap()
        if (apiKeys.isEmpty()) throw IllegalStateException("Chưa có Gemini API Key! Vui lòng vào Cài đặt để thêm Key.")

        val validItems = items.filter { it.second.isNotBlank() }
        if (validItems.isEmpty()) return@withContext emptyMap()

        val chunks = validItems.chunked(chunkSize.coerceIn(5, 20))
        val totalChunks = chunks.size
        val systemPrompt = buildSystemPrompt(stylePreset, targetLanguage, customPrompt, isSrt = false)

        val effectiveThreads = threadCount.coerceIn(1, 10).coerceAtMost(chunks.size)
        val semaphore = Semaphore(effectiveThreads)
        val completedCount = AtomicInteger(0)
        val resultMap = java.util.concurrent.ConcurrentHashMap<Int, String>()

        progressCallback?.invoke(
            0.05f,
            if (effectiveThreads > 1) "Đang chạy $effectiveThreads luồng Gemini dịch $totalChunks nhóm câu lỗi..."
            else "Đang chuẩn bị dịch ${validItems.size} câu lỗi..."
        )

        coroutineScope {
            val deferredList = chunks.mapIndexed { chunkIdx, chunkList ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            val chunkResult = translateNumberedChunk(chunkList, systemPrompt)
                            resultMap.putAll(chunkResult)

                            // Nếu nhóm có câu nào bị AI bỏ sót, tự động dịch bổ sung từng câu
                            val missing = chunkList.filter { !chunkResult.containsKey(it.first) }
                            missing.forEach { (id, text) ->
                                try {
                                    val single = translateSingleTextInternal(text, systemPrompt)
                                    if (single.isNotBlank()) {
                                        resultMap[id] = single
                                    }
                                } catch (_: Exception) {}
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GeminiTranslator", "Lỗi dịch khối $chunkIdx: ${e.message}")
                            // Thử lại từng câu trong khối nếu cả khối bị lỗi định dạng
                            chunkList.forEach { (id, text) ->
                                try {
                                    val singleTrans = translateSingleTextInternal(text, systemPrompt)
                                    if (singleTrans.isNotBlank()) {
                                        resultMap[id] = singleTrans
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        val done = completedCount.incrementAndGet()
                        val pct = 0.05f + (done.toFloat() / totalChunks) * 0.90f
                        progressCallback?.invoke(pct, "Gemini đã dịch xong $done/$totalChunks nhóm (${resultMap.size} câu thành công)...")
                    }
                }
            }
            deferredList.awaitAll()
        }

        progressCallback?.invoke(1.0f, "Dịch xong ${resultMap.size}/${validItems.size} câu!")
        return@withContext resultMap
    }

    private fun translateNumberedChunk(
        items: List<Pair<Int, String>>,
        systemPrompt: String,
        maxRetries: Int = 3
    ): Map<Int, String> {
        val sb = StringBuilder()
        sb.append("Hãy dịch chính xác các câu thoại sau đây sang ngôn ngữ đích theo số định danh [ID].\n")
        sb.append("QUY TẮC BẮT BUỘC:\n")
        sb.append("1. Bắt buộc giữ nguyên số định danh [ID] ở đầu mỗi câu (ví dụ: [101]: <bản dịch tiếng Việt>).\n")
        sb.append("2. Phiên âm 100% họ tên, tên riêng, chức vụ sang âm Hán Việt chuẩn mực (Ví dụ: 余昭昭 -> Dư Chiêu Chiêu, 顾总 -> Cố tổng).\n")
        sb.append("3. Không dịch nửa vời, tuyệt đối không để sót chữ Hán trong bản dịch.\n")
        sb.append("4. Mỗi câu một dòng theo đúng định dạng: [ID]: <bản dịch>\n\n")
        sb.append("DANH SÁCH CÂU CẦN DỊCH:\n")
        items.forEach { (id, text) ->
            sb.append("[$id]: ${text.trim()}\n")
        }

        val prompt = sb.toString()
        var lastError: Exception? = null
        val attempts = maxRetries.coerceAtLeast(apiKeys.size)
        // Hỗ trợ linh hoạt các format: [101]: text, **[101]:** text, 101. text, - [101]: text, etc.
        val lineRegex = Regex("""^[ \t\-\*•]*(?:\*\*)?(?:\[|\#)?(\d+)(?:\]|\.|\:|\))?(?:\*\*)?[:\s\-–—]+(.+)$""")

        for (attempt in 0 until attempts) {
            val key = getNextApiKey()
            try {
                val rawResponse = callGeminiRestApi(prompt, systemPrompt, key)
                val resultMap = mutableMapOf<Int, String>()

                rawResponse.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("```")) return@forEach
                    val match = lineRegex.find(trimmed)
                    if (match != null) {
                        val id = match.groupValues[1].toIntOrNull()
                        var text = match.groupValues[2].trim()
                        if (text.startsWith("**") && text.endsWith("**") && text.length > 4) {
                            text = text.substring(2, text.length - 2).trim()
                        }
                        text = text.removeSurrounding("\"").removeSurrounding("'").trim()
                        if (id != null && text.isNotBlank()) {
                            resultMap[id] = text
                        }
                    }
                }

                if (resultMap.isNotEmpty()) {
                    return resultMap
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw IOException("Dịch nhóm câu thất bại: ${lastError?.message}", lastError)
    }

    /**
     * Dịch một câu đơn lẻ bằng Gemini AI (cho nút Dịch lẻ trên từng thẻ câu).
     */
    suspend fun translateSingleText(
        text: String,
        stylePreset: String = "Zhihu",
        customPrompt: String = "",
        targetLanguage: String = "Tiếng Việt"
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text
        if (apiKeys.isEmpty()) throw IllegalStateException("Chưa có Gemini API Key! Vui lòng vào Cài đặt để thêm Key.")

        val systemPrompt = buildSystemPrompt(stylePreset, targetLanguage, customPrompt, isSrt = false)
        return@withContext translateSingleTextInternal(text, systemPrompt)
    }

    private fun translateSingleTextInternal(text: String, systemPrompt: String): String {
        val prompt = "Hãy dịch câu thoại sau đây sang ngôn ngữ đích. Chỉ trả về duy nhất nội dung bản dịch, không giải thích, phiên âm toàn bộ tên riêng sang Hán Việt:\n$text"
        val attempts = 3.coerceAtLeast(apiKeys.size)
        var lastError: Exception? = null
        for (attempt in 0 until attempts) {
            val key = getNextApiKey()
            try {
                val res = callGeminiRestApi(prompt, systemPrompt, key).trim()
                if (res.isNotBlank()) {
                    val cleaned = res.replace(Regex("""^\[\d+\][\s:\-]+"""), "").trim()
                    return if (cleaned.isNotBlank()) cleaned else res
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("Dịch câu thất bại: ${lastError?.message}", lastError)
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

    private fun buildSystemPrompt(
        stylePreset: String,
        targetLanguage: String,
        customPrompt: String,
        isSrt: Boolean = true
    ): String {
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

        val srtRules = if (isSrt) {
            """

            QUY TẮC BẢO TOÀN CẤU TRÚC PHỤ ĐỀ SRT:
            1. Đầu vào có bao nhiêu khối phụ đề (ID từ 1 đến N), đầu ra BẮT BUỘC PHẢI CÓ ĐỦ CHÍNH XÁC bấy nhiêu khối.
            2. Giữ nguyên số thứ tự ID và dòng Timecode (00:00:00,000 --> 00:00:00,000).
            3. Dưới mỗi timecode CHỈ ĐƯỢC ghi bản dịch ở ngôn ngữ đích. TUYỆT ĐỐI KHÔNG lặp lại câu gốc, không xuất song ngữ.
            4. KHÔNG thêm lời chào, nhãn "bản gốc/bản dịch" hoặc giải thích ngoài định dạng SRT chuẩn.
            """.trimIndent()
        } else ""

        return """
            Bạn là chuyên gia dịch thuật phụ đề video và lời thoại phim chuyên nghiệp hàng đầu thế giới.
            NGÔN NGỮ ĐÍCH CẦN DỊCH: $targetLanguage.

            YÊU CẦU PHONG CÁCH:
            $styleGuide

            $userCustomSection

            QUY TẮC BẮT BUỘC VỀ TÊN NHÂN VẬT & TỪ NGỮ (KHI DỊCH SANG TIẾNG VIỆT):
            1. PHIÊN ÂM 100% SANG HÁN VIỆT: Toàn bộ họ tên nhân vật, tên riêng, biệt danh, chức vụ BẮT BUỘC phải chuyển sang âm Hán Việt chuẩn mực (Ví dụ: 余昭昭 -> Dư Chiêu Chiêu, 顾总 -> Cố tổng, 陆爷 -> Lục gia, 李特助 -> trợ lý Lý...).
            2. TUYỆT ĐỐI KHÔNG DỊCH NỬA VỜI: Nghiêm cấm tuyệt đối việc dịch một nửa tiếng Việt một nửa để lại chữ Hán (CẤM: 'Dư 昭昭', 'Cố 总'...).
            3. 100% THUẦN NGÔN NGỮ ĐÍCH: Không để sót bất kỳ chữ Hán nào trong kết quả dịch.
            $srtRules
        """.trimIndent()
    }
}
