package com.example.wifichat

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wifi_chat_settings", Context.MODE_PRIVATE)

    var userName: String by mutableStateOf(prefs.getString("user_name", "Пользователь") ?: "Пользователь")
        set(value) { field = value; prefs.edit().putString("user_name", value).apply() }

    var avatarPath: String? by mutableStateOf(prefs.getString("avatar_path", null))
        set(value) { field = value; prefs.edit().putString("avatar_path", value).apply() }

    var bubbleColorIndex: Int by mutableStateOf(prefs.getInt("bubble_color", 0))
        set(value) { field = value; prefs.edit().putInt("bubble_color", value).apply() }

    var textSizeIndex: Int by mutableStateOf(prefs.getInt("text_size", 1))
        set(value) { field = value; prefs.edit().putInt("text_size", value).apply() }

    var vibrationEnabled: Boolean by mutableStateOf(prefs.getBoolean("vibration", true))
        set(value) { field = value; prefs.edit().putBoolean("vibration", value).apply() }

    var soundEnabled: Boolean by mutableStateOf(prefs.getBoolean("sound", true))
        set(value) { field = value; prefs.edit().putBoolean("sound", value).apply() }

    var autoScrollEnabled: Boolean by mutableStateOf(prefs.getBoolean("auto_scroll", true))
        set(value) { field = value; prefs.edit().putBoolean("auto_scroll", value).apply() }

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
