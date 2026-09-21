package com.capcut.capsub

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.capcut.capsub.data.model.ProcessStage
import com.capcut.capsub.data.model.SubtitleDocument
import com.capcut.capsub.data.repository.SettingsRepository
import com.capcut.capsub.domain.service.TranscribeWorkerService
import com.capcut.capsub.ui.history.HistoryScreen
import com.capcut.capsub.ui.home.HomeScreen
import com.capcut.capsub.ui.home.ProgressBottomSheet
import com.capcut.capsub.ui.player.VideoPlayerScreen
import com.capcut.capsub.ui.settings.SettingsScreen
import com.capcut.capsub.ui.theme.CapSubTheme
import com.capcut.capsub.ui.theme.DarkBackground
import com.capcut.capsub.ui.theme.DarkCard
import com.capcut.capsub.ui.theme.PrimaryEmerald
import com.capcut.capsub.ui.tts.TtsStudioScreen

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()

        setContent {
            CapSubTheme {
                val repo = remember { SettingsRepository(this) }
                val progress by TranscribeWorkerService.sharedProgressFlow.collectAsState()

                var currentScreen by remember { mutableStateOf("main") } // "main", "settings", "player"
                var selectedTab by remember { mutableStateOf(0) } // 0: Studio (Tạo Phụ Đề), 1: Lịch Sử & Player
                var activeVideoUri by remember { mutableStateOf<Uri?>(null) }
                var activeSubtitleDoc by remember { mutableStateOf<SubtitleDocument?>(null) }
                var showProgressSheet by remember { mutableStateOf(false) }
                var lastHandledJobDocId by remember { mutableStateOf<Int?>(null) }

                // Khi tiến trình hoàn tất, lưu lịch sử và tự động chuyển sang Player ĐÚNG 1 LẦN
                LaunchedEffect(progress.stage, progress.resultDocument) {
                    if (progress.stage == ProcessStage.COMPLETED && progress.resultDocument != null && activeVideoUri != null) {
                        val docId = progress.resultDocument.hashCode()
                        if (docId != lastHandledJobDocId) {
                            lastHandledJobDocId = docId
                            val doc = progress.resultDocument!!
                            activeSubtitleDoc = doc

                            showProgressSheet = false
                            selectedTab = 1
                            currentScreen = "player"
                        }
                    }
                }

                when (currentScreen) {
                    "settings" -> {
                        SettingsScreen(onBack = { currentScreen = "main" })
                    }
                    "player" -> {
                        val videoUri = activeVideoUri
                        val doc = activeSubtitleDoc
                        if (videoUri != null && doc != null) {
                            VideoPlayerScreen(
                                videoUri = videoUri,
                                subtitleDoc = doc,
                                onBack = {
                                    activeSubtitleDoc = null
                                    activeVideoUri = null
                                    currentScreen = "main"
                                }
                            )
                        } else {
                            activeSubtitleDoc = null
                            activeVideoUri = null
                            currentScreen = "main"
                        }
                    }
                    else -> {
                        Scaffold(
                            containerColor = DarkBackground,
                            bottomBar = {
                                NavigationBar(
                                    containerColor = DarkCard,
                                    contentColor = Color.White
                                ) {
                                    NavigationBarItem(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Tạo Phụ Đề") },
                                        label = { Text("Tạo Phụ Đề") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = PrimaryEmerald,
                                            selectedTextColor = PrimaryEmerald,
                                            indicatorColor = PrimaryEmerald.copy(alpha = 0.2f),
                                            unselectedIconColor = Color.Gray,
                                            unselectedTextColor = Color.Gray
                                        )
                                    )

                                    NavigationBarItem(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = "Lồng Tiếng AI") },
                                        label = { Text("Lồng Tiếng AI") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = PrimaryEmerald,
                                            selectedTextColor = PrimaryEmerald,
                                            indicatorColor = PrimaryEmerald.copy(alpha = 0.2f),
                                            unselectedIconColor = Color.Gray,
                                            unselectedTextColor = Color.Gray
                                        )
                                    )

                                    NavigationBarItem(
                                        selected = selectedTab == 2,
                                        onClick = { selectedTab = 2 },
                                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Lịch Sử & Player") },
                                        label = { Text("Lịch Sử & Player") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = PrimaryEmerald,
                                            selectedTextColor = PrimaryEmerald,
                                            indicatorColor = PrimaryEmerald.copy(alpha = 0.2f),
                                            unselectedIconColor = Color.Gray,
                                            unselectedTextColor = Color.Gray
                                        )
                                    )
                                }
                            }
                        ) { paddingValues ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                            ) {
                                when (selectedTab) {
                                    0 -> {
                                        HomeScreen(
                                            onNavigateToSettings = { currentScreen = "settings" },
                                            onStartProcessing = { uri, name, durationMs, sourceLang, targetLang, model, style, customPrompt ->
                                                if (model.startsWith("gemini") && repo.geminiApiKeys.isEmpty()) {
                                                    Toast.makeText(this@MainActivity, "Vui lòng nhập Gemini API Key trong Cài đặt trước khi dùng Gemini!", Toast.LENGTH_LONG).show()
                                                    currentScreen = "settings"
                                                    return@HomeScreen
                                                }

                                                activeVideoUri = uri

                                                showProgressSheet = true
                                                repo.selectedModel = model
                                                repo.selectedStyle = style
                                                repo.targetLanguage = targetLang
                                                if (style == "custom" && customPrompt.isNotBlank()) {
                                                    repo.geminiCustomPrompt = customPrompt
                                                }
                                                lastHandledJobDocId = null

                                                TranscribeWorkerService.start(
                                                    context = this@MainActivity,
                                                    videoUri = uri,
                                                    videoName = name,
                                                    durationMs = durationMs,
                                                    sourceLang = sourceLang,
                                                    customPrompt = customPrompt,
                                                    outSrtFile = null
                                                )
                                            }
                                        )
                                    }
                                    1 -> {
                                        TtsStudioScreen(
                                            currentSubtitleDoc = activeSubtitleDoc,
                                            currentVideoUri = activeVideoUri,
                                            onSubtitleLoaded = { doc ->
                                                activeSubtitleDoc = doc
                                            },
                                            onNavigateToPlayer = { uri, doc ->
                                                activeVideoUri = uri ?: activeVideoUri
                                                activeSubtitleDoc = doc
                                                currentScreen = "player"
                                            },
                                            onNavigateToSettings = { currentScreen = "settings" }
                                        )
                                    }
                                    else -> {
                                        HistoryScreen(
                                            onNavigateToSettings = { currentScreen = "settings" },
                                            onPlayHistoryItem = { uri, doc ->
                                                activeVideoUri = uri
                                                activeSubtitleDoc = doc
                                                currentScreen = "player"
                                            }
                                        )
                                    }
                                }

                                if (showProgressSheet || progress.isRunning) {
                                    ProgressBottomSheet(
                                        progress = progress,
                                        onCancel = {
                                            val cancelIntent = android.content.Intent(this@MainActivity, TranscribeWorkerService::class.java).apply {
                                                action = TranscribeWorkerService.ACTION_CANCEL
                                            }
                                            startService(cancelIntent)
                                            showProgressSheet = false
                                        },
                                        onDismiss = { showProgressSheet = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
