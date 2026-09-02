package com.example.ui.components

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
fun DialogBlurEffect(radiusDp: Int = 25, dimAmount: Float = 0.55f) {
    val view = LocalView.current
    val context = LocalContext.current

    LaunchedEffect(view) {
        var current: Any? = view
        var dialogWindow: Window? = null
        while (current != null) {
            if (current is DialogWindowProvider) {
                dialogWindow = current.window
                break
            }
            if (current is View) {
                val parent = current.parent
                if (parent is DialogWindowProvider) {
                    dialogWindow = parent.window
                    break
                }
                current = parent
            } else {
                break
            }
        }

        dialogWindow?.let { w ->
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(dimAmount)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val radiusPx = (radiusDp * context.resources.displayMetrics.density).toInt()
                w.attributes = w.attributes.apply {
                    blurBehindRadius = radiusPx
                }
            }
        }
    }
}
