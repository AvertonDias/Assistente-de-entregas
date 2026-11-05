package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class CpfVerifiedEntry(
    val cpfDigits: String,
    val verifiedName: String = "",
    val verifiedTimestamp: Long = System.currentTimeMillis()
)

object CpfVerificationTracker {
    private const val PREFS_NAME = "cpf_verification_prefs"
    private const val KEY_VERIFIED_DATA = "verified_cpfs_data"

    private val _verifiedCpfs = MutableStateFlow<Map<String, CpfVerifiedEntry>>(emptyMap())
    val verifiedCpfs: StateFlow<Map<String, CpfVerifiedEntry>> = _verifiedCpfs.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_VERIFIED_DATA, null) ?: "{}"
        try {
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, CpfVerifiedEntry>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = json.getJSONObject(key)
                map[key] = CpfVerifiedEntry(
                    cpfDigits = key,
                    verifiedName = obj.optString("name", ""),
                    verifiedTimestamp = obj.optLong("time", System.currentTimeMillis())
                )
            }
            _verifiedCpfs.value = map
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isCpfVerified(rawCpf: String): Boolean {
        val digits = rawCpf.filter { it.isDigit() }
        if (digits.length != 11) return false
        return _verifiedCpfs.value.containsKey(digits)
    }

    fun getVerifiedName(rawCpf: String): String? {
        val digits = rawCpf.filter { it.isDigit() }
        return _verifiedCpfs.value[digits]?.verifiedName?.takeIf { it.isNotBlank() }
    }

    fun markCpfVerified(rawCpf: String, officialName: String = "") {
        val digits = rawCpf.filter { it.isDigit() }
        if (digits.length != 11) return

        val currentMap = _verifiedCpfs.value.toMutableMap()
        currentMap[digits] = CpfVerifiedEntry(
            cpfDigits = digits,
            verifiedName = officialName,
            verifiedTimestamp = System.currentTimeMillis()
        )
        _verifiedCpfs.value = currentMap

        val ctx = appContext ?: return
        try {
            val json = JSONObject()
            for ((key, entry) in currentMap) {
                val obj = JSONObject()
                obj.put("name", entry.verifiedName)
                obj.put("time", entry.verifiedTimestamp)
                json.put(key, obj)
            }
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_VERIFIED_DATA, json.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
