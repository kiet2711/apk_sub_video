package com.capcut.capsub.data.model

data class SubtitleItem(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    var translatedText: String = ""
) {
    /**
     * Lấy nội dung hiển thị theo chế độ:
     * - "translated": Ưu tiên tiếng Việt đã dịch (fallback sang gốc nếu chưa có)
     * - "original": Tiếng gốc nguyên bản (Trung/Anh)
     * - "bilingual": Dòng trên gốc, dòng dưới dịch
     */
    fun getDisplayText(mode: String = "translated"): String {
        return when (mode.lowercase()) {
            "original" -> originalText
            "bilingual" -> if (translatedText.isNotBlank()) "$originalText\n$translatedText" else originalText
            else -> translatedText.ifBlank { originalText }
        }
    }

    /**
     * Định dạng mốc thời gian chuẩn SRT: 00:01:23,456 --> 00:01:26,789
     */
    fun formatSrtTimecode(): String {
        return "${msToSrt(startMs)} --> ${msToSrt(endMs)}"
    }

    /**
     * Định dạng mốc thời gian chuẩn ASS: 0:01:23.45
     */
    fun formatAssTimecode(): Pair<String, String> {
        return Pair(msToAss(startMs), msToAss(endMs))
    }

    companion object {
        fun msToSrt(ms: Long): String {
            val totalSec = (ms / 1000).coerceAtLeast(0)
            val milli = (ms % 1000).coerceAtLeast(0)
            val hours = totalSec / 3600
            val mins = (totalSec % 3600) / 60
            val secs = totalSec % 60
            return String.format("%02d:%02d:%02d,%03d", hours, mins, secs, milli)
        }

        fun msToAss(ms: Long): String {
            val totalSec = (ms / 1000).coerceAtLeast(0)
            val centi = ((ms % 1000) / 10).coerceAtLeast(0)
            val hours = totalSec / 3600
            val mins = (totalSec % 3600) / 60
            val secs = totalSec % 60
            return String.format("%d:%02d:%02d.%02d", hours, mins, secs, centi)
        }
    }
}
