package com.capcut.capsub.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capcut.capsub.ui.theme.DarkCard
import com.capcut.capsub.ui.theme.PrimaryEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualVolumeSheet(
    originalVolume: Float,
    aiVolume: Float,
    onOriginalVolumeChange: (Float) -> Unit,
    onAiVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkCard,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = PrimaryEmerald,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Điều Khiển Âm Thanh Độc Lập",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 1. THANH CHỈNH TIẾNG GỐC (VIDEO GỐC)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F2029), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Âm lượng Video Gốc",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "${(originalVolume * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64B5F6)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onOriginalVolumeChange(if (originalVolume > 0f) 0f else 1.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (originalVolume > 0f) Icons.Default.VolumeDown else Icons.Default.VolumeMute,
                            contentDescription = "Mute",
                            tint = Color.Gray
                        )
                    }

                    Slider(
                        value = originalVolume,
                        onValueChange = onOriginalVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF64B5F6),
                            activeTrackColor = Color(0xFF64B5F6),
                            inactiveTrackColor = Color(0xFF323444)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. THANH CHỈNH TIẾNG AI LỒNG TIẾNG
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F2029), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Âm lượng Giọng Đọc AI",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "${(aiVolume * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryEmerald
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onAiVolumeChange(if (aiVolume > 0f) 0f else 1.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (aiVolume > 0f) Icons.Default.VolumeDown else Icons.Default.VolumeOff,
                            contentDescription = "Tắt tiếng AI",
                            tint = Color.Gray
                        )
                    }

                    Slider(
                        value = aiVolume,
                        onValueChange = onAiVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryEmerald,
                            activeTrackColor = PrimaryEmerald,
                            inactiveTrackColor = Color(0xFF323444)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
