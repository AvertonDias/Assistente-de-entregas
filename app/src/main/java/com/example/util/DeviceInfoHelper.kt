package com.example.util

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import com.example.accessibility.AutomationState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Utilitário completo para extração e formatação de dados de diagnóstico do dispositivo,
 * hardware, sistema operacional, recursos e estado da aplicação para análise aprofundada de erros.
 */
object DeviceInfoHelper {

    data class DeviceSummary(
        val manufacturer: String,
        val model: String,
        val brand: String,
        val product: String,
        val device: String,
        val board: String,
        val hardware: String,
        val supportedAbis: String,
        val fingerprint: String,
        val androidVersion: String,
        val sdkInt: Int,
        val securityPatch: String,
        val buildId: String,
        val screenResolution: String,
        val screenDensityDpi: Int,
        val screenDensityScale: Float,
        val fontScale: Float,
        val screenOrientation: String,
        val totalRamFormatted: String,
        val availRamFormatted: String,
        val isLowMemory: Boolean,
        val jvmHeapUsedMb: Long,
        val jvmHeapMaxMb: Long,
        val internalStorageTotal: String,
        val internalStorageFree: String,
        val batteryLevelPct: Int,
        val batteryStatus: String,
        val isPowerSaveMode: Boolean,
        val isIgnoringBatteryOptimizations: Boolean,
        val networkType: String,
        val appVersionName: String,
        val appVersionCode: Long,
        val appPackageName: String,
        val appUptimeFormatted: String
    )

    /**
     * Coleta informações consolidadas do dispositivo e hardware de forma segura.
     */
    fun getDeviceSummary(context: Context): DeviceSummary {
        val appContext = context.applicationContext

        // Display & Tela
        val metrics = appContext.resources.displayMetrics
        val screenRes = "${metrics.widthPixels} x ${metrics.heightPixels} px"
        val orientation = when (appContext.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "Paisagem (Horizontal)"
            Configuration.ORIENTATION_PORTRAIT -> "Retrato (Vertical)"
            else -> "Indefinido"
        }

        // Memória RAM & Heap
        val actManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamStr = formatBytes(memInfo.totalMem)
        val availRamStr = formatBytes(memInfo.availMem)

        val runtime = Runtime.getRuntime()
        val jvmHeapMaxMb = runtime.maxMemory() / (1024 * 1024)
        val jvmHeapTotalMb = runtime.totalMemory() / (1024 * 1024)
        val jvmHeapFreeMb = runtime.freeMemory() / (1024 * 1024)
        val jvmHeapUsedMb = jvmHeapTotalMb - jvmHeapFreeMb

        // Armazenamento
        var totalStorageBytes = 0L
        var freeStorageBytes = 0L
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            totalStorageBytes = stat.blockCountLong * blockSize
            freeStorageBytes = stat.availableBlocksLong * blockSize
        } catch (_: Throwable) {
            try {
                val stat = StatFs(appContext.filesDir.path)
                val blockSize = stat.blockSizeLong
                totalStorageBytes = stat.blockCountLong * blockSize
                freeStorageBytes = stat.availableBlocksLong * blockSize
            } catch (_: Throwable) {}
        }

