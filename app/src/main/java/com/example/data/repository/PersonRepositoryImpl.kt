package com.example.data.repository

import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.Person
import com.example.util.AddressNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PersonRepositoryImpl(
    private val personDao: PersonDao
) : PersonRepository {

    override fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons()

    override fun getPersonById(id: Long): Flow<Person?> = personDao.getPersonByIdFlow(id)

    override suspend fun getPersonByIdDirect(id: Long): Person? = personDao.getPersonById(id)

    override fun searchPersons(query: String): Flow<List<Person>> {
        val trimmed = query.trim()
        return if (trimmed.isBlank()) {
            personDao.getAllPersons()
        } else {
            personDao.searchPersons(trimmed)
        }
    }

    override suspend fun findPersonsByAddress(rawAddress: String): List<Person> {
        if (rawAddress.isBlank()) return emptyList()

        val all = personDao.getAllPersons().first()
        if (all.isEmpty()) return emptyList()

        // Match preciso de endereço e número
        val exactMatches = all.filter { p ->
            AddressNormalizer.matchesPrecise(rawAddress, p.endereco, p.numero)
        }
        if (exactMatches.isNotEmpty()) {
            return exactMatches
        }

        // Fallback apenas se não encontrou com endereço e número isolados
        return all.filter { p ->
            AddressNormalizer.matchesPrecise(rawAddress, "${p.endereco}, ${p.numero} ${p.bairro} ${p.cidade}", p.numero)
        }
    }

    override suspend fun insertPerson(person: Person): Long = personDao.insertPerson(person)

    override suspend fun insertAll(persons: List<Person>) = personDao.insertAll(persons)

    override suspend fun updatePerson(person: Person) = personDao.updatePerson(person)

    override suspend fun deletePerson(person: Person) = personDao.deletePerson(person)

    override suspend fun deletePersonById(id: Long) = personDao.deletePersonById(id)

    override suspend fun countPersons(): Int = personDao.countPersons()

    override suspend fun markPersonUsed(id: Long) {
        val person = personDao.getPersonById(id) ?: return
        personDao.updatePerson(person.copy(dataAtualizacao = System.currentTimeMillis()))
    }

    override suspend fun cleanupInactiveReceiversOlderThan5Years(): Int {
        val fiveYearsInMillis = 5L * 365L * 24L * 60L * 60L * 1000L // 5 anos
        val cutoff = System.currentTimeMillis() - fiveYearsInMillis
        val expiredList = personDao.getPersonsOlderThan(cutoff)
        
        var countCleared = 0
        for (person in expiredList) {
            // O endereço É MANTIDO (endereco, numero, complemento, bairro, cidade, uf)
            // Os recebedores secundários (coRecebedoresJson) TAMBÉM SÃO MANTIDOS
            // Apaga APENAS o recebedor principal específico (nome, documento e assinatura)
            if (person.nome.isNotBlank() || person.documento.isNotBlank() || person.assinatura.isNotBlank()) {
                val cleanedPerson = person.copy(
                    nome = "",
                    documento = "",
                    assinatura = "",
                    observacao = if (person.observacao.isNotBlank()) "${person.observacao} (Recebedor principal limpo por expiração de 5 anos)" else "Recebedor principal limpo por expiração de 5 anos",
                    dataAtualizacao = System.currentTimeMillis()
                )
                personDao.updatePerson(cleanedPerson)
                countCleared++
            }
        }
        return countCleared
    }
}
