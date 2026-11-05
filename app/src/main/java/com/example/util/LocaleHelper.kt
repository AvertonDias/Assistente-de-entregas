package com.example.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    val PORTUGUESE_BRAZIL = Locale("pt", "BR")

    fun applyPortugueseLocale(context: Context): Context {
        Locale.setDefault(PORTUGUESE_BRAZIL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                localeManager?.applicationLocales = LocaleList(PORTUGUESE_BRAZIL)
            } catch (_: Throwable) {}
        }

        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(PORTUGUESE_BRAZIL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(PORTUGUESE_BRAZIL)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        }
        return context.createConfigurationContext(config)
    }
}
