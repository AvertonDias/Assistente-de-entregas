package com.example.data.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
    suspend fun setBubbleEnabled(enabled: Boolean)
    suspend fun setBubbleSize(sizeDp: Int)
    suspend fun setCompactMode(compact: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setConfirmBeforeFill(confirm: Boolean)
    suspend fun setConfirmBeforeSendSignature(confirm: Boolean)
    suspend fun setThemeMode(mode: String) // SYSTEM, LIGHT, DARK
    suspend fun setScanTargetMode(mode: String) // NEXT_DELIVERY, ALL_SCREEN, CUSTOM_AREA
    suspend fun setCustomScanArea(left: Float, top: Float, right: Float, bottom: Float)
    suspend fun setSignatureSpeedMode(speed: String) // "ULTRA_SLOW", "SLOW", "NORMAL", "FAST"
    suspend fun setUpdateJsonUrl(url: String)
    suspend fun exportAllDataJson(): String
    suspend fun importDataJson(jsonStr: String, merge: Boolean): ImportResult
}

data class AppSettings(
    val bubbleEnabled: Boolean = true,
    val bubbleSizeDp: Int = 56,
    val initialX: Int = 100,
    val initialY: Int = 300,
    val isCompactMode: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val confirmBeforeFill: Boolean = false,
    val confirmBeforeSendSignature: Boolean = true,
    val themeMode: String = "SYSTEM",
    val scanTargetMode: String = "NEXT_DELIVERY", // "NEXT_DELIVERY", "ALL_SCREEN", "CUSTOM_AREA"
    val scanAreaLeft: Float = 0f,
    val scanAreaTop: Float = 0.10f,
    val scanAreaRight: Float = 1.0f,
    val scanAreaBottom: Float = 0.40f,
    val customAreaConfigured: Boolean = false,
    val signatureSpeedMode: String = "ULTRA_SLOW",
    val updateJsonUrl: String = ""
)

data class ImportResult(
    val success: Boolean,
    val importedPersons: Int = 0,
    val importedDeliveries: Int = 0,
    val message: String = ""
)
