package com.capcut.capsub.ui.tts

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.model.SubtitleItem
import com.capcut.capsub.data.model.VoiceItem
import com.capcut.capsub.data.model.VoicePresets
import com.capcut.capsub.data.repository.SettingsRepository
import com.capcut.capsub.domain.tts.TtsGenerationManager
import com.capcut.capsub.ui.theme.DarkBackground
import com.capcut.capsub.ui.theme.DarkCard
import com.capcut.capsub.ui.theme.PrimaryEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsStudioScreen(
    currentSubtitleDoc: SubtitleDocument?,
    currentVideoUri: Uri?,
    onSubtitleLoaded: (SubtitleDocument) -> Unit,
    onNavigateToPlayer: (Uri?, SubtitleDocument) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val ttsManager = remember { TtsGenerationManager(context) }
    val progress by ttsManager.progress.collectAsState()

    val historyRepo = remember { com.capcut.capsub.data.repository.HistoryRepository(context) }
    var effectiveVideoUri by remember(currentVideoUri) {
        mutableStateOf(currentVideoUri)
    }

    var activeDoc by remember(currentSubtitleDoc) {
        mutableStateOf(currentSubtitleDoc)
    }

    var selectedVoice by remember {
        val savedVoiceId = repo.selectedTtsVoice
        mutableStateOf(VoicePresets.VIETNAMESE_VOICES.find { it.voiceType == savedVoiceId } ?: VoicePresets.DEFAULT_VOICE)
    }
    var threadCount by remember { mutableIntStateOf(repo.ttsThreadCount) }
    var showErrorReview by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(progress.failedItems, progress.isRunning) {
        if (!progress.isRunning && progress.failedItems.isNotEmpty()) {
            showErrorReview = true
        }
    }

    androidx.compose.runtime.LaunchedEffect(activeDoc, selectedVoice) {
        activeDoc?.let { doc ->
            com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(context, doc, selectedVoice.voiceType)
        }
    }

    // Launcher chọn video nếu chưa có video URI
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            effectiveVideoUri = uri
            val doc = activeDoc
            if (doc != null) {
                onNavigateToPlayer(uri, doc)
            }
        }
    }

    // Launcher mở file SRT ngoài
    val pickSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader(Charsets.UTF_8).readText()
                    val doc = SubtitleDocument.parseSrt(content)
                    if (doc.items.isNotEmpty()) {
                        com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(context, doc, selectedVoice.voiceType)
                        activeDoc = doc
                        onSubtitleLoaded(doc)
                        Toast.makeText(context, "Đã nạp ${doc.items.size} câu phụ đề từ SRT!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "File SRT không có câu thoại hợp lệ", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi đọc file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Lồng Tiếng AI (TTS Studio)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Cài đặt", tint = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. CHỌN NGUỒN PHỤ ĐỀ
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1. Danh Sách Phụ Đề",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            OutlinedButton(
                                onClick = { pickSrtLauncher.launch("*/*") },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryEmerald)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Nạp SRT ngoài", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val doc = activeDoc
                        if (doc != null && doc.items.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1F2029), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = PrimaryEmerald,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Đã sẵn sàng: ${doc.items.size} câu phụ đề",
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    val voicedCount = doc.items.count { !it.audioFilePath.isNullOrBlank() }
                                    Text(
                                        text = if (voicedCount > 0) "Đã tạo giọng: $voicedCount / ${doc.items.size} câu" else "Chưa tạo giọng đọc",
                                        color = if (voicedCount > 0) PrimaryEmerald else Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        activeDoc = null
                                        effectiveVideoUri = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Bỏ chọn phụ đề này",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1F2029), RoundedCornerShape(10.dp))
                                    .padding(vertical = 20.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Chưa có phụ đề để lồng tiếng",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tạo phụ đề từ Tab 'Tạo Phụ Đề' hoặc bấm 'Nạp SRT ngoài' ở trên",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // 2. CHỌN GIỌNG ĐỌC CAPCUT
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "2. Chọn Giọng Đọc CapCut (${VoicePresets.VIETNAMESE_VOICES.size} giọng)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(VoicePresets.VIETNAMESE_VOICES) { voice ->
                                val isSelected = voice.voiceType == selectedVoice.voiceType
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) PrimaryEmerald.copy(alpha = 0.2f) else Color(0xFF1F2029))
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) PrimaryEmerald else Color(0xFF323444),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            selectedVoice = voice
                                            repo.selectedTtsVoice = voice.voiceType
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = voice.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) PrimaryEmerald else Color.White,
                                            fontSize = 13.sp
                                        )
                                        if (voice.description.isNotBlank()) {
                                            Text(
                                                text = voice.description,
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. CẤU HÌNH ĐA LUỒNG (1 - 100 LUỒNG)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "3. Số Luồng Song Song",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "$threadCount luồng",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryEmerald
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = threadCount.toFloat(),
                            onValueChange = {
                                val intVal = it.toInt().coerceIn(1, 100)
                                threadCount = intVal
                                repo.ttsThreadCount = intVal
                            },
                            valueRange = 1f..100f,
                            steps = 99,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryEmerald,
                                activeTrackColor = PrimaryEmerald,
                                inactiveTrackColor = Color(0xFF323444)
                            )
                        )

                        Text(
                            text = "Mặc định 50 luồng. Hệ thống tự động phân chia và co giãn tốc độ đọc khớp khít mốc thời gian SRT.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // 4. TIẾN TRÌNH & THAO TÁC
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (progress.isRunning) {
                            val pct = if (progress.totalCount > 0) progress.completedCount.toFloat() / progress.totalCount else 0f
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Đang lồng tiếng: ${progress.completedCount}/${progress.totalCount} câu",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${(pct * 100).toInt()}%",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryEmerald
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = PrimaryEmerald,
                                trackColor = Color(0xFF323444)
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Tốc độ: ~${"%.1f".format(progress.speedPerSec)} câu/giây",
                                fontSize = 12.sp,
                                color = Color(0xFF64B5F6)
                            )
                            Text(
                                text = progress.currentSentence,
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = { ttsManager.cancel() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hủy tiến trình")
                            }
                        } else {
                            val voicedCount = activeDoc?.items?.count { !it.audioFilePath.isNullOrBlank() } ?: 0
                            val totalCount = activeDoc?.items?.size ?: 0
                            val isFullyCompleted = totalCount > 0 && voicedCount == totalCount

                            if (progress.isFinished) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = progress.currentSentence.ifBlank { "Đã hoàn thành lồng tiếng $totalCount câu thoại!" },
                                        color = PrimaryEmerald,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            } else if (progress.currentSentence.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = progress.currentSentence,
                                        color = Color(0xFFFFB74D),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            if (progress.failedItems.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showErrorReview = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D))
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Mở bảng xử lý ${progress.failedItems.size} câu lỗi")
                                }
                            }

                            val canStart = totalCount > 0
                            val buttonText = when {
                                isFullyCompleted -> "Tạo Lại Giọng AI Toàn Bộ"
                                voicedCount > 0 -> "Tiếp Tục Lồng Tiếng ($voicedCount/$totalCount)"
                                else -> "Bắt Đầu Lồng Tiếng AI"
                            }

                            Button(
                                onClick = {
                                    val doc = activeDoc ?: return@Button
                                    ttsManager.startGeneration(
                                        subtitleDoc = doc,
                                        voice = selectedVoice,
                                        threadCount = threadCount,
                                        forceRegenerate = isFullyCompleted,
                                        onCompleted = {
                                            com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(context, doc, selectedVoice.voiceType)
                                            effectiveVideoUri?.let { historyRepo.updateSubtitleForUri(it, doc, selectedVoice.voiceType) }
                                        }
                                    )
                                },
                                enabled = canStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryEmerald,
                                    disabledContainerColor = Color(0xFF2A2B36)
                                )
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buttonText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (canStart) Color.Black else Color.Gray
                                )
                            }

                            val hasAudio = activeDoc?.items?.any { !it.audioFilePath.isNullOrBlank() } == true
                            if (hasAudio) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        val doc = activeDoc ?: return@OutlinedButton
                                        val uri = effectiveVideoUri
                                        if (uri != null) {
                                            onNavigateToPlayer(uri, doc)
                                        } else {
                                            Toast.makeText(context, "Vui lòng chọn video để phát cùng phụ đề!", Toast.LENGTH_SHORT).show()
                                            pickVideoLauncher.launch("video/*")
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryEmerald)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mở Xem Ngay Trên Trình Phát", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showErrorReview && progress.failedItems.isNotEmpty()) {
        TtsErrorReviewDialog(
            failedItems = progress.failedItems,
            isRetrying = progress.isRunning,
            threadCount = threadCount,
            onRetryOne = { itemId, editedText ->
                val doc = activeDoc ?: return@TtsErrorReviewDialog
                showErrorReview = false
                ttsManager.retryFailedItem(
                    subtitleDoc = doc,
                    voice = selectedVoice,
                    itemId = itemId,
                    editedText = editedText,
                    threadCount = threadCount,
                    onCompleted = {
                        com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(
                            context,
                            doc,
                            selectedVoice.voiceType
                        )
                        effectiveVideoUri?.let { historyRepo.updateSubtitleForUri(it, doc, selectedVoice.voiceType) }
                    }
                )
            },
            onRetryAll = { editedTexts ->
                val doc = activeDoc ?: return@TtsErrorReviewDialog
                showErrorReview = false
                ttsManager.retryFailedItems(
                    subtitleDoc = doc,
                    voice = selectedVoice,
                    threadCount = threadCount,
                    editedTexts = editedTexts,
                    onCompleted = {
                        com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(
                            context,
                            doc,
                            selectedVoice.voiceType
                        )
                        effectiveVideoUri?.let { historyRepo.updateSubtitleForUri(it, doc, selectedVoice.voiceType) }
                    }
                )
            },
            onDismiss = { showErrorReview = false }
        )
    }
}

