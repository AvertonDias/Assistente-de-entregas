package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.StreetCatalog
import kotlinx.coroutines.flow.Flow

@Dao
interface StreetCatalogDao {

    @Query("""
        SELECT * FROM catalogo_ruas 
        WHERE (cidade LIKE '%' || :city || '%' OR :city = '')
        AND (nome LIKE '%' || :query || '%' OR tipo || ' ' || nome LIKE '%' || :query || '%' OR bairro LIKE '%' || :query || '%')
        ORDER BY nome ASC 
        LIMIT :limit
    """)
    fun searchStreets(query: String, city: String, limit: Int = 20): Flow<List<StreetCatalog>>

    @Query("""
        SELECT * FROM catalogo_ruas 
        WHERE (cidade LIKE '%' || :city || '%' OR :city = '')
        AND (nome LIKE '%' || :query || '%' OR tipo || ' ' || nome LIKE '%' || :query || '%')
        ORDER BY nome ASC 
        LIMIT :limit
    """)
    suspend fun searchStreetsSync(query: String, city: String, limit: Int = 20): List<StreetCatalog>

    @Query("SELECT * FROM catalogo_ruas WHERE cidade = :city ORDER BY nome ASC")
    fun getStreetsByCity(city: String): Flow<List<StreetCatalog>>

    @Query("SELECT * FROM catalogo_ruas WHERE cidade = :city ORDER BY nome ASC")
    suspend fun getAllStreetsByCitySync(city: String): List<StreetCatalog>

    @Query("SELECT * FROM catalogo_ruas ORDER BY nome ASC")
    suspend fun getAllStreetsSync(): List<StreetCatalog>

    @Query("SELECT DISTINCT cidade FROM catalogo_ruas ORDER BY cidade ASC")
    fun getAvailableCities(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM catalogo_ruas WHERE cidade = :city")
    suspend fun countStreetsByCity(city: String): Int

    @Query("SELECT COUNT(*) FROM catalogo_ruas")
    suspend fun countAllStreets(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(street: StreetCatalog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(streets: List<StreetCatalog>)

    @Update
    suspend fun update(street: StreetCatalog)

    @Delete
    suspend fun delete(street: StreetCatalog)

    @Query("DELETE FROM catalogo_ruas WHERE cidade = :city")
    suspend fun deleteByCity(city: String)
}
