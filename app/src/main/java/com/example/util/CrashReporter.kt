package com.example.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Utilitário centralizado de diagnóstico e Crash Reporting seguro.
 * Registra erros internamente sem causar falhas de inicialização do app.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val MAX_LOGS = 100
    private val inMemoryLogs = ConcurrentLinkedQueue<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var appContext: Context? = null

    fun init(context: Context) {
        try {
            appContext = context.applicationContext
            log("CrashReporter inicializado com segurança.")
            
            // Configura captura global de exceções não tratadas
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    recordException(throwable, "Uncaught in ${thread.name}")
                    saveCrashToFile(throwable)
                } catch (_: Throwable) {}
                defaultHandler?.uncaughtException(thread, throwable)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Falha ao inicializar CrashReporter: ${e.message}")
        }
    }

    /**
     * Registra uma mensagem nos logs do sistema e do aplicativo.
     */
    fun log(message: String) {
        try {
            val timestamp = dateFormat.format(Date())
            val formatted = "[$timestamp] $message"
            Log.d(TAG, formatted)
            
            inMemoryLogs.add(formatted)
            while (inMemoryLogs.size > MAX_LOGS) {
                inMemoryLogs.poll()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Registra um erro capturado.
     */
    fun recordException(throwable: Throwable, reason: String? = null) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stack = sw.toString().take(500)
            
            val msg = if (reason != null) {
                "⚠️ ERRO [$reason]: ${throwable.localizedMessage}\n$stack"
            } else {
                "⚠️ ERRO: ${throwable.localizedMessage}\n$stack"
            }
            log(msg)
        } catch (_: Throwable) {}
    }

    private fun saveCrashToFile(throwable: Throwable) {
        try {
            val context = appContext ?: return
            val file = File(context.filesDir, "last_crash_report.txt")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val text = "DATA: ${Date()}\nERRO: ${throwable.message}\nSTACKTRACE:\n$sw"
            file.writeText(text)
        } catch (_: Throwable) {}
    }

    fun getLastCrashReport(): String? {
        return try {
            val context = appContext ?: return null
            val file = File(context.filesDir, "last_crash_report.txt")
            if (file.exists()) file.readText() else null
        } catch (_: Throwable) {
            null
        }
    }

    fun getRecentLogs(): List<String> {
        return inMemoryLogs.toList()
    }
}
