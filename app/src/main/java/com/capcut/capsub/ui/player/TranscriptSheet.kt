package com.capcut.capsub.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.model.SubtitleItem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Danh sách kịch bản phụ đề tương tác bên dưới Player.
 * Tự động cuộn theo câu đang phát, bấm vào câu nào sẽ tua video tới câu đó.
 * Hỗ trợ nút ✏️ để sửa trực tiếp câu phụ đề dịch.
 */
@Composable
fun TranscriptSheet(
    document: SubtitleDocument,
    activeSubtitle: SubtitleItem?,
    displayMode: String = "translated",
    onSeekTo: (Long) -> Unit,
    onSubtitleEdited: ((SubtitleItem, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var editingItem by remember { mutableStateOf<SubtitleItem?>(null) }

    // Tự động cuộn danh sách tới câu đang phát
    LaunchedEffect(activeSubtitle?.id) {
        activeSubtitle?.let { active ->
            val index = document.items.indexOfFirst { it.id == active.id }
            if (index != -1) {
                listState.animateScrollToItem((index - 1).coerceAtLeast(0))
            }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        itemsIndexed(document.items) { index, item ->
            val isActive = activeSubtitle?.id == item.id
            TranscriptItemCard(
                item = item,
                isActive = isActive,
                displayMode = displayMode,
                onClick = { onSeekTo(item.startMs) },
                onEditClick = { editingItem = item }
            )
        }
    }

    editingItem?.let { item ->
        EditSubtitleDialog(
            item = item,
            onSave = { newText ->
                item.translatedText = newText
                onSubtitleEdited?.invoke(item, newText)
            },
            onDismiss = { editingItem = null }
        )
    }
}

@Composable
fun TranscriptItemCard(
    item: SubtitleItem,
    isActive: Boolean,
    displayMode: String = "translated",
    onClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
    }

    val timecodeColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.formatSrtTimecode(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = timecodeColor
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Text(
                            text = "▶ ĐANG PHÁT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Sửa câu",
                            tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            }

            val normalizedMode = displayMode.lowercase()
            val translated = item.getTranslationOnlyText().ifBlank { item.originalText }
            val primaryText = if (normalizedMode == "original") item.originalText else translated
            Text(
                text = primaryText,
                fontSize = 15.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Chỉ hiển thêm câu gốc khi người dùng chọn đúng chế độ Song Ngữ.
            if (normalizedMode == "bilingual" && translated != item.originalText.trim()) {
                Text(
                    text = item.originalText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
