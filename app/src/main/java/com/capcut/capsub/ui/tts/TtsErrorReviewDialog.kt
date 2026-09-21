package com.capcut.capsub.ui.tts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    onRetryOne: (itemId: Int, editedText: String) -> Unit,
    onRetryAll: (editedTexts: Map<Int, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val editedTexts = remember(failedItems) {
        failedItems.map { it.itemId to it.text }.toMutableStateMap()
    }

    Dialog(
        onDismissRequest = { if (!isRetrying) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB74D))
                    Spacer(modifier = Modifier.padding(horizontal = 5.dp))
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

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(failedItems, key = { it.itemId }) { failure ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2029)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Câu #${failure.itemId}",
                                    color = PrimaryEmerald,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = failure.reason,
                                    color = Color(0xFFFF8A80),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 7.dp)
                                )
                                OutlinedTextField(
                                    value = editedTexts[failure.itemId].orEmpty(),
                                    onValueChange = { editedTexts[failure.itemId] = it },
                                    enabled = !isRetrying,
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
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onRetryOne(failure.itemId, editedTexts[failure.itemId].orEmpty())
                                        },
                                        enabled = !isRetrying && editedTexts[failure.itemId].orEmpty().isNotBlank()
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Text(" Thử lại câu này")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onRetryAll(editedTexts.toMap()) },
                    enabled = !isRetrying && failedItems.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                    Text(
                        text = if (isRetrying) "Đang thử lại..." else "Thử lại tất cả (${threadCount} luồng)",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isRetrying,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Text("Đóng và giữ các câu đã thành công")
                }
            }
        }
    }
}
