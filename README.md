# CapSub Studio - Android App (CapCut STT & Gemini AI Translator)

Ứng dụng Android độc lập (100% Client-side, không cần Server trung gian) chuyển đổi giọng nói trong video/audio thành phụ đề SRT qua API CapCut Cloud, dịch phụ đề ngữ cảnh bằng Google Gemini AI và phát video với phụ đề mềm (VLC Style) kèm tính năng **Hộp Đen (BlackBox)** che phụ đề cứng có sẵn.

---

## 🌟 Tính Năng Nổi Bật

1. **Nhận diện giọng nói siêu chuẩn (CapCut STT Cloud):**
   - Hỗ trợ tiếng Trung (`zh-CN`), tiếng Anh (`en-US`), tiếng Việt (`vi-VN`), tiếng Nhật, tiếng Hàn...
   - Tự động chia nhỏ (Chunked Upload 5MB) và phân đoạn 10 phút/lần đối với video dài 1-3 tiếng.
2. **Dịch thuật AI theo ngữ cảnh (Google Gemini API):**
   - Hỗ trợ nạp danh sách nhiều API Key (Round-Robin xoay vòng và tự đổi key ngay khi gặp lỗi 429).
   - Smart Chunking: Gom 40-60 dòng/request, bảo toàn 100% timecode và phiên âm Hán Việt chuẩn mực (Dư Chiêu Chiêu, Cố tổng...).
   - Đa dạng phong cách: *Phim ngắn Zhihu (vả mặt, kịch tính)*, *Thuần Việt văn học*, *Cổ trang tiên hiệp*...
3. **Phát Video & Phụ Đề Mềm (VLC-Style Player - AndroidX Media3 ExoPlayer):**
   - Không cần render lại video (tiết kiệm 100% thời gian render, không nóng máy, không hao pin).
   - **Hộp Đen (BlackBox):** Tạo nền đen mờ che sạch phụ đề tiếng Trung cứng có sẵn trên video, hiển thị tiếng Việt rõ nét phía trên. Có thể kéo thả điều chỉnh vị trí hộp đen theo ý muốn.
   - Chuyển đổi 3 chế độ: *Chỉ Tiếng Việt* | *Song Ngữ* | *Tắt Phụ Đề*.
   - Tab kịch bản phụ đề cuộn theo video thời gian thực, chạm vào câu nào sẽ tua video tới câu đó.
   - Xuất file `.srt` lưu về máy hoặc chia sẻ qua Zalo/Telegram.
4. **Tối ưu hiệu năng & Chống quá tải di động:**
   - Dùng Android Native `MediaExtractor` + `MediaMuxer`: Tách audio M4A từ video 30 phút chỉ mất 1-2 giây với 0 MB phụ thuộc ngoài.
   - Luồng đọc buffer 5MB: App chỉ tốn ~35MB - 45MB RAM dù video nặng 1GB hay 5GB.
   - `ForegroundService` + Notification: Chống bị Android tắt tiến trình khi người dùng khóa màn hình.

---

## 🛠️ Hướng Dẫn Mở & Biên Dịch Trong Android Studio

1. Mở **Android Studio** (phiên bản Koala / Ladybug hoặc mới hơn).
2. Chọn **Open** và trỏ đến thư mục: `D:\tool_capcut_tts_stt\port_adr`.
3. Chờ Gradle Sync hoàn tất các thư viện:
   - AndroidX Media3 (ExoPlayer 1.4.1)
   - Jetpack Compose (Material 3)
   - OkHttp 4.12.0
   - Kotlin Coroutines & Serialization
4. Cắm điện thoại Android (bật USB Debugging) hoặc mở máy ảo Android (Emulator Android 10 trở lên).
5. Bấm nút **Run (Shift + F10)** để cài đặt ứng dụng lên máy.

---

## 📁 Cấu Trúc Mã Nguồn

```
port_adr/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/capcut/capsub/
│   │       ├── CapSubApplication.kt
│   │       ├── MainActivity.kt               # Điều hướng Home, Player, Settings
│   │       ├── data/
│   │       │   ├── api/
│   │       │   │   ├── CapCutSigner.kt       # Chữ ký MD5, SHA-256, AWS SigV4
│   │       │   │   ├── CapCutVodUploader.kt  # Upload chunk 5MB lên ByteDance VOD
│   │       │   │   ├── CapCutSttClient.kt    # Tạo task & polling STT CapCut
│   │       │   │   └── GeminiTranslator.kt   # Dịch SRT bằng Gemini REST API
│   │       │   ├── model/
│   │       │   │   ├── DeviceConfig.kt       # Giả lập thiết bị Mac/PC
│   │       │   │   ├── SubtitleItem.kt       # Model câu phụ đề
│   │       │   │   ├── SubtitleDocument.kt   # Quản lý danh sách SRT/ASS
│   │       │   │   └── ProcessProgress.kt    # Tiến trình 4 giai đoạn
│   │       │   └── repository/
│   │       │       └── SettingsRepository.kt # Lưu API Key, font size, BlackBox
│   │       ├── domain/
│   │       │   ├── media/
│   │       │   │   ├── AudioExtractor.kt     # Tách audio M4A bằng MediaMuxer
│   │       │   │   └── AudioChunker.kt       # Cắt đoạn 10 phút cho video dài
│   │       │   ├── pipeline/
│   │       │   │   └── SubtitlingPipeline.kt # Điều phối 4 bước xử lý
│   │       │   └── service/
│   │       │       └── TranscribeWorkerService.kt # ForegroundService chạy ngầm
│   │       ├── player/
│   │       │   └── PlayerManager.kt          # Điều khiển Media3 ExoPlayer
│   │       └── ui/
│   │           ├── home/
│   │           │   ├── HomeScreen.kt         # Chọn file & chọn phong cách dịch
│   │           │   └── ProgressBottomSheet.kt# Modal tiến trình 4 bước
│   │           ├── player/
│   │           │   ├── VideoPlayerScreen.kt  # Màn hình phát video
│   │           │   ├── SubtitleOverlay.kt    # Phụ đề nổi + Hộp đen che sub cứng
│   │           │   └── TranscriptSheet.kt    # Danh sách phụ đề tương tác
│   │           ├── settings/
│   │           │   └── SettingsScreen.kt     # Quản lý Gemini API Key
│   │           └── theme/
│   │               ├── Color.kt
│   │               └── Theme.kt
│   └── src/test/java/com/capcut/capsub/
│       ├── CapCutSignerTest.kt               # Kiểm tra thuật toán mã hóa & chữ ký
│       └── SubtitleParserTest.kt             # Kiểm tra parser & export SRT/ASS
├── build.gradle.kts
├── settings.gradle.kts
├── PROJECT_PLAN.md
└── README.md
```
