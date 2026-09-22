package com.capcut.capsub.domain.media

import android.util.Log
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.model.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class BilibiliTarget(
    val bvid: String? = null,
    val aid: String? = null,
    val epid: String? = null,
    val seasonId: String? = null,
    val pageIndex: Int = 1,
    val rawUrl: String = ""
)

data class BilibiliVideoDetails(
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
    val desc: String,
    val coverUrl: String,
    val durationSeconds: Long,
    val author: String,
    val pages: List<BilibiliPage> = emptyList()
)

data class BilibiliPage(
    val cid: Long,
    val page: Int,
    val part: String,
    val durationSeconds: Long
)

data class BilibiliStreamInfo(
    val isDash: Boolean,
    val durationSeconds: Long,
    val audioUrl: String?,
    val videoUrl: String?,
    val audioBandwidth: Long = 0L,
    val videoBandwidth: Long = 0L
)

data class BilibiliSubtitleInfo(
    val id: Long,
    val lan: String,
    val lanDoc: String,
    val isAi: Boolean,
    val subtitleUrl: String
)

/**
 * Trình giải mã link Bilibili (b23.tv, BV, av, ep), ký số bảo mật WBI và trích xuất luồng audio/phụ đề.
 */
object BilibiliResolver {

    private const val TAG = "BilibiliResolver"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private const val REFERER = "https://www.bilibili.com/"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    private var cachedImgKey: String? = null
    private var cachedSubKey: String? = null
    private var lastWbiFetch: Long = 0L

