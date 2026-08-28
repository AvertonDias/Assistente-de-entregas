package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object ClipboardHelper {

    fun copyToClipboard(context: Context, label: String, text: String, showToast: Boolean = true): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && text.isNotBlank()) {
                val clip = ClipData.newPlainText(label, text)
                clipboard.setPrimaryClip(clip)
                if (showToast) {
                    Toast.makeText(context, "$label copiado.", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
