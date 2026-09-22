package com.capcut.capsub.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capcut.capsub.data.model.SubtitleItem
import kotlin.math.roundToInt

/**
 * Lớp phủ phụ đề mềm (Soft Subtitle Overlay) hiển thị trực tiếp trên Video Player (VLC Style).
 * Tích hợp tính năng Hộp Đen (BlackBox) che hoàn toàn phụ đề tiếng Trung cứng có sẵn trên video,
 * hỗ trợ cử chỉ kéo thả để di chuyển vị trí hộp đen theo từng video khác nhau.
 */
@Composable
fun SubtitleOverlay(
    activeSubtitle: SubtitleItem?,
    displayMode: String = "translated",
    isBlackBoxEnabled: Boolean = true,
    blackBoxOpacity: Float = 0.92f,
    fontSizeSp: TextUnit = 20.sp,
    textColor: Color = Color.White,
    offsetY: Float = 0f,
    onOffsetYChange: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (activeSubtitle == null || displayMode == "none") return

    var localOffsetY by remember(offsetY) { mutableFloatStateOf(offsetY) }
    val translationOnly = activeSubtitle.getTranslationOnlyText()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, localOffsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    localOffsetY += dragAmount.y
                    onOffsetYChange?.invoke(localOffsetY)
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Nền Hộp Đen (BlackBox) che phụ đề gốc
        val boxBackgroundModifier = if (isBlackBoxEnabled) {
            Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = blackBoxOpacity.coerceIn(0.1f, 1.0f)))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        } else {
            Modifier
                .wrapContentHeight()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        }

        Box(
            modifier = boxBackgroundModifier,
            contentAlignment = Alignment.Center
        ) {
            when (displayMode.lowercase()) {
                "bilingual" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Dòng chữ gốc (vàng nhạt / xám)
                        Text(
                            text = activeSubtitle.originalText,
                            fontSize = (fontSizeSp.value * 0.85f).sp,
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        // Dòng tiếng Việt dịch
                        if (translationOnly.isNotBlank() && translationOnly != activeSubtitle.originalText.trim()) {
                            Text(
                                text = translationOnly,
                                fontSize = fontSizeSp,
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                "original" -> {
                    Text(
                        text = activeSubtitle.originalText,
                        fontSize = fontSizeSp,
                        color = Color(0xFFFFEB3B),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    // Mặc định: Bản dịch tiếng Việt
                    val text = translationOnly.ifBlank { activeSubtitle.originalText }
                    Text(
                        text = text,
                        fontSize = fontSizeSp,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
