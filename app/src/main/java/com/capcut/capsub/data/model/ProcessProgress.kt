package com.capcut.capsub.data.model

enum class ProcessStage {
    IDLE,
    EXTRACTING_AUDIO,   // Giai đoạn 1: Tách âm thanh M4A từ video
    UPLOADING_VOD,      // Giai đoạn 2: Tải âm thanh lên CapCut Cloud
    STT_TRANSCRIBING,   // Giai đoạn 3: AI CapCut nhận diện giọng nói
    AI_TRANSLATING,     // Giai đoạn 4: Gemini dịch phụ đề sang tiếng Việt
    COMPLETED,          // Hoàn tất
    ERROR,              // Lỗi
    CANCELLED           // Huỷ bởi người dùng
}

data class ProcessProgress(
    val stage: ProcessStage = ProcessStage.IDLE,
    val progress: Float = 0.0f, // Từ 0.0f đến 1.0f
    val message: String = "",
    val error: Throwable? = null,
    val resultDocument: SubtitleDocument? = null
) {
    val isRunning: Boolean get() = stage in listOf(
        ProcessStage.EXTRACTING_AUDIO,
        ProcessStage.UPLOADING_VOD,
        ProcessStage.STT_TRANSCRIBING,
        ProcessStage.AI_TRANSLATING
    )
}
