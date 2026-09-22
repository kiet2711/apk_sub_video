package com.capcut.capsub.data.model

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class SubtitleDocument(
    val items: MutableList<SubtitleItem> = mutableListOf()
) {
    val size: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()

    fun reindex() {
        items.sortBy { it.startMs }
        items.forEachIndexed { index, item ->
            item.id = index + 1
        }
    }

    fun normalizeTranslations() {
        items.forEach { it.normalizeTranslation() }
    }

    /** Khôi phục file cũ từng lưu "dòng Trung + dòng Việt" trong cùng một field. */
    fun recoverLegacyBilingualText(sourceLanguage: String) {
        if (!sourceLanguage.lowercase().startsWith("zh")) return
        items.forEach { item ->
            if (item.translatedText.trim() != item.originalText.trim()) return@forEach
            splitChineseSourceAndTranslation(item.originalText)?.let { (source, translation) ->
                item.originalText = source
                item.translatedText = translation
            }
        }
    }

    fun getActiveItem(currentPositionMs: Long): SubtitleItem? {
        // Tìm câu phụ đề khớp với thời điểm phát hiện tại (ưu tiên [startMs, endMs) để tránh dính biên)
        return items.firstOrNull { currentPositionMs >= it.startMs && currentPositionMs < it.endMs }
            ?: items.firstOrNull { currentPositionMs in it.startMs..it.endMs }
    }

    /**
     * Xuất ra chuỗi định dạng SRT chuẩn UTF-8
     */
    fun toSrtString(mode: String = "translated"): String {
        val sb = StringBuilder()
        items.forEachIndexed { index, item ->
            sb.append(index + 1).append("\n")
            sb.append(item.formatSrtTimecode()).append("\n")
            sb.append(item.getDisplayText(mode)).append("\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    /**
     * Lưu file SRT vào bộ nhớ máy
     */
    fun saveToFile(file: File, mode: String = "translated") {
        file.parentFile?.mkdirs()
        file.writeText(toSrtString(mode), Charsets.UTF_8)
    }

    /**
     * Xuất ra định dạng ASS với phong cách Hộp Đen (BlackBox) che phụ đề cứng
     */
    fun toAssString(mode: String = "translated"): String {
        val header = """
            [Script Info]
            Title: CapSub Studio Subtitles with BlackBox
            ScriptType: v4.00+
            WrapStyle: 0
            PlayResX: 1920
            PlayResY: 1080
            ScaledBorderAndShadow: yes

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: BlackBox,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,-1,0,0,0,100,100,0,0,3,14,0,2,30,30,95,1

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        """.trimIndent() + "\n"

        val dialogues = items.map { item ->
            val (startAss, endAss) = item.formatAssTimecode()
            val text = item.getDisplayText(mode).replace("\n", "\\N")
            "Dialogue: 0,$startAss,$endAss,BlackBox,,0,0,0,,$text"
        }

        return header + dialogues.joinToString("\n") + "\n"
    }

    companion object {
        private val HAN_CHARACTER = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")

        internal fun splitChineseSourceAndTranslation(text: String): Pair<String, String>? {
            val lines = text.replace("\r\n", "\n")
                .replace("\r", "\n")
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (lines.size < 2 || !HAN_CHARACTER.containsMatchIn(lines.first())) return null

            val translationStart = lines.indexOfFirst { !HAN_CHARACTER.containsMatchIn(it) }
            if (translationStart <= 0 || translationStart >= lines.size) return null

            val source = lines.take(translationStart).joinToString("\n").trim()
            val translation = lines.drop(translationStart).joinToString("\n").trim()
            return if (source.isNotBlank() && translation.isNotBlank()) source to translation else null
        }

        /**
         * Parser đọc file SRT linh hoạt (bảo vệ chống lỗi khoảng trắng, định dạng giờ)
         */
        fun parseSrt(srtContent: String): SubtitleDocument {
            val list = mutableListOf<SubtitleItem>()
            if (srtContent.isBlank()) return SubtitleDocument(list)

            // Dọn dẹp markdown code block nếu có
            val cleaned = srtContent.replace("```srt", "", ignoreCase = true)
                .replace("```", "")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim()

            val blocks = cleaned.split(Regex("\n\\s*\n"))
            for (block in blocks) {
                val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
                if (lines.isEmpty()) continue

                // Tìm dòng chứa "-->"
                val timecodeLineIndex = lines.indexOfFirst { it.contains("-->") }
                if (timecodeLineIndex == -1) continue

                val timecode = lines[timecodeLineIndex]
                val timeParts = timecode.split(Regex("\\s*-->\\s*"))
                if (timeParts.size != 2) continue

                val startMs = parseSrtTimestamp(timeParts[0])
                val endMs = parseSrtTimestamp(timeParts[1])

                val textLines = lines.subList(timecodeLineIndex + 1, lines.size)
                val text = textLines.joinToString("\n").trim()

                val id = lines.getOrNull(0)?.toIntOrNull() ?: (list.size + 1)
                list.add(
                    SubtitleItem(
                        id = id,
                        startMs = startMs,
                        endMs = endMs,
                        originalText = text,
                        translatedText = text
                    )
                )
            }
            val doc = SubtitleDocument(list)
            doc.reindex()
            return doc
        }

        private fun parseSrtTimestamp(timestampStr: String): Long {
            try {
                val cleaned = timestampStr.trim().replace(".", ",")
                val parts = cleaned.split(",")
                val hms = parts[0]
                val milli = if (parts.size > 1) parts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L else 0L

                val hmsParts = hms.split(":")
                val h = hmsParts.getOrNull(0)?.toLongOrNull() ?: 0L
                val m = hmsParts.getOrNull(1)?.toLongOrNull() ?: 0L
                val s = hmsParts.getOrNull(2)?.toLongOrNull() ?: 0L

                return (h * 3600 + m * 60 + s) * 1000 + milli
            } catch (e: Exception) {
                return 0L
            }
        }
    }
}
