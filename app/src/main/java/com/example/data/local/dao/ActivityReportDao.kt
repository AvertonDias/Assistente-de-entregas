package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AppActionLog
import com.example.data.local.entity.DailyActivitySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityReportDao {
    @Query("SELECT * FROM resumo_diario_atividades WHERE dateKey = :dateKey LIMIT 1")
    fun getDailySummaryFlow(dateKey: String): Flow<DailyActivitySummary?>

    @Query("SELECT * FROM resumo_diario_atividades WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getDailySummary(dateKey: String): DailyActivitySummary?

    @Query("SELECT * FROM resumo_diario_atividades ORDER BY dateKey DESC")
    fun getAllSummariesFlow(): Flow<List<DailyActivitySummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSummary(summary: DailyActivitySummary)

    @Query("SELECT * FROM historico_acoes_app WHERE dateKey = :dateKey ORDER BY timestamp DESC")
    fun getActionsByDateFlow(dateKey: String): Flow<List<AppActionLog>>

    @Query("SELECT * FROM historico_acoes_app WHERE dateKey = :dateKey ORDER BY timestamp ASC")
    suspend fun getActionsByDate(dateKey: String): List<AppActionLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: AppActionLog): Long

    @Query("SELECT COUNT(*) FROM historico_acoes_app WHERE dateKey = :dateKey")
    fun countActionsByDateFlow(dateKey: String): Flow<Int>

    @Query("DELETE FROM historico_acoes_app WHERE dateKey = :dateKey")
    suspend fun deleteActionsByDate(dateKey: String)

    @Query("DELETE FROM resumo_diario_atividades WHERE dateKey = :dateKey")
    suspend fun deleteSummaryByDate(dateKey: String)

    @Query("SELECT DISTINCT dateKey FROM resumo_diario_atividades ORDER BY dateKey DESC")
    fun getAvailableDatesFlow(): Flow<List<String>>
}
