package com.capcut.capsub.ui.history

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.capcut.capsub.data.api.GeminiTranslator
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.repository.HistoryRepository
import com.capcut.capsub.data.repository.SettingsRepository
import com.capcut.capsub.ui.theme.DarkCard
import com.capcut.capsub.ui.theme.PrimaryEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportSubtitleDialog(
    onDismiss: () -> Unit,
    onSuccessPlay: (Uri, SubtitleDocument) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository(context) }
    val historyRepo = remember { HistoryRepository(context) }

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoName by remember { mutableStateOf<String?>(null) }
    var selectedSrtUri by remember { mutableStateOf<Uri?>(null) }
    var selectedSrtName by remember { mutableStateOf<String?>(null) }

    var isTranslating by remember { mutableStateOf(false) }
    var translationProgressText by remember { mutableStateOf("") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {}
            selectedVideoUri = uri
            selectedVideoName = getFileNameFromUri(context, uri) ?: "Video_Import.mp4"
        }
    }

    val srtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {}
            selectedSrtUri = uri
            selectedSrtName = getFileNameFromUri(context, uri) ?: "subtitles.srt"
        }
    }

    Dialog(
        onDismissRequest = { if (!isTranslating) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = !isTranslating
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkCard,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Tiêu đề & Nút đóng
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📥 Nhập Video & Phụ Đề",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (!isTranslating) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Chọn Video
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E2029))
                        .border(
                            1.dp,
                            if (selectedVideoUri != null) PrimaryEmerald else Color(0xFF2E313D),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = !isTranslating) {
                            videoPickerLauncher.launch(arrayOf("video/*"))
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selectedVideoUri != null) Icons.Default.CheckCircle else Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = if (selectedVideoUri != null) PrimaryEmerald else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedVideoName ?: "Chọn file Video từ máy",
                                color = if (selectedVideoUri != null) Color.White else Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedVideoUri != null) "Đã chọn video (Bấm để đổi)" else "Hỗ trợ MP4, MKV, MOV",
                                color = if (selectedVideoUri != null) PrimaryEmerald else Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        if (selectedVideoUri != null && !isTranslating) {
                            IconButton(
                                onClick = {
                                    selectedVideoUri = null
                                    selectedVideoName = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Bỏ chọn", tint = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Chọn File Phụ Đề
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E2029))
                        .border(
                            1.dp,
                            if (selectedSrtUri != null) PrimaryEmerald else Color(0xFF2E313D),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = !isTranslating) {
                            srtPickerLauncher.launch(arrayOf("*/*"))
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selectedSrtUri != null) Icons.Default.CheckCircle else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (selectedSrtUri != null) PrimaryEmerald else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedSrtName ?: "Chọn file Phụ đề (.srt, .vtt)",
                                color = if (selectedSrtUri != null) Color.White else Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedSrtUri != null) "Đã nhận file phụ đề (Bấm để đổi)" else "File phụ đề rời có sẵn",
                                color = if (selectedSrtUri != null) PrimaryEmerald else Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        if (selectedSrtUri != null && !isTranslating) {
                            IconButton(
                                onClick = {
                                    selectedSrtUri = null
                                    selectedSrtName = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Bỏ chọn", tint = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Trạng thái đang dịch
                if (isTranslating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = PrimaryEmerald,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = translationProgressText,
                            color = PrimaryEmerald,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Nút 1: Xem ngay (Cần Video + Sub)
                    Button(
                        onClick = {
                            val vUri = selectedVideoUri ?: return@Button
                            val sUri = selectedSrtUri ?: return@Button
                            try {
                                val srtText = context.contentResolver.openInputStream(sUri)?.use {
                                    it.reader(Charsets.UTF_8).readText()
                                } ?: ""
                                val doc = SubtitleDocument.parseSrt(srtText)
                                if (doc.isEmpty) {
                                    Toast.makeText(context, "Không đọc được nội dung phụ đề SRT.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                // Lưu vào lịch sử
                                historyRepo.saveHistory(
                                    videoUri = vUri,
                                    videoName = selectedVideoName ?: "Video Import",
                                    durationMs = 0L,
                                    document = doc,
                                    translationEngine = "File SRT có sẵn",
                                    sourceLanguage = "auto"
                                )
                                onDismiss()
                                onSuccessPlay(vUri, doc)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Lỗi nạp file: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = (selectedVideoUri != null && selectedSrtUri != null),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryEmerald,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xem Ngay với Phụ Đề Này", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Nút 2: Dịch file phụ đề gốc bằng AI (Gemini đa luồng)
                    OutlinedButton(
                        onClick = {
                            val sUri = selectedSrtUri ?: return@OutlinedButton

                            if (repo.geminiApiKeys.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Vui lòng cấu hình Gemini API Key trong Cài đặt trước!",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@OutlinedButton
                            }

                            scope.launch(Dispatchers.IO) {
                                try {
                                    isTranslating = true
                                    translationProgressText = "Đang đọc file phụ đề..."
                                    val srtText = context.contentResolver.openInputStream(sUri)?.use {
                                        it.reader(Charsets.UTF_8).readText()
                                    } ?: ""
                                    val doc = SubtitleDocument.parseSrt(srtText)
                                    if (doc.isEmpty) {
                                        withContext(Dispatchers.Main) {
                                            isTranslating = false
                                            Toast.makeText(context, "File phụ đề rỗng hoặc không đúng định dạng.", Toast.LENGTH_SHORT).show()
                                        }
                                        return@launch
                                    }

                                    val model = if (repo.selectedModel.startsWith("gemini")) repo.selectedModel else "gemini-3.5-flash-lite"
                                    val translator = GeminiTranslator(apiKeys = repo.geminiApiKeys, modelId = model)

                                    val translatedDoc = translator.translateSubtitles(
                                        document = doc,
                                        stylePreset = repo.selectedStyle,
                                        customPrompt = repo.geminiCustomPrompt,
                                        targetLanguage = repo.targetLanguage,
                                        chunkSize = 45,
                                        threadCount = repo.geminiThreadCount
                                    ) { pct, msg ->
                                        translationProgressText = msg
                                    }

                                    val vUri = selectedVideoUri
                                    if (vUri != null) {
                                        historyRepo.saveHistory(
                                            videoUri = vUri,
                                            videoName = selectedVideoName ?: "Video Import",
                                            durationMs = 0L,
                                            document = translatedDoc,
                                            translationEngine = "$model (${repo.geminiThreadCount} luồng)",
                                            sourceLanguage = "auto"
                                        )
                                        withContext(Dispatchers.Main) {
                                            isTranslating = false
                                            onDismiss()
                                            onSuccessPlay(vUri, translatedDoc)
                                        }
                                    } else {
                                        // Trường hợp chỉ dịch SRT không có video: Lưu vào file tạm trong cache
                                        val exportFile = java.io.File(context.cacheDir, "${selectedSrtName ?: "translated"}_viet.srt")
                                        translatedDoc.saveToFile(exportFile, mode = "translated")
                                        withContext(Dispatchers.Main) {
                                            isTranslating = false
                                            Toast.makeText(context, "Dịch xong ${translatedDoc.size} câu! Lưu tại: ${exportFile.name}", Toast.LENGTH_LONG).show()
                                            onDismiss()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isTranslating = false
                                        Toast.makeText(context, "Lỗi dịch phụ đề: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = (selectedSrtUri != null),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryEmerald)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Dịch Phụ Đề Gốc Bằng AI (Gemini ${repo.geminiThreadCount} Luồng)",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index)
            }
        }
    }
    return name ?: uri.lastPathSegment
}
