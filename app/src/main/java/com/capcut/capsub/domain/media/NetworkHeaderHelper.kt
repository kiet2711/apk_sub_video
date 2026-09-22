package com.capcut.capsub.domain.media

import android.net.Uri

/**
 * Tiện ích quản lý HTTP Headers cho việc stream và tải luồng video/audio từ các nền tảng online.
 * Tự động chèn Referer và User-Agent phù hợp để vượt qua cơ chế chặn Anti-Hotlink (HTTP 403 Forbidden).
 */
object NetworkHeaderHelper {

    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * Kiểm tra xem Uri có phải là URL mạng (http/https) hay không.
     */
    fun isRemoteUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }

    fun isRemoteUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.trim().lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    /**
     * Tự động lọc và bóc tách URL sạch từ chuỗi văn bản (kể cả khi người dùng dán kèm tiêu đề, icon, tiếng Trung từ nút Share).
     * Ví dụ: "【免费观看...】 https://b23.tv/xyz" -> "https://b23.tv/xyz"
     */
    fun extractCleanUrl(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val trimmed = input.trim()

        // 1. Tìm URL dạng https://... hoặc http://...
        val httpRegex = Regex("https?://[^\\s]+")
        val httpMatch = httpRegex.find(trimmed)
        if (httpMatch != null) {
            return httpMatch.value
        }

        // 2. Tìm link rút gọn b23.tv/... không có https://
        val b23Regex = Regex("b23\\.tv/[a-zA-Z0-9]+")
        val b23Match = b23Regex.find(trimmed)
        if (b23Match != null) {
            return "https://" + b23Match.value
        }

        // 3. Tìm mã BV...
        val bvRegex = Regex("BV[a-zA-Z0-9]{10}", RegexOption.IGNORE_CASE)
        val bvMatch = bvRegex.find(trimmed)
        if (bvMatch != null) {
            return "https://www.bilibili.com/video/" + bvMatch.value
        }

        // 4. Tìm mã av...
        val avRegex = Regex("av(\\d+)", RegexOption.IGNORE_CASE)
        val avMatch = avRegex.find(trimmed)
        if (avMatch != null) {
            return "https://www.bilibili.com/video/" + avMatch.value
        }

        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    /**
     * Cung cấp danh sách Headers phù hợp cho từng domain.
     */
    fun getHeadersForUri(uri: Uri?, cookie: String? = null): Map<String, String> {
        if (uri == null || !isRemoteUri(uri)) return emptyMap()
        return getHeadersForUrl(uri.toString(), cookie)
    }

    /**
     * Cung cấp danh sách Headers phù hợp cho từng URL chuỗi.
     */
    fun getHeadersForUrl(url: String?, cookie: String? = null): Map<String, String> {
        if (url.isNullOrBlank() || !isRemoteUrl(url)) return emptyMap()

        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = DEFAULT_USER_AGENT

        val lowerUrl = url.lowercase()

        when {
            // Bilibili CDN & API
            lowerUrl.contains("bilivideo.com") || lowerUrl.contains("bilibili.com") || lowerUrl.contains("biliapi.net") -> {
                headers["Referer"] = "https://www.bilibili.com/"
                headers["Origin"] = "https://www.bilibili.com"
                if (!cookie.isNullOrBlank()) {
                    val formatted = if (!cookie.contains("=")) "SESSDATA=$cookie" else cookie
                    headers["Cookie"] = formatted
                }
            }
            // Douyin CDN
            lowerUrl.contains("douyin.com") || lowerUrl.contains("douyinvod.com") || lowerUrl.contains("iesdouyin.com") -> {
                headers["Referer"] = "https://www.douyin.com/"
                headers["Origin"] = "https://www.douyin.com"
            }
            // TikTok CDN
            lowerUrl.contains("tiktok.com") || lowerUrl.contains("tiktokv.com") -> {
                headers["Referer"] = "https://www.tiktok.com/"
                headers["Origin"] = "https://www.tiktok.com"
            }
            // Kuaishou CDN
            lowerUrl.contains("kuaishou.com") || lowerUrl.contains("yximgs.com") -> {
                headers["Referer"] = "https://www.kuaishou.com/"
            }
        }

        return headers
    }

    /**
     * Lấy tên gợi ý ngắn gọn dựa trên URL hoặc domain
     */
    fun getSuggestedTitle(url: String): String {
        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: ""
            val lastPathSegment = uri.lastPathSegment

            val sourceTag = when {
                host.contains("bilivideo") || host.contains("bilibili") -> "Bilibili"
                host.contains("douyin") -> "Douyin"
                host.contains("tiktok") -> "TikTok"
                host.contains("kuaishou") -> "Kuaishou"
                host.contains("youtube") || host.contains("googlevideo") -> "YouTube"
                else -> host.ifBlank { "Online" }
            }

            if (!lastPathSegment.isNullOrBlank() && lastPathSegment.contains(".")) {
                val cleanName = lastPathSegment.substringBeforeLast(".")
                if (cleanName.length in 3..40) {
                    return "$sourceTag - $cleanName"
                }
            }

            return "Video Online ($sourceTag)"
        } catch (_: Exception) {
            return "Video Online"
        }
    }
}
