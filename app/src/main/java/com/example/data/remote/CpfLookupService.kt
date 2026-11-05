package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CpfLookupService {
    private const val TAG = "CpfLookupService"
    private const val BASE_URL = "https://apicpf.com/api/consulta"

    const val FALLBACK_API_KEY = "ff10aa9e1548fddf98ea6891711222c6f5ba60abe2c342800340dbebb62bcb9d"

    val defaultApiKey: String
        get() = try {
            val key = BuildConfig.APICPF_KEY
            if (key.isNullOrBlank() || key.startsWith("MY_")) {
                FALLBACK_API_KEY
            } else {
                key
            }
        } catch (_: Throwable) {
            FALLBACK_API_KEY
        }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    data class CpfLookupResult(
        val success: Boolean,
        val cpf: String = "",
        val nome: String = "",
        val genero: String = "",
        val dataNascimento: String = "",
        val errorMessage: String? = null
    )

    suspend fun lookupCpf(rawCpf: String, customApiKey: String? = null): CpfLookupResult = withContext(Dispatchers.IO) {
        val cleanCpf = rawCpf.filter { it.isDigit() }
        if (cleanCpf.length != 11) {
            return@withContext CpfLookupResult(success = false, errorMessage = "CPF deve conter 11 dígitos")
        }

        val effectiveKey = (customApiKey?.trim()?.takeIf { it.isNotBlank() }) ?: defaultApiKey

        try {
            val url = "$BASE_URL?cpf=$cleanCpf"
            val request = Request.Builder()
                .url(url)
                .addHeader("X-API-KEY", effectiveKey)
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            val json = if (body.isNotBlank()) try { JSONObject(body) } catch (_: Throwable) { null } else null

            val creditsHeader = response.header("X-Credits-Remaining")
                ?: response.header("x-credits-remaining")
                ?: response.header("X-RateLimit-Remaining")
                ?: response.header("x-ratelimit-remaining")
                ?: response.header("X-Credits")
                ?: response.header("x-credits")

            val jsonCredits = json?.let { obj ->
                if (obj.has("credits_remaining")) obj.optInt("credits_remaining")
                else if (obj.has("credits")) obj.optInt("credits")
                else if (obj.has("saldo")) obj.optInt("saldo")
                else if (obj.has("creditos")) obj.optInt("creditos")
                else null
            }

            val remainingCredits = creditsHeader?.toIntOrNull() ?: jsonCredits

            try {
                if (com.example.DeliveryApp.instance != null) {
                    CpfApiUsageTracker.recordLookup(com.example.DeliveryApp.instance, remainingCredits)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Usage tracking error: ${e.message}")
            }

            if (!response.isSuccessful && body.isBlank()) {
                return@withContext CpfLookupResult(
                    success = false,
                    errorMessage = "Erro na consulta (HTTP ${response.code})"
                )
            }

            val code = json?.optInt("code", response.code) ?: response.code

            if (code == 200 && json != null && json.has("data")) {
                val data = json.getJSONObject("data")
                val nome = data.optString("nome", "").trim()
                val genero = data.optString("genero", "").trim()
                val nasc = data.optString("data_nascimento", "").trim()
                if (nome.isNotBlank()) {
                    try {
                        CpfVerificationTracker.markCpfVerified(cleanCpf, nome)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Verification tracking error: ${e.message}")
                    }
                }
                return@withContext CpfLookupResult(
                    success = nome.isNotBlank(),
                    cpf = cleanCpf,
                    nome = nome,
                    genero = genero,
                    dataNascimento = nasc,
                    errorMessage = if (nome.isBlank()) "Nome não retornado para este CPF" else null
                )
            } else {
                val msg = json?.optString("message", "CPF não localizado na base") ?: "CPF não localizado na base"
                return@withContext CpfLookupResult(success = false, errorMessage = msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao consultar CPF $cleanCpf", e)
            return@withContext CpfLookupResult(
                success = false,
                errorMessage = e.localizedMessage ?: "Falha de conexão com o servidor de consulta"
            )
        }
    }
}
