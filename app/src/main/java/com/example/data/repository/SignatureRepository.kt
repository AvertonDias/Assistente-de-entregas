package com.example.data.repository

import com.example.data.model.SignatureData
import kotlinx.coroutines.flow.Flow

interface SignatureRepository {
    suspend fun saveSignature(personId: Long?, deliveryId: Long?, signature: SignatureData): String
    suspend fun getSignatureForDelivery(deliveryId: Long): SignatureData?
    suspend fun clearSignatureForPerson(personId: Long)
}
