package com.example.data.repository

import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.Person
import com.example.util.AddressNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PersonRepositoryImpl(
    private val personDao: PersonDao
) : PersonRepository {

    private fun sortPersonsByStreetNameOnly(list: List<Person>): List<Person> {
        return list.sortedWith(
            compareBy<Person> { p ->
                val parsed = AddressNormalizer.parseAddressComponents(p.endereco, p.numero, p.complemento, p.bairro)
                AddressNormalizer.getStreetSortKey(parsed.street)
            }.thenBy { p ->
                val parsed = AddressNormalizer.parseAddressComponents(p.endereco, p.numero, p.complemento, p.bairro)
                val numDigits = parsed.number.filter { it.isDigit() }
                if (numDigits.isNotBlank()) numDigits.toIntOrNull() ?: Int.MAX_VALUE else Int.MAX_VALUE
            }.thenBy { p ->
                val parsed = AddressNormalizer.parseAddressComponents(p.endereco, p.numero, p.complemento, p.bairro)
                AddressNormalizer.getUnitSortKey(parsed.complement)
            }.thenBy { it.nome.lowercase() }
        )
    }

    override fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons().map { list ->
        sortPersonsByStreetNameOnly(list)
    }

    override fun getPersonById(id: Long): Flow<Person?> = personDao.getPersonByIdFlow(id)

    override suspend fun getPersonByIdDirect(id: Long): Person? = personDao.getPersonById(id)

    override fun searchPersons(query: String): Flow<List<Person>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return getAllPersons()
        }

        val queryNormalized = AddressNormalizer.normalize(trimmed)
        val querySortKey = AddressNormalizer.getStreetSortKey(trimmed)
        val queryDigits = trimmed.filter { it.isDigit() }
        val queryDocDigits = trimmed.filter { it.isLetterOrDigit() }

        return personDao.getAllPersons().map { list ->
            val filtered = list.filter { p ->
                val fullAddr = "${p.endereco} ${p.numero} ${p.complemento} ${p.bairro} ${p.cidade}"
                val addrNorm = AddressNormalizer.normalize(p.endereco)
                val streetKey = AddressNormalizer.getStreetSortKey(p.endereco)
                val nameNorm = AddressNormalizer.normalize(p.nome)
                val compNorm = AddressNormalizer.normalize(p.complemento)
                val bairroNorm = AddressNormalizer.normalize(p.bairro)
                val coRecNorm = AddressNormalizer.normalize(p.coRecebedoresJson)
                val docDigits = p.documento.filter { it.isLetterOrDigit() }

                // Verificações diretas
                addrNorm.contains(queryNormalized, ignoreCase = true) ||
                streetKey.contains(querySortKey, ignoreCase = true) ||
                nameNorm.contains(queryNormalized, ignoreCase = true) ||
                compNorm.contains(queryNormalized, ignoreCase = true) ||
                bairroNorm.contains(queryNormalized, ignoreCase = true) ||
                coRecNorm.contains(queryNormalized, ignoreCase = true) ||
                (queryDocDigits.isNotBlank() && docDigits.contains(queryDocDigits, ignoreCase = true)) ||
                (queryDigits.isNotBlank() && p.numero.contains(queryDigits)) ||
                AddressNormalizer.matches(p.endereco, trimmed) ||
                AddressNormalizer.matches(fullAddr, trimmed)
            }

            sortPersonsByStreetNameOnly(filtered)
        }
    }

    override suspend fun findPersonsByAddress(rawAddress: String): List<Person> {
        if (rawAddress.isBlank()) return emptyList()

        val all = personDao.getAllPersons().first()
        if (all.isEmpty()) return emptyList()

        val parsedRaw = AddressNormalizer.parseAddressComponents(rawAddress)
        val hasSpecificNumber = parsedRaw.number.isNotBlank()

        // Match preciso de endereço, número e complemento considerando todas as variações cadastradas
        val matches = all.filter { p ->
            AddressNormalizer.matchesPrecise(rawAddress, p.endereco, p.numero, p.complemento) ||
            AddressNormalizer.matchesPrecise(rawAddress, "${p.endereco}, ${p.numero}", p.numero, p.complemento) ||
            AddressNormalizer.matchesPrecise(rawAddress, "${p.endereco}, ${p.numero} ${p.complemento} ${p.bairro} ${p.cidade}", p.numero, p.complemento) ||
            (!hasSpecificNumber && p.endereco.isNotBlank() && AddressNormalizer.matches(rawAddress, p.endereco))
        }.distinctBy { it.id }

        return sortPersonsByStreetNameOnly(matches)
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
