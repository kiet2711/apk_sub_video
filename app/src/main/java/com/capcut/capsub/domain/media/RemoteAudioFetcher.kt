package com.capcut.capsub.domain.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.capcut.capsub.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class RemoteVideoInfo(
    val url: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val isAccessible: Boolean,
    val errorMessage: String? = null,
    val isBilibili: Boolean = false,
    val bvid: String? = null,
    val cid: Long = 0L,
    val coverUrl: String? = null,
    val hasExistingSubtitles: Boolean = false,
    val subtitleUrl: String? = null,
    val audioStreamUrl: String? = null,
    val videoStreamUrl: String? = null
)

/**
 * Tiện ích hỗ trợ phân tích thông tin và trích xuất luồng âm thanh từ URL video trực tuyến.
 * Tích hợp BilibiliResolver (DASH Audio riêng, nạp Subtitles Bilibili) và MultiThreadDownloader (8-32 luồng).
 */
object RemoteAudioFetcher {

    private const val TAG = "RemoteAudioFetcher"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Phân tích và lấy thông tin video online (thời lượng, kích thước, tiêu đề, phụ đề gốc nếu có).
     */
    suspend fun probeRemoteVideo(url: String, cookie: String = ""): RemoteVideoInfo = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()

        // 1. Kiểm tra nếu là link Bilibili (web, b23.tv, BV, av)
        if (BilibiliResolver.isBilibiliUrl(cleanUrl)) {
            try {
                val target = BilibiliResolver.resolveUrl(cleanUrl)
                if (!target.bvid.isNullOrBlank() || !target.aid.isNullOrBlank()) {
                    val details = BilibiliResolver.getVideoDetails(target, cookie)
                    val subtitles = BilibiliResolver.getSubtitles(details.bvid, details.cid, cookie)
                    val stream = BilibiliResolver.getPlayStream(details.bvid, details.cid, cookie)

                    val bestSub = subtitles.firstOrNull { !it.isAi } ?: subtitles.firstOrNull()

                    return@withContext RemoteVideoInfo(
                        url = cleanUrl,
                        title = details.title,
                        durationMs = details.durationSeconds * 1000L,
                        sizeBytes = if (stream.audioBandwidth > 0 && details.durationSeconds > 0) {
                            (stream.audioBandwidth * details.durationSeconds) / 8L
                        } else 0L,
                        isAccessible = true,
                        isBilibili = true,
                        bvid = details.bvid,
                        cid = details.cid,
                        coverUrl = details.coverUrl,
                        hasExistingSubtitles = subtitles.isNotEmpty(),
                        subtitleUrl = bestSub?.subtitleUrl,
                        audioStreamUrl = stream.audioUrl,
                        videoStreamUrl = stream.videoUrl
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi probe qua Bilibili API: ${e.message}. Chuyển sang HTTP probe.")
            }
        }

        // 2. Thăm dò thông thường qua HTTP HEAD/GET
        val headers = NetworkHeaderHelper.getHeadersForUrl(cleanUrl, cookie)
        val title = NetworkHeaderHelper.getSuggestedTitle(cleanUrl)
        var durationMs = 0L
        var sizeBytes = 0L

        try {
            val reqBuilder = Request.Builder().url(cleanUrl).head()
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            val response = httpClient.newCall(reqBuilder.build()).execute()

            if (response.isSuccessful) {
                sizeBytes = response.header("Content-Length")?.toLongOrNull() ?: 0L
            } else {
                val rangeReq = Request.Builder()
                    .url(cleanUrl)
                    .addHeader("Range", "bytes=0-1024")
                headers.forEach { (k, v) -> rangeReq.addHeader(k, v) }
                val rangeResp = httpClient.newCall(rangeReq.build()).execute()
                if (rangeResp.isSuccessful || rangeResp.code == 206) {
                    val cr = rangeResp.header("Content-Range")
                    sizeBytes = cr?.substringAfterLast("/")?.toLongOrNull() ?: 0L
                    if (sizeBytes == 0L) {
                        sizeBytes = rangeResp.header("Content-Length")?.toLongOrNull() ?: 0L
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi kiểm tra HTTP header: ${e.message}")
        }

        // Tính thời lượng nếu có tham số bitrate (bw)
        try {
            val uri = Uri.parse(cleanUrl)
            val bw = uri.getQueryParameter("bw")?.toLongOrNull()
            if (bw != null && bw > 0L && sizeBytes > 0L) {
                durationMs = (sizeBytes * 8L * 1000L) / bw
            }
        } catch (_: Exception) {}

        RemoteVideoInfo(
            url = cleanUrl,
            title = title,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            isAccessible = true
        )
    }

    /**
     * Trích xuất luồng âm thanh M4A từ URL trực tiếp qua mạng.
     * Sử dụng MultiThreadDownloader tải đa luồng song song (8-32 luồng) và bóc tách DASH Audio riêng cho Bilibili.
     */
    fun extractAudioFromRemoteUrl(
        context: Context,
        remoteUri: Uri,
        outputFile: File,
        progressCallback: ((Float, String) -> Unit)? = null
    ): Long {
        val settings = SettingsRepository(context)
        val concurrency = settings.downloadThreadCount
        val sessData = settings.bilibiliSessData
        val urlStr = remoteUri.toString()

        outputFile.parentFile?.mkdirs()

        // 1. Kiểm tra nếu là link Bilibili (hoặc b23.tv / BV / av)
        if (BilibiliResolver.isBilibiliUrl(urlStr)) {
            try {
                progressCallback?.invoke(0.05f, "Đang phân tích luồng audio DASH Bilibili...")
                val target = runBlocking { BilibiliResolver.resolveUrl(urlStr) }
                if (!target.bvid.isNullOrBlank() || !target.aid.isNullOrBlank()) {
                    val details = runBlocking { BilibiliResolver.getVideoDetails(target, sessData) }
                    val stream = runBlocking { BilibiliResolver.getPlayStream(details.bvid, details.cid, sessData) }

                    val audioUrl = stream.audioUrl
                    if (!audioUrl.isNullOrBlank()) {
                        Log.i(TAG, "Tìm thấy luồng audio Bilibili riêng biệt (~30-50MB), tiến hành tải đa luồng...")
                        val tempAudioFile = File(outputFile.parentFile, "bili_audio_stream.m4s")
                        val headers = NetworkHeaderHelper.getHeadersForUrl(audioUrl, sessData)

                        runBlocking {
                            MultiThreadDownloader.downloadFile(
                                url = audioUrl,
                                outputFile = tempAudioFile,
                                headers = headers,
                                concurrency = concurrency,
                                progressCallback = progressCallback
                            )
                        }

                        progressCallback?.invoke(0.95f, "Đang hoàn tất đóng gói file âm thanh...")
                        try {
                            AudioExtractor.extractAudio(context, Uri.fromFile(tempAudioFile), outputFile)
                        } catch (e: Exception) {
                            Log.w(TAG, "MediaExtractor không đọc được định dạng m4s trực tiếp, sao chép file: ${e.message}")
                            tempAudioFile.copyTo(outputFile, overwrite = true)
                        } finally {
                            tempAudioFile.delete()
                        }

                        return outputFile.length()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi bóc tách audio DASH Bilibili riêng: ${e.message}, chuyển sang tải đa luồng tổng quát", e)
            }
        }

        // 2. Link video MP4 thông thường hoặc fallback: Tải đa luồng song song qua MultiThreadDownloader
        progressCallback?.invoke(0.05f, "Đang tải video đa luồng ($concurrency luồng)...")
        val headers = NetworkHeaderHelper.getHeadersForUrl(urlStr, sessData)
        val tempVideoFile = File(outputFile.parentFile, "temp_stream_download.mp4")

        try {
            runBlocking {
                MultiThreadDownloader.downloadFile(
                    url = urlStr,
                    outputFile = tempVideoFile,
                    headers = headers,
                    concurrency = concurrency,
                    progressCallback = progressCallback
                )
            }

            // Bóc tách audio từ file cục bộ siêu tốc (1-2 giây)
            progressCallback?.invoke(0.95f, "Đang bóc tách âm thanh nội bộ...")
            AudioExtractor.extractAudio(
                context = context,
                videoUri = Uri.fromFile(tempVideoFile),
                outputFile = outputFile
            )
            return outputFile.length()
        } finally {
            try {
                tempVideoFile.delete()
            } catch (_: Exception) {}
        }
    }
}
