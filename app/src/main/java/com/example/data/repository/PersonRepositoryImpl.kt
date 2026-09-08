package com.example.data.repository

import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.Person
import com.example.data.model.Recebedor
import com.example.util.AddressNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PersonRepositoryImpl(
    private val personDao: PersonDao
) : PersonRepository {

    private data class PersonSortKey(
        val person: Person,
        val streetKey: String,
        val numValue: Int,
        val numRaw: String,
        val unitKey: String,
        val nameLower: String
    )

    private fun sortPersonsByStreetNameOnly(list: List<Person>): List<Person> {
        if (list.size <= 1) return list
        return list.map { p ->
            val parsed = AddressNormalizer.parseAddressComponents(p.endereco, p.numero, p.complemento, p.bairro)
            val effectiveNumber = if (p.numero.isNotBlank()) p.numero else parsed.number
            val numDigits = effectiveNumber.filter { it.isDigit() }
            val effectiveStreet = if (parsed.street.isNotBlank()) parsed.street else p.endereco
            val effectiveComplement = if (p.complemento.isNotBlank()) p.complemento else parsed.complement
            PersonSortKey(
                person = p,
                streetKey = AddressNormalizer.getStreetSortKey(effectiveStreet),
                numValue = if (numDigits.isNotBlank()) numDigits.toIntOrNull() ?: Int.MAX_VALUE else Int.MAX_VALUE,
                numRaw = effectiveNumber.trim().lowercase(),
                unitKey = AddressNormalizer.getUnitSortKey(effectiveComplement),
                nameLower = p.nome.lowercase()
            )
        }.sortedWith(
            compareBy<PersonSortKey> { it.streetKey }
                .thenBy { it.numValue }
                .thenBy { it.numRaw }
                .thenBy { it.unitKey }
                .thenByDescending { it.person.dataAtualizacao }
                .thenBy { it.nameLower }
        ).map { it.person }
    }

    override fun getAllPersons(): Flow<List<Person>> = personDao.getAllPersons()
        .map { list -> sortPersonsByStreetNameOnly(list) }
        .flowOn(Dispatchers.Default)

    override suspend fun getAllPersonsDirect(): List<Person> = withContext(Dispatchers.Default) {
        sortPersonsByStreetNameOnly(personDao.getAllPersonsDirect())
    }

    override fun getPersonById(id: Long): Flow<Person?> = personDao.getPersonByIdFlow(id)

    override suspend fun getPersonByIdDirect(id: Long): Person? = personDao.getPersonById(id)

    override fun searchPersons(query: String): Flow<List<Person>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return personDao.getAllPersons()
                .map { sortPersonsByStreetNameOnly(it) }
                .flowOn(Dispatchers.Default)
        }

        return personDao.getAllPersons().map { allList ->
            filterPersonsByQuery(allList, trimmed)
        }.flowOn(Dispatchers.Default)
    }

    private fun filterPersonsByQuery(list: List<Person>, query: String): List<Person> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return sortPersonsByStreetNameOnly(list)

        val normQuery = AddressNormalizer.normalize(trimmed)
        if (normQuery.isBlank()) return sortPersonsByStreetNameOnly(list)

        val queryNumbers = AddressNormalizer.extractNumbers(trimmed)
        val queryTokens = normQuery.split(" ").filter { it.isNotBlank() }

        // Identifica se a busca é essencialmente numérica (apenas número ou prefixos como Nº, Casa, Apto)
        val isNumberOnlySearch = queryTokens.all { token ->
            token.all { it.isDigit() } || token in setOf("N", "NUM", "NUMERO", "NO", "CASA", "CS", "AP", "APT", "APTO")
        } && queryNumbers.isNotEmpty()

        val primaryQueryNumber = queryNumbers.lastOrNull() ?: ""

        data class ScoredPerson(val person: Person, val score: Int)

        val scoredList = mutableListOf<ScoredPerson>()

        for (p in list) {
            val pNormStreet = AddressNormalizer.normalize(p.endereco)
            val pStrippedStreet = AddressNormalizer.normalize(AddressNormalizer.stripStreetType(p.endereco))
            val pNormNumber = AddressNormalizer.normalize(p.numero)
            val pDigitsNumber = p.numero.filter { it.isDigit() }
            val pNormComplement = AddressNormalizer.normalize(p.complemento)
            val pNormBairro = AddressNormalizer.normalize(p.bairro)
            val pNormCidade = AddressNormalizer.normalize(p.cidade)
            val pNormName = AddressNormalizer.normalize(p.nome)
            val pNormDoc = AddressNormalizer.normalize(p.documento)

            val pCoRecebedores = try {
                if (p.coRecebedoresJson.isNotBlank()) Recebedor.listFromJson(p.coRecebedoresJson) else emptyList()
            } catch (_: Throwable) { emptyList() }
            val pCoRecNames = pCoRecebedores.map { AddressNormalizer.normalize("${it.nome} ${it.documento}") }

            val fullSearchable = buildString {
                append(pNormStreet).append(" ")
                append(pStrippedStreet).append(" ")
                append(pNormNumber).append(" ")
                append(pNormComplement).append(" ")
                append(pNormBairro).append(" ")
                append(pNormCidade).append(" ")
                append(pNormName).append(" ")
                append(pNormDoc).append(" ")
                for (rec in pCoRecNames) {
                    append(rec).append(" ")
                }
            }

            var score = 0

            if (isNumberOnlySearch && primaryQueryNumber.isNotBlank()) {
                // Caso A: Busca puramente pelo número (ex: "123", "nº 123", "num 123")
                when {
                    pNormNumber == primaryQueryNumber || pDigitsNumber == primaryQueryNumber -> {
                        score = 1000 // Número exato da residência
                    }
                    pNormNumber.startsWith(primaryQueryNumber) -> {
                        score = 600 // Começa com o número (ex: 123A)
                    }
                    pNormNumber.contains(primaryQueryNumber) -> {
                        score = 400 // Contém o número
                    }
                    pNormComplement.contains(primaryQueryNumber) -> {
                        score = 350 // Número no complemento (ex: Apto 123)
                    }
                    pNormStreet.contains(primaryQueryNumber) -> {
                        score = 300 // Número no nome da rua (ex: Rua 123)
                    }
                    pNormDoc.contains(primaryQueryNumber) -> {
                        score = 250 // No documento
                    }
                }
            } else {
                // Caso B: Busca com palavras e números (ex: "Flores 123", "Rua das Flores, 123", "Paraná 100")
                // ou busca por nome/rua ("Flores", "Carlos", "São Paulo")

                // Verifica se todos os tokens da busca estão presentes na pessoa/endereço
                val allTokensMatch = queryTokens.all { token ->
                    fullSearchable.contains(token) || 
                    (token.length >= 3 && AddressNormalizer.extractStreetSignificantWords(fullSearchable).any { sWord ->
                        AddressNormalizer.isSimilarWord(token, sWord)
                    })
                }

                if (allTokensMatch) {
                    score = 200

                    // Bônus se tiver número e casar perfeitamente com o número da casa
                    if (queryNumbers.isNotEmpty()) {
                        val hasExactNumber = queryNumbers.any { qNum ->
                            pNormNumber == qNum || pDigitsNumber == qNum
                        }
                        if (hasExactNumber) {
                            score += 800 // Bônus gigante: bateu a rua e o número exato!
                        } else if (queryNumbers.any { pNormNumber.contains(it) || pNormComplement.contains(it) }) {
                            score += 400
                        }
                    }

                    // Bônus se a rua bate com a busca
                    if (pNormStreet.contains(normQuery) || pStrippedStreet.contains(normQuery) || normQuery.contains(pStrippedStreet)) {
                        score += 300
                    }

                    // Bônus se o nome bate
                    if (pNormName.contains(normQuery) || normQuery.contains(pNormName)) {
                        score += 250
                    }
                } else if (queryNumbers.isNotEmpty()) {
                    // Se nem todos os tokens bateram como substring contínua,
                    // mas tem número e o número é exato, e ao menos uma palavra da rua ou nome bateu
                    val hasExactNum = queryNumbers.any { pNormNumber == it || pDigitsNumber == it }
                    val hasAnyWordMatch = queryTokens.filter { !it.all { c -> c.isDigit() } }.any { word ->
                        word.length >= 3 && fullSearchable.contains(word)
                    }
                    if (hasExactNum && hasAnyWordMatch) {
                        score = 750
                    }
                }

                // Verificação adicional de endereço completo via AddressNormalizer
                if (score == 0 && AddressNormalizer.matchesPrecise(trimmed, p.endereco, p.numero, p.complemento)) {
                    score = 700
                } else if (score == 0 && queryNumbers.isEmpty() && AddressNormalizer.matches(trimmed, p.endereco)) {
                    score = 300
                }
            }

            if (score > 0) {
                scoredList.add(ScoredPerson(p, score))
            }
        }

        return scoredList.sortedWith(
            compareByDescending<ScoredPerson> { it.score }
                .thenBy { AddressNormalizer.getStreetSortKey(it.person.endereco) }
                .thenBy { it.person.numero.filter { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.person.numero.lowercase() }
                .thenBy { it.person.nome.lowercase() }
        ).map { it.person }
    }

    override suspend fun findPersonsByAddress(rawAddress: String): List<Person> = withContext(Dispatchers.Default) {
        if (rawAddress.isBlank()) return@withContext emptyList()

        val parsedRaw = AddressNormalizer.parseAddressComponents(rawAddress)
        val numStr = parsedRaw.number.trim()
        val allNumbersInQuery = AddressNormalizer.extractNumbers(rawAddress)
        val hasSpecificNumber = numStr.isNotBlank() || allNumbersInQuery.isNotEmpty()
        val effectiveNumber = numStr.ifBlank { allNumbersInQuery.firstOrNull() ?: "" }
        val normEffectiveNumber = AddressNormalizer.normalizeNumber(effectiveNumber)

        val sigWords = AddressNormalizer.extractStreetSignificantWords(parsedRaw.street.ifBlank { rawAddress })
        val keyword = sigWords.firstOrNull() ?: ""

        // Busca candidatas filtradas via SQL indexado no Room em milissegundos
        var candidates = personDao.findCandidatePersons(effectiveNumber, keyword)
        if (candidates.isEmpty() && normEffectiveNumber != effectiveNumber && normEffectiveNumber.isNotBlank()) {
            candidates = personDao.findCandidatePersons(normEffectiveNumber, keyword)
        }
        if (candidates.isEmpty()) {
            candidates = personDao.getAllPersonsDirect()
        }
        if (candidates.isEmpty()) return@withContext emptyList()

        // Match preciso de endereço, número e complemento considerando apenas as candidatas
        val matches = candidates.filter { p ->
            AddressNormalizer.matchesPrecise(rawAddress, p.endereco, p.numero, p.complemento) ||
            AddressNormalizer.matchesPrecise(rawAddress, "${p.endereco}, ${p.numero}", p.numero, p.complemento) ||
            AddressNormalizer.matchesPrecise(rawAddress, "${p.endereco}, ${p.numero} ${p.complemento} ${p.bairro} ${p.cidade}", p.numero, p.complemento) ||
            (parsedRaw.street.isNotBlank() && AddressNormalizer.matchesPrecise("${parsedRaw.street}, ${parsedRaw.number}", p.endereco, p.numero, p.complemento)) ||
            (!hasSpecificNumber && p.endereco.isNotBlank() && AddressNormalizer.matches(rawAddress, p.endereco))
        }.distinctBy { it.id }

        if (matches.isNotEmpty()) {
            sortPersonsByStreetNameOnly(matches)
        } else {
            // Se o endereço buscado possui um número específico (ex: "Rua X, 115"), NÃO retornar registros com números diferentes!
            // Retorna vazio para que o assistente identifique como NÃO SALVO e permita cadastrar o novo destinatário no número correto.
            if (hasSpecificNumber) {
                emptyList()
            } else {
                val all = personDao.getAllPersonsDirect()
                filterPersonsByQuery(all, rawAddress)
            }
        }
    }

    override suspend fun insertPerson(person: Person): Long = personDao.insertPerson(person)

    override suspend fun insertAll(persons: List<Person>) {
        if (persons.isEmpty()) return
        // Batching em lotes de 50 para máxima compatibilidade e evitar estouros de SQLite Cursor/Buffer
        persons.chunked(50).forEach { chunk ->
            personDao.insertAll(chunk)
        }
    }

    override suspend fun updatePerson(person: Person) = personDao.updatePerson(person)

    override suspend fun deletePerson(person: Person) = personDao.deletePerson(person)

    override suspend fun deletePersonById(id: Long) = personDao.deletePersonById(id)

    override suspend fun countPersons(): Int = personDao.countPersons()

    override suspend fun markPersonUsed(id: Long) {
        val person = personDao.getPersonById(id) ?: return
        personDao.updatePerson(person.copy(dataAtualizacao = System.currentTimeMillis()))
    }

    override suspend fun prioritizeRecebedor(personId: Long, recebedorId: String): Person? = withContext(Dispatchers.IO) {
        val person = personDao.getPersonById(personId) ?: return@withContext null
        val now = System.currentTimeMillis()

        val isAlreadyMain = recebedorId.isBlank() ||
                recebedorId == "main" ||
                recebedorId == "p_${person.id}_main" ||
                recebedorId.endsWith("_main")

        if (isAlreadyMain) {
            val updated = person.copy(dataAtualizacao = now)
            personDao.updatePerson(updated)
            return@withContext updated
        }

        val extras = Recebedor.listFromJson(person.coRecebedoresJson)
        val cleanRecId = recebedorId
            .removePrefix("p_${person.id}_co_")
            .removePrefix("p_${person.id}_")
            .removePrefix("co_")

        val targetIdx = extras.indexOfFirst {
            it.id == cleanRecId ||
            it.id == recebedorId ||
            "p_${person.id}_co_${it.id}" == recebedorId ||
            (it.id.startsWith("co_") && it.id.removePrefix("co_") == cleanRecId)
        }

        if (targetIdx < 0) {
            val byNameIdx = extras.indexOfFirst {
                it.nome.isNotBlank() && recebedorId.contains(it.nome, ignoreCase = true)
            }
            if (byNameIdx < 0) {
                val updated = person.copy(dataAtualizacao = now)
                personDao.updatePerson(updated)
                return@withContext updated
            } else {
                return@withContext reorderWithTarget(person, extras, byNameIdx, now)
            }
        }

        return@withContext reorderWithTarget(person, extras, targetIdx, now)
    }

    private suspend fun reorderWithTarget(
        person: Person,
        extras: List<Recebedor>,
        targetIdx: Int,
        now: Long
    ): Person {
        val targetRec = extras[targetIdx]
        val oldMain = Recebedor(
            id = java.util.UUID.randomUUID().toString().take(8),
            nome = person.nome,
            documento = person.documento,
            assinatura = person.assinatura,
            dataUso = person.dataAtualizacao
        )

        val newExtras = mutableListOf<Recebedor>()
        if (oldMain.nome.isNotBlank()) {
            newExtras.add(oldMain)
        }
        for (i in extras.indices) {
            if (i != targetIdx) {
                newExtras.add(extras[i])
            }
        }

        val updatedPerson = person.copy(
            nome = targetRec.nome,
            documento = targetRec.documento,
            assinatura = targetRec.assinatura,
            coRecebedoresJson = Recebedor.listToJson(newExtras),
            dataAtualizacao = now
        )

        personDao.updatePerson(updatedPerson)
        return updatedPerson
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

    private fun getCanonicalAddressKey(p: Person): String {
        val parsed = AddressNormalizer.parseAddressComponents(p.endereco, p.numero, p.complemento, p.bairro)
        val streetKey = AddressNormalizer.getStreetSortKey(parsed.street)
        val numDigits = parsed.number.filter { it.isDigit() }
        val numKey = if (numDigits.isNotBlank()) numDigits else AddressNormalizer.normalize(parsed.number)
        val compKey = AddressNormalizer.getUnitSortKey(parsed.complement)
        return "$streetKey|$numKey|$compKey"
    }

    private fun areAddressesSame(p1: Person, p2: Person): Boolean {
        val k1 = getCanonicalAddressKey(p1)
        val k2 = getCanonicalAddressKey(p2)
        if (k1.isNotBlank() && k1 == k2) return true

        val full1 = "${p1.endereco} ${p1.numero}".trim()
        val full2 = "${p2.endereco} ${p2.numero}".trim()
        if (AddressNormalizer.matchesPrecise(full1, p2.endereco, p2.numero, p2.complemento)) return true
        if (AddressNormalizer.matchesPrecise(full2, p1.endereco, p1.numero, p1.complemento)) return true

        return false
    }

    override suspend fun consolidateDuplicateAddressPersons(): Int = withContext(Dispatchers.IO) {
        val allPersons = personDao.getAllPersonsDirect()
        if (allPersons.size <= 1) return@withContext 0

        // Agrupamento O(N) por chave de endereço canônica
        val clusters = allPersons.groupBy { getCanonicalAddressKey(it) }.values

        var consolidatedCount = 0

        for (cluster in clusters) {
            if (cluster.size <= 1) continue

            // Seleciona a pessoa base (prioriza quem tem assinatura preenchida, ou menor ID)
            val basePerson = cluster.minByOrNull { p ->
                val hasSig = if (p.assinatura.isNotBlank()) 0 else 1
                hasSig * 1000000L + p.id
            } ?: cluster.first()

            val allRecebedores = mutableListOf<Recebedor>()

            for (p in cluster) {
                if (p.nome.isNotBlank() || p.documento.isNotBlank()) {
                    allRecebedores.add(
                        Recebedor(
                            id = if (p.id == basePerson.id) "main" else "co_${p.id}_main",
                            nome = p.nome.trim(),
                            documento = p.documento.trim(),
                            assinatura = p.assinatura
                        )
                    )
                }
                if (p.coRecebedoresJson.isNotBlank()) {
                    try {
                        val extras = Recebedor.listFromJson(p.coRecebedoresJson)
                        for (extra in extras) {
                            if (extra.nome.isNotBlank() || extra.documento.isNotBlank()) {
                                allRecebedores.add(extra)
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }

            // Desduplica recebedores por nome e documento, mantendo a melhor versão (com assinatura)
            val deduplicated = mutableListOf<Recebedor>()
            for (rec in allRecebedores) {
                val normName = AddressNormalizer.normalize(rec.nome)
                val cleanDoc = rec.documento.filter { it.isLetterOrDigit() }

                val existingIdx = deduplicated.indexOfFirst { ex ->
                    val exNorm = AddressNormalizer.normalize(ex.nome)
                    val exDoc = ex.documento.filter { it.isLetterOrDigit() }
                    if (cleanDoc.isNotBlank() && exDoc.isNotBlank()) {
                        cleanDoc == exDoc || (normName.isNotBlank() && normName == exNorm)
                    } else {
                        normName.isNotBlank() && normName == exNorm
                    }
                }

                if (existingIdx >= 0) {
                    val current = deduplicated[existingIdx]
                    if (current.assinatura.isBlank() && rec.assinatura.isNotBlank()) {
                        deduplicated[existingIdx] = current.copy(
                            assinatura = rec.assinatura,
                            documento = if (current.documento.isBlank()) rec.documento else current.documento
                        )
                    } else if (current.documento.isBlank() && rec.documento.isNotBlank()) {
                        deduplicated[existingIdx] = current.copy(documento = rec.documento)
                    }
                } else {
                    deduplicated.add(rec)
                }
            }

            if (deduplicated.isEmpty()) continue

            val primary = deduplicated.first()
            val extraList = deduplicated.drop(1)

            val bestObs = (listOf(basePerson.observacao) + cluster.map { it.observacao })
                .firstOrNull { it.isNotBlank() } ?: ""

            val updatedBase = basePerson.copy(
                nome = primary.nome,
                documento = primary.documento,
                assinatura = primary.assinatura,
                observacao = bestObs,
                coRecebedoresJson = Recebedor.listToJson(extraList),
                dataAtualizacao = System.currentTimeMillis()
            )

            personDao.updatePerson(updatedBase)

            val duplicates = cluster.filter { it.id != basePerson.id }
            for (dup in duplicates) {
                personDao.deletePerson(dup)
                consolidatedCount++
            }
        }

        consolidatedCount
    }

    override suspend fun sanitizeCorruptedAddressRecords(): Int = withContext(Dispatchers.IO) {
        val all = personDao.getAllPersonsDirect()
        var fixedCount = 0
        for (p in all) {
            val hasAbalhadoresInComp = p.complemento.contains("ABALHADORES", ignoreCase = true)
            val isBrokenStreet = p.endereco.trim().equals("Rua dos", ignoreCase = true) ||
                    p.endereco.trim().equals("Trabalhadores", ignoreCase = true) ||
                    (p.endereco.contains("Trabalhadores", ignoreCase = true) && hasAbalhadoresInComp)

            if (hasAbalhadoresInComp || isBrokenStreet) {
                val cleanComp = p.complemento.replace(Regex("""(?i)\b(?:torre\s+)?abalhadores\b"""), "").trim()
                val correctedStreet = if (p.endereco.trim().equals("Rua dos", ignoreCase = true) ||
                    p.endereco.trim().equals("Trabalhadores", ignoreCase = true) ||
                    p.endereco.contains("Trabalhadores", ignoreCase = true)
                ) {
                    "Rua dos Trabalhadores"
                } else {
                    p.endereco
                }

                if (p.endereco != correctedStreet || p.complemento != cleanComp) {
                    personDao.updatePerson(
                        p.copy(
                            endereco = correctedStreet,
                            complemento = cleanComp
                        )
                    )
                    fixedCount++
                }
            }
        }
        fixedCount
    }
}
