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
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                    @Suppress("DEPRECATION")
                    val density = ctx.resources.displayMetrics.density
                    val yOffsetPx = (75 * density).toInt()
                    toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, yOffsetPx)
                }
                toast.show()
            } catch (_: Exception) {
                try {
                    val toast = Toast.makeText(context, message, duration)
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                        @Suppress("DEPRECATION")
                        val density = context.resources.displayMetrics.density
                        val yOffsetPx = (75 * density).toInt()
                        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, yOffsetPx)
                    }
                    toast.show()
                } catch (_: Exception) {}
            }
        }
    }

    fun showLong(context: Context, message: String) {
        show(context, message, Toast.LENGTH_LONG)
    }
}
