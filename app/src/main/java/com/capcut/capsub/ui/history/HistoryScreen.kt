package com.capcut.capsub.ui.history

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capcut.capsub.data.model.HistoryItem
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.repository.HistoryRepository
import com.capcut.capsub.ui.theme.DarkBackground
import com.capcut.capsub.ui.theme.DarkCard
import com.capcut.capsub.ui.theme.PrimaryEmerald
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onNavigateToSettings: () -> Unit,
    onPlayHistoryItem: (Uri, SubtitleDocument) -> Unit,
    onOpenInTts: ((Uri, SubtitleDocument) -> Unit)? = null
) {
    val context = LocalContext.current
    val historyRepo = remember { HistoryRepository(context) }
    val historyList by HistoryRepository.historyFlow.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        historyRepo.refreshHistory()
    }

    // Xuất SRT
    var exportingItem by remember { mutableStateOf<HistoryItem?>(null) }
    val exportSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri: Uri? ->
        val item = exportingItem
        if (uri != null && item != null) {
            try {
                val file = File(item.srtFilePath)
                if (file.exists()) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(file.readBytes())
                    }
                    Toast.makeText(context, "Đã xuất file SRT thành công!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi xuất file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Trình Phát & Lịch Sử",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Cài đặt", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Banner "Nhập Video & Phụ Đề Có Sẵn"
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showImportDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(PrimaryEmerald.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Input, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nhập Video & Phụ Đề Có Sẵn",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Xem với phụ đề rời hoặc dịch file sub gốc bằng AI",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Tiêu đề danh sách
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DANH SÁCH ĐÃ DỊCH (${historyList.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Danh sách Video
            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Chưa có video nào trong lịch sử", color = Color.Gray, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Các video tạo phụ đề xong sẽ hiển thị tại đây để xem lại", color = Color.DarkGray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(historyList, key = { it.id }) { item ->
                        HistoryCard(
                            item = item,
                            onPlay = {
                                val doc = historyRepo.loadSubtitleDocument(item)
                                onPlayHistoryItem(Uri.parse(item.videoUri), doc)
                            },
                            onOpenInTts = if (onOpenInTts != null) {
                                {
                                    val doc = historyRepo.loadSubtitleDocument(item)
                                    onOpenInTts(Uri.parse(item.videoUri), doc)
                                }
                            } else null,
                            onExportSrt = {
                                exportingItem = item
                                exportSrtLauncher.launch("${item.videoName.substringBeforeLast(".")}.srt")
                            },
                            onDelete = {
                                historyRepo.deleteHistory(item.id)
                            }
                        )
                    }
                }
            }
        }

        if (showImportDialog) {
            ImportSubtitleDialog(
                onDismiss = { showImportDialog = false },
                onSuccessPlay = { uri, doc ->
                    showImportDialog = false
                    historyRepo.refreshHistory()
                    onPlayHistoryItem(uri, doc)
                }
            )
        }
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    onPlay: () -> Unit,
    onOpenInTts: (() -> Unit)? = null,
    onExportSrt: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(item.createdAt) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(item.createdAt))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.videoName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Xoá", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("${item.sentenceCount} câu", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E2029), labelColor = PrimaryEmerald)
                )

                val voiceDisplayName = remember(item.ttsVoice) {
                    if (!item.ttsVoice.isNullOrBlank()) {
                        com.capcut.capsub.data.model.VoicePresets.VIETNAMESE_VOICES
                            .find { it.voiceType == item.ttsVoice }?.displayName ?: item.ttsVoice
                    } else null
                }

                if (voiceDisplayName != null) {
                    AssistChip(
                        onClick = {},
                        label = { Text("🎤 $voiceDisplayName", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF132F24),
                            labelColor = PrimaryEmerald
                        )
                    )
                } else {
                    AssistChip(
                        onClick = {},
                        label = { Text("Chưa lồng tiếng", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF252631),
                            labelColor = Color.Gray
                        )
                    )
                }

                Text(dateStr, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElevatedButton(
                        onClick = onPlay,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xem Video", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (onOpenInTts != null) {
                        OutlinedButton(
                            onClick = onOpenInTts,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (item.ttsVoice != null) "Đổi giọng" else "Lồng tiếng", fontSize = 12.sp)
                        }
                    }
                }

                IconButton(onClick = onExportSrt) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Xuất SRT", tint = Color.LightGray, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
