package com.capcut.capsub.ui.home

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capcut.capsub.ui.theme.CardBorder
import com.capcut.capsub.ui.theme.DarkBackground
import com.capcut.capsub.ui.theme.DarkSurface
import com.capcut.capsub.ui.theme.PrimaryEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onStartProcessing: (Uri, String, Long, String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { com.capcut.capsub.data.repository.SettingsRepository(context) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var fileDurationMs by remember { mutableLongStateOf(0L) }
    var fileSizeMb by remember { mutableStateOf("") }

    var selectedLang by remember { mutableStateOf("zh-CN") }
    var selectedLangLabel by remember { mutableStateOf("🇨🇳 Tiếng Trung (zh-CN)") }

    var selectedTargetLang by remember { mutableStateOf(repo.targetLanguage) }
    var selectedTargetLangLabel by remember { mutableStateOf(repo.targetLanguageLabel) }

    var selectedStyle by remember { mutableStateOf(repo.selectedStyle) }
    var selectedStyleLabel by remember {
        mutableStateOf(
            when (repo.selectedStyle) {
                "custom" -> "✍️ Tự nhập Prompt tùy chỉnh..."
                "ThuanViet" -> "📖 Thuần Việt Văn Học (Trau chuốt, mượt mà)"
                "CoTrang" -> "⚔️ Cổ Trang Tiên Hiệp (Hán Việt chuẩn)"
                "Auto" -> "✨ Tự Động AI (Theo ngữ cảnh)"
                else -> "🎬 Phim Ngắn Zhihu (Vả mặt, kịch tính)"
            }
        )
    }
    var customPromptText by remember { mutableStateOf(repo.geminiCustomPrompt) }

    var selectedEngine by remember { mutableStateOf(repo.selectedModel) }
    var selectedEngineLabel by remember {
        mutableStateOf(
            when (repo.selectedModel) {
                "gemini-3.5-flash-lite" -> "🤖 Gemini 3.5 Flash-Lite (RPD cao - Cần API Key)"
                "gemini-3.1-flash-lite" -> "🤖 Gemini 3.1 Flash-Lite (Khuyên dùng - Cần API Key)"
                "none" -> "🚫 Giữ Nguyên Tiếng Gốc (Không dịch)"
                else -> "⚡ CapCut Dịch Sẵn (Miễn phí 100% - Không cần Key)"
            }
        )
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {}
            selectedUri = uri
            // Lấy tên và kích thước file
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: "video.mp4"
                    val bytes = cursor.getLong(sizeIndex)
                    fileSizeMb = "%.1f MB".format(bytes / (1024.0 * 1024.0))
                }
            }
            // Lấy thời lượng video
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                fileDurationMs = timeStr?.toLongOrNull() ?: 0L
                retriever.release()
            } catch (e: Exception) {
                fileDurationMs = 0L
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("CapSub AI Studio", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Cài đặt")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 1. CARD CHỌN FILE
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(16.dp))
                    .clickable {
                        filePicker.launch(arrayOf("video/*", "audio/*"))
                    },
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (selectedUri != null) fileName else "Chạm để chọn Video hoặc Âm thanh",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedUri != null) {
                            val mins = (fileDurationMs / 1000) / 60
                            val secs = (fileDurationMs / 1000) % 60
                            "⏱️ %02d:%02d  •  💾 %s".format(mins, secs, fileSizeMb)
                        } else {
                            "Hỗ trợ MP4, MKV, MOV, MP3, M4A"
                        },
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. CẤU HÌNH NHẬN DIỆN & DỊCH THUẬT
            Text(
                text = "CẤU HÌNH NHẬN DIỆN & DỊCH THUẬT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Ngôn ngữ gốc
            SettingDropdown(
                label = "🌐 Ngôn ngữ lời thoại gốc:",
                currentValue = selectedLangLabel,
                options = listOf(
                    "zh-CN" to "🇨🇳 Tiếng Trung (zh-CN)",
                    "en-US" to "🇺🇸 Tiếng Anh (en-US)",
                    "vi-VN" to "🇻🇳 Tiếng Việt (vi-VN)",
                    "ja-JP" to "🇯🇵 Tiếng Nhật (ja-JP)",
                    "ko-KR" to "🇰🇷 Tiếng Hàn (ko-KR)"
                ),
                onSelect = { code, label ->
                    selectedLang = code
                    selectedLangLabel = label
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bộ máy Dịch thuật
            SettingDropdown(
                label = "🤖 Bộ máy Dịch thuật phụ đề:",
                currentValue = selectedEngineLabel,
                options = listOf(
                    "capcut" to "⚡ CapCut Dịch Sẵn (Miễn phí 100% - Không cần Key)",
                    "gemini-3.5-flash-lite" to "🤖 Gemini 3.5 Flash-Lite (RPD cao - Cần API Key)",
                    "gemini-3.1-flash-lite" to "🤖 Gemini 3.1 Flash-Lite (Khuyên dùng - Cần API Key)",
                    "none" to "🚫 Giữ Nguyên Tiếng Gốc (Không dịch)"
                ),
                onSelect = { code, label ->
                    selectedEngine = code
                    selectedEngineLabel = label
                }
            )

            // Ngôn ngữ dịch sang (Ngôn ngữ đích)
            if (selectedEngine != "none") {
                Spacer(modifier = Modifier.height(16.dp))
                SettingDropdown(
                    label = "🎯 Dịch sang ngôn ngữ:",
                    currentValue = selectedTargetLangLabel,
                    options = listOf(
                        "vi-VN" to "🇻🇳 Tiếng Việt (Mặc định)",
                        "en-US" to "🇺🇸 Tiếng Anh (English)",
                        "zh-CN" to "🇨🇳 Tiếng Trung (Giản thể)",
                        "ja-JP" to "🇯🇵 Tiếng Nhật (日本語)",
                        "ko-KR" to "🇰🇷 Tiếng Hàn (한국어)",
                        "fr-FR" to "🇫🇷 Tiếng Pháp (Français)",
                        "ru-RU" to "🇷🇺 Tiếng Nga (Русский)",
                        "es-ES" to "🇪🇸 Tiếng Tây Ban Nha (Español)",
                        "th-TH" to "🇹🇭 Tiếng Thái (ไทย)"
                    ),
                    onSelect = { code, label ->
                        selectedTargetLang = code
                        selectedTargetLangLabel = label
                        repo.targetLanguage = code
                        repo.targetLanguageLabel = label
                    }
                )
            }

            // Chỉ hiển thị phong cách dịch khi người dùng chọn Gemini AI
            if (selectedEngine.startsWith("gemini")) {
                Spacer(modifier = Modifier.height(16.dp))
                SettingDropdown(
                    label = "🎭 Phong cách dịch ngữ cảnh (Gemini):",
                    currentValue = selectedStyleLabel,
                    options = listOf(
                        "Zhihu" to "🎬 Phim Ngắn Zhihu (Vả mặt, kịch tính)",
                        "ThuanViet" to "📖 Thuần Việt Văn Học (Trau chuốt, mượt mà)",
                        "CoTrang" to "⚔️ Cổ Trang Tiên Hiệp (Hán Việt chuẩn)",
                        "Auto" to "✨ Tự Động AI (Theo ngữ cảnh)",
                        "custom" to "✍️ Tự nhập Prompt tùy chỉnh..."
                    ),
                    onSelect = { code, label ->
                        selectedStyle = code
                        selectedStyleLabel = label
                        repo.selectedStyle = code
                    }
                )

                if (selectedStyle == "custom") {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customPromptText,
                        onValueChange = {
                            customPromptText = it
                            repo.geminiCustomPrompt = it
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        placeholder = {
                            Text(
                                "Nhập hướng dẫn prompt dịch cho Gemini (vd: Dịch theo lối cổ trang, xưng hô huynh/muội, giữ câu ngắn...)",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedBorderColor = PrimaryEmerald,
                            unfocusedBorderColor = Color(0xFF333544),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // 3. NÚT BẮT ĐẦU
            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val finalCustomPrompt = if (selectedStyle == "custom") customPromptText.trim() else ""
                    onStartProcessing(
                        uri,
                        fileName.ifBlank { "Video_${System.currentTimeMillis()}" },
                        fileDurationMs,
                        selectedLang,
                        selectedTargetLang,
                        selectedEngine,
                        selectedStyle,
                        finalCustomPrompt
                    )
                },
                enabled = selectedUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryEmerald,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "🚀 BẮT ĐẦU TẠO PHỤ ĐỀ & DỊCH THUẬT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun SettingDropdown(
    label: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFFC0C0C0), fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface)
                .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = currentValue, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                options.forEach { (code, optLabel) ->
                    DropdownMenuItem(
                        text = { Text(optLabel, color = Color.White) },
                        onClick = {
                            onSelect(code, optLabel)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
