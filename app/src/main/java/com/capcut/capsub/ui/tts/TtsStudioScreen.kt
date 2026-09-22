package com.capcut.capsub.ui.tts

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import com.capcut.capsub.domain.media.NetworkHeaderHelper
import com.capcut.capsub.domain.media.RemoteAudioFetcher
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.capcut.capsub.ui.theme.CardBorder
import com.capcut.capsub.ui.theme.DarkBackground
import com.capcut.capsub.ui.theme.DarkCard
import com.capcut.capsub.ui.theme.PrimaryEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsStudioScreen(
    currentSubtitleDoc: SubtitleDocument?,
    currentVideoUri: Uri?,
    onSubtitleLoaded: (SubtitleDocument) -> Unit,
    onVideoSelected: ((Uri?) -> Unit)? = null,
    onNavigateToPlayer: (Uri?, SubtitleDocument) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val ttsManager = remember { TtsGenerationManager(context) }
    val progress by ttsManager.progress.collectAsState()

    val historyRepo = remember { com.capcut.capsub.data.repository.HistoryRepository(context) }
    val historyList by com.capcut.capsub.data.repository.HistoryRepository.historyFlow.collectAsState()
    var effectiveVideoUri by remember(currentVideoUri) {
        mutableStateOf(currentVideoUri)
    }

    var videoFileName by remember { mutableStateOf("") }
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var videoSizeMb by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(effectiveVideoUri) {
        val uri = effectiveVideoUri
        if (uri != null) {
            if (NetworkHeaderHelper.isRemoteUri(uri)) {
                videoFileName = NetworkHeaderHelper.getSuggestedTitle(uri.toString())
                videoSizeMb = "Trực tuyến"
                try {
                    val info = kotlinx.coroutines.withTimeoutOrNull(6000L) {
                        RemoteAudioFetcher.probeRemoteVideo(uri.toString())
                    }
                    if (info != null) {
                        videoFileName = info.title
                        videoDurationMs = info.durationMs
                        if (info.sizeBytes > 0) {
                            videoSizeMb = "%.1f MB".format(info.sizeBytes / (1024.0 * 1024.0))
                        }
                    }
                } catch (_: Exception) {}
            } else {
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex >= 0) videoFileName = cursor.getString(nameIndex) ?: "video.mp4"
                            if (sizeIndex >= 0) {
                                val bytes = cursor.getLong(sizeIndex)
                                videoSizeMb = "%.1f MB".format(bytes / (1024.0 * 1024.0))
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (videoFileName.isBlank()) {
                    videoFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "video.mp4"
                }

                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    videoDurationMs = timeStr?.toLongOrNull() ?: 0L
                    retriever.release()
                } catch (_: Exception) {
                    videoDurationMs = 0L
                }
            }
        } else {
            videoFileName = ""
            videoDurationMs = 0L
            videoSizeMb = ""
        }
    }

    var activeDoc by remember(currentSubtitleDoc) {
        mutableStateOf(currentSubtitleDoc)
    }

    androidx.compose.runtime.LaunchedEffect(currentSubtitleDoc) {
        activeDoc = currentSubtitleDoc
    }
    androidx.compose.runtime.LaunchedEffect(currentVideoUri) {
        effectiveVideoUri = currentVideoUri
    }

    var selectedVoice by remember {
        val savedVoiceId = repo.selectedTtsVoice
        mutableStateOf(VoicePresets.VIETNAMESE_VOICES.find { it.voiceType == savedVoiceId } ?: VoicePresets.DEFAULT_VOICE)
    }

    val voiceListState = rememberLazyListState()
    val sortedVoices = remember(selectedVoice) {
        val list = VoicePresets.VIETNAMESE_VOICES.toMutableList()
        val idx = list.indexOfFirst { it.voiceType == selectedVoice.voiceType }
        if (idx > 0) {
            val item = list.removeAt(idx)
            list.add(0, item)
        }
        list
    }

    androidx.compose.runtime.LaunchedEffect(selectedVoice.voiceType) {
        voiceListState.animateScrollToItem(0)
    }

    var threadCount by remember { mutableIntStateOf(repo.ttsThreadCount) }
    var showErrorReview by remember { mutableStateOf(false) }
    var showHistoryPicker by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showVideoLinkDialog by remember { mutableStateOf(false) }
    var showChooseVideoSourceDialog by remember { mutableStateOf(false) }

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

    // Launcher chọn video giống Tab 1 (OpenDocument)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            effectiveVideoUri = uri
            onVideoSelected?.invoke(uri)
        }
    }

    // Launcher chọn video khi bấm phát nếu chưa có video URI
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            effectiveVideoUri = uri
            onVideoSelected?.invoke(uri)
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
            // 0. CHỌN VIDEO PHÁT KÈM (HỖ TRỢ CẢ FILE MÁY LẪN LINK ONLINE)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            BorderStroke(
                                width = 1.dp,
                                color = if (effectiveVideoUri != null) PrimaryEmerald.copy(alpha = 0.5f) else CardBorder
                            ),
                            RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "0. Video Nguồn",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { videoPickerLauncher.launch(arrayOf("video/*")) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("File máy", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { showVideoLinkDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryEmerald)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Nhập Link", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (effectiveVideoUri != null) {
                            val isRemote = NetworkHeaderHelper.isRemoteUri(effectiveVideoUri)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1F2029), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isRemote) Icons.Default.Link else Icons.Default.VideoFile,
                                    contentDescription = null,
                                    tint = PrimaryEmerald,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = videoFileName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val mins = (videoDurationMs / 1000) / 60
                                    val secs = (videoDurationMs / 1000) % 60
                                    val durText = if (videoDurationMs > 0) "%02d:%02d".format(mins, secs) else "Tự động"
                                    Text(
                                        text = "⏱️ $durText  •  💾 $videoSizeMb ${if (isRemote) "(Online)" else ""}",
                                        fontSize = 12.sp,
                                        color = if (isRemote) PrimaryEmerald else Color.Gray
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        effectiveVideoUri = null
                                        onVideoSelected?.invoke(null)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Bỏ chọn video",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1F2029))
                                    .clickable { showChooseVideoSourceDialog = true }
                                    .padding(vertical = 16.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Chạm để chọn File từ máy hoặc dán Link online",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

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

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { showHistoryPicker = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64B5F6))
                                ) {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lịch sử", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { pickSrtLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryEmerald)
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Nạp SRT", fontSize = 12.sp)
                                }
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

                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { showTranslateDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64B5F6))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Dịch phụ đề bằng Gemini AI",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. Chọn Giọng Đọc CapCut",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Box(
                                modifier = Modifier
                                    .background(PrimaryEmerald.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "🎤 ${selectedVoice.displayName}",
                                    color = PrimaryEmerald,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            state = voiceListState,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sortedVoices, key = { it.voiceType }) { voice ->
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = voice.displayName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) PrimaryEmerald else Color.White,
                                                fontSize = 13.sp
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = PrimaryEmerald,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        if (voice.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = voice.description,
                                                color = if (isSelected) PrimaryEmerald.copy(alpha = 0.8f) else Color.Gray,
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
                            val hasAudio = activeDoc?.items?.any { !it.audioFilePath.isNullOrBlank() } == true

                            if (!hasAudio) {
                                Button(
                                    onClick = {
                                        val doc = activeDoc ?: return@Button
                                        ttsManager.startGeneration(
                                            subtitleDoc = doc,
                                            voice = selectedVoice,
                                            threadCount = threadCount,
                                            forceRegenerate = false,
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
                                        text = "Bắt Đầu Lồng Tiếng AI",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (canStart) Color.Black else Color.Gray
                                    )
                                }

                                if (canStart) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val doc = activeDoc ?: return@OutlinedButton
                                            val uri = effectiveVideoUri
                                            if (uri != null) {
                                                onNavigateToPlayer(uri, doc)
                                            } else {
                                                showChooseVideoSourceDialog = true
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
                                        Text(
                                            text = "Xem Vietsub Ngay (Không Cần Lồng Tiếng)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val doc = activeDoc ?: return@Button
                                        val uri = effectiveVideoUri
                                        if (uri != null) {
                                            onNavigateToPlayer(uri, doc)
                                        } else {
                                            showChooseVideoSourceDialog = true
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryEmerald,
                                        disabledContainerColor = Color(0xFF2A2B36)
                                    )
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Mở Xem Video (Đã Lồng Tiếng AI)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val regenButtonText = if (isFullyCompleted) {
                                    "Tạo Lại Giọng AI Toàn Bộ"
                                } else {
                                    "Tiếp Tục Lồng Tiếng ($voicedCount/$totalCount)"
                                }

                                OutlinedButton(
                                    onClick = {
                                        val doc = activeDoc ?: return@OutlinedButton
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = PrimaryEmerald)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = regenButtonText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TextButton(
                                        onClick = {
                                            val doc = activeDoc ?: return@TextButton
                                            val uri = effectiveVideoUri
                                            if (uri != null) {
                                                onNavigateToPlayer(uri, doc)
                                            } else {
                                                Toast.makeText(context, "Vui lòng chọn video để phát cùng phụ đề!", Toast.LENGTH_SHORT).show()
                                                pickVideoLauncher.launch(arrayOf("video/*"))
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "Hoặc xem video chỉ với Vietsub (âm thanh gốc)",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
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

    if (showHistoryPicker) {
        AlertDialog(
            onDismissRequest = { showHistoryPicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = PrimaryEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chọn Video Từ Lịch Sử", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                if (historyList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Chưa có video nào trong lịch sử", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(historyList, key = { it.id }) { item ->
                            val isVoiced = !item.ttsVoice.isNullOrBlank()
                            val voiceDisplayName = if (isVoiced) {
                                VoicePresets.VIETNAMESE_VOICES.find { it.voiceType == item.ttsVoice }?.displayName ?: item.ttsVoice
                            } else null

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val loadedDoc = historyRepo.loadSubtitleDocument(item)
                                        if (loadedDoc.items.isNotEmpty()) {
                                            effectiveVideoUri = Uri.parse(item.videoUri)
                                            item.ttsVoice?.let { voiceId ->
                                                VoicePresets.VIETNAMESE_VOICES.find { it.voiceType == voiceId }?.let {
                                                    selectedVoice = it
                                                }
                                            }
                                            com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(context, loadedDoc, selectedVoice.voiceType)
                                            activeDoc = loadedDoc
                                            onSubtitleLoaded(loadedDoc)
                                            showHistoryPicker = false
                                            Toast.makeText(context, "Đã nạp video '${item.videoName}'!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Không tìm thấy phụ đề của video này", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2029)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = item.videoName,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.sentenceCount} câu • ${item.sourceLanguage.uppercase()}",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )

                                        if (isVoiced && voiceDisplayName != null) {
                                            Box(
                                                modifier = Modifier
                                                    .background(PrimaryEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "🎤 $voiceDisplayName",
                                                    color = PrimaryEmerald,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF2A2B36), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Chưa lồng tiếng",
                                                    color = Color.LightGray,
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
            },
            confirmButton = {
                TextButton(onClick = { showHistoryPicker = false }) {
                    Text("Đóng", color = PrimaryEmerald)
                }
            },
            containerColor = DarkCard
        )
    }

    if (showTranslateDialog) {
        activeDoc?.let { doc ->
            GeminiTranslateSubtitleDialog(
                subtitleDoc = doc,
                onDismiss = { showTranslateDialog = false },
                onNavigateToSettings = onNavigateToSettings,
                onTranslationCompleted = { translatedDoc ->
                    activeDoc = translatedDoc
                    onSubtitleLoaded(translatedDoc)
                    com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(
                        context,
                        translatedDoc,
                        selectedVoice.voiceType
                    )
                    effectiveVideoUri?.let { uri ->
                        historyRepo.updateSubtitleForUri(uri, translatedDoc, selectedVoice.voiceType)
                    }
                }
            )
        }
    }

    // DIALOG NHẬP LINK VIDEO ONLINE
    if (showVideoLinkDialog) {
        var inputLinkText by remember { mutableStateOf("") }
        var linkError by remember { mutableStateOf<String?>(null) }
        val clipboard = LocalClipboardManager.current

        AlertDialog(
            onDismissRequest = { showVideoLinkDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = PrimaryEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nhập Link Video Online", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Dán link video trực tiếp (Bilibili, Douyin, MP4, M3U8...):",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputLinkText,
                        onValueChange = {
                            inputLinkText = it
                            linkError = null
                        },
                        placeholder = { Text("https://.../video.mp4", color = Color.Gray, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryEmerald,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = clipboard.getText()?.text?.trim() ?: ""
                                if (clip.isNotBlank()) {
                                    inputLinkText = clip
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryEmerald)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dán từ Clipboard", fontSize = 12.sp)
                        }
                    }
                    if (linkError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(linkError!!, color = Color(0xFFFFB74D), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanUrl = NetworkHeaderHelper.extractCleanUrl(inputLinkText)
                        if (cleanUrl.isBlank() || !NetworkHeaderHelper.isRemoteUrl(cleanUrl)) {
                            linkError = "Vui lòng nhập link hợp lệ (http://, https:// hoặc link Bilibili/b23.tv)"
                            return@Button
                        }
                        val uri = Uri.parse(cleanUrl)
                        effectiveVideoUri = uri
                        onVideoSelected?.invoke(uri)
                        showVideoLinkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
                ) {
                    Text("Xác Nhận", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVideoLinkDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            },
            containerColor = DarkCard
        )
    }


    // DIALOG CHỌN NGUỒN VIDEO KHI BẤM PHÁT MÀ CHƯA CÓ VIDEO
    if (showChooseVideoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showChooseVideoSourceDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VideoFile, contentDescription = null, tint = PrimaryEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chọn Nguồn Video", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Bạn muốn phát phụ đề này với nguồn video nào?", color = Color.Gray, fontSize = 13.sp)

                    Button(
                        onClick = {
                            showChooseVideoSourceDialog = false
                            pickVideoLauncher.launch(arrayOf("video/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2B36))
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📁 Chọn File Video Từ Máy", color = Color.White)
                    }

                    Button(
                        onClick = {
                            showChooseVideoSourceDialog = false
                            showVideoLinkDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🔗 Dán Link Video Online", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showChooseVideoSourceDialog = false }) {
                    Text("Đóng", color = Color.Gray)
                }
            },
            containerColor = DarkCard
        )
    }
}

