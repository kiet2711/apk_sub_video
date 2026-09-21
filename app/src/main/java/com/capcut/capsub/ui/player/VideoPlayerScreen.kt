package com.capcut.capsub.ui.player

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.repository.SettingsRepository
import com.capcut.capsub.player.PlayerManager
import com.capcut.capsub.ui.theme.DarkBackground
import com.capcut.capsub.ui.theme.PrimaryEmerald

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    subtitleDoc: SubtitleDocument,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val repo = remember { SettingsRepository(context) }
    val historyRepo = remember { com.capcut.capsub.data.repository.HistoryRepository(context) }
    val playerManager = remember { PlayerManager(context) }

    val toggleOrientation = {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    androidx.activity.compose.BackHandler {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var originalVolume by remember { mutableStateOf(repo.originalAudioVolume) }
    var aiVolume by remember { mutableStateOf(repo.aiAudioVolume) }
    var showVolumeSheet by remember { mutableStateOf(false) }

    val historyItem = remember(videoUri) {
        historyRepo.getHistoryList().firstOrNull { it.videoUri == videoUri.toString() }
    }
    val effectiveVoice = historyItem?.ttsVoice ?: repo.selectedTtsVoice

    DisposableEffect(videoUri, effectiveVoice) {
        com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(context, subtitleDoc, effectiveVoice)
        playerManager.initialize(videoUri, originalVolume)
        playerManager.ttsAudioScheduler.setSubtitleDocument(subtitleDoc)
        playerManager.ttsAudioScheduler.setAiVolume(aiVolume)
        onDispose {
            playerManager.release()
        }
    }

    var subtitleDocVersion by remember { mutableStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(subtitleDocVersion) {
        if (subtitleDocVersion > 0) {
            com.capcut.capsub.domain.tts.TtsCacheHelper.linkAudioFiles(context, subtitleDoc, effectiveVoice)
            playerManager.ttsAudioScheduler.setSubtitleDocument(subtitleDoc)
        }
    }

    val currentPositionMs by playerManager.currentPositionMs.collectAsState()
    val activeSubtitle = remember(currentPositionMs, subtitleDocVersion) {
        subtitleDoc.getActiveItem(currentPositionMs)
    }

    // Cấu hình hiển thị phụ đề trực tiếp
    var subtitleMode by remember { mutableStateOf(repo.subtitleMode) }
    var isBlackBoxEnabled by remember { mutableStateOf(repo.isBlackBoxEnabled) }
    var fontSize by remember { mutableStateOf(repo.subtitleFontSizeSp) }
    var offsetY by remember { mutableStateOf(repo.subtitleOffsetY) }
    var blackBoxOpacity by remember { mutableStateOf(repo.blackBoxOpacity) }
    var colorHex by remember { mutableStateOf(repo.subtitleColorHex) }
    var showCustomizerSheet by remember { mutableStateOf(false) }

    // Bộ xuất file SRT
    val exportSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(subtitleDoc.toSrtString(subtitleMode).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Đã lưu file phụ đề SRT thành công!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi lưu file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (isLandscape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // ExoPlayer Surface View (Full screen)
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = playerManager.exoPlayer
                        useController = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            val textColor = try {
                val cleanHex = colorHex.removePrefix("#")
                Color(cleanHex.toLong(16).toInt() or -0x1000000)
            } catch (e: Exception) {
                Color.White
            }

            // Subtitle Overlay
            SubtitleOverlay(
                activeSubtitle = activeSubtitle,
                displayMode = subtitleMode,
                isBlackBoxEnabled = isBlackBoxEnabled,
                blackBoxOpacity = blackBoxOpacity,
                fontSizeSp = fontSize.sp,
                textColor = textColor,
                offsetY = offsetY,
                onOffsetYChange = { newY ->
                    offsetY = newY
                    repo.subtitleOffsetY = newY
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
            )

            // Top Floating Controls Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Thu nhỏ màn hình", tint = Color.White)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = {
                            val nextMode = when (subtitleMode) {
                                "translated" -> "bilingual"
                                "bilingual" -> "none"
                                else -> "translated"
                            }
                            subtitleMode = nextMode
                            repo.subtitleMode = nextMode
                        },
                        label = {
                            Text(
                                text = when (subtitleMode) {
                                    "translated" -> "🌐 Tiếng Việt"
                                    "bilingual" -> "🌐 Song Ngữ"
                                    else -> "Tắt Sub"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (subtitleMode != "none") PrimaryEmerald.copy(alpha = 0.2f) else Color(0xFF2A2B36),
                            labelColor = if (subtitleMode != "none") PrimaryEmerald else Color.Gray
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = { showCustomizerSheet = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Chỉnh Sub", tint = PrimaryEmerald)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = { showVolumeSheet = true }) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "Âm lượng", tint = PrimaryEmerald)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = { exportSrtLauncher.launch("subtitles.srt") }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Lưu SRT", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = toggleOrientation) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Thu nhỏ màn hình", tint = Color.White)
                    }
                }
            }
        }
    } else {
        Scaffold(
            containerColor = DarkBackground
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 1. THANH ĐIỀU KHIỂN TRÊN CÙNG
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Chuyển chế độ Sub
                        AssistChip(
                            onClick = {
                                val nextMode = when (subtitleMode) {
                                    "translated" -> "bilingual"
                                    "bilingual" -> "none"
                                    else -> "translated"
                                }
                                subtitleMode = nextMode
                                repo.subtitleMode = nextMode
                            },
                            label = {
                                Text(
                                    text = when (subtitleMode) {
                                        "translated" -> "🌐 Tiếng Việt"
                                        "bilingual" -> "🌐 Song Ngữ"
                                        else -> "Tắt Sub"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (subtitleMode != "none") PrimaryEmerald.copy(alpha = 0.2f) else Color(0xFF2A2B36),
                                labelColor = if (subtitleMode != "none") PrimaryEmerald else Color.Gray
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Nút mở bảng Tùy chỉnh Sub Live
                        IconButton(onClick = { showCustomizerSheet = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Chỉnh Sub", tint = PrimaryEmerald)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Nút mở bảng Điều Khiển Âm Lượng Kép
                        IconButton(onClick = { showVolumeSheet = true }) {
                            Icon(Icons.Default.GraphicEq, contentDescription = "Âm lượng", tint = PrimaryEmerald)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Xuất SRT
                        IconButton(onClick = { exportSrtLauncher.launch("subtitles.srt") }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Lưu SRT", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Nút xoay ngang màn hình / Toàn màn hình
                        IconButton(onClick = toggleOrientation) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Toàn màn hình", tint = Color.White)
                        }
                    }
                }

                // 2. KHUNG HÌNH VIDEO & PHỤ ĐỀ NỔI (EXOPLAYER + SUBTITLE OVERLAY)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    // ExoPlayer Surface View
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = playerManager.exoPlayer
                                useController = true
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    val textColor = try {
                        val cleanHex = colorHex.removePrefix("#")
                        Color(cleanHex.toLong(16).toInt() or -0x1000000)
                    } catch (e: Exception) {
                        Color.White
                    }

                    // Lớp phụ đề nổi (VLC Style + BlackBox)
                    SubtitleOverlay(
                        activeSubtitle = activeSubtitle,
                        displayMode = subtitleMode,
                        isBlackBoxEnabled = isBlackBoxEnabled,
                        blackBoxOpacity = blackBoxOpacity,
                        fontSizeSp = fontSize.sp,
                        textColor = textColor,
                        offsetY = offsetY,
                        onOffsetYChange = { newY ->
                            offsetY = newY
                            repo.subtitleOffsetY = newY
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 54.dp)
                    )
                }

                // 3. DANH SÁCH KỊCH BẢN PHỤ ĐỀ (Bấm vào tua tới câu đó, sửa câu)
                TranscriptSheet(
                    document = subtitleDoc,
                    activeSubtitle = activeSubtitle,
                    onSeekTo = { seekMs -> playerManager.seekTo(seekMs) },
                    onSubtitleEdited = { _, _ ->
                        subtitleDocVersion++
                        historyRepo.updateSubtitleForUri(videoUri, subtitleDoc)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    if (showCustomizerSheet) {
        SubtitleCustomizerSheet(
            fontSize = fontSize,
            offsetY = offsetY,
            isBlackBoxEnabled = isBlackBoxEnabled,
            blackBoxOpacity = blackBoxOpacity,
            colorHex = colorHex,
            onFontSizeChange = {
                fontSize = it
                repo.subtitleFontSizeSp = it
            },
            onOffsetYChange = {
                offsetY = it
                repo.subtitleOffsetY = it
            },
            onBlackBoxToggle = {
                isBlackBoxEnabled = it
                repo.isBlackBoxEnabled = it
            },
            onBlackBoxOpacityChange = {
                blackBoxOpacity = it
                repo.blackBoxOpacity = it
            },
            onColorHexChange = {
                colorHex = it
                repo.subtitleColorHex = it
            },
            onDismiss = { showCustomizerSheet = false }
        )
    }

    if (showVolumeSheet) {
        DualVolumeSheet(
            originalVolume = originalVolume,
            aiVolume = aiVolume,
            onOriginalVolumeChange = {
                originalVolume = it
                repo.originalAudioVolume = it
                playerManager.setOriginalVolume(it)
            },
            onAiVolumeChange = {
                aiVolume = it
                repo.aiAudioVolume = it
                playerManager.ttsAudioScheduler.setAiVolume(it)
            },
            onDismiss = { showVolumeSheet = false }
        )
    }
}
