package com.capcut.capsub.ui.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.capcut.capsub.data.model.ProcessProgress
import com.capcut.capsub.data.model.ProcessStage
import com.capcut.capsub.ui.theme.DarkSurface
import com.capcut.capsub.ui.theme.PrimaryEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressBottomSheet(
    progress: ProcessProgress,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (!progress.isRunning) onDismiss()
        },
        sheetState = sheetState,
        containerColor = DarkSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "TIẾN TRÌNH TỰ ĐỘNG",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryEmerald,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Đang tạo & dịch phụ đề",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Linear Progress
            LinearProgressIndicator(
                progress = { progress.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = PrimaryEmerald,
                trackColor = Color(0xFF2A2B36)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = progress.message,
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(progress.progress * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryEmerald
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4 BƯỚC TIẾN TRÌNH
            StepItem(
                stepNumber = 1,
                title = "Tách âm thanh M4A (Không re-encode)",
                isCompleted = progress.progress >= 0.20f,
                isCurrent = progress.stage == ProcessStage.EXTRACTING_AUDIO
            )
            Spacer(modifier = Modifier.height(12.dp))

            StepItem(
                stepNumber = 2,
                title = "Tải lên CapCut VOD Cloud (5MB Chunks)",
                isCompleted = progress.progress >= 0.40f,
                isCurrent = progress.stage == ProcessStage.UPLOADING_VOD
            )
            Spacer(modifier = Modifier.height(12.dp))

            StepItem(
                stepNumber = 3,
                title = "AI CapCut nhận diện giọng nói (STT)",
                isCompleted = progress.progress >= 0.70f,
                isCurrent = progress.stage == ProcessStage.STT_TRANSCRIBING
            )
            Spacer(modifier = Modifier.height(12.dp))

            StepItem(
                stepNumber = 4,
                title = "Gemini AI dịch theo ngữ cảnh nhân vật",
                isCompleted = progress.stage == ProcessStage.COMPLETED,
                isCurrent = progress.stage == ProcessStage.AI_TRANSLATING
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Nút Hủy
            if (progress.isRunning) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Huỷ Tác Vụ", color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StepItem(
    stepNumber: Int,
    title: String,
    isCompleted: Boolean,
    isCurrent: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        when {
            isCompleted -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PrimaryEmerald,
                    modifier = Modifier.size(20.dp)
                )
            }
            isCurrent -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = PrimaryEmerald,
                    strokeWidth = 2.dp
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = Color(0xFF555768),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "$stepNumber. $title",
            fontSize = 14.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCompleted || isCurrent) Color.White else Color(0xFF7A7D91)
        )
    }
}
