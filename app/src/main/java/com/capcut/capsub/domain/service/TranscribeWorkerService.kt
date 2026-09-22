package com.capcut.capsub.domain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.capcut.capsub.MainActivity
import com.capcut.capsub.data.model.ProcessProgress
import com.capcut.capsub.data.model.ProcessStage
import com.capcut.capsub.data.repository.SettingsRepository
import com.capcut.capsub.domain.media.NetworkHeaderHelper
import com.capcut.capsub.domain.pipeline.SubtitlingPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Foreground Service giúp app duy trì pipeline xử lý liên tục mà không bị hệ thống Android tắt (Doze Mode).
 */
class TranscribeWorkerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentPipeline: SubtitlingPipeline? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CapSub:PipelineWakeLock").apply {
            acquire(30 * 60 * 1000L) // 30 phút tối đa
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_CANCEL) {
            currentPipeline?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }

        val rawVideoUriStr = intent?.getStringExtra(EXTRA_VIDEO_URI) ?: return START_NOT_STICKY
        val videoUriStr = if (NetworkHeaderHelper.isRemoteUrl(rawVideoUriStr) || rawVideoUriStr.contains("b23.tv") || rawVideoUriStr.contains("BV")) {
            NetworkHeaderHelper.extractCleanUrl(rawVideoUriStr)
        } else {
            rawVideoUriStr
        }
        val videoNameExtra = intent.getStringExtra(EXTRA_VIDEO_NAME)
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
        val sourceLang = intent.getStringExtra(EXTRA_SOURCE_LANG) ?: "zh-CN"
        val customPromptExtra = intent.getStringExtra(EXTRA_CUSTOM_PROMPT)
        val outSrtPath = intent.getStringExtra(EXTRA_OUT_SRT)

        startForeground(NOTIFICATION_ID, buildNotification("Đang chuẩn bị xử lý...", 0))

        val repo = SettingsRepository(this)
        val finalCustomPrompt = if (!customPromptExtra.isNullOrBlank()) customPromptExtra else repo.geminiCustomPrompt

        val pipeline = SubtitlingPipeline(
            context = applicationContext,
            apiKeys = repo.geminiApiKeys,
            translationEngine = repo.selectedModel,
            stylePreset = repo.selectedStyle,
            customPrompt = finalCustomPrompt,
            targetLanguage = repo.targetLanguage,
            geminiThreadCount = repo.geminiThreadCount
        )
        currentPipeline = pipeline

        serviceScope.launch {
            val progressJob = launch {
                pipeline.progressFlow.collect { progress ->
                    // COMPLETED chỉ được phát ra cho UI sau khi lịch sử đã ghi thành công.
                    if (progress.stage != ProcessStage.COMPLETED) {
                        _sharedProgressFlow.value = progress
                        val pct = (progress.progress * 100).toInt()
                        updateNotification(progress.message, pct)
                    }
                }
            }

            try {
                val outSrtFile = if (outSrtPath != null) File(outSrtPath) else null
                val resultDoc = pipeline.execute(
                    videoUri = Uri.parse(videoUriStr),
                    totalDurationMs = durationMs,
                    sourceLanguage = sourceLang,
                    outputSrtFile = outSrtFile
                )
                withContext(Dispatchers.IO) {
                    val historyRepo = com.capcut.capsub.data.repository.HistoryRepository(applicationContext)
                    val vUri = Uri.parse(videoUriStr)
                    val vName = videoNameExtra?.takeIf { it.isNotBlank() }
                        ?: getFileName(applicationContext, vUri)
                        ?: "Video_${System.currentTimeMillis()}"
                    historyRepo.saveHistory(
                        videoUri = vUri,
                        videoName = vName,
                        durationMs = durationMs,
                        document = resultDoc,
                        translationEngine = repo.selectedModel,
                        sourceLanguage = sourceLang
                    )
                }

                val completed = ProcessProgress(
                    stage = ProcessStage.COMPLETED,
                    progress = 1.0f,
                    message = "Hoàn tất! Đã lưu ${resultDoc.size} câu phụ đề vào lịch sử.",
                    resultDocument = resultDoc
                )
                _sharedProgressFlow.value = completed
                updateNotification(completed.message, 100)
            } catch (e: Exception) {
                val pipelineState = pipeline.progressFlow.value
                val terminalState = if (pipelineState.stage == ProcessStage.COMPLETED) {
                    ProcessProgress(
                        stage = ProcessStage.ERROR,
                        message = "Đã tạo phụ đề nhưng không thể lưu lịch sử: ${e.message}",
                        error = e
                    )
                } else {
                    pipelineState
                }
                _sharedProgressFlow.value = terminalState
                updateNotification(terminalState.message, (terminalState.progress * 100).toInt())
                android.util.Log.e("CapSubWorker", terminalState.message, e)
            } finally {
                progressJob.cancel()
                stopSelf(startId)
            }
        }

        return START_STICKY
    }

    private fun getFileName(context: android.content.Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    override fun onDestroy() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tiến trình tạo phụ đề CapSub",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị tiến trình tách âm thanh, nhận diện giọng nói và dịch phụ đề"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String, progress: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, TranscribeWorkerService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CapSub AI Studio")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setProgress(100, progress, progress <= 0)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Huỷ", cancelPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message, progress))
    }

    companion object {
        const val CHANNEL_ID = "capsub_pipeline_channel"
        const val NOTIFICATION_ID = 1001

        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_NAME = "extra_video_name"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_SOURCE_LANG = "extra_source_lang"
        const val EXTRA_CUSTOM_PROMPT = "extra_custom_prompt"
        const val EXTRA_OUT_SRT = "extra_out_srt"
        const val ACTION_CANCEL = "com.capcut.capsub.ACTION_CANCEL"

        private val _sharedProgressFlow = MutableStateFlow(ProcessProgress())
        val sharedProgressFlow: StateFlow<ProcessProgress> = _sharedProgressFlow

        fun start(
            context: Context,
            videoUri: Uri,
            videoName: String,
            durationMs: Long,
            sourceLang: String,
            customPrompt: String,
            outSrtFile: File?
        ) {
            val intent = Intent(context, TranscribeWorkerService::class.java).apply {
                data = videoUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(EXTRA_VIDEO_URI, videoUri.toString())
                putExtra(EXTRA_VIDEO_NAME, videoName)
                putExtra(EXTRA_DURATION_MS, durationMs)
                putExtra(EXTRA_SOURCE_LANG, sourceLang)
                putExtra(EXTRA_CUSTOM_PROMPT, customPrompt)
                putExtra(EXTRA_OUT_SRT, outSrtFile?.absolutePath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
