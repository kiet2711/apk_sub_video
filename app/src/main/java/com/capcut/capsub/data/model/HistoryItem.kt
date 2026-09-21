package com.capcut.capsub.data.model

import kotlinx.serialization.Serializable

@Serializable
data class HistoryItem(
    val id: String,
    val videoUri: String,
    val videoName: String,
    val durationMs: Long,
    val srtFilePath: String,
    val sentenceCount: Int,
    val translationEngine: String,
    val sourceLanguage: String = "zh-CN",
    val createdAt: Long = System.currentTimeMillis()
)
