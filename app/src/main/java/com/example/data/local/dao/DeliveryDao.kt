package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.Delivery
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryDao {

    @Query("SELECT * FROM entregas ORDER BY dataHora DESC")
    fun getAllDeliveries(): Flow<List<Delivery>>

    @Query("SELECT * FROM entregas WHERE id = :id LIMIT 1")
    suspend fun getDeliveryById(id: Long): Delivery?

    @Query("SELECT * FROM entregas WHERE pessoaId = :pessoaId ORDER BY dataHora DESC")
    fun getDeliveriesByPersonId(pessoaId: Long): Flow<List<Delivery>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDelivery(delivery: Delivery): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deliveries: List<Delivery>)

    @Update
    suspend fun updateDelivery(delivery: Delivery)

    @Delete
    suspend fun deleteDelivery(delivery: Delivery)

    @Query("DELETE FROM entregas WHERE id = :id")
    suspend fun deleteDeliveryById(id: Long)

    @Query("DELETE FROM entregas")
    suspend fun clearAllDeliveries()

    @Query("SELECT COUNT(*) FROM entregas")
    fun countDeliveriesFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM entregas WHERE dataHora >= :startOfDayTimestamp")
    fun countTodayDeliveriesFlow(startOfDayTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM entregas WHERE nomePreenchido = 1 OR documentoPreenchido = 1")
    fun countAutoFilledFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM entregas WHERE assinatura != ''")
    fun countSignaturesCollectedFlow(): Flow<Int>
}