        // Bateria
        var batteryPct = -1
        var batteryStatusStr = "Desconhecido"
        try {
            val bFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = appContext.registerReceiver(null, bFilter)
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100) / scale
                }
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val chargePlug = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                batteryStatusStr = when (chargePlug) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "Carregador Tomada (AC)"
                    BatteryManager.BATTERY_PLUGGED_USB -> "Cabo USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Indução Sem Fio"
                    else -> if (isCharging) "Carregando" else "Na Bateria"
                }
            }
        } catch (_: Throwable) {}

        // Economia de Bateria & Otimizações
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode == true
        val isIgnoringBatteryOpt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) == true
        } else true

        // Conectividade
        val connMgr = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val netType = try {
            val activeNet = connMgr?.activeNetwork
            val caps = activeNet?.let { connMgr.getNetworkCapabilities(it) }
            when {
                caps == null -> "Sem Conexão"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi (Conectado)"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados Móveis (4G/5G)"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Conectado"
            }
        } catch (_: Throwable) {
            "Não disponível"
        }

        // Informações da Aplicação
        var appVer = "1.0.0"
        var appCode = 1L
        try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            appVer = pInfo.versionName ?: "1.0.0"
            appCode = PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (_: Throwable) {}

        // Uptime da Sessão
        val uptimeMs = SystemClock.elapsedRealtime()
        val hours = TimeUnit.MILLISECONDS.toHours(uptimeMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(uptimeMs) % 60
        val uptimeStr = String.format(Locale.getDefault(), "%02dh %02dm %02ds", hours, minutes, seconds)

        val secPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else "N/A"

        return DeviceSummary(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
            model = Build.MODEL,
            brand = Build.BRAND,
            product = Build.PRODUCT,
            device = Build.DEVICE,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            fingerprint = Build.FINGERPRINT,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = secPatch,
            buildId = Build.ID,
            screenResolution = screenRes,
            screenDensityDpi = metrics.densityDpi,
            screenDensityScale = metrics.density,
            fontScale = appContext.resources.configuration.fontScale,
            screenOrientation = orientation,
            totalRamFormatted = totalRamStr,
            availRamFormatted = availRamStr,
            isLowMemory = memInfo.lowMemory,
            jvmHeapUsedMb = jvmHeapUsedMb,
            jvmHeapMaxMb = jvmHeapMaxMb,
            internalStorageTotal = formatBytes(totalStorageBytes),
            internalStorageFree = formatBytes(freeStorageBytes),
            batteryLevelPct = batteryPct,
            batteryStatus = batteryStatusStr,
            isPowerSaveMode = isPowerSave,
            isIgnoringBatteryOptimizations = isIgnoringBatteryOpt,
            networkType = netType,
            appVersionName = appVer,
            appVersionCode = appCode,
            appPackageName = appContext.packageName,
            appUptimeFormatted = uptimeStr
        )
    }

    /**
     * Gera relatório em texto puro formatado e estruturado para envio por e-mail,
     * compartilhamento via mensageiro ou cópia para análise técnica detalhada.
     */
    fun generateFullDiagnosticReport(
        context: Context,
        autoState: AutomationState? = null,
        includeLogs: Boolean = true
    ): String {
        val summary = getDeviceSummary(context)
        val hasOverlay = PermissionUtils.hasOverlayPermission(context)
        val hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(context) ||
                (autoState?.isServiceActive == true)
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        return buildString {
            appendLine("==========================================================")
            appendLine("🔍 RELATÓRIO COMPLETO DE DIAGNÓSTICO & DADOS DO DISPOSITIVO")
            appendLine("   Assistente de Entregas - Análise de Erros e Desempenho")
            appendLine("==========================================================")
            appendLine("Data e Hora da Geração: ${sdf.format(Date())}")
            appendLine("Tempo de Atividade (Uptime): ${summary.appUptimeFormatted}")
            appendLine()

            appendLine("📱 [DADOS DO DISPOSITIVO & HARDWARE]")
            appendLine(" • Fabricante: ${summary.manufacturer}")
            appendLine(" • Modelo Comercial: ${summary.model}")
            appendLine(" • Marca: ${summary.brand}")
            appendLine(" • Produto / Dispositivo: ${summary.product} (${summary.device})")
            appendLine(" • Placa / Hardware: ${summary.board} / ${summary.hardware}")
            appendLine(" • Arquiteturas (ABIs): ${summary.supportedAbis}")
            appendLine(" • Fingerprint da ROM: ${summary.fingerprint}")
            appendLine()

            appendLine("🤖 [SISTEMA OPERACIONAL & VERSÃO]")
            appendLine(" • Versão do Android: ${summary.androidVersion}")
            appendLine(" • Nível de API (SDK): ${summary.sdkInt}")
            appendLine(" • Patch de Segurança: ${summary.securityPatch}")
            appendLine(" • ID da Compilação (Build): ${summary.buildId}")
            appendLine()

            appendLine("🖥️ [DISPLAY & TELA]")
            appendLine(" • Resolução: ${summary.screenResolution}")
            appendLine(" • Densidade de Tela: ${summary.screenDensityDpi} DPI (Escala: ${summary.screenDensityScale}x)")
            appendLine(" • Escala da Fonte do Usuário: ${summary.fontScale}x")
            appendLine(" • Orientação da Tela: ${summary.screenOrientation}")
            appendLine()

            appendLine("⚡ [ENERGIA & BATERIA]")
            appendLine(" • Nível de Carga: ${if (summary.batteryLevelPct >= 0) "${summary.batteryLevelPct}%" else "Não disponível"}")
            appendLine(" • Fonte de Alimentação: ${summary.batteryStatus}")
            appendLine(" • Modo Economia de Bateria: ${if (summary.isPowerSaveMode) "ATIVADO (Pode pausar serviços de fundo)" else "Desativado (Normal)"}")
            appendLine(" • Isenção de Otimização de Bateria: ${if (summary.isIgnoringBatteryOptimizations) "ISENTO (Ideal para manter a bolha ativa)" else "NÃO ISENTO (SO pode encerrar o serviço em segundo plano)"}")
            appendLine()

            appendLine("💾 [MEMÓRIA & ARMAZENAMENTO]")
            appendLine(" • Memória RAM Total: ${summary.totalRamFormatted}")
            appendLine(" • Memória RAM Disponível: ${summary.availRamFormatted}")
            appendLine(" • Alerta de Baixa Memória (Low RAM): ${if (summary.isLowMemory) "SIM (Crítico!)" else "NÃO (Estável)"}")
            appendLine(" • Heap do App (JVM): Usando ${summary.jvmHeapUsedMb} MB de até ${summary.jvmHeapMaxMb} MB")
            appendLine(" • Armazenamento Interno Total: ${summary.internalStorageTotal}")
            appendLine(" • Armazenamento Interno Livre: ${summary.internalStorageFree}")
            appendLine()

            appendLine("🌐 [REDE & CONECTIVIDADE]")
            appendLine(" • Status de Conexão: ${summary.networkType}")
            appendLine()

            appendLine("🛡️ [SUBSISTEMAS & PERMISSÕES CRÍTICAS]")
            appendLine(" • Sobreposição de Tela (Overlay): ${if (hasOverlay) "AUTORIZADO (OK)" else "PENDENTE / NEGADO"}")
            appendLine(" • Serviço de Acessibilidade no SO: ${if (hasAccessibility) "HABILITADO (OK)" else "DESABILITADO (PENDENTE)"}")
            appendLine(" • Motor de Automação Conectado: ${if (autoState?.isServiceActive == true) "ATIVO (OK)" else "AGUARDANDO CONEXÃO"}")
            appendLine(" • Permissão de Notificações: ${if (notificationsEnabled) "HABILITADO (OK)" else "DESABILITADO"}")
            appendLine()

            appendLine("📦 [DADOS DA APLICAÇÃO]")
            appendLine(" • Pacote: ${summary.appPackageName}")
            appendLine(" • Versão: v${summary.appVersionName} (Build ${summary.appVersionCode})")
            appendLine()

            if (autoState != null) {
                appendLine("🎯 [ESTADO DA JANELA ATUAL / AUTOMAÇÃO]")
                appendLine(" • Aplicativo em Foco: ${autoState.currentPackageName.ifBlank { "Nenhum pacote capturado" }}")
                appendLine(" • Classe da Janela: ${autoState.currentClassName.ifBlank { "Nenhuma classe detectada" }}")
                appendLine(" • Endereço Detectado na Tela: ${autoState.detectedAddressText.ifBlank { "Nenhum no momento" }}")
                appendLine(" • Morador Selecionado: ${autoState.selectedRecebedor?.nome ?: "Nenhum"}")
                appendLine(" • Morador Combinado: ${autoState.matchedPerson?.nome ?: "Nenhum"}")
                appendLine(" • Modos Ativos: Multi-Recebedores=${autoState.availableRecebedores.size > 1}, Assinatura=${autoState.isDrawingSignature}")
                appendLine()
            }

            appendLine("💥 [ÚLTIMO ERRO / CRASH CRÍTICO SALVO]")
            val lastCrash = CrashReporter.getLastCrashReport()
            if (!lastCrash.isNullOrBlank()) {
                appendLine(lastCrash)
            } else {
                appendLine("Nenhum registro de falha crítica (crash) encontrado no armazenamento.")
            }
            appendLine()

            if (includeLogs) {
                appendLine("📜 [HISTÓRICO RECENTE DE TELEMETRIA & OPERAÇÕES]")
                if (autoState != null && autoState.logs.isNotEmpty()) {
                    appendLine("--- Eventos de Automação & Acessibilidade (Últimos ${autoState.logs.takeLast(30).size}): ---")
                    autoState.logs.takeLast(30).forEach { log ->
                        val statusEmoji = if (log.isSuccess) "✓" else "✗"
                        appendLine("[$statusEmoji] ${log.formattedTime} [${log.tag}] ${log.message}")
                    }
                    appendLine()
                }

                val recentCrashLogs = CrashReporter.getRecentLogs()
                if (recentCrashLogs.isNotEmpty()) {
                    appendLine("--- Logs Internos de Diagnóstico (Últimos ${recentCrashLogs.takeLast(25).size}): ---")
                    recentCrashLogs.takeLast(25).forEach { log ->
                        appendLine(log)
                    }
                    appendLine()
                }
            }

            appendLine("==========================================================")
            appendLine("FIM DO RELATÓRIO DE DIAGNÓSTICO")
            appendLine("==========================================================")
        }
    }

    /**
     * Copia o texto para a área de transferência do sistema.
     */
    fun copyToClipboard(context: Context, label: String, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return if (gb >= 1.0) {
            String.format(Locale.US, "%.2f GB", gb)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
