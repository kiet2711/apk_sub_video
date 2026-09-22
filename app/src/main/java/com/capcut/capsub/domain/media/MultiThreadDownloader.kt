package com.capcut.capsub.domain.media

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Trình tải đa luồng song song (Multi-Thread Range Downloader) hiệu năng cao.
 * Hỗ trợ xoay vòng các cụm máy chủ CDN Bilibili (Tencent, Alibaba, Huawei) để bứt phá băng thông quốc tế.
 */
object MultiThreadDownloader {

    private const val TAG = "MultiThreadDownloader"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val CDN_FALLBACK_HOSTS = listOf(
        "upos-sz-mirrorcos.bilivideo.com",
        "upos-sz-mirrorali.bilivideo.com",
        "upos-sz-mirrorhw.bilivideo.com",
        "upos-sz-mirror08c.bilivideo.com"
    )

    data class Chunk(val index: Int, val start: Long, val end: Long)

    /**
     * Tải tệp từ URL về file đích với số luồng song song tuỳ chỉnh (mặc định 16 luồng).
     */
    suspend fun downloadFile(
        url: String,
        outputFile: File,
        headers: Map<String, String> = emptyMap(),
        concurrency: Int = 16,
        progressCallback: ((Float, String) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val effectiveConcurrency = concurrency.coerceIn(8, 32)
        progressCallback?.invoke(0.02f, "Đang kết nối và kiểm tra kích thước...")

        outputFile.parentFile?.mkdirs()

        // 1. Thăm dò dung lượng file qua Range 0-1023
        var totalBytes = probeFileSize(cleanUrl, headers)

        if (totalBytes <= 0L) {
            Log.w(TAG, "Máy chủ không trả về Content-Range, chuyển sang tải stream đơn luồng")
            return@withContext downloadSingleStream(cleanUrl, outputFile, headers, progressCallback)
        }

        val totalMB = totalBytes / (1024.0 * 1024.0)
        Log.i(TAG, "Bắt đầu tải đa luồng: %.2f MB với $effectiveConcurrency luồng song song".format(totalMB))

        // 2. Chia file thành các phân đoạn 3MB
        val chunkSize = 3L * 1024L * 1024L
        val chunks = mutableListOf<Chunk>()
        var curr = 0L
        var chunkIdx = 0
        while (curr < totalBytes) {
            val end = (curr + chunkSize - 1L).coerceAtMost(totalBytes - 1L)
            chunks.add(Chunk(chunkIdx++, curr, end))
            curr = end + 1L
        }

        // 3. Khởi tạo tệp và cấp phát dung lượng
        val raf = RandomAccessFile(outputFile, "rw")
        try {
            raf.setLength(totalBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Không thể setLength trước cho file: ${e.message}")
        }

        val rafLock = Any()
        val downloadedBytes = AtomicLong(0L)
        val chunkQueueIndex = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        var lastNotifyTime = 0L

        // 4. Khởi chạy các worker coroutine song song
        val workers = (0 until effectiveConcurrency).map { workerId ->
            async(Dispatchers.IO) {
                while (true) {
                    val idx = chunkQueueIndex.getAndIncrement()
                    if (idx >= chunks.size) break
                    val chunk = chunks[idx]

                    val buffer = fetchChunkWithRetry(cleanUrl, chunk.start, chunk.end, headers, workerId)
                    synchronized(rafLock) {
                        raf.seek(chunk.start)
                        raf.write(buffer)
                    }

                    val currentDownloaded = downloadedBytes.addAndGet(buffer.size.toLong())
                    val now = System.currentTimeMillis()
                    if (now - lastNotifyTime > 250L || currentDownloaded >= totalBytes) {
                        lastNotifyTime = now
                        val elapsedSec = (now - startTime) / 1000.0
                        val speedMBs = if (elapsedSec > 0.1) (currentDownloaded / (1024.0 * 1024.0)) / elapsedSec else 0.0
                        val pct = (currentDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                        val msg = "Đang tải đa luồng (%d luồng): %.1f / %.1f MB (%.1f MB/s)".format(
                            effectiveConcurrency,
                            currentDownloaded / (1024.0 * 1024.0),
                            totalMB,
                            speedMBs
                        )
                        progressCallback?.invoke(pct, msg)
                    }
                }
            }
        }

        try {
            workers.awaitAll()
        } finally {
            try {
                raf.close()
            } catch (_: Exception) {}
        }

        val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
        val avgSpeed = if (totalTime > 0.1) totalMB / totalTime else 0.0
        Log.i(TAG, "Tải hoàn tất sau %.2f s! Tốc độ trung bình: %.2f MB/s".format(totalTime, avgSpeed))
        progressCallback?.invoke(1.0f, "Tải hoàn tất: %.1f MB (%.1f MB/s)".format(totalMB, avgSpeed))

        return@withContext totalBytes
    }

    private fun probeFileSize(url: String, headers: Map<String, String>): Long {
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("Range", "bytes=0-1023")
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            val resp = httpClient.newCall(reqBuilder.build()).execute()
            if (resp.isSuccessful || resp.code == 206) {
                val cr = resp.header("Content-Range")
                if (!cr.isNullOrBlank() && cr.contains("/")) {
                    val total = cr.substringAfterLast("/").toLongOrNull()
                    if (total != null && total > 0L) return total
                }
                val cl = resp.header("Content-Length")?.toLongOrNull()
                if (cl != null && cl > 1024L) return cl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi probe kích thước: ${e.message}")
        }
        return 0L
    }

    private fun fetchChunkWithRetry(
        rawUrl: String,
        start: Long,
        end: Long,
        headers: Map<String, String>,
        workerId: Int,
        maxRetries: Int = 4
    ): ByteArray {
        var lastEx: Exception? = null

        for (attempt in 0 until maxRetries) {
            try {
                // Xoay vòng CDN mirror nếu là link video Bilibili (upos)
                var targetUrl = rawUrl
                if (rawUrl.contains("upos-")) {
                    val host = CDN_FALLBACK_HOSTS[(workerId + attempt) % CDN_FALLBACK_HOSTS.size]
                    targetUrl = rawUrl.replace(Regex("upos-[^/]+"), host)
                }

                val reqBuilder = Request.Builder()
                    .url(targetUrl)
                    .addHeader("Range", "bytes=$start-$end")
                headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                val resp = httpClient.newCall(reqBuilder.build()).execute()
                if (resp.isSuccessful || resp.code == 206) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        return bytes
                    }
                }
            } catch (e: Exception) {
                lastEx = e
                Thread.sleep(150L * (attempt + 1))
            }
        }

        throw lastEx ?: IllegalStateException("Không thể tải phân đoạn $start-$end sau $maxRetries lần thử")
    }

    private fun downloadSingleStream(
        url: String,
        outputFile: File,
        headers: Map<String, String>,
        progressCallback: ((Float, String) -> Unit)?
    ): Long {
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        val resp = httpClient.newCall(reqBuilder.build()).execute()
        if (!resp.isSuccessful) {
            throw IllegalStateException("Tải thất bại (HTTP ${resp.code})")
        }

        val body = resp.body ?: throw IllegalStateException("Phản hồi rỗng")
        val totalBytes = body.contentLength()
        var downloaded = 0L

        outputFile.outputStream().use { out ->
            body.byteStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                var lastNotify = 0L
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastNotify > 250L && totalBytes > 0L) {
                        lastNotify = now
                        val pct = (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                        progressCallback?.invoke(pct, "Đang tải: %.1f / %.1f MB".format(
                            downloaded / (1024.0 * 1024.0),
                            totalBytes / (1024.0 * 1024.0)
                        ))
                    }
                }
            }
        }
        return downloaded
    }
}
