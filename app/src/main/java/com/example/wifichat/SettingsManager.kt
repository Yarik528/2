package com.example.wifichat

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wifi_chat_settings", Context.MODE_PRIVATE)

    private val _userName = mutableStateOf(prefs.getString("user_name", "Пользователь") ?: "Пользователь")
    var userName: String
        get() = _userName.value
        set(value) {
            _userName.value = value
            prefs.edit().putString("user_name", value).apply()
        }

    private val _avatarPath = mutableStateOf<String?>(prefs.getString("avatar_path", null))
    var avatarPath: String?
        get() = _avatarPath.value
        set(value) {
            _avatarPath.value = value
            prefs.edit().putString("avatar_path", value).apply()
        }

    private val _bubbleColorIndex = mutableStateOf(prefs.getInt("bubble_color", 0))
    var bubbleColorIndex: Int
        get() = _bubbleColorIndex.value
        set(value) {
            _bubbleColorIndex.value = value
            prefs.edit().putInt("bubble_color", value).apply()
        }

    private val _textSizeIndex = mutableStateOf(prefs.getInt("text_size", 1))
    var textSizeIndex: Int
        get() = _textSizeIndex.value
        set(value) {
            _textSizeIndex.value = value
            prefs.edit().putInt("text_size", value).apply()
        }

    private val _vibrationEnabled = mutableStateOf(prefs.getBoolean("vibration", true))
    var vibrationEnabled: Boolean
        get() = _vibrationEnabled.value
        set(value) {
            _vibrationEnabled.value = value
            prefs.edit().putBoolean("vibration", value).apply()
        }

    private val _soundEnabled = mutableStateOf(prefs.getBoolean("sound", true))
    var soundEnabled: Boolean
        get() = _soundEnabled.value
        set(value) {
            _soundEnabled.value = value
            prefs.edit().putBoolean("sound", value).apply()
        }

    private val _autoScrollEnabled = mutableStateOf(prefs.getBoolean("auto_scroll", true))
    var autoScrollEnabled: Boolean
        get() = _autoScrollEnabled.value
        set(value) {
            _autoScrollEnabled.value = value
            prefs.edit().putBoolean("auto_scroll", value).apply()
        }

    fun getBubbleColor(): Color = when (bubbleColorIndex) {
        0 -> Color(0xFF00BCD4) // Бирюзовый
        1 -> Color(0xFF4CAF50) // Зеленый
        2 -> Color(0xFFFF5722) // Оранжевый
        3 -> Color(0xFF9C27B0) // Фиолетовый
        4 -> Color(0xFF2196F3) // Синий
        else -> Color(0xFF00BCD4)
    }

    fun getTextSize(): Float = when (textSizeIndex) {
        0 -> 14f
        1 -> 16f
        2 -> 18f
        else -> 16f
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        userName = "Пользователь"
        avatarPath = null
        bubbleColorIndex = 0
        textSizeIndex = 1
        vibrationEnabled = true
        soundEnabled = true
        autoScrollEnabled = true
    }
}
