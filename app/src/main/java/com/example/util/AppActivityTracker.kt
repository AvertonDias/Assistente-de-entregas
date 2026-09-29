package com.example.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.example.DeliveryApp
import com.example.data.repository.ActivityReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AppActivityTracker {

    private var repository: ActivityReportRepository? = null
    private var scope: CoroutineScope? = null

    private var activeActivitiesCount = 0
    private var foregroundStartTime: Long = 0L
    private var periodicTimeFlushJob: Job? = null

    // Rastreamento de tempo de serviço flutuante ativo
    private var isFloatingActive = false
    private var floatingStartTime: Long = 0L
    private var periodicFloatingFlushJob: Job? = null

    fun init(app: DeliveryApp, activityReportRepository: ActivityReportRepository) {
        this.repository = activityReportRepository
        this.scope = app.applicationScope

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                if (activeActivitiesCount == 0) {
                    onAppEnteredForeground()
                }
                activeActivitiesCount++
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                activeActivitiesCount = maxOf(0, activeActivitiesCount - 1)
                if (activeActivitiesCount == 0) {
                    onAppEnteredBackground()
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun onAppEnteredForeground() {
        foregroundStartTime = SystemClock.elapsedRealtime()
        logAction(
            actionType = "APP_OPEN",
            title = "Aplicativo Aberto",
            details = "Sessão iniciada na interface do usuário.",
            category = "Sessão"
        )
        startPeriodicFlush()
    }

    private fun onAppEnteredBackground() {
        stopPeriodicFlush()
        val now = SystemClock.elapsedRealtime()
        if (foregroundStartTime > 0L) {
            val elapsed = now - foregroundStartTime
            if (elapsed > 1000L) {
                repository?.addActiveTime(elapsed)
                val durationSec = elapsed / 1000
                val durationFormatted = if (durationSec >= 60) "${durationSec / 60} min ${durationSec % 60}s" else "${durationSec}s"
                logAction(
                    actionType = "APP_BACKGROUND",
                    title = "App em Segundo Plano",
                    details = "Duração desta sessão: $durationFormatted.",
                    category = "Sessão",
                    durationMs = elapsed
                )
            }
            foregroundStartTime = 0L
        }
    }

    private fun startPeriodicFlush() {
        periodicTimeFlushJob?.cancel()
        periodicTimeFlushJob = scope?.launch(Dispatchers.Default) {
            while (isActive) {
                delay(30_000) // Salva o tempo a cada 30 segundos enquanto ativo para garantir que não perde se o app fechar
                if (foregroundStartTime > 0L) {
                    val now = SystemClock.elapsedRealtime()
                    val chunk = now - foregroundStartTime
                    if (chunk > 0L) {
                        repository?.addActiveTime(chunk)
                        foregroundStartTime = now // reseta a referência para o próximo chunk
                    }
                }
            }
        }
    }

    private fun stopPeriodicFlush() {
        periodicTimeFlushJob?.cancel()
        periodicTimeFlushJob = null
    }

    private fun startPeriodicFloatingFlush() {
        periodicFloatingFlushJob?.cancel()
        periodicFloatingFlushJob = scope?.launch(Dispatchers.Default) {
            while (isActive) {
                delay(30_000)
                if (floatingStartTime > 0L) {
                    val now = SystemClock.elapsedRealtime()
                    val chunk = now - floatingStartTime
                    if (chunk > 0L) {
                        repository?.addActiveTime(chunk)
                        floatingStartTime = now
                    }
                }
            }
        }
    }

    private fun stopPeriodicFloatingFlush() {
        periodicFloatingFlushJob?.cancel()
        periodicFloatingFlushJob = null
    }

    fun onFloatingAssistantStateChanged(isActive: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (isActive && !isFloatingActive) {
            isFloatingActive = true
            floatingStartTime = now
            logAction(
                actionType = "BUBBLE_ENABLED",
                title = "Assistente Flutuante Ativado",
                details = "Balão de sobreposição pronto para leitura na tela.",
                category = "Automação"
            )
            startPeriodicFloatingFlush()
        } else if (!isActive && isFloatingActive) {
            stopPeriodicFloatingFlush()
            isFloatingActive = false
            val elapsed = now - floatingStartTime
            if (elapsed > 1000L) {
                repository?.addActiveTime(elapsed)
                val durationSec = elapsed / 1000
                val durationFormatted = if (durationSec >= 60) "${durationSec / 60} min ${durationSec % 60}s" else "${durationSec}s"
                logAction(
                    actionType = "BUBBLE_DISABLED",
                    title = "Assistente Flutuante Pausado",
                    details = "Tempo em operação: $durationFormatted.",
                    category = "Automação",
                    durationMs = elapsed
                )
            }
            floatingStartTime = 0L
        }
    }

    fun logAction(
        actionType: String,
        title: String,
        details: String = "",
        category: String = "Geral",
        durationMs: Long = 0L,
        incrementDelivery: Boolean = false,
        incrementAddress: Boolean = false,
        incrementSignature: Boolean = false,
        incrementPerson: Boolean = false,
        incrementSearch: Boolean = false
    ) {
        repository?.recordAction(
            actionType = actionType,
            title = title,
            details = details,
            category = category,
            durationMs = durationMs,
            incrementDelivery = incrementDelivery,
            incrementAddress = incrementAddress,
            incrementSignature = incrementSignature,
            incrementPerson = incrementPerson,
            incrementSearch = incrementSearch
        )
    }
}
