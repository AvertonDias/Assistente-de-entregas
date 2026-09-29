package com.example.util

import android.content.Context
import android.content.SharedPreferences

data class RecipientDraft(
    val street: String = "",
    val number: String = "",
    val complement: String = "",
    val neighborhood: String = "",
    val name: String = "",
    val document: String = "",
    val signatureJson: String = "",
    val timestamp: Long = 0L
)

object RecipientDraftManager {
    private const val PREFS_NAME = "recipient_draft_prefs"
    private const val KEY_STREET = "draft_street"
    private const val KEY_NUMBER = "draft_number"
    private const val KEY_COMPLEMENT = "draft_complement"
    private const val KEY_NEIGHBORHOOD = "draft_neighborhood"
    private const val KEY_NAME = "draft_name"
    private const val KEY_DOCUMENT = "draft_document"
    private const val KEY_SIGNATURE = "draft_signature"
    private const val KEY_TIMESTAMP = "draft_timestamp"

    private const val MAX_AGE_MS = 2 * 60 * 60 * 1000L // 2 horas de validade

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveDraft(
        context: Context,
        street: String,
        number: String,
        complement: String,
        neighborhood: String,
        name: String,
        document: String,
        signatureJson: String
    ) {
        // Apenas salva se houver conteúdo relevante
        if (name.isBlank() && document.isBlank() && signatureJson.isBlank() && street.isBlank()) {
            return
        }
        getPrefs(context).edit()
            .putString(KEY_STREET, street)
            .putString(KEY_NUMBER, number)
            .putString(KEY_COMPLEMENT, complement)
            .putString(KEY_NEIGHBORHOOD, neighborhood)
            .putString(KEY_NAME, name)
            .putString(KEY_DOCUMENT, document)
            .putString(KEY_SIGNATURE, signatureJson)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getDraft(context: Context): RecipientDraft? {
        val prefs = getPrefs(context)
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        if (timestamp == 0L || (System.currentTimeMillis() - timestamp) > MAX_AGE_MS) {
            return null
        }
        val name = prefs.getString(KEY_NAME, "").orEmpty()
        val document = prefs.getString(KEY_DOCUMENT, "").orEmpty()
        val signatureJson = prefs.getString(KEY_SIGNATURE, "").orEmpty()
        val street = prefs.getString(KEY_STREET, "").orEmpty()

        if (name.isBlank() && document.isBlank() && signatureJson.isBlank()) {
            return null
        }

        return RecipientDraft(
            street = street,
            number = prefs.getString(KEY_NUMBER, "").orEmpty(),
            complement = prefs.getString(KEY_COMPLEMENT, "").orEmpty(),
            neighborhood = prefs.getString(KEY_NEIGHBORHOOD, "").orEmpty(),
            name = name,
            document = document,
            signatureJson = signatureJson,
            timestamp = timestamp
        )
    }

    fun clearDraft(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
