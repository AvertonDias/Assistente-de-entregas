package com.example.data.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

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
        pullFromCloudBackground()
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

    private fun saveToPrefs(map: Map<String, CpfVerifiedEntry>) {
        val ctx = appContext ?: return
        try {
            val json = JSONObject()
            for ((key, entry) in map) {
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

    fun isCpfVerified(rawCpf: String): Boolean {
        val digits = rawCpf.filter { it.isDigit() }
        if (digits.length != 11) return false
        return _verifiedCpfs.value.containsKey(digits)
    }

    fun getVerifiedName(rawCpf: String): String? {
        val digits = rawCpf.filter { it.isDigit() }
        return _verifiedCpfs.value[digits]?.verifiedName?.takeIf { it.isNotBlank() }
    }

    fun getAllVerifiedEntries(): Map<String, CpfVerifiedEntry> {
        return _verifiedCpfs.value
    }

    fun importVerifiedEntries(entries: Map<String, CpfVerifiedEntry>) {
        if (entries.isEmpty()) return
        val current = _verifiedCpfs.value.toMutableMap()
        var changed = false
        for ((key, entry) in entries) {
            val existing = current[key]
            if (existing == null || (entry.verifiedName.isNotBlank() && existing.verifiedName.isBlank())) {
                current[key] = entry
                changed = true
            }
        }
        if (changed) {
            _verifiedCpfs.value = current
            saveToPrefs(current)
        }
    }

    fun markCpfVerified(rawCpf: String, officialName: String = "", syncToCloud: Boolean = true) {
        val digits = rawCpf.filter { it.isDigit() }
        if (digits.length != 11) return

        val currentMap = _verifiedCpfs.value.toMutableMap()
        val entry = CpfVerifiedEntry(
            cpfDigits = digits,
            verifiedName = officialName,
            verifiedTimestamp = System.currentTimeMillis()
        )
        currentMap[digits] = entry
        _verifiedCpfs.value = currentMap
        saveToPrefs(currentMap)

        if (syncToCloud) {
            syncSingleCpfToFirebase(digits, officialName)
        }
    }

    private fun resolveUserId(context: Context): String {
        return try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            if (user != null && user.uid.isNotBlank()) {
                user.uid
            } else {
                val prefs = context.getSharedPreferences("app_delivery_auth_prefs", Context.MODE_PRIVATE)
                val localEmail = prefs.getString("logged_email", null)
                if (!localEmail.isNullOrBlank()) {
                    "user_${localEmail.replace(".", "_").replace("@", "_")}"
                } else {
                    "local_device"
                }
            }
        } catch (_: Throwable) {
            "local_device"
        }
    }

    private fun getFirestore(context: Context): FirebaseFirestore? {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val app = FirebaseApp.initializeApp(context)
                if (app == null) {
                    val options = FirebaseOptions.fromResource(context)
                    if (options != null) {
                        FirebaseApp.initializeApp(context, options)
                    }
                }
            }
            FirebaseFirestore.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    private fun syncSingleCpfToFirebase(digits: String, officialName: String) {
        val ctx = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firestore = getFirestore(ctx) ?: return@launch
                val userId = resolveUserId(ctx)
                val docRef = firestore.collection("users")
                    .document(userId)
                    .collection("cpfs_verificados")
                    .document(digits)

                val data = mapOf(
                    "cpfDigits" to digits,
                    "verifiedName" to officialName,
                    "verifiedTimestamp" to System.currentTimeMillis(),
                    "verified" to true
                )
                docRef.set(data, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w("CpfVerification", "Aviso sync Firebase CPF verificado: ${e.message}")
            }
        }
    }

    private fun pullFromCloudBackground() {
        val ctx = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firestore = getFirestore(ctx) ?: return@launch
                val userId = resolveUserId(ctx)
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("cpfs_verificados")
                    .get()
                    .await()

                if (!snapshot.isEmpty) {
                    val incoming = mutableMapOf<String, CpfVerifiedEntry>()
                    for (doc in snapshot.documents) {
                        val digits = doc.id.filter { it.isDigit() }
                        if (digits.length == 11) {
                            incoming[digits] = CpfVerifiedEntry(
                                cpfDigits = digits,
                                verifiedName = doc.getString("verifiedName") ?: "",
                                verifiedTimestamp = doc.getLong("verifiedTimestamp") ?: System.currentTimeMillis()
                            )
                        }
                    }
                    if (incoming.isNotEmpty()) {
                        importVerifiedEntries(incoming)
                    }
                }
            } catch (_: Throwable) {
                // Offline ou falha de rede
            }
        }
    }
}
