package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.DeliveryApp

class DeliveryAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val app = application as? DeliveryApp
            if (app != null) {
                AccessibilityAutomationEngine.init(app.personRepository, app.settingsRepository)
            }
            AccessibilityAutomationEngine.registerService(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: ""
        val cls = event.className?.toString() ?: ""

        // Ignorar eventos do próprio teclado se necessário
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            val root = rootInActiveWindow
            AccessibilityAutomationEngine.onWindowOrContentChanged(pkg, cls, root)
        }
    }

    override fun onInterrupt() {
        // Chamado quando o serviço é interrompido pelo sistema
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AccessibilityAutomationEngine.unregisterService()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityAutomationEngine.unregisterService()
        super.onDestroy()
    }
}
