package com.capcut.capsub.ui.tts

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.capcut.capsub.data.api.GeminiTranslator
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.repository.SettingsRepository
import com.capcut.capsub.ui.theme.CardBorder
import com.capcut.capsub.ui.theme.DarkCard
import com.capcut.capsub.ui.theme.DarkSurface
import com.capcut.capsub.ui.theme.PrimaryEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GeminiTranslateSubtitleDialog(
    subtitleDoc: SubtitleDocument,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onTranslationCompleted: (SubtitleDocument) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository(context) }
    val apiKeys = repo.geminiApiKeys
    val hasApiKey = apiKeys.isNotEmpty()

    // 2 model duy nhất theo yêu cầu
    val modelOptions = remember {
        listOf(
            "gemini-3.5-flash-lite" to "🤖 Gemini 3.5 Flash-Lite (RPD cao)",
            "gemini-3.1-flash-lite" to "🤖 Gemini 3.1 Flash-Lite (Khuyên dùng)"
        )
    }
    var selectedModel by remember {
        mutableStateOf(
            if (repo.selectedModel == "gemini-3.1-flash-lite") "gemini-3.1-flash-lite"
            else "gemini-3.5-flash-lite"
        )
    }
    var selectedModelLabel by remember(selectedModel) {
        mutableStateOf(modelOptions.find { it.first == selectedModel }?.second ?: modelOptions[0].second)
    }

    // Danh sách ngôn ngữ dịch
    val langOptions = remember {
        listOf(
            "Tiếng Việt" to "🇻🇳 Tiếng Việt",
            "Tiếng Anh" to "🇺🇸 Tiếng Anh (English)",
            "Tiếng Trung" to "🇨🇳 Tiếng Trung (Chinese)",
            "Tiếng Nhật" to "🇯🇵 Tiếng Nhật (Japanese)",
            "Tiếng Hàn" to "🇰🇷 Tiếng Hàn (Korean)"
        )
    }
    var selectedTargetLang by remember { mutableStateOf(repo.targetLanguage.ifBlank { "Tiếng Việt" }) }
    var selectedTargetLangLabel by remember(selectedTargetLang) {
        mutableStateOf(langOptions.find { it.first == selectedTargetLang }?.second ?: "🇻🇳 Tiếng Việt")
    }

    var contextPromptText by remember { mutableStateOf(repo.geminiCustomPrompt) }

    var isTranslating by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var progressMessage by remember { mutableStateOf("") }
    var translationJob by remember { mutableStateOf<Job?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isTranslating) onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = PrimaryEmerald,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dịch Phụ Đề (Gemini AI)",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Dịch ${subtitleDoc.items.size} câu phụ đề sang ngôn ngữ đích để lồng tiếng hoặc xem Vietsub:",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )

                // 1. Chọn ngôn ngữ đích
                CompactSettingDropdown(
                    label = "🌐 Ngôn ngữ dịch sang:",
                    currentValue = selectedTargetLangLabel,
                    options = langOptions,
                    enabled = !isTranslating,
                    onSelect = { code, label ->
                        selectedTargetLang = code
                        selectedTargetLangLabel = label
                    }
                )

                // 2. Chọn Model Gemini (3.5 Flash-Lite hoặc 3.1 Flash-Lite)
                CompactSettingDropdown(
                    label = "🤖 Model Gemini:",
                    currentValue = selectedModelLabel,
                    options = modelOptions,
                    enabled = !isTranslating,
                    onSelect = { code, label ->
                        selectedModel = code
                        selectedModelLabel = label
                    }
                )

                // 3. Nhập ngữ cảnh / gợi ý dịch (Tuỳ chọn)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✍️ Ngữ cảnh / Gợi ý dịch:",
                            fontSize = 12.sp,
                            color = Color(0xFFB0B0B8),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Có thể bỏ trống",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = contextPromptText,
                        onValueChange = { contextPromptText = it },
                        placeholder = {
                            Text(
                                text = "Ví dụ: Phim cổ trang, xưng hô huynh - đệ; video review công nghệ; văn phong vui vẻ...",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        },
                        enabled = !isTranslating,
                        minLines = 2,
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryEmerald,
                            unfocusedBorderColor = CardBorder,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            disabledContainerColor = DarkSurface,
                            cursorColor = PrimaryEmerald
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. Trạng thái API Key
                if (hasApiKey) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Đã cấu hình ${apiKeys.size} Gemini API Key",
                            color = PrimaryEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Chưa có Gemini API Key!",
                                color = Color(0xFFFFB74D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        TextButton(
                            onClick = {
                                onDismiss()
                                onNavigateToSettings()
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "👉 Bấm vào đây để mở Cài Đặt và thêm Key",
                                color = PrimaryEmerald,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 4. Thanh tiến trình khi đang dịch
                if (isTranslating) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = PrimaryEmerald,
                            trackColor = Color(0xFF2A2B36)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = progressMessage,
                            color = PrimaryEmerald,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isTranslating) {
                TextButton(
                    onClick = {
                        translationJob?.cancel()
                        isTranslating = false
                        progressMessage = "Đã huỷ dịch."
                    }
                ) {
                    Text("Huỷ dịch", color = Color(0xFFFF6B6B))
                }
            } else {
                Button(
                    onClick = {
                        if (!hasApiKey) {
                            Toast.makeText(context, "Vui lòng nhập Gemini API Key trong Cài Đặt!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isTranslating = true
                        progressFraction = 0.05f
                        progressMessage = "Đang chuẩn bị dịch..."
                        translationJob = scope.launch(Dispatchers.IO) {
                            try {
                                val translator = GeminiTranslator(
                                    apiKeys = apiKeys,
                                    modelId = selectedModel
                                )
                                val promptToUse = if (contextPromptText.isNotBlank()) {
                                    contextPromptText.trim()
                                } else {
                                    repo.geminiCustomPrompt
                                }
                                val translatedDoc = translator.translateSubtitles(
                                    document = subtitleDoc,
                                    stylePreset = repo.selectedStyle,
                                    customPrompt = promptToUse,
                                    targetLanguage = selectedTargetLang,
                                    chunkSize = 45,
                                    threadCount = repo.geminiThreadCount
                                ) { pct, msg ->
                                    progressFraction = pct
                                    progressMessage = msg
                                }

                                withContext(Dispatchers.Main) {
                                    isTranslating = false
                                    Toast.makeText(context, "Đã dịch xong ${subtitleDoc.items.size} câu phụ đề!", Toast.LENGTH_SHORT).show()
                                    onTranslationCompleted(translatedDoc)
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isTranslating = false
                                    progressMessage = "Lỗi dịch: ${e.message}"
                                    Toast.makeText(context, "Lỗi dịch: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = hasApiKey && subtitleDoc.items.isNotEmpty(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryEmerald,
                        disabledContainerColor = Color(0xFF2A2B36)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = if (hasApiKey) Color.Black else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bắt Đầu Dịch",
                        color = if (hasApiKey) Color.Black else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        },
        dismissButton = {
            if (!isTranslating) {
                TextButton(onClick = onDismiss) {
                    Text("Đóng", color = Color.Gray)
                }
            }
        },
        containerColor = DarkCard
    )
}

@Composable
private fun CompactSettingDropdown(
    label: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    enabled: Boolean = true,
    onSelect: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFFB0B0B8),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentValue,
                    fontSize = 13.sp,
                    color = if (enabled) Color.White else Color.Gray,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (enabled) Color.Gray else Color.DarkGray
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                options.forEach { (code, optLabel) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optLabel,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        },
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