/**
 * Trình phân tích chuỗi SRT thủ công cực nhẹ để nạp file SRT ngoài.
 */
private fun parseSrtContent(content: String): SubtitleDocument {
    val items = mutableListOf<SubtitleItem>()
    val blocks = content.replace("\r\n", "\n").replace("\r", "\n").split("\n\n")

    blocks.forEachIndexed { index, block ->
        val lines = block.trim().lines().filter { it.isNotBlank() }
        if (lines.size >= 2) {
            val timecodeLine = if (lines[0].contains("-->")) lines[0] else if (lines.size >= 2 && lines[1].contains("-->")) lines[1] else null
            if (timecodeLine != null) {
                val parts = timecodeLine.split("-->").map { it.trim() }
                if (parts.size == 2) {
                    val startMs = parseSrtTimestamp(parts[0])
                    val endMs = parseSrtTimestamp(parts[1])
                    val textLines = lines.dropWhile { !it.contains("-->") }.drop(1)
                    val text = textLines.joinToString("\n").trim()
                    if (text.isNotBlank()) {
                        items.add(
                            SubtitleItem(
                                id = index + 1,
                                startMs = startMs,
                                endMs = endMs,
                                originalText = text,
                                translatedText = text
                            )
                        )
                    }
                }
            }
        }
    }
    return SubtitleDocument(items)
}

private fun parseSrtTimestamp(timeStr: String): Long {
    return try {
        val parts = timeStr.replace(",", ".").split(":")
        val hours = parts[0].trim().toLong()
        val minutes = parts[1].trim().toLong()
        val secondsParts = parts[2].trim().split(".")
        val seconds = secondsParts[0].toLong()
        val millis = if (secondsParts.size > 1) secondsParts[1].take(3).padEnd(3, '0').toLong() else 0L
        (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
    } catch (_: Exception) {
        0L
    }
}
