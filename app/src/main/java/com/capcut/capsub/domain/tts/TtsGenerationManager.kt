package com.capcut.capsub.domain.tts

import android.content.Context
import android.util.Log
import com.capcut.capsub.data.api.CapCutTtsClient
import com.capcut.capsub.data.model.DeviceConfig
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.model.SubtitleItem
import com.capcut.capsub.data.model.VoiceItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

data class TtsFailedItem(
    val itemId: Int,
    val text: String,
    val reason: String,
    val filePath: String = ""
)

data class TtsProgressState(
    val isRunning: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val currentSentence: String = "",
    val speedPerSec: Float = 0f,
    val isCancelled: Boolean = false,
    val isFinished: Boolean = false,
    val errorMessage: String? = null,
    val failedItems: List<TtsFailedItem> = emptyList()
)

/**
 * Quản lý tạo TTS đa luồng và luôn kiểm kê lại toàn bộ cache sau mỗi đợt.
 * Số luồng giữ nguyên cấu hình của người dùng (1..100, mặc định 50).
 */
class TtsGenerationManager(private val context: Context) {

    private val _progress = MutableStateFlow(TtsProgressState())
    val progress: StateFlow<TtsProgressState> = _progress.asStateFlow()

    companion object {
        fun isPronounceable(text: String): Boolean {
            return text.any { it.isLetterOrDigit() }
        }

        fun isTrulyBlankSubtitle(item: SubtitleItem): Boolean {
            return !item.originalText.any { it.isLetterOrDigit() } && !item.translatedText.any { it.isLetterOrDigit() }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    fun startGeneration(
        subtitleDoc: SubtitleDocument,
        voice: VoiceItem,
        threadCount: Int = 50,
        subMode: String = "translated",
        forceRegenerate: Boolean = false,
        onCompleted: (() -> Unit)? = null
    ) {
        stopActiveJob()
        subtitleDoc.reindex()
        val cacheDir = TtsCacheHelper.getCacheDir(context, subtitleDoc, voice.voiceType)
        val items = subtitleDoc.items.filter { item ->
            val validation = AudioFileValidator.validate(File(cacheDir, "sub_${item.id}.mp3"))
            !isTrulyBlankSubtitle(item) && (forceRegenerate || !validation.isValid)
        }

        if (items.isEmpty()) {
            finishFromAudit(subtitleDoc, voice, subMode, emptyMap(), onCompleted)
            return
        }

        runBatch(
            subtitleDoc = subtitleDoc,
            voice = voice,
            threadCount = threadCount,
            subMode = subMode,
            items = items,
            forceRegenerate = forceRegenerate,
            onCompleted = onCompleted
        )
    }

    fun retryFailedItems(
        subtitleDoc: SubtitleDocument,
        voice: VoiceItem,
        threadCount: Int = 50,
        subMode: String = "translated",
        editedTexts: Map<Int, String> = emptyMap(),
        onCompleted: (() -> Unit)? = null
    ) {
        val failedIds = _progress.value.failedItems.map { it.itemId }.toSet()
        if (failedIds.isEmpty()) return
        applyEditedTexts(subtitleDoc, editedTexts, subMode)
        val items = subtitleDoc.items.filter { it.id in failedIds && !isTrulyBlankSubtitle(it) }
        stopActiveJob()
        runBatch(subtitleDoc, voice, threadCount, subMode, items, true, onCompleted)
    }

    fun retryFailedItem(
        subtitleDoc: SubtitleDocument,
        voice: VoiceItem,
        itemId: Int,
        editedText: String? = null,
        threadCount: Int = 50,
        subMode: String = "translated",
        onCompleted: (() -> Unit)? = null
    ) {
        val item = subtitleDoc.items.firstOrNull { it.id == itemId } ?: return
        if (editedText != null) {
            applyEditedTexts(subtitleDoc, mapOf(itemId to editedText), subMode)
        }
        if (isTrulyBlankSubtitle(item)) return
        stopActiveJob()
        runBatch(subtitleDoc, voice, threadCount, subMode, listOf(item), true, onCompleted)
    }

    /**
     * Bỏ qua tất cả câu lỗi hiện tại, đánh dấu hoàn tất và giữ nguyên các câu đã tạo thành công.
     */
    fun skipFailedItems(
        subtitleDoc: SubtitleDocument,
        voice: VoiceItem,
        subMode: String = "translated",
        onCompleted: (() -> Unit)? = null
    ) {
        stopActiveJob()
        val audit = TtsCacheHelper.auditAndLinkAudioFiles(context, subtitleDoc, voice.voiceType)
        val validTargetCount = subtitleDoc.items.count { isPronounceable(textFor(it, subMode)) }
        _progress.update { state ->
            state.copy(
                isRunning = false,
                isFinished = true,
                failedItems = emptyList(),
                completedCount = audit.linkedCount,
                totalCount = validTargetCount,
                currentSentence = "Đã bỏ qua các câu lỗi. Đã sẵn sàng phát video (${audit.linkedCount} câu)!",
                errorMessage = null
            )
        }
        onCompleted?.invoke()
    }

    private fun runBatch(
        subtitleDoc: SubtitleDocument,
        voice: VoiceItem,
        threadCount: Int,
        subMode: String,
        items: List<SubtitleItem>,
        forceRegenerate: Boolean,
        onCompleted: (() -> Unit)?
    ) {
        if (items.isEmpty()) {
            finishFromAudit(subtitleDoc, voice, subMode, emptyMap(), onCompleted)
            return
        }

        val total = items.size
        val effectiveThreads = threadCount.coerceIn(1, 100)
        _progress.value = TtsProgressState(
            isRunning = true,
            totalCount = total,
            currentSentence = "Đang khởi tạo $effectiveThreads luồng tổng hợp (${voice.displayName})..."
        )

        activeJob = scope.launch {
            val cacheDir = TtsCacheHelper.getCacheDir(context, subtitleDoc, voice.voiceType)
            val processedCounter = AtomicInteger(0)
            val successCounter = AtomicInteger(0)
            val failureReasons = ConcurrentHashMap<Int, String>()
            val startTimeMs = System.currentTimeMillis()
            val lastProgressUpdateMs = AtomicLong(0L)

            // Hàng đợi phân phối câu thoại theo mô hình Worker Pool (tránh tràn RAM)
            val channel = Channel<SubtitleItem>(capacity = effectiveThreads * 2)

            // Producer nạp các câu thoại vào hàng đợi
            val producer = launch {
                for (item in items) {
                    if (_progress.value.isCancelled) break
                    channel.send(item)
                }
                channel.close()
            }

            // Tạo đúng effectiveThreads Worker xử lý song song
            val workers = (0 until effectiveThreads).map { workerIdx ->
                launch {
                    val client = CapCutTtsClient(device = DeviceConfig().randomize())
                    // Khởi động so le nhẹ để tránh nghẽn kết nối trong cùng 1 mili-giây
                    delay(workerIdx * 15L)

                    for (item in channel) {
                        if (_progress.value.isCancelled) break
                        val text = textFor(item, subMode)
                        val destFile = File(cacheDir, "sub_${item.id}.mp3")

                        if (!isPronounceable(text)) {
                            // Câu chỉ chứa ký tự dấu câu / không phát âm được -> không gọi CapCut API tránh lỗi HTTP 400
                            item.audioFilePath = null
                            item.audioDurationMs = 0L
                            if (!isTrulyBlankSubtitle(item)) {
                                failureReasons[item.id] = "Bản dịch bị nuốt/chỉ chứa dấu câu (${text.ifBlank { "trống" }})"
                            }
                            processedCounter.incrementAndGet()
                            continue
                        }

                        try {
                            val currentValidation = AudioFileValidator.validate(destFile)
                            if (forceRegenerate || !currentValidation.isValid) {
                                client.generateSpeechToFile(
                                    text = text,
                                    voiceType = voice.voiceType,
                                    resourceId = voice.resourceId,
                                    rate = "1.0",
                                    destFile = destFile
                                )
                            }

                            val validation = AudioFileValidator.validate(destFile)
                            if (!validation.isValid) error(validation.reason)
                            attachAudio(item, destFile, validation.durationMs)
                            successCounter.incrementAndGet()
                        } catch (error: Exception) {
                            item.audioFilePath = null
                            item.audioDurationMs = 0L
                            val reason = error.message ?: error.javaClass.simpleName
                            failureReasons[item.id] = reason
                            Log.e("TtsGenerationManager", "Lỗi câu #${item.id} ('${text.take(30)}'): $reason", error)
                        } finally {
                            val processed = processedCounter.incrementAndGet()
                            val now = System.currentTimeMillis()
                            val lastUpdate = lastProgressUpdateMs.get()

                            // Tiết chế nhịp cập nhật giao diện (throttled ~200ms) để không làm đơ Compose UI
                            if (now - lastUpdate >= 200L || processed == total) {
                                lastProgressUpdateMs.set(now)
                                val elapsedSec = (now - startTimeMs) / 1000f
                                val speed = if (elapsedSec > 0.5f) processed / elapsedSec else 0f
                                val failed = processed - successCounter.get()
                                _progress.update { state ->
                                    state.copy(
                                        completedCount = processed,
                                        speedPerSec = speed,
                                        currentSentence = if (failed > 0) {
                                            "Đã xử lý $processed/$total câu ($failed câu đang lỗi)"
                                        } else {
                                            "Đã xử lý $processed/$total: ${text.take(35)}"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            producer.join()
            workers.joinAll()

            if (!_progress.value.isCancelled) {
                finishFromAudit(subtitleDoc, voice, subMode, failureReasons, onCompleted)
            }
        }
    }

    private fun finishFromAudit(
        subtitleDoc: SubtitleDocument,
        voice: VoiceItem,
        subMode: String,
        failureReasons: Map<Int, String>,
        onCompleted: (() -> Unit)?
    ) {
        val audit = TtsCacheHelper.auditAndLinkAudioFiles(context, subtitleDoc, voice.voiceType)
        val failedItems = audit.issues.mapNotNull { issue ->
            val item = subtitleDoc.items.firstOrNull { it.id == issue.itemId } ?: return@mapNotNull null
            val text = textFor(item, subMode)
            if (isTrulyBlankSubtitle(item)) return@mapNotNull null
            val reason = if (!isPronounceable(text)) {
                "Bản dịch bị nuốt/chỉ chứa dấu câu (${text.ifBlank { "trống" }})"
            } else {
                failureReasons[item.id] ?: issue.reason
            }
            TtsFailedItem(
                itemId = item.id,
                text = text.ifBlank { item.originalText },
                reason = reason,
                filePath = issue.filePath
            )
        }.sortedBy { it.itemId }

        val validTargetCount = subtitleDoc.items.count { !isTrulyBlankSubtitle(it) }
        val successCount = validTargetCount - failedItems.size
        val message = if (failedItems.isEmpty()) {
            "Đã hoàn thành lồng tiếng toàn bộ $successCount câu thoại!"
        } else {
            "Đã tạo $successCount/$validTargetCount câu. Còn ${failedItems.size} câu cần xử lý."
        }
        _progress.value = TtsProgressState(
            isRunning = false,
            completedCount = successCount,
            totalCount = validTargetCount,
            currentSentence = message,
            isFinished = failedItems.isEmpty(),
            failedItems = failedItems,
            errorMessage = failedItems.firstOrNull()?.reason
        )
        onCompleted?.invoke()
    }

    private fun applyEditedTexts(doc: SubtitleDocument, editedTexts: Map<Int, String>, subMode: String) {
        editedTexts.forEach { (itemId, newText) ->
            val cleanText = newText.trim()
            val item = doc.items.firstOrNull { it.id == itemId } ?: return@forEach
            if (subMode.equals("original", ignoreCase = true)) {
                item.originalText = cleanText
            } else {
                item.translatedText = cleanText
            }
        }
    }

    private fun textFor(item: SubtitleItem, subMode: String): String = when (subMode.lowercase()) {
        "original" -> item.originalText.trim()
        else -> item.translatedText.ifBlank { item.originalText }.trim()
    }

    private fun attachAudio(item: SubtitleItem, file: File, durationMs: Long) {
        item.audioFilePath = file.absolutePath
        item.audioDurationMs = durationMs
        val srtDurationMs = (item.endMs - item.startMs).coerceAtLeast(200L)
        item.playbackSpeed = if (durationMs > srtDurationMs) {
            val factor = (durationMs.toFloat() / srtDurationMs.toFloat()).coerceIn(1.0f, 2.2f)
            Math.round(factor * 10f) / 10f
        } else {
            1.0f
        }
    }

    fun cancel() {
        if (activeJob?.isActive == true) {
            _progress.update {
                it.copy(isRunning = false, isCancelled = true, currentSentence = "Đã hủy tiến trình lồng tiếng")
            }
            activeJob?.cancel()
        }
    }

    private fun stopActiveJob() {
        activeJob?.cancel()
        activeJob = null
    }
}
