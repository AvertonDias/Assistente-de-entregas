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

data class ScreenDraft(
    val personId: Long = 0L,
    val address: String = "",
    val number: String = "",
    val complement: String = "",
    val neighborhood: String = "",
    val recebedoresJson: String = "",
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

    private const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L // 7 dias de validade

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
        if (name.isBlank() && document.isBlank() && signatureJson.isBlank() && street.isBlank() && number.isBlank() && complement.isBlank() && neighborhood.isBlank()) {
            clearDraft(context)
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
        val number = prefs.getString(KEY_NUMBER, "").orEmpty()
        val complement = prefs.getString(KEY_COMPLEMENT, "").orEmpty()
        val neighborhood = prefs.getString(KEY_NEIGHBORHOOD, "").orEmpty()

        if (name.isBlank() && document.isBlank() && signatureJson.isBlank() && street.isBlank() && number.isBlank() && complement.isBlank() && neighborhood.isBlank()) {
            return null
        }

        return RecipientDraft(
            street = street,
            number = number,
            complement = complement,
            neighborhood = neighborhood,
            name = name,
            document = document,
            signatureJson = signatureJson,
            timestamp = timestamp
        )
    }

    fun clearDraft(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun saveScreenDraft(
        context: Context,
        personId: Long,
        address: String,
        number: String,
        complement: String,
        neighborhood: String,
        recebedoresJson: String
    ) {
        val prefix = "screen_draft_${personId}_"
        if (address.isBlank() && number.isBlank() && complement.isBlank() && neighborhood.isBlank() && recebedoresJson.isBlank()) {
            clearScreenDraft(context, personId)
            return
        }
        getPrefs(context).edit()
            .putString("${prefix}address", address)
            .putString("${prefix}number", number)
            .putString("${prefix}complement", complement)
            .putString("${prefix}neighborhood", neighborhood)
            .putString("${prefix}recebedores", recebedoresJson)
            .putLong("${prefix}timestamp", System.currentTimeMillis())
            .apply()
    }

    fun getScreenDraft(context: Context, personId: Long): ScreenDraft? {
        val prefs = getPrefs(context)
        val prefix = "screen_draft_${personId}_"
        val timestamp = prefs.getLong("${prefix}timestamp", 0L)
        if (timestamp == 0L || (System.currentTimeMillis() - timestamp) > MAX_AGE_MS) {
            return null
        }
        val address = prefs.getString("${prefix}address", "").orEmpty()
        val number = prefs.getString("${prefix}number", "").orEmpty()
        val complement = prefs.getString("${prefix}complement", "").orEmpty()
        val neighborhood = prefs.getString("${prefix}neighborhood", "").orEmpty()
        val recebedores = prefs.getString("${prefix}recebedores", "").orEmpty()

        if (address.isBlank() && number.isBlank() && complement.isBlank() && neighborhood.isBlank() && recebedores.isBlank()) {
            return null
        }

        return ScreenDraft(
            personId = personId,
            address = address,
            number = number,
            complement = complement,
            neighborhood = neighborhood,
            recebedoresJson = recebedores,
            timestamp = timestamp
        )
    }

    fun clearScreenDraft(context: Context, personId: Long) {
        val prefix = "screen_draft_${personId}_"
        getPrefs(context).edit()
            .remove("${prefix}address")
            .remove("${prefix}number")
            .remove("${prefix}complement")
            .remove("${prefix}neighborhood")
            .remove("${prefix}recebedores")
            .remove("${prefix}timestamp")
            .apply()
    }
}
