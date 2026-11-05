package com.example.data.repository

import com.example.data.local.entity.StreetCatalog
import kotlinx.coroutines.flow.Flow

interface StreetCatalogRepository {
    fun searchStreets(query: String, city: String, limit: Int = 20): Flow<List<StreetCatalog>>
    suspend fun searchStreetsSync(query: String, city: String, limit: Int = 20): List<StreetCatalog>
    fun getStreetsByCity(city: String): Flow<List<StreetCatalog>>
    fun getAvailableCities(): Flow<List<String>>
    suspend fun getSelectedCity(): String
    suspend fun setSelectedCity(city: String)
    suspend fun addStreet(street: StreetCatalog): Long
    suspend fun deleteStreet(street: StreetCatalog)
    suspend fun ensureDefaultStreetsLoaded()
    suspend fun importStreets(city: String, uf: String, streets: List<StreetCatalog>): Int
}
