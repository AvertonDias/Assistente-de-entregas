package com.example.data.repository

import com.example.data.local.entity.Delivery
import kotlinx.coroutines.flow.Flow

interface DeliveryRepository {
    fun getAllDeliveries(): Flow<List<Delivery>>
    suspend fun getDeliveryById(id: Long): Delivery?
    fun getDeliveriesByPersonId(pessoaId: Long): Flow<List<Delivery>>
    suspend fun insertDelivery(delivery: Delivery): Long
    suspend fun insertAll(deliveries: List<Delivery>)
    suspend fun updateDelivery(delivery: Delivery)
    suspend fun deleteDelivery(delivery: Delivery)
    suspend fun deleteDeliveryById(id: Long)
    suspend fun clearAllDeliveries()
    fun getDeliveryStats(): Flow<DeliveryStats>
}

data class DeliveryStats(
    val totalDeliveries: Int = 0,
    val todayDeliveries: Int = 0,
    val autoFilledCount: Int = 0,
    val signaturesCollectedCount: Int = 0
)
