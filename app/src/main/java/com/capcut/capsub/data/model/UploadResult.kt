package com.capcut.capsub.data.model

data class UploadResult(
    val vid: String,
    val md5: String,
    val durationMs: Long,
    val size: Long = 0,
    val storeUri: String = ""
)
