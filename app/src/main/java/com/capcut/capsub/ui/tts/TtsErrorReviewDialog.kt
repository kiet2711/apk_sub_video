package com.capcut.capsub.ui.tts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.capcut.capsub.domain.tts.TtsFailedItem
import com.capcut.capsub.ui.theme.DarkCard
import com.capcut.capsub.ui.theme.PrimaryEmerald

@Composable
fun TtsErrorReviewDialog(
    failedItems: List<TtsFailedItem>,
    isRetrying: Boolean,
    threadCount: Int,
    geminiThreadCount: Int = 2,
    geminiApiKeysAvailable: Boolean = false,
    onRetryOne: (itemId: Int, editedText: String) -> Unit,
    onRetryAll: (editedTexts: Map<Int, String>) -> Unit,
    onTranslateWithGemini: (items: List<Pair<Int, String>>, progressCb: (Float, String) -> Unit, onDone: (Map<Int, String>) -> Unit) -> Unit = { _, _, _ -> },
    onTranslateSingleWithGemini: (itemId: Int, text: String, onDone: (String) -> Unit) -> Unit = { _, _, _ -> },
    onSkipErrors: () -> Unit,
    onDismiss: () -> Unit
) {
    val editedTexts = remember(failedItems) {
        failedItems.map { it.itemId to it.text }.toMutableStateMap()
    }

    var isTranslatingAll by remember { mutableStateOf(false) }
    var translatingItemIds by remember { mutableStateOf(setOf<Int>()) }
    var translationProgressPct by remember { mutableFloatStateOf(0f) }
    var translationProgressMsg by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = { if (!isRetrying && !isTranslatingAll) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Tiêu đề
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB74D))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Câu TTS cần xử lý",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Còn ${failedItems.size} câu chưa có audio hợp lệ",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                // Tiến trình dịch thuật AI nếu đang dịch
                if (isTranslatingAll) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFA5B4FC)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (translationProgressMsg.isNotBlank()) translationProgressMsg else "Đang dịch đa luồng với Gemini AI...",
                                    color = Color(0xFFA5B4FC),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { translationProgressPct.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF818CF8),
                                trackColor = Color(0xFF312E81)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Danh sách các câu lỗi
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(failedItems, key = { it.itemId }) { failure ->
                        val isSingleTranslating = translatingItemIds.contains(failure.itemId)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2029)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Câu #${failure.itemId}",
                                        color = PrimaryEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    if (failure.text.any { it.toString().matches(Regex("[\\u4e00-\\u9fff]")) }) {
                                        Text(
                                            text = "Chứa chữ Hán",
                                            color = Color(0xFFFFB74D),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Text(
                                    text = failure.reason,
                                    color = Color(0xFFFF8A80),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 7.dp)
                                )
                                OutlinedTextField(
                                    value = editedTexts[failure.itemId].orEmpty(),
                                    onValueChange = { editedTexts[failure.itemId] = it },
                                    enabled = !isRetrying && !isTranslatingAll,
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = PrimaryEmerald,
                                        unfocusedBorderColor = Color(0xFF484A5A)
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Nút Dịch AI đơn lẻ
                                    OutlinedButton(
                                        onClick = {
                                            val curText = editedTexts[failure.itemId].orEmpty()
                                            if (curText.isNotBlank()) {
                                                translatingItemIds = translatingItemIds + failure.itemId
                                                onTranslateSingleWithGemini(failure.itemId, curText) { transText ->
                                                    editedTexts[failure.itemId] = transText
                                                    translatingItemIds = translatingItemIds - failure.itemId
                                                }
                                            }
                                        },
                                        enabled = !isRetrying && !isTranslatingAll && !isSingleTranslating && geminiApiKeysAvailable,
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        if (isSingleTranslating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFFA5B4FC)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Đang dịch...", fontSize = 11.sp)
                                        } else {
                                            Icon(
                                                Icons.Default.Translate,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = Color(0xFFA5B4FC)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Dịch AI", color = Color(0xFFA5B4FC), fontSize = 11.sp)
                                        }
                                    }

                                    // Nút Thử lại câu này
                                    OutlinedButton(
                                        onClick = {
                                            onRetryOne(failure.itemId, editedTexts[failure.itemId].orEmpty())
                                        },
                                        enabled = !isRetrying && !isTranslatingAll && editedTexts[failure.itemId].orEmpty().isNotBlank()
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Thử lại câu này", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1. NÚT DỊCH TẤT CẢ BẰNG GEMINI ĐA LUỒNG
                Button(
                    onClick = {
                        if (!isTranslatingAll && !isRetrying) {
                            isTranslatingAll = true
                            translationProgressPct = 0.05f
                            translationProgressMsg = "Đang khởi chạy Gemini ${geminiThreadCount} luồng dịch câu lỗi..."
                            val itemsToTranslate = failedItems.map { failure ->
                                val text = editedTexts[failure.itemId]?.takeIf { it.isNotBlank() } ?: failure.text
                                failure.itemId to text
                            }
                            onTranslateWithGemini(
                                itemsToTranslate,
                                { pct, msg ->
                                    translationProgressPct = pct
                                    translationProgressMsg = msg
                                },
                                { transMap ->
                                    transMap.forEach { (id, trans) ->
                                        if (trans.isNotBlank()) {
                                            editedTexts[id] = trans
                                        }
                                    }
                                    isTranslatingAll = false
                                }
                            )
                        }
                    },
                    enabled = !isRetrying && !isTranslatingAll && geminiApiKeysAvailable && failedItems.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isTranslatingAll) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đang dịch đa luồng với Gemini AI...", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (geminiApiKeysAvailable) "Dịch lại tất cả bằng Gemini (${geminiThreadCount} luồng)" else "Dịch Gemini (Chưa cấu hình API Key)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. NÚT THỬ LẠI TẤT CẢ
                Button(
                    onClick = { onRetryAll(editedTexts.toMap()) },
                    enabled = !isRetrying && !isTranslatingAll && failedItems.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRetrying) "Đang tạo âm thanh..." else "Thử tạo lại tất cả (${threadCount} luồng)",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3. NÚT BỎ QUA CÁC CÂU LỖI & TIẾP TỤC
                Button(
                    onClick = onSkipErrors,
                    enabled = !isRetrying && !isTranslatingAll,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF261D16),
                        contentColor = Color(0xFFFFB74D)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFFFB74D))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bỏ qua các câu lỗi & Tiếp tục",
                        color = Color(0xFFFFB74D),
                        fontWeight = FontWeight.Bold
                    )
                }

                // 4. NÚT ĐÓNG
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isRetrying && !isTranslatingAll,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Đóng cửa sổ (sửa sau)")
                }
            }
        }
    }
}
