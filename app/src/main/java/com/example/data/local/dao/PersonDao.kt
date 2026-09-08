package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.Person
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM pessoas ORDER BY endereco ASC, numero ASC, nome ASC")
    fun getAllPersons(): Flow<List<Person>>

    @Query("SELECT * FROM pessoas ORDER BY endereco ASC, numero ASC, nome ASC")
    suspend fun getAllPersonsDirect(): List<Person>

    @Query("SELECT * FROM pessoas ORDER BY endereco ASC, numero ASC, nome ASC LIMIT :limit")
    suspend fun getPersonsLimit(limit: Int): List<Person>

    @Query("""
        SELECT * FROM pessoas 
        WHERE (:number != '' AND (numero = :number OR numero LIKE :number || '%' OR endereco LIKE '%' || :number || '%' OR (endereco || ' ' || numero) LIKE '%' || :number || '%'))
           OR (:keyword != '' AND (endereco LIKE '%' || :keyword || '%' OR nome LIKE '%' || :keyword || '%'))
        ORDER BY endereco ASC, numero ASC
        LIMIT 200
    """)
    suspend fun findCandidatePersons(number: String, keyword: String): List<Person>

    @Query("SELECT * FROM pessoas WHERE id = :id LIMIT 1")
    suspend fun getPersonById(id: Long): Person?

    @Query("SELECT * FROM pessoas WHERE id = :id LIMIT 1")
    fun getPersonByIdFlow(id: Long): Flow<Person?>

    @Query("""
        SELECT * FROM pessoas 
        WHERE nome LIKE '%' || :query || '%' 
           OR endereco LIKE '%' || :query || '%' 
           OR numero LIKE '%' || :query || '%'
           OR (endereco || ' ' || numero) LIKE '%' || :query || '%'
           OR (endereco || ', ' || numero) LIKE '%' || :query || '%'
           OR coRecebedoresJson LIKE '%' || :query || '%'
        ORDER BY endereco ASC, numero ASC, nome ASC
    """)
    fun searchPersons(query: String): Flow<List<Person>>

    @Query("""
        SELECT * FROM pessoas 
        WHERE (:query = '' OR 
               nome LIKE '%' || :query || '%' OR 
               endereco LIKE '%' || :query || '%' OR 
               numero LIKE '%' || :query || '%' OR 
               (endereco || ' ' || numero) LIKE '%' || :query || '%' OR 
               (endereco || ', ' || numero) LIKE '%' || :query || '%' OR 
               coRecebedoresJson LIKE '%' || :query || '%')
        ORDER BY endereco ASC, numero ASC, nome ASC
        LIMIT :limit
    """)
    fun searchPersonsFast(query: String, limit: Int = 200): Flow<List<Person>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(persons: List<Person>)

    @Update
    suspend fun updatePerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    @Query("DELETE FROM pessoas WHERE id = :id")
    suspend fun deletePersonById(id: Long)

    @Query("SELECT COUNT(*) FROM pessoas")
    suspend fun countPersons(): Int

    @Query("SELECT * FROM pessoas WHERE dataAtualizacao < :cutoffTimestamp")
    suspend fun getPersonsOlderThan(cutoffTimestamp: Long): List<Person>
}
