package com.capcut.capsub.domain.pipeline

import android.content.Context
import android.net.Uri
import com.capcut.capsub.data.api.CapCutSigner
import com.capcut.capsub.data.api.CapCutSttClient
import com.capcut.capsub.data.api.CapCutVodUploader
import com.capcut.capsub.data.api.GeminiTranslator
import com.capcut.capsub.data.model.DeviceConfig
import com.capcut.capsub.data.model.ProcessProgress
import com.capcut.capsub.data.model.ProcessStage
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.domain.media.AudioChunker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bộ điều phối toàn diện Pipeline 4 bước:
 * [1. Tách âm thanh M4A] -> [2. Upload CapCut VOD] -> [3. CapCut STT] -> [4. Gemini Dịch]
 */
class SubtitlingPipeline(
    private val context: Context,
    private val apiKeys: List<String>,
    private val translationEngine: String = "capcut", // "capcut", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "none"
    private val stylePreset: String = "Zhihu",
    private val customPrompt: String = "",
    private val targetLanguage: String = "vi-VN",
    private val geminiThreadCount: Int = 2
) {
    private val _progressFlow = MutableStateFlow(ProcessProgress())
    val progressFlow: StateFlow<ProcessProgress> = _progressFlow

    @Volatile
    private var isCancelled = false

    fun cancel() {
        isCancelled = true
        _progressFlow.value = ProcessProgress(stage = ProcessStage.CANCELLED, message = "Đã huỷ bởi người dùng.")
    }

    suspend fun execute(
        videoUri: Uri,
        totalDurationMs: Long,
        sourceLanguage: String = "zh-CN",
        outputSrtFile: File? = null
    ): SubtitleDocument = withContext(Dispatchers.IO) {
        val sessionDir = File(context.cacheDir, "sub_session_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}")
        sessionDir.mkdirs()

        try {
            isCancelled = false
            checkCancelled()

            // -----------------------------------------------------------------
            // BƯỚC 1: TRÍCH XUẤT / CẮT ÂM THANH M4A
            // -----------------------------------------------------------------
            _progressFlow.value = ProcessProgress(
                stage = ProcessStage.EXTRACTING_AUDIO,
                progress = 0.05f,
                message = "Đang tách luồng âm thanh từ video..."
            )

            val chunkList = AudioChunker.sliceMedia(
                context = context,
                videoUri = videoUri,
                totalDurationMs = totalDurationMs,
                tempDir = sessionDir,
                chunkDurationSec = 600L
            ) { pct, msg ->
                _progressFlow.value = ProcessProgress(
                    stage = ProcessStage.EXTRACTING_AUDIO,
                    progress = 0.05f + pct * 0.15f,
                    message = msg
                )
            }

            checkCancelled()

            // -----------------------------------------------------------------
            // BƯỚC 2 & 3: TẢI LÊN CAPCUT VOD & NHẬN DIỆN GIỌNG NÓI (STT) - ĐA LUỒNG
            // -----------------------------------------------------------------
            val numChunks = chunkList.size
            val useCapcutTrans = (translationEngine == "capcut")
            _progressFlow.value = ProcessProgress(
                stage = ProcessStage.UPLOADING_VOD,
                progress = 0.20f,
                message = if (numChunks > 1) "Đang khởi chạy đa luồng cho $numChunks phân đoạn..." else "Đang tải lên CapCut Cloud..."
            )

            val semaphore = Semaphore(3) // Tối đa 3 phân đoạn xử lý đồng thời
            val chunkProgressMap = ConcurrentHashMap<Int, Float>()
            val chunkStatusMap = ConcurrentHashMap<Int, String>()

            fun updateCombinedProgress() {
                val totalProgress = chunkProgressMap.values.sum() / numChunks.coerceAtLeast(1)
                val overall = 0.20f + totalProgress * 0.50f // Đi từ 20% đến 70%
                val statusOverview = if (numChunks > 1) {
                    chunkStatusMap.entries.sortedBy { it.key }
                        .joinToString(" | ") { "P${it.key + 1}: ${it.value}" }
                } else {
                    chunkStatusMap[0] ?: "Đang xử lý..."
                }
                val currentStage = if (totalProgress >= 0.40f) ProcessStage.STT_TRANSCRIBING else ProcessStage.UPLOADING_VOD
                _progressFlow.value = ProcessProgress(
                    stage = currentStage,
                    progress = overall.coerceIn(0.20f, 0.70f),
                    message = statusOverview
                )
            }

            // Khởi tạo trạng thái ban đầu cho các chunk
            for (i in 0 until numChunks) {
                chunkProgressMap[i] = 0f
                chunkStatusMap[i] = "Chờ xử lý"
            }

            val deferredResults = coroutineScope {
                chunkList.mapIndexed { idx, chunk ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            checkCancelled()

                            // Mỗi luồng worker sở hữu một DeviceConfig ngẫu nhiên độc lập
                            val workerDevice = DeviceConfig().randomize()
                            val workerUploader = CapCutVodUploader(device = workerDevice)
                            val workerSttClient = CapCutSttClient(device = workerDevice)

                            // 1. Tải lên VOD
                            chunkStatusMap[idx] = "Đang tải..."
                            updateCombinedProgress()

                            val uploadResult = workerUploader.uploadFile(chunk.file) { pct, msg ->
                                chunkProgressMap[idx] = pct * 0.40f
                                chunkStatusMap[idx] = "Tải ${(pct * 100).toInt()}%"
                                updateCombinedProgress()
                            }

                            checkCancelled()

                            // 2. Nhận diện giọng nói STT
                            chunkProgressMap[idx] = 0.40f
                            chunkStatusMap[idx] = if (useCapcutTrans) "CapCut dịch..." else "Đang STT..."
                            updateCombinedProgress()

                            val chunkDuration = if (chunk.durationMs > 0) chunk.durationMs else uploadResult.durationMs
                            val chunkDoc = workerSttClient.transcribeAudio(
                                audioVid = uploadResult.vid,
                                audioMd5 = uploadResult.md5,
                                durationMs = chunkDuration,
                                language = sourceLanguage,
                                useTranslation = useCapcutTrans,
                                translationLanguage = targetLanguage,
                                timeOffsetMs = chunk.startMs
                            ) { pct, msg ->
                                chunkProgressMap[idx] = 0.40f + pct * 0.60f
                                chunkStatusMap[idx] = if (useCapcutTrans) "Dịch ${(pct * 100).toInt()}%" else "STT ${(pct * 100).toInt()}%"
                                updateCombinedProgress()
                            }

                            chunkProgressMap[idx] = 1.0f
                            chunkStatusMap[idx] = "Xong (${chunkDoc.size} câu)"
                            updateCombinedProgress()

                            Pair(idx, chunkDoc)
                        }
                    }
                }
            }

            val results = deferredResults.awaitAll().sortedBy { it.first }
            val allSubtitles = SubtitleDocument()
            results.forEach { (_, doc) ->
                allSubtitles.items.addAll(doc.items)
            }

            if (allSubtitles.isEmpty) {
                throw IllegalStateException("Không nhận diện được bất kỳ câu thoại nào trong tệp này.")
            }

            checkCancelled()

            // -----------------------------------------------------------------
            // BƯỚC 4: DỊCH PHỤ ĐỀ (NẾU CHỌN GEMINI AI)
            // -----------------------------------------------------------------
            val finalDoc = if (translationEngine.startsWith("gemini")) {
                _progressFlow.value = ProcessProgress(
                    stage = ProcessStage.AI_TRANSLATING,
                    progress = 0.70f,
                    message = "Đang chuẩn bị dịch ${allSubtitles.size} câu phụ đề qua $translationEngine..."
                )

                val translator = GeminiTranslator(
                    apiKeys = apiKeys,
                    modelId = translationEngine
                )

                translator.translateSubtitles(
                    document = allSubtitles,
                    stylePreset = stylePreset,
                    customPrompt = customPrompt,
                    targetLanguage = targetLanguage,
                    chunkSize = 45,
                    threadCount = geminiThreadCount
                ) { pct, msg ->
                    val overallPct = 0.70f + pct * 0.28f
                    _progressFlow.value = ProcessProgress(
                        stage = ProcessStage.AI_TRANSLATING,
                        progress = overallPct,
                        message = msg
                    )
                }
            } else {
                // Đã được dịch bởi CapCut hoặc chọn giữ nguyên tiếng gốc
                allSubtitles
            }

            // Lưu file SRT nếu có chỉ định
            outputSrtFile?.let {
                finalDoc.saveToFile(it, mode = "translated")
            }

            _progressFlow.value = ProcessProgress(
                stage = ProcessStage.COMPLETED,
                progress = 1.0f,
                message = "Hoàn tất! Đã tạo ${finalDoc.size} câu phụ đề.",
                resultDocument = finalDoc
            )

            return@withContext finalDoc
        } catch (e: Exception) {
            if (isCancelled || e is CancellationException) {
                _progressFlow.value = ProcessProgress(stage = ProcessStage.CANCELLED, message = "Đã huỷ bởi người dùng.")
                throw e
            }
            _progressFlow.value = ProcessProgress(stage = ProcessStage.ERROR, message = "Lỗi: ${e.message}", error = e)
            throw e
        } finally {
            // Dọn dẹp bộ nhớ tạm
            try {
                sessionDir.deleteRecursively()
            } catch (ignored: Exception) {}
        }
    }

    private fun checkCancelled() {
        if (isCancelled) throw CancellationException("Tác vụ đã bị huỷ bởi người dùng.")
    }
}
