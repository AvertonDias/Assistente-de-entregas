package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.dao.DeliveryDao
import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.Delivery
import com.example.data.local.entity.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepositoryImpl(
    private val context: Context,
    private val personDao: PersonDao,
    private val deliveryDao: DeliveryDao
) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_delivery_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettingsFromPrefs())

    override fun getSettings(): Flow<AppSettings> = _settingsFlow.asStateFlow()

    private fun loadSettingsFromPrefs(): AppSettings {
        return AppSettings(
            bubbleEnabled = prefs.getBoolean("bubble_enabled", true),
            bubbleSizeDp = prefs.getInt("bubble_size_dp", 56),
            initialX = prefs.getInt("initial_x", 100),
            initialY = prefs.getInt("initial_y", 300),
            isCompactMode = prefs.getBoolean("compact_mode", false),
            vibrationEnabled = prefs.getBoolean("vibration_enabled", true),
            soundEnabled = prefs.getBoolean("sound_enabled", true),
            confirmBeforeFill = prefs.getBoolean("confirm_before_fill", false),
            confirmBeforeSendSignature = prefs.getBoolean("confirm_before_signature", true),
            themeMode = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM",
            scanTargetMode = prefs.getString("scan_target_mode", "NEXT_DELIVERY") ?: "NEXT_DELIVERY",
            scanAreaLeft = prefs.getFloat("scan_area_left", 0f),
            scanAreaTop = prefs.getFloat("scan_area_top", 0.10f),
            scanAreaRight = prefs.getFloat("scan_area_right", 1.0f),
            scanAreaBottom = prefs.getFloat("scan_area_bottom", 0.40f),
            customAreaConfigured = prefs.getBoolean("custom_area_configured", false),
            signatureSpeedMode = prefs.getString("signature_speed_mode", "ULTRA_SLOW") ?: "ULTRA_SLOW",
            updateJsonUrl = prefs.getString("update_json_url", "") ?: ""
        )
    }

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = _settingsFlow.value
        val updated = transform(current)
        saveSettingsToPrefs(updated)
        _settingsFlow.value = updated
    }

    private fun saveSettingsToPrefs(settings: AppSettings) {
        prefs.edit()
            .putBoolean("bubble_enabled", settings.bubbleEnabled)
            .putInt("bubble_size_dp", settings.bubbleSizeDp)
            .putInt("initial_x", settings.initialX)
            .putInt("initial_y", settings.initialY)
            .putBoolean("compact_mode", settings.isCompactMode)
            .putBoolean("vibration_enabled", settings.vibrationEnabled)
            .putBoolean("sound_enabled", settings.soundEnabled)
            .putBoolean("confirm_before_fill", settings.confirmBeforeFill)
            .putBoolean("confirm_before_signature", settings.confirmBeforeSendSignature)
            .putString("theme_mode", settings.themeMode)
            .putString("themeMode", settings.themeMode)
            .putString("scan_target_mode", settings.scanTargetMode)
            .putFloat("scan_area_left", settings.scanAreaLeft)
            .putFloat("scan_area_top", settings.scanAreaTop)
            .putFloat("scan_area_right", settings.scanAreaRight)
            .putFloat("scan_area_bottom", settings.scanAreaBottom)
            .putBoolean("custom_area_configured", settings.customAreaConfigured)
            .putString("signature_speed_mode", settings.signatureSpeedMode)
            .putString("update_json_url", settings.updateJsonUrl)
            .apply()
    }

    override suspend fun setBubbleEnabled(enabled: Boolean) = updateSettings { it.copy(bubbleEnabled = enabled) }
    override suspend fun setBubbleSize(sizeDp: Int) = updateSettings { it.copy(bubbleSizeDp = sizeDp) }
    override suspend fun setCompactMode(compact: Boolean) = updateSettings { it.copy(isCompactMode = compact) }
    override suspend fun setVibrationEnabled(enabled: Boolean) = updateSettings { it.copy(vibrationEnabled = enabled) }
    override suspend fun setSoundEnabled(enabled: Boolean) = updateSettings { it.copy(soundEnabled = enabled) }
    override suspend fun setConfirmBeforeFill(confirm: Boolean) = updateSettings { it.copy(confirmBeforeFill = confirm) }
    override suspend fun setConfirmBeforeSendSignature(confirm: Boolean) = updateSettings { it.copy(confirmBeforeSendSignature = confirm) }
    override suspend fun setThemeMode(mode: String) = updateSettings { it.copy(themeMode = mode) }
    override suspend fun setScanTargetMode(mode: String) = updateSettings { it.copy(scanTargetMode = mode) }
    override suspend fun setSignatureSpeedMode(speed: String) = updateSettings { it.copy(signatureSpeedMode = speed) }
    override suspend fun setUpdateJsonUrl(url: String) = updateSettings { it.copy(updateJsonUrl = url) }
    override suspend fun setCustomScanArea(left: Float, top: Float, right: Float, bottom: Float) = updateSettings {
        it.copy(
            scanAreaLeft = left,
            scanAreaTop = top,
            scanAreaRight = right,
            scanAreaBottom = bottom,
            customAreaConfigured = true,
            scanTargetMode = "CUSTOM_AREA"
        )
    }

    override suspend fun exportAllDataJson(): String = withContext(Dispatchers.IO) {
        val persons = personDao.getAllPersons().first()
        val settings = _settingsFlow.value

        val root = JSONObject()
        root.put("version", 1)
        root.put("exportTimestamp", System.currentTimeMillis())

        val personsArray = JSONArray()
        for (p in persons) {
            val pObj = JSONObject()
            pObj.put("id", p.id)
            pObj.put("nome", p.nome)
            pObj.put("documento", p.documento)
            pObj.put("endereco", p.endereco)
            pObj.put("numero", p.numero)
            pObj.put("assinatura", p.assinatura)
            pObj.put("coRecebedoresJson", p.coRecebedoresJson)
            pObj.put("dataCriacao", p.dataCriacao)
            pObj.put("dataAtualizacao", p.dataAtualizacao)
            personsArray.put(pObj)
        }
        root.put("pessoas", personsArray)

        val sObj = JSONObject()
        sObj.put("bubbleEnabled", settings.bubbleEnabled)
        sObj.put("bubbleSizeDp", settings.bubbleSizeDp)
        sObj.put("isCompactMode", settings.isCompactMode)
        sObj.put("themeMode", settings.themeMode)
        root.put("configuracoes", sObj)

        root.toString(2)
    }

    override suspend fun importDataJson(jsonStr: String, merge: Boolean): ImportResult = withContext(Dispatchers.IO) {
        try {
            if (jsonStr.isBlank()) return@withContext ImportResult(false, message = "Arquivo JSON vazio.")
            val root = JSONObject(jsonStr)

            val personsArray = root.optJSONArray("pessoas")

            if (personsArray == null) {
                return@withContext ImportResult(false, message = "Formato inválido: lista 'pessoas' não encontrada.")
            }

            var countP = 0

            val newPersons = mutableListOf<Person>()
            for (i in 0 until personsArray.length()) {
                val pObj = personsArray.getJSONObject(i)
                newPersons.add(
                    Person(
                        id = if (merge) 0 else pObj.optLong("id", 0),
                        nome = pObj.optString("nome", "Sem Nome"),
                        documento = pObj.optString("documento", ""),
                        endereco = pObj.optString("endereco", ""),
                        numero = pObj.optString("numero", ""),
                        complemento = "",
                        bairro = "",
                        cidade = "",
                        uf = "",
                        observacao = "",
                        assinatura = pObj.optString("assinatura", ""),
                        coRecebedoresJson = pObj.optString("coRecebedoresJson", ""),
                        dataCriacao = pObj.optLong("dataCriacao", System.currentTimeMillis()),
                        dataAtualizacao = pObj.optLong("dataAtualizacao", System.currentTimeMillis())
                    )
                )
            }
            personDao.insertAll(newPersons)
            countP = newPersons.size

            ImportResult(
                success = true,
                importedPersons = countP,
                importedDeliveries = 0,
                message = "Importação concluída com sucesso ($countP destinatários)."
            )
        } catch (e: Exception) {
            ImportResult(false, message = "Erro ao processar JSON: ${e.localizedMessage}")
        }
    }
}
