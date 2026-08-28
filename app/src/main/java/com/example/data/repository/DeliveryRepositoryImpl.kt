package com.example.data.repository

import com.example.data.local.dao.DeliveryDao
import com.example.data.local.entity.Delivery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar

class DeliveryRepositoryImpl(
    private val deliveryDao: DeliveryDao
) : DeliveryRepository {

    override fun getAllDeliveries(): Flow<List<Delivery>> = deliveryDao.getAllDeliveries()

    override suspend fun getDeliveryById(id: Long): Delivery? = deliveryDao.getDeliveryById(id)

    override fun getDeliveriesByPersonId(pessoaId: Long): Flow<List<Delivery>> =
        deliveryDao.getDeliveriesByPersonId(pessoaId)

    override suspend fun insertDelivery(delivery: Delivery): Long = deliveryDao.insertDelivery(delivery)

    override suspend fun insertAll(deliveries: List<Delivery>) = deliveryDao.insertAll(deliveries)

    override suspend fun updateDelivery(delivery: Delivery) = deliveryDao.updateDelivery(delivery)

    override suspend fun deleteDelivery(delivery: Delivery) = deliveryDao.deleteDelivery(delivery)

    override suspend fun deleteDeliveryById(id: Long) = deliveryDao.deleteDeliveryById(id)

    override suspend fun clearAllDeliveries() = deliveryDao.clearAllDeliveries()

    override fun getDeliveryStats(): Flow<DeliveryStats> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis

        return combine(
            deliveryDao.countDeliveriesFlow(),
            deliveryDao.countTodayDeliveriesFlow(startOfDay),
            deliveryDao.countAutoFilledFlow(),
            deliveryDao.countSignaturesCollectedFlow()
        ) { total, today, autoFilled, signatures ->
            DeliveryStats(
                totalDeliveries = total,
                todayDeliveries = today,
                autoFilledCount = autoFilled,
                signaturesCollectedCount = signatures
            )
        }
    }
}
