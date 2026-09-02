package com.example.data.repository

import com.example.data.local.entity.Person
import kotlinx.coroutines.flow.Flow

interface PersonRepository {
    fun getAllPersons(): Flow<List<Person>>
    fun getPersonById(id: Long): Flow<Person?>
    suspend fun getPersonByIdDirect(id: Long): Person?
    fun searchPersons(query: String): Flow<List<Person>>
    suspend fun findPersonsByAddress(rawAddress: String): List<Person>
    suspend fun insertPerson(person: Person): Long
    suspend fun insertAll(persons: List<Person>)
    suspend fun updatePerson(person: Person)
    suspend fun deletePerson(person: Person)
    suspend fun deletePersonById(id: Long)
    suspend fun countPersons(): Int
    suspend fun cleanupInactiveReceiversOlderThan5Years(): Int
    suspend fun markPersonUsed(id: Long)
}
