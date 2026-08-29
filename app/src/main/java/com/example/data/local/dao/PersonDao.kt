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

    @Query("SELECT * FROM pessoas WHERE id = :id LIMIT 1")
    suspend fun getPersonById(id: Long): Person?

    @Query("SELECT * FROM pessoas WHERE id = :id LIMIT 1")
    fun getPersonByIdFlow(id: Long): Flow<Person?>

    @Query("""
        SELECT * FROM pessoas 
        WHERE nome LIKE '%' || :query || '%' 
           OR documento LIKE '%' || :query || '%' 
           OR endereco LIKE '%' || :query || '%' 
           OR bairro LIKE '%' || :query || '%' 
           OR cidade LIKE '%' || :query || '%' 
           OR numero LIKE '%' || :query || '%'
        ORDER BY endereco ASC, numero ASC, nome ASC
    """)
    fun searchPersons(query: String): Flow<List<Person>>

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
}
