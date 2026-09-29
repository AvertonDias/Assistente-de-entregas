package com.example.data.repository

import com.example.data.local.dao.ActivityReportDao
import com.example.data.local.entity.AppActionLog
import com.example.data.local.entity.DailyActivitySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface ActivityReportRepository {
    fun getTodayDateKey(): String
    fun getDailySummary(dateKey: String): Flow<DailyActivitySummary?>
    fun getActionsByDate(dateKey: String): Flow<List<AppActionLog>>
    fun getAvailableDates(): Flow<List<String>>

    fun recordAction(
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
    )

    fun addActiveTime(durationMs: Long)
    suspend fun clearHistoryForDate(dateKey: String)
    suspend fun buildDailyReportExportText(dateKey: String): String
}

class ActivityReportRepositoryImpl(
    private val dao: ActivityReportDao,
    private val scope: CoroutineScope
) : ActivityReportRepository {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun getTodayDateKey(): String {
        return dateFormat.format(Date())
    }

    override fun getDailySummary(dateKey: String): Flow<DailyActivitySummary?> {
        return dao.getDailySummaryFlow(dateKey)
    }

    override fun getActionsByDate(dateKey: String): Flow<List<AppActionLog>> {
        return dao.getActionsByDateFlow(dateKey)
    }

    override fun getAvailableDates(): Flow<List<String>> {
        return dao.getAvailableDatesFlow()
    }

    override fun recordAction(
        actionType: String,
        title: String,
        details: String,
        category: String,
        durationMs: Long,
        incrementDelivery: Boolean,
        incrementAddress: Boolean,
        incrementSignature: Boolean,
        incrementPerson: Boolean,
        incrementSearch: Boolean
    ) {
        val now = System.currentTimeMillis()
        val dateKey = getTodayDateKey()

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Grava no histórico de ações
                val log = AppActionLog(
                    dateKey = dateKey,
                    timestamp = now,
                    actionType = actionType,
                    title = title,
                    details = details,
                    category = category,
                    durationMs = durationMs
                )
                dao.insertAction(log)

                // 2. Atualiza ou cria o sumário do dia
                val currentSummary = dao.getDailySummary(dateKey)
                val updatedSummary = if (currentSummary != null) {
                    currentSummary.copy(
                        lastActiveTime = now,
                        totalDeliveries = currentSummary.totalDeliveries + (if (incrementDelivery) 1 else 0),
                        totalAddressesDetected = currentSummary.totalAddressesDetected + (if (incrementAddress) 1 else 0),
                        totalSignaturesApplied = currentSummary.totalSignaturesApplied + (if (incrementSignature) 1 else 0),
                        totalPeopleCreatedOrEdited = currentSummary.totalPeopleCreatedOrEdited + (if (incrementPerson) 1 else 0),
                        totalSearches = currentSummary.totalSearches + (if (incrementSearch) 1 else 0),
                        totalActionsCount = currentSummary.totalActionsCount + 1
                    )
                } else {
                    DailyActivitySummary(
                        dateKey = dateKey,
                        totalActiveTimeMs = 0L,
                        firstOpenTime = now,
                        lastActiveTime = now,
                        totalDeliveries = if (incrementDelivery) 1 else 0,
                        totalAddressesDetected = if (incrementAddress) 1 else 0,
                        totalSignaturesApplied = if (incrementSignature) 1 else 0,
                        totalPeopleCreatedOrEdited = if (incrementPerson) 1 else 0,
                        totalSearches = if (incrementSearch) 1 else 0,
                        totalActionsCount = 1
                    )
                }
                dao.insertOrUpdateSummary(updatedSummary)
            } catch (e: Throwable) {
                android.util.Log.e("ActivityReport", "Erro ao gravar acao: ${e.message}", e)
            }
        }
    }

    override fun addActiveTime(durationMs: Long) {
        if (durationMs <= 0) return
        val now = System.currentTimeMillis()
        val dateKey = getTodayDateKey()

        scope.launch(Dispatchers.IO) {
            try {
                val current = dao.getDailySummary(dateKey)
                if (current != null) {
                    val updated = current.copy(
                        totalActiveTimeMs = current.totalActiveTimeMs + durationMs,
                        lastActiveTime = now
                    )
                    dao.insertOrUpdateSummary(updated)
                } else {
                    val newSummary = DailyActivitySummary(
                        dateKey = dateKey,
                        totalActiveTimeMs = durationMs,
                        firstOpenTime = now,
                        lastActiveTime = now
                    )
                    dao.insertOrUpdateSummary(newSummary)
                }
            } catch (e: Throwable) {
                android.util.Log.e("ActivityReport", "Erro ao somar tempo: ${e.message}", e)
            }
        }
    }

    override suspend fun clearHistoryForDate(dateKey: String) {
        withContext(Dispatchers.IO) {
            dao.deleteActionsByDate(dateKey)
            dao.deleteSummaryByDate(dateKey)
        }
    }

    override suspend fun buildDailyReportExportText(dateKey: String): String = withContext(Dispatchers.IO) {
        val summary = dao.getDailySummary(dateKey)
        val actions = dao.getActionsByDate(dateKey)

        val parsedDate = try {
            dateFormat.parse(dateKey)
        } catch (_: Throwable) { null }
        val dateDisplay = if (parsedDate != null) displayDateFormat.format(parsedDate) else dateKey

        val activeTimeFormatted = formatDuration(summary?.totalActiveTimeMs ?: 0L)
        val firstOpenStr = if (summary != null) timeFormat.format(Date(summary.firstOpenTime)) else "--:--"
        val lastActiveStr = if (summary != null) timeFormat.format(Date(summary.lastActiveTime)) else "--:--"

        buildString {
            appendLine("════════════════════════════════════════")
            appendLine("📊 RELATÓRIO DIÁRIO DE ATIVIDADES & AUDITORIA")
            appendLine("📅 Data: $dateDisplay")
            appendLine("════════════════════════════════════════\n")

            appendLine("⏱️ TEMPO E SESSÕES:")
            appendLine(" • Tempo Total em Atividade: $activeTimeFormatted")
            appendLine(" • Primeira Abertura: $firstOpenStr")
            appendLine(" • Última Atividade: $lastActiveStr")
            appendLine()

            appendLine("📈 RESUMO DAS OPERAÇÕES:")
            appendLine(" • Total de Ações Registradas: ${summary?.totalActionsCount ?: actions.size}")
            appendLine(" • Entregas / Preenchimentos: ${summary?.totalDeliveries ?: 0}")
            appendLine(" • Endereços Detectados na Tela: ${summary?.totalAddressesDetected ?: 0}")
            appendLine(" • Assinaturas Aplicadas: ${summary?.totalSignaturesApplied ?: 0}")
            appendLine(" • Destinatários Cadastrados/Editados: ${summary?.totalPeopleCreatedOrEdited ?: 0}")
            appendLine(" • Buscas Efetuadas: ${summary?.totalSearches ?: 0}")
            appendLine()

            appendLine("════════════════════════════════════════")
            appendLine("📝 LINHA DO TEMPO DETALHADA DAS AÇÕES:")
            appendLine("════════════════════════════════════════")

            if (actions.isEmpty()) {
                appendLine("Nenhuma ação registrada nesta data.")
            } else {
                actions.forEachIndexed { index, action ->
                    val timeStr = timeFormat.format(Date(action.timestamp))
                    val durationStr = if (action.durationMs > 0) " (Duração: ${formatDuration(action.durationMs)})" else ""
                    appendLine("[#${index + 1}] $timeStr$durationStr - [${action.category}] ${action.title}")
                    if (action.details.isNotBlank()) {
                        appendLine("     ↳ ${action.details.replace("\n", "\n     ↳ ")}")
                    }
                }
            }
            appendLine("\n════════════════════════════════════════")
            appendLine("Assistente de Entregas • Relatório Completo")
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}min"
            minutes > 0 -> "${minutes}min ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
