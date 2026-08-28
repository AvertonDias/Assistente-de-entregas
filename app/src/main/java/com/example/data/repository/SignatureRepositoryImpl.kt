package com.example.data.repository

import com.example.data.local.dao.DeliveryDao
import com.example.data.local.dao.PersonDao
import com.example.data.model.SignatureData

class SignatureRepositoryImpl(
    private val personDao: PersonDao,
    private val deliveryDao: DeliveryDao
) : SignatureRepository {

    override suspend fun saveSignature(
        personId: Long?,
        deliveryId: Long?,
        signature: SignatureData
    ): String {
        val signatureJson = signature.toJson()

        // Cada assinatura é associada a uma entrega específica
        if (deliveryId != null && deliveryId > 0) {
            val delivery = deliveryDao.getDeliveryById(deliveryId)
            if (delivery != null) {
                deliveryDao.updateDelivery(delivery.copy(assinatura = signatureJson, assinaturaAplicada = true))
            }
        }

        // Opcionalmente associa à pessoa se solicitado
        if (personId != null && personId > 0) {
            val person = personDao.getPersonById(personId)
            if (person != null) {
                personDao.updatePerson(person.copy(assinatura = signatureJson, dataAtualizacao = System.currentTimeMillis()))
            }
        }

        return signatureJson
    }

    override suspend fun getSignatureForDelivery(deliveryId: Long): SignatureData? {
        val delivery = deliveryDao.getDeliveryById(deliveryId) ?: return null
        return if (delivery.assinatura.isNotBlank()) {
            SignatureData.fromJson(delivery.assinatura)
        } else {
            null
        }
    }

    override suspend fun clearSignatureForPerson(personId: Long) {
        val person = personDao.getPersonById(personId) ?: return
        personDao.updatePerson(person.copy(assinatura = "", dataAtualizacao = System.currentTimeMillis()))
    }
}
