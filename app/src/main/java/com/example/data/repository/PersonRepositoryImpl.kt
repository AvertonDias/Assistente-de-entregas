package com.example.data.repository

import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.Person
import com.example.data.model.Recebedor
import com.example.util.AddressNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class PersonRepositoryImpl(
    private val personDao: PersonDao,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : PersonRepository {

    private data class IndexedPerson(
        val person: Person,
        val pNormStreet: String,
        val pStrippedStreet: String,
        val pNormNumber: String,
        val pCleanNumber: String,
        val pDigitsNumber: String,
        val pRawNumber: String,
        val pNormComplement: String,
        val pNormBairro: String,
        val pNormCidade: String,
        val pNormName: String,
        val pNormDoc: String,
        val pDigitsDoc: String,
        val pCoRecNames: List<String>,
        val pCoRecDocs: List<String>,
        val fullSearchable: String,
        val fullWords: List<String>,
        val sigWords: List<String>,
        val streetSortKey: String,
        val numValue: Int,
        val numRaw: String,
        val unitKey: String,
        val nameLower: String
    )

    private fun indexPerson(p: Person): IndexedPerson {
        val parsed = AddressNormalizer.parseAddressComponents(p.endereco, p.numero, p.complemento, p.bairro)
        val effectiveNumber = if (p.numero.isNotBlank()) p.numero else parsed.number
        val numDigits = effectiveNumber.filter { it.isDigit() }
        val effectiveStreet = if (parsed.street.isNotBlank()) parsed.street else p.endereco
        val effectiveComplement = if (p.complemento.isNotBlank()) p.complemento else parsed.complement

        val pNormStreet = AddressNormalizer.normalize(p.endereco)
        val pStrippedStreet = AddressNormalizer.normalize(AddressNormalizer.stripStreetType(p.endereco))
        val pNormNumber = AddressNormalizer.normalize(p.numero)
        val pCleanNumber = AddressNormalizer.normalizeNumber(p.numero)
        val pDigitsNumber = p.numero.filter { it.isDigit() }
        val pRawNumber = p.numero.trim().replace(".", "")
        val pNormComplement = AddressNormalizer.normalize(p.complemento)
        val pNormBairro = AddressNormalizer.normalize(p.bairro)
        val pNormCidade = AddressNormalizer.normalize(p.cidade)
        val pNormName = AddressNormalizer.normalize(p.nome)
        val pNormDoc = AddressNormalizer.normalize(p.documento)
        val pDigitsDoc = p.documento.filter { it.isDigit() }

        val pCoRecebedores = try {
            if (p.coRecebedoresJson.isNotBlank()) Recebedor.listFromJson(p.coRecebedoresJson) else emptyList()
        } catch (_: Throwable) { emptyList() }
        val pCoRecNames = pCoRecebedores.map { AddressNormalizer.normalize("${it.nome} ${it.documento}") }
        val pCoRecDocs = pCoRecebedores.map { it.documento.filter { c -> c.isDigit() } }.filter { it.isNotBlank() }

        val fullSearchable = buildString {
            append(pNormStreet).append(" ")
            append(pStrippedStreet).append(" ")
            append(pNormNumber).append(" ")
            append(pCleanNumber).append(" ")
            append(pDigitsNumber).append(" ")
            append(pRawNumber).append(" ")
            append(pNormComplement).append(" ")
            append(pNormBairro).append(" ")
            append(pNormCidade).append(" ")
            append(pNormName).append(" ")
            append(pNormDoc).append(" ")
            append(pDigitsDoc).append(" ")
            for (rec in pCoRecNames) {
                append(rec).append(" ")
            }
            for (doc in pCoRecDocs) {
                append(doc).append(" ")
            }
        }

        val fullWords = fullSearchable.split(" ").filter { it.isNotBlank() }
        val sigWords = AddressNormalizer.extractStreetSignificantWords(fullSearchable)

        return IndexedPerson(
            person = p,
            pNormStreet = pNormStreet,
            pStrippedStreet = pStrippedStreet,
            pNormNumber = pNormNumber,
            pCleanNumber = pCleanNumber,
            pDigitsNumber = pDigitsNumber,
            pRawNumber = pRawNumber,
            pNormComplement = pNormComplement,
            pNormBairro = pNormBairro,
            pNormCidade = pNormCidade,
            pNormName = pNormName,
            pNormDoc = pNormDoc,
            pDigitsDoc = pDigitsDoc,
            pCoRecNames = pCoRecNames,
            pCoRecDocs = pCoRecDocs,
            fullSearchable = fullSearchable,
            fullWords = fullWords,
            sigWords = sigWords,
            streetSortKey = AddressNormalizer.getStreetSortKey(effectiveStreet),
            numValue = if (numDigits.isNotBlank()) numDigits.toIntOrNull() ?: Int.MAX_VALUE else Int.MAX_VALUE,
            numRaw = effectiveNumber.trim().lowercase(),
            unitKey = AddressNormalizer.getUnitSortKey(effectiveComplement),
            nameLower = p.nome.lowercase()
        )
    }

    private val indexedPersonCache = java.util.concurrent.ConcurrentHashMap<Long, IndexedPerson>()

    @Volatile
    private var numberIndexMap: Map<String, List<IndexedPerson>> = emptyMap()

    @Volatile
    private var wordIndexMap: Map<String, List<IndexedPerson>> = emptyMap()

    private fun rebuildInvertedIndices(list: List<IndexedPerson>) {
        val numMap = HashMap<String, MutableList<IndexedPerson>>()
        val wordMap = HashMap<String, MutableList<IndexedPerson>>()

        for (item in list) {
            if (item.pCleanNumber.isNotBlank()) {
                numMap.getOrPut(item.pCleanNumber) { ArrayList() }.add(item)
            }
            if (item.pDigitsNumber.isNotBlank() && item.pDigitsNumber != item.pCleanNumber) {
                numMap.getOrPut(item.pDigitsNumber) { ArrayList() }.add(item)
            }
            if (item.pRawNumber.isNotBlank() && item.pRawNumber != item.pCleanNumber && item.pRawNumber != item.pDigitsNumber) {
                numMap.getOrPut(item.pRawNumber) { ArrayList() }.add(item)
            }

            for (w in item.sigWords) {
                if (w.length >= 2) {
                    wordMap.getOrPut(w) { ArrayList() }.add(item)
                }
            }
        }
        numberIndexMap = numMap
        wordIndexMap = wordMap
    }

    private fun sortPersonsByStreetNameOnly(list: List<Person>): List<Person> {
        if (list.size <= 1) return list
        return list.map { person ->
            indexedPersonCache[person.id] ?: indexPerson(person)
        }.sortedWith(
            compareBy<IndexedPerson> { it.streetSortKey }
                .thenBy { it.numValue }
                .thenBy { it.numRaw }
                .thenBy { it.unitKey }
                .thenByDescending { it.person.dataAtualizacao }
                .thenBy { it.nameLower }
        ).map { it.person }
    }

    private val cachedIndexedPersons: StateFlow<List<IndexedPerson>> = personDao.getAllPersons()
        .map { list ->
            val result = ArrayList<IndexedPerson>(list.size)
            val currentIds = HashSet<Long>(list.size)

            for (person in list) {
                currentIds.add(person.id)
                val existing = indexedPersonCache[person.id]
                if (existing != null && existing.person == person) {
                    result.add(existing)
                } else {
                    val newlyIndexed = indexPerson(person)
                    indexedPersonCache[person.id] = newlyIndexed
                    result.add(newlyIndexed)
                }
            }

            if (indexedPersonCache.size > currentIds.size) {
                indexedPersonCache.keys.retainAll(currentIds)
            }

            val sorted = result.sortedWith(
                compareBy<IndexedPerson> { it.streetSortKey }
                    .thenBy { it.numValue }
                    .thenBy { it.numRaw }
                    .thenBy { it.unitKey }
                    .thenByDescending { it.person.dataAtualizacao }
                    .thenBy { it.nameLower }
            )

            rebuildInvertedIndices(sorted)
            sorted
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = externalScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    override fun getAllPersons(): Flow<List<Person>> = cachedIndexedPersons
        .map { list -> list.map { it.person } }
        .flowOn(Dispatchers.Default)

    override suspend fun getAllPersonsDirect(): List<Person> = withContext(Dispatchers.Default) {
        val cached = cachedIndexedPersons.value
        if (cached.isNotEmpty()) {
            cached.map { it.person }
        } else {
            val list = personDao.getAllPersonsDirect()
            list.map { indexPerson(it) }.sortedWith(
                compareBy<IndexedPerson> { it.streetSortKey }
                    .thenBy { it.numValue }
                    .thenBy { it.numRaw }
                    .thenBy { it.unitKey }
                    .thenByDescending { it.person.dataAtualizacao }
                    .thenBy { it.nameLower }
            ).map { it.person }
        }
    }

    override fun getPersonById(id: Long): Flow<Person?> = personDao.getPersonByIdFlow(id)

    override suspend fun getPersonByIdDirect(id: Long): Person? = personDao.getPersonById(id)

    override fun searchPersons(query: String): Flow<List<Person>> {
        val trimmed = query.trim()
        return cachedIndexedPersons.map { indexedList ->
            val listToUse = if (indexedList.isEmpty()) {
                try {
                    val direct = personDao.getAllPersonsDirect()
                    direct.map { indexPerson(it) }
                } catch (e: Exception) {
                    indexedList
                }
            } else {
                indexedList
            }

            if (trimmed.isBlank()) {
                listToUse.map { it.person }
            } else {
                filterIndexedPersonsByQuery(listToUse, trimmed)
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun filterIndexedPersonsByQuery(list: List<IndexedPerson>, query: String): List<Person> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return list.map { it.person }
        }

        val normQuery = AddressNormalizer.normalize(trimmed)
        if (normQuery.isBlank()) {
            return list.map { it.person }
        }

        val cleanQueryNumbers = AddressNormalizer.extractNumbers(trimmed)
            .map { AddressNormalizer.normalizeNumber(it) }
            .filter { it.isNotBlank() }
            .distinct()
        val queryTokens = normQuery.split(" ").filter { it.isNotBlank() }

        val commonPrefixes = setOf(
            "RUA", "R", "AV", "AVENIDA", "TRAVESSA", "TRAV", "TV", "ALAMEDA", "AL",
            "PRACA", "PCA", "PR", "RODOVIA", "ROD", "ESTRADA", "EST",
            "DR", "DRA", "DOUTOR", "DOUTORA", "CEL", "CORONEL", "PROF", "PROFESSOR",
            "STA", "SANTA", "STO", "SANTO",
            "DE", "DA", "DO", "DOS", "DAS", "E", "EM", "NO", "NA", "NOS", "NAS", "AO", "AOS",
            "NUMERO", "NUM", "N", "NO", "CASA", "CS", "AP", "APT", "APTO", "BLOCO", "BL"
        )

        // Identifica se a busca possui nome de rua e número juntos (busca de endereço)
        val parsedQueryAddr = AddressNormalizer.parseAddressComponents(trimmed)
        val strippedQueryStreet = AddressNormalizer.stripStreetType(parsedQueryAddr.street)
        val queryHasStreetAndNumber = parsedQueryAddr.street.isNotBlank() &&
            strippedQueryStreet.isNotBlank() &&
            !strippedQueryStreet.all { it.isDigit() || it == '-' || it == ',' || it == '.' } &&
            cleanQueryNumbers.isNotEmpty()

        // Identifica se a busca é essencialmente numérica (apenas número ou prefixos como Nº, Casa, Apto)
        val isNumberOnlySearch = queryTokens.all { token ->
            token.all { it.isDigit() } || token in setOf("N", "NUM", "NUMERO", "NO", "CASA", "CS", "AP", "APT", "APTO")
        } && cleanQueryNumbers.isNotEmpty()

        val primaryQueryNumber = cleanQueryNumbers.firstOrNull() ?: ""

        val coreTokens = if (queryTokens.size > 1) {
            queryTokens.filter { it !in commonPrefixes }.ifEmpty { queryTokens }
        } else {
            queryTokens
        }

        data class ScoredIndexedPerson(val item: IndexedPerson, val score: Int)

        val scoredList = ArrayList<ScoredIndexedPerson>(list.size)

        for (item in list) {
            fun matchesToken(token: String): Boolean {
                if (item.fullSearchable.contains(token)) return true
                if (token.all { it.isDigit() }) {
                    if (item.pCleanNumber == token || item.pDigitsNumber == token || item.pRawNumber.equals(token, ignoreCase = true) ||
                        item.pDigitsDoc.contains(token) || item.pNormComplement.contains(token) || item.pCoRecDocs.any { it.contains(token) }) {
                        return true
                    }
                }
                if (item.fullWords.any { it.startsWith(token) }) return true
                if (token.length >= 3 && item.sigWords.any { AddressNormalizer.isSimilarWord(token, it) }) {
                    return true
                }
                return false
            }

            var score = 0

            if (queryHasStreetAndNumber) {
                // Busca de endereço (Rua + Número): OBRIGATORIAMENTE rua E número devem pertencer à mesma residência!
                val streetMatches = AddressNormalizer.areStreetNamesMatching(parsedQueryAddr.street, item.person.endereco)
                val numberMatches = cleanQueryNumbers.any { qNum ->
                    item.pCleanNumber == qNum || item.pDigitsNumber == qNum || item.pRawNumber.equals(qNum, ignoreCase = true) ||
                    AddressNormalizer.areNumbersMatching(qNum, item.person.numero)
                }

                if (streetMatches && numberMatches) {
                    score = 1500
                    if (parsedQueryAddr.complement.isNotBlank() && item.pNormComplement.contains(AddressNormalizer.normalize(parsedQueryAddr.complement))) {
                        score += 200
                    }
                } else if (streetMatches && cleanQueryNumbers.isEmpty()) {
                    score = 400
                } else {
                    // Rua diferente! Rejeita imediatamente (score = 0) para NUNCA puxar pessoas de outra rua com mesmo número!
                    score = 0
                }
            } else if (isNumberOnlySearch && primaryQueryNumber.isNotBlank()) {
                // Caso A: Busca puramente pelo número (ex: "123", "1.843", "nº 123", "num 1843")
                when {
                    item.pCleanNumber == primaryQueryNumber || item.pDigitsNumber == primaryQueryNumber || item.pRawNumber.equals(primaryQueryNumber, ignoreCase = true) -> {
                        score = 1000 // Número exato da residência
                    }
                    item.pCleanNumber.startsWith(primaryQueryNumber) || item.pDigitsNumber.startsWith(primaryQueryNumber) -> {
                        score = 600 // Começa com o número (ex: 123A)
                    }
                    item.pCleanNumber.contains(primaryQueryNumber) || item.pDigitsNumber.contains(primaryQueryNumber) || item.pNormNumber.contains(primaryQueryNumber) -> {
                        score = 400 // Contém o número
                    }
                    item.pNormComplement.contains(primaryQueryNumber) -> {
                        score = 350 // Número no complemento (ex: Apto 123)
                    }
                    item.pNormStreet.contains(primaryQueryNumber) -> {
                        score = 300 // Número no nome da rua (ex: Rua 123)
                    }
                    item.pDigitsDoc.contains(primaryQueryNumber) || item.pNormDoc.contains(primaryQueryNumber) -> {
                        score = 250 // No documento
                    }
                }
            } else {
                // Caso B: Busca com palavras (ex: "Flores", "Carlos", "São Paulo")
                val allCoreTokensMatch = coreTokens.all { matchesToken(it) }

                if (allCoreTokensMatch) {
                    score = 200

                    // Bônus se tiver número e casar perfeitamente com o número da casa da mesma rua
                    if (cleanQueryNumbers.isNotEmpty()) {
                        val hasExactNumber = cleanQueryNumbers.any { qNum ->
                            item.pCleanNumber == qNum || item.pDigitsNumber == qNum || item.pRawNumber.equals(qNum, ignoreCase = true)
                        }
                        if (hasExactNumber && AddressNormalizer.areStreetNamesMatching(normQuery, item.person.endereco)) {
                            score += 800 // Bônus: bateu a rua e o número exato!
                        } else if (hasExactNumber) {
                            score += 100
                        }
                    }

                    // Bônus se a rua bate com a busca
                    if (item.pNormStreet.contains(normQuery) || item.pStrippedStreet.contains(normQuery) || normQuery.contains(item.pStrippedStreet)) {
                        score += 300
                    }

                    // Bônus se o nome bate
                    if (item.pNormName.contains(normQuery) || normQuery.contains(item.pNormName) || item.pCoRecNames.any { it.contains(normQuery) }) {
                        score += 250
                    }

                    // Bônus se o documento bate
                    if (item.pDigitsDoc.isNotBlank() && normQuery.filter { it.isDigit() }.let { it.isNotBlank() && (item.pDigitsDoc.contains(it) || it.contains(item.pDigitsDoc)) }) {
                        score += 250
                    }
                }

                // Verificação adicional de endereço completo via AddressNormalizer
                if (score == 0 && AddressNormalizer.matchesPrecise(trimmed, item.person.endereco, item.person.numero, item.person.complemento)) {
                    score = 700
                } else if (score == 0 && cleanQueryNumbers.isEmpty() && AddressNormalizer.matches(trimmed, item.person.endereco)) {
                    score = 300
                }
            }

            if (score > 0) {
                scoredList.add(ScoredIndexedPerson(item, score))
            }
        }

        val scored = scoredList.sortedWith(
            compareByDescending<ScoredIndexedPerson> { it.score }
                .thenBy { it.item.streetSortKey }
                .thenBy { it.item.numValue }
                .thenBy { it.item.numRaw }
                .thenBy { it.item.unitKey }
                .thenBy { it.item.nameLower }
        ).map { it.item.person }
        return scored
    }

    private fun filterPersonsByQuery(list: List<Person>, query: String): List<Person> {
        return filterIndexedPersonsByQuery(list.map { indexPerson(it) }, query)
    }

    override suspend fun findPersonsByAddress(rawAddress: String): List<Person> = withContext(Dispatchers.Default) {
        if (rawAddress.isBlank()) return@withContext emptyList()

        val parsedRaw = AddressNormalizer.parseAddressComponents(rawAddress)
        val numStr = parsedRaw.number.trim()
        val allNumbersInQuery = AddressNormalizer.extractNumbers(rawAddress)
        val hasSpecificNumber = numStr.isNotBlank() || allNumbersInQuery.isNotEmpty()
        val effectiveNumber = numStr.ifBlank { allNumbersInQuery.firstOrNull() ?: "" }
        val normEffectiveNumber = AddressNormalizer.normalizeNumber(effectiveNumber)

        val queryStreet = parsedRaw.street.ifBlank { rawAddress }
        // Se a busca não possui nome de rua válido (ex: apenas números), não deve casar com nenhuma rua!
        val strippedQueryStreet = AddressNormalizer.stripStreetType(queryStreet)
        if (strippedQueryStreet.isBlank() || strippedQueryStreet.all { it.isDigit() || it == '-' || it == ',' || it == '.' }) {
            return@withContext emptyList()
        }

        val sigWords = AddressNormalizer.extractStreetSignificantWords(queryStreet)
        val keyword = sigWords.firstOrNull { it.length >= 3 && it.any { c -> c.isLetter() } } ?: sigWords.firstOrNull() ?: ""

        val cached = cachedIndexedPersons.value
        val candidates = if (cached.isNotEmpty()) {
            val numBucket = if (effectiveNumber.isNotBlank()) {
                numberIndexMap[effectiveNumber] ?: numberIndexMap[normEffectiveNumber]
            } else null

            val wordBucket = if (keyword.isNotBlank()) {
                wordIndexMap[keyword]
            } else null

            if (numBucket != null || wordBucket != null) {
                val candidateSet = LinkedHashSet<IndexedPerson>((numBucket?.size ?: 0) + (wordBucket?.size ?: 0))
                if (numBucket != null) candidateSet.addAll(numBucket)
                if (wordBucket != null) candidateSet.addAll(wordBucket)
                candidateSet.take(200).map { it.person }
            } else {
                cached.filter { item ->
                    (effectiveNumber.isNotBlank() && (item.pCleanNumber == effectiveNumber || item.pDigitsNumber == effectiveNumber || item.pRawNumber == effectiveNumber || item.fullSearchable.contains(effectiveNumber))) ||
                    (keyword.isNotBlank() && (item.pNormStreet.contains(keyword) || item.pNormName.contains(keyword) || item.sigWords.contains(keyword)))
                }.take(200).map { it.person }
            }
        } else {
            // Fallback para consulta SQL caso o cache ainda esteja na primeira inicialização
            var dbCandidates = personDao.findCandidatePersons(effectiveNumber, keyword)
            if (dbCandidates.isEmpty()) {
                for (w in sigWords) {
                    if (w != keyword && (w.length >= 3 || w.any { c -> c.isLetter() })) {
                        dbCandidates = personDao.findCandidatePersons(effectiveNumber, w)
                        if (dbCandidates.isNotEmpty()) break
                    }
                }
            }
            if (dbCandidates.isEmpty() && normEffectiveNumber != effectiveNumber && normEffectiveNumber.isNotBlank()) {
                dbCandidates = personDao.findCandidatePersons(normEffectiveNumber, keyword)
            }
            if (dbCandidates.isEmpty() && keyword.isNotBlank()) {
                dbCandidates = personDao.findCandidatePersons("", keyword)
            }
            dbCandidates
        }
        if (candidates.isEmpty()) return@withContext emptyList()

        // Match preciso de endereço: OBRIGATORIAMENTE rua E número devem bater juntos!
        val hasQueryComplement = parsedRaw.complement.isNotBlank()
        val rawMatches = candidates.filter { p ->
            val streetMatches = AddressNormalizer.areStreetNamesMatching(queryStreet, p.endereco)
            val numMatches = if (hasSpecificNumber && effectiveNumber.isNotBlank()) {
                val pNum = p.numero.trim()
                val parsedP = AddressNormalizer.parseAddressComponents(p.endereco, p.numero, p.complemento)
                val targetNum = pNum.ifBlank { parsedP.number }
                targetNum.isNotBlank() && AddressNormalizer.areNumbersMatching(effectiveNumber, targetNum)
            } else true

            streetMatches && numMatches && (
                AddressNormalizer.matchesPrecise(rawAddress, p.endereco, p.numero, p.complemento) ||
                AddressNormalizer.matchesPrecise(rawAddress, "${p.endereco}, ${p.numero}", p.numero, p.complemento) ||
                AddressNormalizer.matchesPrecise(rawAddress, "${p.endereco}, ${p.numero} ${p.complemento} ${p.bairro} ${p.cidade}", p.numero, p.complemento) ||
                (!hasQueryComplement && parsedRaw.street.isNotBlank() && parsedRaw.number.isNotBlank() && AddressNormalizer.matchesPrecise("${parsedRaw.street}, ${parsedRaw.number}", p.endereco, p.numero, p.complemento))
            )
        }.distinctBy { it.id }

        if (rawMatches.isNotEmpty()) {
            sortPersonsByStreetNameOnly(rawMatches)
        } else {
            emptyList()
        }
    }

    override suspend fun insertPerson(person: Person): Long {
        val id = personDao.insertPerson(person)
        com.example.util.SavedRecipientLookupHelper.invalidateCache()
        com.example.util.AppActivityTracker.logAction(
            actionType = "PERSON_CREATED",
            title = "Destinatário Cadastrado",
            details = "Nome: ${person.nome}\nEndereço: ${person.endereco}, ${person.numero}",
            category = "Cadastros",
            incrementPerson = true
        )
        return id
    }

    override suspend fun insertAll(persons: List<Person>) {
        if (persons.isEmpty()) return
        // Batching em lotes de 50 para máxima compatibilidade e evitar estouros de SQLite Cursor/Buffer
        persons.chunked(50).forEach { chunk ->
            personDao.insertAll(chunk)
        }
        com.example.util.SavedRecipientLookupHelper.invalidateCache()
        com.example.util.AppActivityTracker.logAction(
            actionType = "PERSONS_IMPORTED",
            title = "Destinatários Importados em Lote",
            details = "${persons.size} novos destinatários adicionados à base.",
            category = "Cadastros",
            incrementPerson = true
        )
    }

    override suspend fun updatePerson(person: Person) {
        personDao.updatePerson(person)
        com.example.util.SavedRecipientLookupHelper.invalidateCache()
        com.example.util.AppActivityTracker.logAction(
            actionType = "PERSON_UPDATED",
            title = "Destinatário Atualizado",
            details = "Nome: ${person.nome}\nEndereço: ${person.endereco}, ${person.numero}",
            category = "Cadastros",
            incrementPerson = true
        )
    }

    override suspend fun deletePerson(person: Person) {
        personDao.deletePerson(person)
        com.example.util.SavedRecipientLookupHelper.invalidateCache()
        com.example.util.AppActivityTracker.logAction(
            actionType = "PERSON_DELETED",
            title = "Destinatário Removido",
            details = "Nome: ${person.nome} (Endereço: ${person.endereco})",
            category = "Cadastros"
        )
    }

    override suspend fun deletePersonById(id: Long) {
        val person = personDao.getPersonById(id)
        personDao.deletePersonById(id)
        if (person != null) {
            com.example.util.AppActivityTracker.logAction(
                actionType = "PERSON_DELETED",
                title = "Destinatário Removido",
                details = "Nome: ${person.nome} (Endereço: ${person.endereco})",
                category = "Cadastros"
            )
        }
    }

    override suspend fun countPersons(): Int = personDao.countPersons()

    override suspend fun markPersonUsed(id: Long) {
        val person = personDao.getPersonById(id) ?: return
        personDao.updatePerson(person.copy(dataAtualizacao = System.currentTimeMillis()))
    }

    override suspend fun prioritizeRecebedor(personId: Long, recebedorId: String, recebedorNome: String?): Person? = withContext(Dispatchers.IO) {
        val person = personDao.getPersonById(personId) ?: return@withContext null
        val now = System.currentTimeMillis()

        val isAlreadyMain = (recebedorId.isBlank() ||
                recebedorId == "main" ||
                recebedorId == "p_${person.id}_main" ||
                recebedorId.endsWith("_main")) &&
                (recebedorNome.isNullOrBlank() || person.nome.trim().equals(recebedorNome.trim(), ignoreCase = true))

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

        var targetIdx = extras.indexOfFirst {
            it.id == cleanRecId ||
            it.id == recebedorId ||
            "p_${person.id}_co_${it.id}" == recebedorId ||
            (it.id.startsWith("co_") && it.id.removePrefix("co_") == cleanRecId)
        }

        if (targetIdx < 0 && !recebedorNome.isNullOrBlank()) {
            val normTarget = com.example.util.AddressNormalizer.normalize(recebedorNome.trim())
            targetIdx = extras.indexOfFirst {
                com.example.util.AddressNormalizer.normalize(it.nome.trim()) == normTarget
            }
        }

        if (targetIdx < 0) {
            val byNameIdx = extras.indexOfFirst {
                it.nome.isNotBlank() && (
                    recebedorId.contains(it.nome, ignoreCase = true) ||
                    (!recebedorNome.isNullOrBlank() && it.nome.contains(recebedorNome, ignoreCase = true))
                )
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
        // IMPORTANTE: Só consolida se houver número de imóvel válido e preenchido!
        // Sem número de imóvel, NUNCA agrupar pois podem ser residências diferentes na mesma rua.
        val clusters = allPersons.filter { p ->
            val parsed = AddressNormalizer.parseAddressComponents(p.endereco, p.numero, p.complemento, p.bairro)
            val numDigits = parsed.number.filter { it.isDigit() }
            val numKey = if (numDigits.isNotBlank()) numDigits else AddressNormalizer.normalize(parsed.number)
            numKey.isNotBlank() && numKey != "sn" && numKey != "s/n" && parsed.street.isNotBlank()
        }.groupBy { getCanonicalAddressKey(it) }.values

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

    override suspend fun moveOrCopyReceiver(
        sourcePerson: Person?,
        receiver: Recebedor,
        isMove: Boolean,
        targetAddress: String,
        targetNumber: String,
        targetComplement: String,
        targetBairro: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanStreet = AddressNormalizer.capitalizeWords(targetAddress.trim())
        val cleanNum = targetNumber.trim()
        val cleanComp = targetComplement.trim()
        val cleanBairro = AddressNormalizer.capitalizeWords(targetBairro.trim())

        if (cleanStreet.isBlank()) return@withContext false

        val freshSource = if (sourcePerson != null && sourcePerson.id > 0) {
            personDao.getPersonById(sourcePerson.id) ?: sourcePerson
        } else sourcePerson

        // Coleta todos os moradores do cadastro de origem
        val allSourceReceivers = mutableListOf<Recebedor>()
        if (freshSource != null) {
            if (freshSource.nome.isNotBlank() || freshSource.documento.isNotBlank()) {
                allSourceReceivers.add(
                    Recebedor(
                        id = "main",
                        nome = freshSource.nome,
                        documento = freshSource.documento,
                        assinatura = freshSource.assinatura
                    )
                )
            }
            if (freshSource.coRecebedoresJson.isNotBlank()) {
                try {
                    allSourceReceivers.addAll(Recebedor.listFromJson(freshSource.coRecebedoresJson))
                } catch (_: Throwable) {}
            }
        }

        // CASO 1: É uma mudança de complemento/endereço (isMove=true) e o cadastro de origem possui APENAS este morador
        // Nesse caso, atualizamos diretamente o complemento e dados da pessoa existente
        if (isMove && freshSource != null && allSourceReceivers.size <= 1) {
            val updated = freshSource.copy(
                endereco = cleanStreet,
                numero = cleanNum,
                complemento = cleanComp,
                bairro = cleanBairro.ifBlank { freshSource.bairro },
                dataAtualizacao = System.currentTimeMillis()
            )
            personDao.updatePerson(updated)
            return@withContext true
        }

        // CASO 2: O cadastro de origem possui múltiplos moradores e estamos movendo ou copiando um morador específico
        val searchKey = if (cleanNum.isNotBlank()) "$cleanStreet, $cleanNum" else cleanStreet
        val existingList = findPersonsByAddress(searchKey)

        // Busca se já existe OUTRA residência/unidade com esse mesmo complemento exato
        val targetPerson = existingList.firstOrNull { candidate ->
            candidate.id != freshSource?.id &&
            AddressNormalizer.areStreetNamesMatching(candidate.endereco, cleanStreet) &&
            AddressNormalizer.areNumbersMatching(candidate.numero, cleanNum) &&
            (if (cleanComp.isNotBlank()) {
                candidate.complemento.trim().equals(cleanComp, ignoreCase = true) ||
                AddressNormalizer.normalize(candidate.complemento) == AddressNormalizer.normalize(cleanComp)
            } else {
                candidate.complemento.isBlank()
            })
        }

        val newRec = receiver.copy(
            id = "rec_${System.currentTimeMillis()}",
            nome = AddressNormalizer.capitalizeWords(receiver.nome.trim()),
            documento = receiver.documento.trim()
            // Assinatura totalmente preservada
        )

        if (targetPerson != null) {
            if (targetPerson.nome.isBlank()) {
                val updatedTarget = targetPerson.copy(
                    nome = newRec.nome,
                    documento = newRec.documento,
                    assinatura = newRec.assinatura,
                    dataAtualizacao = System.currentTimeMillis()
                )
                personDao.updatePerson(updatedTarget)
            } else {
                val existingExtras = Recebedor.listFromJson(targetPerson.coRecebedoresJson).toMutableList()
                existingExtras.add(newRec)
                val updatedTarget = targetPerson.copy(
                    coRecebedoresJson = Recebedor.listToJson(existingExtras.distinctBy { "${it.nome.trim().lowercase()}_${it.documento.trim()}" }),
                    dataAtualizacao = System.currentTimeMillis()
                )
                personDao.updatePerson(updatedTarget)
            }
        } else {
            // Cria um novo registro de residência para o novo complemento
            val newPerson = Person(
                nome = newRec.nome,
                documento = newRec.documento,
                endereco = cleanStreet,
                numero = cleanNum,
                complemento = cleanComp,
                bairro = cleanBairro,
                assinatura = newRec.assinatura,
                dataAtualizacao = System.currentTimeMillis()
            )
            personDao.insertPerson(newPerson)
        }

        // Se foi mover, remove do cadastro de origem onde existiam outros moradores
        if (isMove && freshSource != null && freshSource.id > 0) {
            val matchIndex = allSourceReceivers.indexOfFirst {
                it.id == receiver.id || (it.nome.trim().equals(receiver.nome.trim(), ignoreCase = true) && it.documento.trim() == receiver.documento.trim())
            }
            if (matchIndex != -1) {
                removeReceiverFromPerson(freshSource, matchIndex)
            }
        }

        true
    }

    override suspend fun removeReceiverFromPerson(person: Person, receiverIndex: Int): Unit = withContext(Dispatchers.IO) {
        val freshPerson = (if (person.id > 0) personDao.getPersonById(person.id) else null) ?: person
        val allRecebedores = mutableListOf<Recebedor>()
        if (freshPerson.nome.isNotBlank() || freshPerson.documento.isNotBlank()) {
            allRecebedores.add(
                Recebedor(
                    id = "main",
                    nome = freshPerson.nome,
                    documento = freshPerson.documento,
                    assinatura = freshPerson.assinatura
                )
            )
        }
        if (freshPerson.coRecebedoresJson.isNotBlank()) {
            try {
                allRecebedores.addAll(Recebedor.listFromJson(freshPerson.coRecebedoresJson))
            } catch (_: Throwable) {}
        }

        if (receiverIndex in allRecebedores.indices) {
            val updatedList = allRecebedores.filterIndexed { i, _ -> i != receiverIndex }
            if (updatedList.isEmpty()) {
                personDao.deletePerson(freshPerson)
            } else {
                val newPrimary = updatedList.first()
                val newExtras = updatedList.drop(1)
                val updatedPerson = freshPerson.copy(
                    nome = newPrimary.nome,
                    documento = newPrimary.documento,
                    assinatura = newPrimary.assinatura,
                    coRecebedoresJson = Recebedor.listToJson(newExtras),
                    dataAtualizacao = System.currentTimeMillis()
                )
                personDao.updatePerson(updatedPerson)
            }
        }
    }

    override suspend fun deleteReceiverFromAddress(personId: Long, receiver: Recebedor): Boolean = withContext(Dispatchers.IO) {
        val freshPerson = (if (personId > 0) personDao.getPersonById(personId) else null)
            ?: personDao.getAllPersonsDirect().firstOrNull { it.id == personId }
            ?: return@withContext false

        val allReceivers = mutableListOf<Recebedor>()
        if (freshPerson.nome.isNotBlank() || freshPerson.documento.isNotBlank()) {
            allReceivers.add(
                Recebedor(
                    id = "main",
                    nome = freshPerson.nome,
                    documento = freshPerson.documento,
                    assinatura = freshPerson.assinatura
                )
            )
        }
        if (freshPerson.coRecebedoresJson.isNotBlank()) {
            try {
                allReceivers.addAll(Recebedor.listFromJson(freshPerson.coRecebedoresJson))
            } catch (_: Throwable) {}
        }

        val cleanId = receiver.id
            .removePrefix("p_${freshPerson.id}_co_")
            .removePrefix("p_${freshPerson.id}_main")

        val matchIndex = allReceivers.indexOfFirst {
            it.id == receiver.id ||
            it.id == cleanId ||
            (it.nome.trim().equals(receiver.nome.trim(), ignoreCase = true) &&
             (receiver.documento.isBlank() || it.documento.trim() == receiver.documento.trim()))
        }

        if (matchIndex != -1) {
            removeReceiverFromPerson(freshPerson, matchIndex)
            try {
                consolidateDuplicateAddressPersons()
            } catch (_: Throwable) {}
            true
        } else {
            if (allReceivers.size == 1 && allReceivers.first().nome.trim().equals(receiver.nome.trim(), ignoreCase = true)) {
                removeReceiverFromPerson(freshPerson, 0)
                try {
                    consolidateDuplicateAddressPersons()
                } catch (_: Throwable) {}
                true
            } else {
                false
            }
        }
    }
}
