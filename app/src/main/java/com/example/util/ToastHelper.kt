package com.example.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast

object ToastHelper {

    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        if (message.isBlank()) return
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val ctx = context.applicationContext ?: context
                val toast = Toast.makeText(ctx, message, duration)
                val density = ctx.resources.displayMetrics.density
                val yOffsetPx = (75 * density).toInt()
                // Garante que a notificação apareça sempre na parte inferior da tela
                toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, yOffsetPx)
                toast.show()
            } catch (_: Exception) {
                try {
                    val toast = Toast.makeText(context, message, duration)
                    val density = context.resources.displayMetrics.density
                    val yOffsetPx = (75 * density).toInt()
                    toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, yOffsetPx)
                    toast.show()
                } catch (_: Exception) {}
            }
        }
    }

    fun showLong(context: Context, message: String) {
        show(context, message, Toast.LENGTH_LONG)
    }
}
