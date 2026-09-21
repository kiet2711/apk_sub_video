package com.capcut.capsub.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Quản lý lưu trữ cài đặt ứng dụng (Gemini API Key, cấu hình phụ đề, kiểu hộp đen BlackBox).
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("capsub_settings", Context.MODE_PRIVATE)

    var geminiApiKeys: List<String>
        get() {
            val raw = prefs.getString("gemini_api_keys", "") ?: ""
            return raw.split(",", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }
        }
        set(value) {
            prefs.edit().putString("gemini_api_keys", value.joinToString("\n")).apply()
        }

    var selectedModel: String
        get() = prefs.getString("selected_model", "capcut") ?: "capcut"
        set(value) = prefs.edit().putString("selected_model", value).apply()

    var selectedStyle: String
        get() = prefs.getString("selected_style", "Zhihu") ?: "Zhihu"
        set(value) = prefs.edit().putString("selected_style", value).apply()

    var defaultSourceLanguage: String
        get() = prefs.getString("source_lang", "zh-CN") ?: "zh-CN"
        set(value) = prefs.edit().putString("source_lang", value).apply()

    var subtitleFontSizeSp: Float
        get() = prefs.getFloat("sub_font_size", 20.0f)
        set(value) = prefs.edit().putFloat("sub_font_size", value).apply()

    var isBlackBoxEnabled: Boolean
        get() = prefs.getBoolean("blackbox_enabled", true)
        set(value) = prefs.edit().putBoolean("blackbox_enabled", value).apply()

    var blackBoxHeightDp: Int
        get() = prefs.getInt("blackbox_height", 64)
        set(value) = prefs.edit().putInt("blackbox_height", value).apply()

    var blackBoxOpacity: Float
        get() = prefs.getFloat("blackbox_opacity", 0.92f)
        set(value) = prefs.edit().putFloat("blackbox_opacity", value).apply()

    var subtitleOffsetY: Float
        get() = prefs.getFloat("sub_offset_y", 0f)
        set(value) = prefs.edit().putFloat("sub_offset_y", value).apply()

    var subtitleColorHex: String
        get() = prefs.getString("sub_color_hex", "#FFFFFF") ?: "#FFFFFF"
        set(value) = prefs.edit().putString("sub_color_hex", value).apply()

    var subtitleMode: String
        get() = prefs.getString("sub_mode", "translated") ?: "translated"
        set(value) = prefs.edit().putString("sub_mode", value).apply()

    var geminiCustomPrompt: String
        get() = prefs.getString("gemini_custom_prompt", "") ?: ""
        set(value) = prefs.edit().putString("gemini_custom_prompt", value).apply()

    var targetLanguage: String
        get() = prefs.getString("target_lang", "vi-VN") ?: "vi-VN"
        set(value) = prefs.edit().putString("target_lang", value).apply()

    var targetLanguageLabel: String
        get() = prefs.getString("target_lang_label", "🇻🇳 Tiếng Việt (Mặc định)") ?: "🇻🇳 Tiếng Việt (Mặc định)"
        set(value) = prefs.edit().putString("target_lang_label", value).apply()

    var geminiThreadCount: Int
        get() = prefs.getInt("gemini_thread_count", 2).coerceIn(1, 10)
        set(value) = prefs.edit().putInt("gemini_thread_count", value.coerceIn(1, 10)).apply()

    var ttsThreadCount: Int
        get() = prefs.getInt("tts_thread_count", 50).coerceIn(1, 100)
        set(value) = prefs.edit().putInt("tts_thread_count", value.coerceIn(1, 100)).apply()

    var selectedTtsVoice: String
        get() = prefs.getString("selected_tts_voice", "ICL_uranus_vi_female_yuenan1") ?: "ICL_uranus_vi_female_yuenan1"
        set(value) = prefs.edit().putString("selected_tts_voice", value).apply()

    var originalAudioVolume: Float
        get() = prefs.getFloat("original_audio_volume", 1.0f).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat("original_audio_volume", value.coerceIn(0f, 1f)).apply()

    var aiAudioVolume: Float
        get() = prefs.getFloat("ai_audio_volume", 1.0f).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat("ai_audio_volume", value.coerceIn(0f, 1f)).apply()
}
