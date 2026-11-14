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
        val nameLower: String,
        val nameWords: List<String>,
        val compWords: List<String>,
        val bairroWords: List<String>
    )

    private fun indexPerson(p: Person): IndexedPerson {
        val parsed = AddressNormalizer.parseAddressComponents(p.endereco, p.numero, p.complemento, p.bairro)
        val effectiveNumber = if (p.numero.isNotBlank()) p.numero else parsed.number
        val numDigits = effectiveNumber.filter { it.isDigit() }
        val effectiveStreet = if (parsed.street.isNotBlank()) parsed.street else p.endereco
        val effectiveComplement = if (p.complemento.isNotBlank()) p.complemento else parsed.complement

        val pNormStreet = AddressNormalizer.normalize(p.endereco)
        val pStrippedStreet = AddressNormalizer.normalize(AddressNormalizer.stripStreetType(p.endereco))
        val pNormNumber = AddressNormalizer.normalize(effectiveNumber)
        val pCleanNumber = AddressNormalizer.normalizeNumber(effectiveNumber)
        val pDigitsNumber = effectiveNumber.filter { it.isDigit() }
        val pRawNumber = effectiveNumber.trim().replace(".", "")
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

        val fullWords = fullSearchable.split(" ").filter { it.isNotBlank() }.distinct()
        val sigWords = AddressNormalizer.extractStreetSignificantWords(effectiveStreet)
        val nameWords = buildList {
            pNormName.split(" ").filterTo(this) { it.isNotBlank() }
            for (co in pCoRecNames) {
                co.split(" ").filterTo(this) { it.isNotBlank() }
            }
        }.distinct()
        val compWords = pNormComplement.split(" ").filter { it.isNotBlank() }.distinct()
        val bairroWords = buildList {
            pNormBairro.split(" ").filterTo(this) { it.isNotBlank() }
            pNormCidade.split(" ").filterTo(this) { it.isNotBlank() }
        }.distinct()

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
            nameLower = p.nome.lowercase(),
            nameWords = nameWords,
            compWords = compWords,
            bairroWords = bairroWords
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
            for (w in item.fullWords) {
                if (w.length >= 2 && w !in item.sigWords) {
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
                val useIdx = indexedList.isNotEmpty() && wordIndexMap.isNotEmpty()
                filterIndexedPersonsByQuery(listToUse, trimmed, useIndices = useIdx)
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun filterIndexedPersonsByQuery(
        list: List<IndexedPerson>,
        query: String,
        useIndices: Boolean = false
    ): List<Person> {
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

        val candidatesToScore: List<IndexedPerson> = if (useIndices) {
            val candidateSet = LinkedHashSet<IndexedPerson>()
            val wordMapSnapshot = wordIndexMap
            val numMapSnapshot = numberIndexMap

            if (queryHasStreetAndNumber) {
                for (qNum in cleanQueryNumbers) {
                    numMapSnapshot[qNum]?.let { candidateSet.addAll(it) }
                }
                val streetWords = AddressNormalizer.extractStreetSignificantWords(parsedQueryAddr.street)
                for (w in streetWords) {
                    if (w.length >= 2) {
                        wordMapSnapshot[w]?.let { candidateSet.addAll(it) }
                    }
                }
                if (candidateSet.isEmpty()) {
                    for ((key, bucket) in wordMapSnapshot) {
                        if (streetWords.any { w -> w.length >= 2 && (key.startsWith(w) || (w.length >= 3 && AddressNormalizer.isSimilarWord(w, key))) }) {
                            candidateSet.addAll(bucket)
                        }
                    }
                }
                candidateSet.toList()
            } else if (isNumberOnlySearch && primaryQueryNumber.isNotBlank()) {
                numMapSnapshot[primaryQueryNumber]?.let { candidateSet.addAll(it) }
                for ((k, bucket) in numMapSnapshot) {
                    if (k.contains(primaryQueryNumber)) {
                        candidateSet.addAll(bucket)
                    }
                }
                for ((k, bucket) in wordMapSnapshot) {
                    if (k.contains(primaryQueryNumber)) {
                        candidateSet.addAll(bucket)
                    }
                }
                candidateSet.toList()
            } else if (coreTokens.all { it.length >= 2 }) {
                for (token in coreTokens) {
                    wordMapSnapshot[token]?.let { candidateSet.addAll(it) }
                    for ((key, bucket) in wordMapSnapshot) {
                        if (key.contains(token) || (token.length >= 3 && AddressNormalizer.isSimilarWord(token, key))) {
                            candidateSet.addAll(bucket)
                        }
                    }
                    if (token.any { it.isDigit() }) {
                        for ((k, bucket) in numMapSnapshot) {
                            if (k.contains(token)) {
                                candidateSet.addAll(bucket)
                            }
                        }
                    }
                }
                if (candidateSet.isEmpty() && coreTokens.size == 1 && coreTokens[0].length >= 6) {
                    list
                } else {
                    candidateSet.toList()
                }
            } else {
                list
            }
        } else {
            list
        }

        data class ScoredIndexedPerson(val item: IndexedPerson, val score: Int)

        val scoredList = ArrayList<ScoredIndexedPerson>(candidatesToScore.size.coerceAtMost(256))

        for (item in candidatesToScore) {
            fun matchesToken(token: String): Boolean {
                if (item.fullSearchable.contains(token)) return true
                if (token.all { it.isDigit() }) {
                    if (item.pCleanNumber == token || item.pDigitsNumber == token || item.pRawNumber.equals(token, ignoreCase = true) ||
                        item.pDigitsDoc.contains(token) || item.pNormComplement.contains(token) || item.pCoRecDocs.any { it.contains(token) }) {
                        return true
                    }
                }
                if (item.fullWords.any { it.startsWith(token) }) return true
                if (token.length >= 3 && item.fullWords.any { it.length >= 2 && AddressNormalizer.isSimilarWord(token, it) }) {
                    return true
                }
                return false
            }

            var score = 0

            if (queryHasStreetAndNumber) {
                // Busca de endereço (Rua + Número): aceita correspondência inteligente de rua (ex: "Vital" ou ordem trocada) + número correspondente
                val streetScore = AddressNormalizer.scoreStreetNamesMatch(parsedQueryAddr.street, item.person.endereco)
                val numberMatches = cleanQueryNumbers.any { qNum ->
                    item.pCleanNumber == qNum || item.pDigitsNumber == qNum || item.pRawNumber.equals(qNum, ignoreCase = true) ||
                    AddressNormalizer.areNumbersMatching(qNum, item.person.numero)
                }

                if (streetScore > 0 && numberMatches) {
                    score = 1500 + (streetScore * 5)
                    if (parsedQueryAddr.complement.isNotBlank() && item.pNormComplement.contains(AddressNormalizer.normalize(parsedQueryAddr.complement))) {
                        score += 200
                    }
                } else if (streetScore > 0 && cleanQueryNumbers.isEmpty()) {
                    score = 400 + (streetScore * 4)
                } else {
                    // Verifica se o número digitado faz parte do próprio nome da rua (ex: "Novembro 15" -> "Rua 15 de Novembro")
                    val fullStreetScore = AddressNormalizer.scoreStreetNamesMatch(normQuery, item.person.endereco)
                    if (fullStreetScore > 0 && !trimmed.contains(",") && cleanQueryNumbers.all { qNum -> item.pNormStreet.contains(qNum) }) {
                        score = 600 + (fullStreetScore * 3)
                    } else {
                        // Rua diferente! Rejeita imediatamente (score = 0) para NUNCA puxar pessoas de outra rua com mesmo número!
                        score = 0
                    }
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
                // Caso B: Busca com palavras (ex: "Vital", "Antônio Francisco da Luz", "Carlos")
                val allCoreTokensMatch = coreTokens.all { matchesToken(it) }
                val streetScore = AddressNormalizer.scoreStreetNamesMatch(normQuery, item.person.endereco)

                if (allCoreTokensMatch) {
                    score = 200

                    // Bônus se tiver número e casar perfeitamente com o número da casa da mesma rua
                    if (cleanQueryNumbers.isNotEmpty()) {
                        val hasExactNumber = cleanQueryNumbers.any { qNum ->
                            item.pCleanNumber == qNum || item.pDigitsNumber == qNum || item.pRawNumber.equals(qNum, ignoreCase = true)
                        }
                        if (hasExactNumber && streetScore > 0) {
                            score += 800 // Bônus: bateu a rua e o número exato!
                        } else if (hasExactNumber) {
                            score += 100
                        }
                    }

                    // Bônus se a rua bate com a busca (exata, parcial ou em ordem trocada)
                    if (item.pNormStreet.contains(normQuery) || item.pStrippedStreet.contains(normQuery) || normQuery.contains(item.pStrippedStreet)) {
                        score += 350
                    } else if (streetScore > 0) {
                        score += streetScore * 3
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

                // Verificação adicional de endereço inteligente via AddressNormalizer (apenas quando streetScore > 0 para evitar reprocessamento em massa)
                if (score == 0 && streetScore > 0) {
                    if (cleanQueryNumbers.isNotEmpty() && AddressNormalizer.matchesPrecise(trimmed, item.person.endereco, item.person.numero, item.person.complemento)) {
                        score = 700
                    } else if (cleanQueryNumbers.isEmpty()) {
                        score = 200 + (streetScore * 3)
                    }
                } else if (score == 0 && cleanQueryNumbers.isEmpty() && coreTokens.size == 1 && coreTokens[0].length >= 6 && AddressNormalizer.matches(trimmed, item.person.endereco)) {
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

            val candidateSet = LinkedHashSet<IndexedPerson>()
            if (numBucket != null) {
                candidateSet.addAll(numBucket)
            }
            for (w in sigWords) {
                if (w.length >= 2) {
                    wordIndexMap[w]?.let { candidateSet.addAll(it) }
                }
            }

            if (candidateSet.isNotEmpty()) {
                candidateSet.map { it.person }
            } else {
                // Fallback inteligente para variações ortográficas, prefixos ou palavras coladas
                cached.filter { item ->
                    val numHit = effectiveNumber.isNotBlank() && (
                        item.pCleanNumber == effectiveNumber ||
                        item.pDigitsNumber == effectiveNumber ||
                        item.pRawNumber == effectiveNumber ||
                        item.fullSearchable.contains(effectiveNumber)
                    )
                    numHit || AddressNormalizer.scoreStreetNamesMatch(queryStreet, item.person.endereco) > 0
                }.map { it.person }
            }
        } else {
            // Fallback para consulta SQL caso o cache ainda esteja na primeira inicialização
            val dbCandidateSet = LinkedHashMap<Long, Person>()
            personDao.findCandidatePersons(effectiveNumber, keyword).forEach { dbCandidateSet[it.id] = it }
            for (w in sigWords) {
                if (w != keyword && (w.length >= 3 || w.any { c -> c.isLetter() })) {
                    personDao.findCandidatePersons(effectiveNumber, w).forEach { dbCandidateSet[it.id] = it }
                }
            }
            if (dbCandidateSet.isEmpty() && normEffectiveNumber != effectiveNumber && normEffectiveNumber.isNotBlank()) {
                personDao.findCandidatePersons(normEffectiveNumber, keyword).forEach { dbCandidateSet[it.id] = it }
            }
            if (dbCandidateSet.isEmpty() && keyword.isNotBlank()) {
                for (w in sigWords) {
                    if (w.length >= 2) {
                        personDao.findCandidatePersons("", w).forEach { dbCandidateSet[it.id] = it }
                    }
                }
            }
            dbCandidateSet.values.toList()
        }
        if (candidates.isEmpty()) return@withContext emptyList()

        // Match inteligente de endereço: rua (completa, parcial ou reordenada) E número (quando presente)
        val hasQueryComplement = parsedRaw.complement.isNotBlank()
        val scoredMatches = candidates.mapNotNull { p ->
            val streetScore = AddressNormalizer.scoreStreetNamesMatch(queryStreet, p.endereco)
            if (streetScore <= 0) return@mapNotNull null

            val numMatches = if (hasSpecificNumber && effectiveNumber.isNotBlank()) {
                val pNum = p.numero.trim()
                val parsedP = AddressNormalizer.parseAddressComponents(p.endereco, p.numero, p.complemento)
                val targetNum = pNum.ifBlank { parsedP.number }
                targetNum.isNotBlank() && AddressNormalizer.areNumbersMatching(effectiveNumber, targetNum)
            } else true
            if (!numMatches) return@mapNotNull null

            val isPreciseMatch =
                AddressNormalizer.matchesPrecise(rawAddress, p.endereco, p.numero, p.complemento) ||
                AddressNormalizer.matchesPrecise(rawAddress, "${p.endereco}, ${p.numero}", p.numero, p.complemento) ||
                AddressNormalizer.matchesPrecise(rawAddress, "${p.endereco}, ${p.numero} ${p.complemento} ${p.bairro} ${p.cidade}", p.numero, p.complemento) ||
                (!hasQueryComplement && parsedRaw.street.isNotBlank() && parsedRaw.number.isNotBlank() && AddressNormalizer.matchesPrecise("${parsedRaw.street}, ${parsedRaw.number}", p.endereco, p.numero, p.complemento))

            if (isPreciseMatch) Pair(p, streetScore) else null
        }.distinctBy { it.first.id }

        if (scoredMatches.isNotEmpty()) {
            scoredMatches.sortedWith(
                compareByDescending<Pair<Person, Int>> { it.second }
                    .thenBy { (indexedPersonCache[it.first.id] ?: indexPerson(it.first)).streetSortKey }
                    .thenBy { (indexedPersonCache[it.first.id] ?: indexPerson(it.first)).numValue }
                    .thenBy { (indexedPersonCache[it.first.id] ?: indexPerson(it.first)).unitKey }
                    .thenByDescending { it.first.dataAtualizacao }
            ).map { it.first }
        } else {
            emptyList()
        }
    }

    private fun isSubsequenceMatch(shortStr: String, longStr: String): Boolean {
        if (shortStr.length < 3 || longStr.length < shortStr.length) return false
        if (shortStr[0] != longStr[0]) return false
        if (longStr.length > shortStr.length * 2 + 2) return false
        var i = 0
        var j = 0
        while (i < shortStr.length && j < longStr.length) {
            if (shortStr[i] == longStr[j]) i++
            j++
        }
        return i == shortStr.length
    }

    private fun scoreTokenAgainstWords(token: String, words: List<String>, fullField: String): Int {
        if (token.isBlank()) return 0
        var best = 0
        val tokenLen = token.length
        val isNumeric = token.all { it.isDigit() }
        val maxDist = when {
            isNumeric -> 0
            tokenLen <= 2 -> 0
            tokenLen <= 5 -> 1
            tokenLen <= 9 -> 2
            else -> 3
        }
        for (w in words) {
            if (w == token) return 100
            if (tokenLen >= 2 && w.startsWith(token)) {
                val prefixScore = if (w.length == tokenLen) 100 else 86 + (tokenLen * 10 / w.length).coerceAtMost(10)
                if (prefixScore > best) best = prefixScore
                continue
            }
            if (tokenLen >= 4 && w.length >= 3 && token.startsWith(w)) {
                if (80 > best) best = 80
                continue
            }
            if (!isNumeric && tokenLen >= 3 && w.contains(token)) {
                if (76 > best) best = 76
                continue
            }
            if (maxDist > 0 && kotlin.math.abs(w.length - tokenLen) <= maxDist) {
                val dist = AddressNormalizer.levenshteinDistance(token, w)
                if (dist <= maxDist) {
                    val maxL = maxOf(tokenLen, w.length)
                    val fuzzyScore = (42 + ((maxL - dist).toFloat() / maxL * 32f)).toInt().coerceIn(42, 74)
                    if (fuzzyScore > best) best = fuzzyScore
                }
            }
            if (!isNumeric && best < 55 && isSubsequenceMatch(token, w)) {
                best = 55
            }
        }
        if (best < 72 && tokenLen >= 3 && fullField.contains(token)) {
            best = 72
        }
        return best
    }

    override suspend fun fuzzySearchForBubble(
        query: String,
        maxResults: Int
    ): BubbleFuzzySearchResult = withContext(Dispatchers.Default) {
        val safeLimit = maxResults.coerceIn(1, 200)
        val trimmed = query.trim()
        val cachedList = cachedIndexedPersons.value
        val list = if (cachedList.isNotEmpty()) {
            cachedList
        } else {
            try {
                personDao.getAllPersonsDirect().map { indexPerson(it) }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        if (list.isEmpty()) {
            return@withContext BubbleFuzzySearchResult(emptyList(), 0, false)
        }
        if (trimmed.isBlank()) {
            val limited = list.take(safeLimit).map { it.person }
            return@withContext BubbleFuzzySearchResult(limited, list.size, list.size > safeLimit)
        }

        val normQuery = AddressNormalizer.normalize(trimmed)
        if (normQuery.isBlank()) {
            return@withContext BubbleFuzzySearchResult(emptyList(), 0, false)
        }

        val digitsQuery = trimmed.filter { it.isDigit() }
        val queryTokens = normQuery.split(" ").filter { it.isNotBlank() }
        val commonPrefixes = setOf(
            "RUA", "R", "AV", "AVENIDA", "TRAVESSA", "TRAV", "TV", "ALAMEDA", "AL",
            "PRACA", "PCA", "PR", "RODOVIA", "ROD", "ESTRADA", "EST",
            "DR", "DRA", "DOUTOR", "DOUTORA", "CEL", "CORONEL", "PROF", "PROFESSOR",
            "STA", "SANTA", "STO", "SANTO",
            "DE", "DA", "DO", "DOS", "DAS", "E", "EM", "NO", "NA", "NOS", "NAS", "AO", "AOS",
            "NUMERO", "NUM", "N", "NO", "CASA", "CS", "AP", "APT", "APTO", "BLOCO", "BL"
        )
        val coreTokens = if (queryTokens.size > 1) {
            queryTokens.filter { it !in commonPrefixes }.ifEmpty { queryTokens }
        } else {
            queryTokens
        }.distinct()

        val cleanQueryNumbers = AddressNormalizer.extractNumbers(trimmed)
            .map { AddressNormalizer.normalizeNumber(it) }
            .filter { it.isNotBlank() }
            .distinct()
        val parsedQueryAddr = AddressNormalizer.parseAddressComponents(trimmed)
        val strippedQueryStreet = AddressNormalizer.stripStreetType(parsedQueryAddr.street)
        val normStrippedQueryStreet = AddressNormalizer.normalize(strippedQueryStreet)
        val queryHasStreetAndNumber = parsedQueryAddr.street.isNotBlank() &&
            strippedQueryStreet.isNotBlank() &&
            !strippedQueryStreet.all { it.isDigit() || it == '-' || it == ',' || it == '.' } &&
            cleanQueryNumbers.isNotEmpty()
        val isNumberOnlySearch = queryTokens.all { token ->
            token.all { it.isDigit() } || token in setOf("N", "NUM", "NUMERO", "NO", "CASA", "CS", "AP", "APT", "APTO")
        } && cleanQueryNumbers.isNotEmpty()
        val primaryQueryNumber = cleanQueryNumbers.firstOrNull() ?: ""
        val streetAlphaTokens = coreTokens.filter { token -> !token.all { it.isDigit() } }

        // Seleção rápida de candidatos pelos índices invertidos (com fallback completo para erros de digitação)
        val wordMapSnapshot = wordIndexMap
        val numMapSnapshot = numberIndexMap
        val candidates: List<IndexedPerson> = if (list.size <= 450 || wordMapSnapshot.isEmpty()) {
            list
        } else {
            val candidateSet = LinkedHashSet<IndexedPerson>(256)
            for (qNum in cleanQueryNumbers) {
                numMapSnapshot[qNum]?.let { candidateSet.addAll(it) }
            }
            for (token in coreTokens) {
                if (token.length >= 2) {
                    wordMapSnapshot[token]?.let { candidateSet.addAll(it) }
                }
                val tokenLen = token.length
                val isNum = token.all { it.isDigit() }
                val maxDist = when {
                    isNum -> 0
                    tokenLen <= 2 -> 0
                    tokenLen <= 5 -> 1
                    tokenLen <= 9 -> 2
                    else -> 3
                }
                for ((key, bucket) in wordMapSnapshot) {
                    if (key.startsWith(token) || (tokenLen >= 3 && key.contains(token))) {
                        candidateSet.addAll(bucket)
                    } else if (maxDist > 0 && kotlin.math.abs(key.length - tokenLen) <= maxDist) {
                        val sameStartOrEnd = key[0] == token[0] || key.last() == token.last()
                        if (sameStartOrEnd && AddressNormalizer.levenshteinDistance(token, key) <= maxDist) {
                            candidateSet.addAll(bucket)
                        }
                    }
                }
                if (isNum) {
                    for ((k, bucket) in numMapSnapshot) {
                        if (k.contains(token)) candidateSet.addAll(bucket)
                    }
                }
            }
            if (candidateSet.size < 15) list else candidateSet.toList()
        }

        data class FuzzyCandidate(val item: IndexedPerson, val score: Int, val matchedCount: Int)

        val scored = ArrayList<FuzzyCandidate>(candidates.size.coerceAtMost(256))
        val normQueryComplement = AddressNormalizer.normalize(parsedQueryAddr.complement)

        for (item in candidates) {
            var totalScore = 0
            var matchedTokens = 0
            var streetMatchedTokens = 0
            var nameMatchedTokens = 0
            var hasNumberMatch = false
            var exactNumberMatch = false

            // 1. Correspondência de Número da residência
            if (cleanQueryNumbers.isNotEmpty()) {
                for (qNum in cleanQueryNumbers) {
                    if (item.pCleanNumber == qNum || item.pDigitsNumber == qNum || item.pRawNumber.equals(qNum, ignoreCase = true)) {
                        exactNumberMatch = true
                        hasNumberMatch = true
                        break
                    } else if (
                        item.pCleanNumber.startsWith(qNum) ||
                        item.pDigitsNumber.startsWith(qNum) ||
                        (qNum.length >= 2 && (item.pCleanNumber.contains(qNum) || item.pDigitsNumber.contains(qNum)))
                    ) {
                        hasNumberMatch = true
                    }
                }
            }

            if (isNumberOnlySearch && primaryQueryNumber.isNotBlank()) {
                when {
                    exactNumberMatch -> totalScore += 1200
                    hasNumberMatch && (item.pCleanNumber.startsWith(primaryQueryNumber) || item.pDigitsNumber.startsWith(primaryQueryNumber)) -> totalScore += 820
                    hasNumberMatch -> totalScore += 560
                    item.pNormComplement.contains(primaryQueryNumber) -> totalScore += 460
                    item.pNormStreet.contains(primaryQueryNumber) -> totalScore += 380
                    item.pDigitsDoc.contains(primaryQueryNumber) || item.pCoRecDocs.any { it.contains(primaryQueryNumber) } -> totalScore += 340
                }
            } else {
                // 2. Pontuação fuzzy por token em cada campo relevante
                for (token in coreTokens) {
                    val isDigitToken = token.all { it.isDigit() }
                    val nameS = scoreTokenAgainstWords(token, item.nameWords, item.pNormName)
                    val streetS = scoreTokenAgainstWords(token, item.sigWords.ifEmpty { item.pStrippedStreet.split(" ").filter { it.isNotBlank() } }, item.pNormStreet)
                    val compS = scoreTokenAgainstWords(token, item.compWords, item.pNormComplement)
                    val bairroS = scoreTokenAgainstWords(token, item.bairroWords, item.pNormBairro)
                    val docS = when {
                        item.pDigitsDoc == token || item.pCoRecDocs.any { it == token } -> 100
                        token.length >= 3 && (item.pDigitsDoc.contains(token) || item.pCoRecDocs.any { it.contains(token) } || item.pNormDoc.contains(token)) -> 90
                        else -> 0
                    }
                    val numS = if (isDigitToken) {
                        when {
                            item.pCleanNumber == token || item.pDigitsNumber == token || item.pRawNumber.equals(token, ignoreCase = true) -> 100
                            item.pCleanNumber.startsWith(token) || item.pDigitsNumber.startsWith(token) -> 80
                            token.length >= 2 && (item.pCleanNumber.contains(token) || item.pDigitsNumber.contains(token)) -> 60
                            else -> 0
                        }
                    } else 0

                    val weightedBest = maxOf(
                        (nameS * 1.35f).toInt(),
                        (streetS * 1.25f).toInt(),
                        (docS * 1.30f).toInt(),
                        (numS * 1.15f).toInt(),
                        (compS * 0.95f).toInt(),
                        (bairroS * 0.80f).toInt()
                    )

                    if (weightedBest > 0) {
                        totalScore += weightedBest
                        matchedTokens++
                        if (streetS >= 45) streetMatchedTokens++
                        if (nameS >= 45) nameMatchedTokens++
                    }
                }

                // Se query possui Rua + Número explícitos, evita retornar casas de outras ruas só pelo número
                if (queryHasStreetAndNumber && streetAlphaTokens.isNotEmpty() && streetMatchedTokens == 0 && nameMatchedTokens == 0) {
                    continue
                }

                // 3. Bônus por correspondência de frase completa / cobertura de tokens
                val coverageRatio = if (coreTokens.isNotEmpty()) matchedTokens.toFloat() / coreTokens.size else 0f
                if (coreTokens.size >= 2) {
                    if (matchedTokens == coreTokens.size) {
                        totalScore += 360
                    } else if (coverageRatio >= 0.6f) {
                        totalScore += (coverageRatio * 180f).toInt()
                    } else if (matchedTokens == 1 && coreTokens.size >= 3 && streetMatchedTokens == 0 && nameMatchedTokens == 0) {
                        // Evita ruído fraco em buscas longas quando apenas 1 token secundário coincide
                        totalScore = 0
                    }
                }

                // Bônus de frase no Nome ou Co-recebedor
                if (item.pNormName.isNotBlank()) {
                    when {
                        item.pNormName == normQuery -> totalScore += 900
                        item.pNormName.startsWith(normQuery) -> totalScore += 620
                        normQuery.length >= 3 && item.pNormName.contains(normQuery) -> totalScore += 450
                        normQuery.length >= 3 && item.pCoRecNames.any { it.contains(normQuery) } -> totalScore += 420
                    }
                }

                // Bônus de frase / proximidade na Rua
                if (item.pStrippedStreet.isNotBlank()) {
                    val streetTarget = if (normStrippedQueryStreet.isNotBlank()) normStrippedQueryStreet else normQuery
                    when {
                        item.pStrippedStreet == streetTarget || item.pNormStreet == normQuery -> totalScore += 820
                        streetTarget.length >= 3 && item.pStrippedStreet.startsWith(streetTarget) -> totalScore += 520
                        streetTarget.length >= 3 && item.pStrippedStreet.contains(streetTarget) -> totalScore += 380
                    }
                }

                // Bônus de documento numérico (CPF/RG)
                if (digitsQuery.length >= 3) {
                    if (item.pDigitsDoc == digitsQuery || item.pCoRecDocs.any { it == digitsQuery }) {
                        totalScore += 950
                    } else if (item.pDigitsDoc.contains(digitsQuery) || item.pCoRecDocs.any { it.contains(digitsQuery) }) {
                        totalScore += 600
                    }
                }

                // Bônus de Rua + Número casando juntos
                if (cleanQueryNumbers.isNotEmpty() && streetMatchedTokens > 0) {
                    if (exactNumberMatch) {
                        totalScore += 850
                    } else if (hasNumberMatch) {
                        totalScore += 320
                    } else if (queryHasStreetAndNumber) {
                        // Penaliza suavemente se a rua bateu mas o número é diferente
                        totalScore = (totalScore * 0.55f).toInt()
                    }
                }

                // Bônus de complemento
                if (normQueryComplement.isNotBlank() && item.pNormComplement.contains(normQueryComplement)) {
                    totalScore += 220
                }
            }

            // Threshold mínimo para eliminar falsos positivos insignificantes
            val minThreshold = when {
                isNumberOnlySearch -> 200
                coreTokens.size == 1 && coreTokens[0].length <= 2 -> 80
                coreTokens.size == 1 -> 55
                else -> 95
            }

            if (totalScore >= minThreshold) {
                if (item.person.documento.isNotBlank()) totalScore += 8
                if (item.person.assinatura.isNotBlank()) totalScore += 4
                scored.add(FuzzyCandidate(item, totalScore, matchedTokens))
            }
        }

        val sorted = scored.sortedWith(
            compareByDescending<FuzzyCandidate> { it.score }
                .thenByDescending { it.matchedCount }
                .thenByDescending { it.item.person.dataAtualizacao }
                .thenBy { it.item.streetSortKey }
                .thenBy { it.item.numValue }
                .thenBy { it.item.unitKey }
                .thenBy { it.item.nameLower }
        )

        val totalMatches = sorted.size
        val limited = if (totalMatches > safeLimit) sorted.take(safeLimit) else sorted
        BubbleFuzzySearchResult(
            items = limited.map { it.item.person },
            totalMatches = totalMatches,
            isLimited = totalMatches > safeLimit
        )
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
            com.example.util.SavedRecipientLookupHelper.invalidateCache()
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
                val mainMatches = targetPerson.nome.trim().equals(newRec.nome, ignoreCase = true) &&
                    targetPerson.documento.trim() == newRec.documento
                if (!mainMatches) {
                    val existingExtras = Recebedor.listFromJson(targetPerson.coRecebedoresJson).toMutableList()
                    existingExtras.add(newRec)
                    val updatedTarget = targetPerson.copy(
                        coRecebedoresJson = Recebedor.listToJson(existingExtras.distinctBy { "${it.nome.trim().lowercase()}_${it.documento.trim()}" }),
                        dataAtualizacao = System.currentTimeMillis()
                    )
                    personDao.updatePerson(updatedTarget)
                }
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
            val cleanTargetId = receiver.id
                .removePrefix("p_${freshSource.id}_co_")
                .removePrefix("p_${freshSource.id}_")
            val matchIndex = allSourceReceivers.indexOfFirst {
                it.id == receiver.id ||
                it.id == cleanTargetId ||
                (it.nome.trim().equals(receiver.nome.trim(), ignoreCase = true) && it.documento.trim() == receiver.documento.trim())
            }
            if (matchIndex != -1) {
                removeReceiverFromPerson(freshSource, matchIndex)
            }
        }

        com.example.util.SavedRecipientLookupHelper.invalidateCache()
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