    fun isBilibiliUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.contains("bilibili.com") ||
                lower.contains("b23.tv") ||
                lower.contains("bilivideo.com") ||
                url.contains(Regex("BV[a-zA-Z0-9]{10}", RegexOption.IGNORE_CASE)) ||
                url.contains(Regex("av\\d+", RegexOption.IGNORE_CASE))
    }

    private fun formatCookie(cookie: String): String {
        val trimmed = cookie.trim()
        if (trimmed.isBlank()) return ""
        return if (!trimmed.contains("=")) {
            "SESSDATA=$trimmed"
        } else if (!trimmed.contains("SESSDATA=")) {
            "SESSDATA=$trimmed"
        } else {
            trimmed
        }
    }

    private fun buildRequestHeaders(cookie: String = ""): Map<String, String> {
        val map = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to REFERER,
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"
        )
        val sess = formatCookie(cookie)
        val defaultCookie = "CURRENT_FNVAL=4048"
        map["Cookie"] = if (sess.isNotBlank()) "$sess; $defaultCookie" else defaultCookie
        return map
    }

    private fun getMixinKey(orig: String): String {
        val sb = StringBuilder()
        for (idx in MIXIN_KEY_ENC_TAB) {
            if (idx < orig.length) {
                sb.append(orig[idx])
            }
        }
        return sb.toString().take(32)
    }

    private suspend fun getWbiKeys(cookie: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedImgKey != null && cachedSubKey != null && (now - lastWbiFetch < 30 * 60 * 1000L)) {
            return@withContext Pair(cachedImgKey!!, cachedSubKey!!)
        }

        try {
            val reqBuilder = Request.Builder().url("https://api.bilibili.com/x/web-interface/nav")
            buildRequestHeaders(cookie).forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val wbiImg = json.optJSONObject("data")?.optJSONObject("wbi_img")
                val imgUrl = wbiImg?.optString("img_url", "") ?: ""
                val subUrl = wbiImg?.optString("sub_url", "") ?: ""
                if (imgUrl.isNotBlank() && subUrl.isNotBlank()) {
                    val imgKey = imgUrl.substringAfterLast("/").substringBefore(".")
                    val subKey = subUrl.substringAfterLast("/").substringBefore(".")
                    cachedImgKey = imgKey
                    cachedSubKey = subKey
                    lastWbiFetch = now
                    return@withContext Pair(imgKey, subKey)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi lấy WBI dynamic keys, sử dụng fallback: ${e.message}")
        }

        Pair("7cd084941338484aae1ad9425b84077c", "4932caff0ff746eab6f01bf08b70ac45")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun encWbi(params: Map<String, String>, imgKey: String, subKey: String): Map<String, String> {
        val mixinKey = getMixinKey(imgKey + subKey)
        val currTime = System.currentTimeMillis() / 1000L
        val mutable = params.toMutableMap()
        mutable["wts"] = currTime.toString()

        val sortedKeys = mutable.keys.sorted()
        val filtered = mutableMapOf<String, String>()
        for (k in sortedKeys) {
            val cleanVal = mutable[k]?.replace(Regex("[!'()*]"), "") ?: ""
            filtered[k] = cleanVal
        }

        val query = filtered.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val wRid = md5(query + mixinKey)
        filtered["w_rid"] = wRid
        return filtered
    }

    /**
     * Phân tích và giải quyết link rút gọn (b23.tv) thành ID video chuẩn (BV/av/ep).
     */
    suspend fun resolveUrl(inputUrl: String): BilibiliTarget = withContext(Dispatchers.IO) {
        var target = inputUrl.trim()
        val urlRegex = Regex("https?://[^\\s]+")
        val match = urlRegex.find(target)
        if (match != null) {
            target = match.value
        }

        if (target.contains("b23.tv")) {
            try {
                val req = Request.Builder().url(target)
                    .addHeader("User-Agent", USER_AGENT)
                    .build()
                val resp = httpClient.newCall(req).execute()
                val finalUrl = resp.request.url.toString()
                if (finalUrl.isNotBlank()) {
                    target = finalUrl
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi giải mã b23.tv shortlink: ${e.message}")
            }
        }

        var bvid: String? = null
        var aid: String? = null
        var epid: String? = null
        var seasonId: String? = null
        var pageIndex = 1

        val bvMatch = Regex("BV[a-zA-Z0-9]{10}", RegexOption.IGNORE_CASE).find(target)
        if (bvMatch != null) bvid = bvMatch.value

        val avMatch = Regex("av(\\d+)", RegexOption.IGNORE_CASE).find(target)
        if (avMatch != null) aid = avMatch.groupValues[1]

        val epMatch = Regex("ep(\\d+)", RegexOption.IGNORE_CASE).find(target)
        if (epMatch != null) epid = epMatch.groupValues[1]

        val ssMatch = Regex("ss(\\d+)", RegexOption.IGNORE_CASE).find(target)
        if (ssMatch != null) seasonId = ssMatch.groupValues[1]

        val pMatch = Regex("[?&]p=(\\d+)", RegexOption.IGNORE_CASE).find(target)
        if (pMatch != null) pageIndex = pMatch.groupValues[1].toIntOrNull() ?: 1

        BilibiliTarget(
            bvid = bvid,
            aid = aid,
            epid = epid,
            seasonId = seasonId,
            pageIndex = pageIndex,
            rawUrl = target
        )
    }

    /**
     * Lấy thông tin chi tiết video (tiêu đề, cid, thời lượng, ảnh bìa).
     */
    suspend fun getVideoDetails(target: BilibiliTarget, cookie: String = ""): BilibiliVideoDetails = withContext(Dispatchers.IO) {
        val (imgKey, subKey) = getWbiKeys(cookie)
        val headers = buildRequestHeaders(cookie)

        val queryMap = mutableMapOf<String, String>()
        if (!target.bvid.isNullOrBlank()) {
            queryMap["bvid"] = target.bvid
        } else if (!target.aid.isNullOrBlank()) {
            queryMap["aid"] = target.aid
        } else {
            throw IllegalArgumentException("Không tìm thấy ID video Bilibili hợp lệ (BV/av)")
        }

        val signed = encWbi(queryMap, imgKey, subKey)
        val queryString = signed.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val url = "https://api.bilibili.com/x/web-interface/wbi/view?$queryString"

        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        val resp = httpClient.newCall(reqBuilder.build()).execute()
        val jsonStr = resp.body?.string() ?: ""
        val json = JSONObject(jsonStr)

        if (json.optInt("code", -1) != 0) {
            throw IllegalStateException(json.optString("message", "Lỗi lấy thông tin video Bilibili"))
        }

        val data = json.getJSONObject("data")
        val bvid = data.optString("bvid", target.bvid ?: "")
        val aid = data.optLong("aid", 0L)
        val title = data.optString("title", "Video Bilibili")
        val desc = data.optString("desc", "")
        val pic = data.optString("pic", "")
        val durationSeconds = data.optLong("duration", 0L)
        val ownerName = data.optJSONObject("owner")?.optString("name", "Bilibili Uploader") ?: ""

        val pagesArr = data.optJSONArray("pages") ?: JSONArray()
        val pages = mutableListOf<BilibiliPage>()
        var resolvedCid = data.optLong("cid", 0L)

        for (i in 0 until pagesArr.length()) {
            val pObj = pagesArr.getJSONObject(i)
            val pCid = pObj.optLong("cid", 0L)
            val pPage = pObj.optInt("page", i + 1)
            val pPart = pObj.optString("part", "Phần $pPage")
            val pDuration = pObj.optLong("duration", 0L)
            pages.add(BilibiliPage(pCid, pPage, pPart, pDuration))
            if (pPage == target.pageIndex) {
                resolvedCid = pCid
            }
        }

        if (resolvedCid == 0L && pages.isNotEmpty()) {
            resolvedCid = pages[0].cid
        }

        BilibiliVideoDetails(
            bvid = bvid,
            aid = aid,
            cid = resolvedCid,
            title = title,
            desc = desc,
            coverUrl = pic,
            durationSeconds = durationSeconds,
            author = ownerName,
            pages = pages
        )
    }

    /**
     * Lấy luồng phát (Audio riêng DASH .m4s & Video stream).
     */
    suspend fun getPlayStream(bvid: String, cid: Long, cookie: String = ""): BilibiliStreamInfo = withContext(Dispatchers.IO) {
        val (imgKey, subKey) = getWbiKeys(cookie)
        val headers = buildRequestHeaders(cookie)

        val params = mapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to "127",
            "fnval" to "4048",
            "fnver" to "0",
            "fourk" to "1",
            "high_quality" to "1"
        )
        val signed = encWbi(params, imgKey, subKey)
        val queryStr = signed.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val playUrl = "https://api.bilibili.com/x/player/wbi/playurl?$queryStr"

        val reqBuilder = Request.Builder().url(playUrl)
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        val resp = httpClient.newCall(reqBuilder.build()).execute()
        val jsonStr = resp.body?.string() ?: ""
        val json = JSONObject(jsonStr)

        if (json.optInt("code", -1) != 0) {
            throw IllegalStateException(json.optString("message", "Lỗi lấy luồng phát video Bilibili"))
        }

        val data = json.getJSONObject("data")
        val dash = data.optJSONObject("dash")

        if (dash != null) {
            val duration = dash.optLong("duration", data.optLong("timelength", 0L) / 1000L)
            var audioUrl: String? = null
            var audioBandwidth = 0L

            val audios = dash.optJSONArray("audio")
            if (audios != null && audios.length() > 0) {
                // Sắp xếp chọn audio có bitrate cao nhất
                var maxBw = 0L
                for (i in 0 until audios.length()) {
                    val aObj = audios.getJSONObject(i)
                    val bw = aObj.optLong("bandwidth", 0L)
                    val baseUrl = aObj.optString("baseUrl").ifEmpty { aObj.optString("base_url") }
                    if (baseUrl.isNotBlank() && bw >= maxBw) {
                        maxBw = bw
                        audioUrl = baseUrl
                        audioBandwidth = bw
                    }
                }
            }

            var videoUrl: String? = null
            var videoBandwidth = 0L
            val videos = dash.optJSONArray("video")
            if (videos != null && videos.length() > 0) {
                // Ưu tiên video 1080P/720P AVC/H.264 để ExoPlayer phát mượt mà nhất
                val vObj = videos.getJSONObject(0)
                videoUrl = vObj.optString("baseUrl").ifEmpty { vObj.optString("base_url") }
                videoBandwidth = vObj.optLong("bandwidth", 0L)
            }

            BilibiliStreamInfo(
                isDash = true,
                durationSeconds = duration,
                audioUrl = audioUrl,
                videoUrl = videoUrl,
                audioBandwidth = audioBandwidth,
                videoBandwidth = videoBandwidth
            )
        } else {
            // Trường hợp Fallback durl (file MP4 gộp)
            val durl = data.optJSONArray("durl")
            val singleUrl = if (durl != null && durl.length() > 0) {
                durl.getJSONObject(0).optString("url")
            } else null
            val duration = data.optLong("timelength", 0L) / 1000L

            BilibiliStreamInfo(
                isDash = false,
                durationSeconds = duration,
                audioUrl = singleUrl,
                videoUrl = singleUrl
            )
        }
    }

    /**
     * Kiểm tra và lấy danh sách phụ đề có sẵn trên Bilibili (Subtitles).
     */
    suspend fun getSubtitles(bvid: String, cid: Long, cookie: String = ""): List<BilibiliSubtitleInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<BilibiliSubtitleInfo>()
        try {
            val (imgKey, subKey) = getWbiKeys(cookie)
            val params = mapOf("bvid" to bvid, "cid" to cid.toString())
            val signed = encWbi(params, imgKey, subKey)
            val queryStr = signed.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            val url = "https://api.bilibili.com/x/player/wbi/v2?$queryStr"

            val reqBuilder = Request.Builder().url(url)
            buildRequestHeaders(cookie).forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            val resp = httpClient.newCall(reqBuilder.build()).execute()
            val jsonStr = resp.body?.string() ?: ""
            val json = JSONObject(jsonStr)

            val subtitles = json.optJSONObject("data")
                ?.optJSONObject("subtitle")
                ?.optJSONArray("subtitles") ?: JSONArray()

            for (i in 0 until subtitles.length()) {
                val sObj = subtitles.getJSONObject(i)
                var subUrl = sObj.optString("subtitle_url", "")
                if (subUrl.startsWith("//")) subUrl = "https:$subUrl"
                if (subUrl.isNotBlank()) {
                    list.add(
                        BilibiliSubtitleInfo(
                            id = sObj.optLong("id", 0L),
                            lan = sObj.optString("lan", ""),
                            lanDoc = sObj.optString("lan_doc", ""),
                            isAi = sObj.optString("lan", "").startsWith("ai-") || sObj.optInt("ai_type", 0) == 1,
                            subtitleUrl = subUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi kiểm tra phụ đề Bilibili: ${e.message}")
        }
        list
    }

    /**
     * Tải nội dung phụ đề JSON của Bilibili và chuyển đổi sang SubtitleDocument.
     */
    suspend fun downloadSubtitleAsDocument(subtitleUrl: String): SubtitleDocument? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(subtitleUrl).build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val jsonStr = resp.body?.string() ?: return@withContext null
            val json = JSONObject(jsonStr)
            val body = json.optJSONArray("body") ?: return@withContext null

            val items = mutableListOf<SubtitleItem>()
            for (i in 0 until body.length()) {
                val itemObj = body.getJSONObject(i)
                val fromSec = itemObj.optDouble("from", 0.0)
                val toSec = itemObj.optDouble("to", 0.0)
                val content = itemObj.optString("content", "").trim()

                if (content.isNotBlank()) {
                    items.add(
                        SubtitleItem(
                            id = i + 1,
                            startMs = (fromSec * 1000.0).toLong(),
                            endMs = (toSec * 1000.0).toLong(),
                            originalText = content,
                            translatedText = content
                        )
                    )
                }
            }
            if (items.isNotEmpty()) SubtitleDocument(items) else null
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi tải phụ đề Bilibili sang SubtitleDocument: ${e.message}")
            null
        }
    }
}
