package com.capcut.capsub.ui.player

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capcut.capsub.ui.theme.DarkCard
import com.capcut.capsub.ui.theme.PrimaryEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleCustomizerSheet(
    fontSize: Float,
    offsetY: Float,
    isBlackBoxEnabled: Boolean,
    blackBoxOpacity: Float,
    colorHex: String,
    onFontSizeChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onBlackBoxToggle: (Boolean) -> Unit,
    onBlackBoxOpacityChange: (Float) -> Unit,
    onColorHexChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val colorOptions = listOf(
        Pair("#FFFFFF", "Trắng"),
        Pair("#FFD54F", "Vàng nghệ"),
        Pair("#FFFF00", "Vàng sáng"),
        Pair("#4DF0A0", "Xanh ngọc"),
        Pair("#FF9800", "Cam"),
        Pair("#80D8FF", "Xanh biển")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚙ Tùy Chỉnh Phụ Đề Live",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(onClick = {
                    onFontSizeChange(20f)
                    onOffsetYChange(0f)
                    onBlackBoxToggle(true)
                    onBlackBoxOpacityChange(0.92f)
                    onColorHexChange("#FFFFFF")
                }) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Đặt lại", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. CỠ CHỮ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Kích thước chữ:", color = Color.LightGray, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = { onFontSizeChange((fontSize - 1).coerceAtLeast(8f)) },
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("A-", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${fontSize.toInt()} sp", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { onFontSizeChange((fontSize + 1).coerceAtMost(36f)) },
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("A+", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Slider(
                value = fontSize,
                onValueChange = onFontSizeChange,
                valueRange = 8f..36f,
                steps = 27,
                colors = SliderDefaults.colors(thumbColor = PrimaryEmerald, activeTrackColor = PrimaryEmerald)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. VỊ TRÍ NÂNG / HẠ (Y-OFFSET)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vị trí (Nâng / Hạ):", color = Color.LightGray, fontSize = 14.sp)
                Text(
                    text = if (offsetY == 0f) "Mặc định" else "${offsetY.toInt()} px",
                    color = PrimaryEmerald,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = offsetY,
                onValueChange = onOffsetYChange,
                valueRange = -150f..150f,
                colors = SliderDefaults.colors(thumbColor = PrimaryEmerald, activeTrackColor = PrimaryEmerald)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. HỘP ĐEN (BLACKBOX)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Hộp Đen che sub cứng:", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Che phụ đề tiếng Trung có sẵn của video", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = isBlackBoxEnabled,
                    onCheckedChange = onBlackBoxToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryEmerald, checkedTrackColor = PrimaryEmerald.copy(alpha = 0.5f))
                )
            }

            if (isBlackBoxEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Độ mờ Hộp Đen:", color = Color.LightGray, fontSize = 13.sp)
                    Text("${(blackBoxOpacity * 100).toInt()}%", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = blackBoxOpacity,
                    onValueChange = onBlackBoxOpacityChange,
                    valueRange = 0.3f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = PrimaryEmerald, activeTrackColor = PrimaryEmerald)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. MÀU SẮC CHỮ
            Text("Màu sắc chữ phụ đề:", color = Color.LightGray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                colorOptions.forEach { (hex, _) ->
                    val color = parseColor(hex)
                    val isSelected = colorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorHexChange(hex) }
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) PrimaryEmerald else Color.Gray,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

private fun parseColor(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorInt = cleanHex.toLong(16).toInt() or -0x1000000
        Color(colorInt)
    } catch (e: Exception) {
        Color.White
    }
}
