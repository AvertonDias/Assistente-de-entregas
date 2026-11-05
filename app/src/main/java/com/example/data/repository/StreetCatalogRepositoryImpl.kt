package com.example.data.repository

import com.example.data.local.MonteSantoStreetData
import com.example.data.local.RegionalStreetData
import com.example.data.local.dao.PersonDao
import com.example.data.local.dao.StreetCatalogDao
import com.example.data.local.entity.StreetCatalog
import com.example.util.AddressNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreetCatalogRepositoryImpl(
    private val streetCatalogDao: StreetCatalogDao,
    private val personDao: PersonDao,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) : StreetCatalogRepository {

    init {
        scope.launch(Dispatchers.IO) {
            ensureDefaultStreetsLoaded()
        }
    }

    override fun searchStreets(query: String, city: String, limit: Int): Flow<List<StreetCatalog>> {
        val trimmedQuery = query.trim()
        val trimmedCity = city.trim()
        return streetCatalogDao.searchStreets(trimmedQuery, trimmedCity, limit)
    }

    override suspend fun searchStreetsSync(query: String, city: String, limit: Int): List<StreetCatalog> {
        val trimmedQuery = query.trim()
        val trimmedCity = city.trim()
        if (trimmedQuery.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            val allInCity = if (trimmedCity.isNotBlank()) {
                streetCatalogDao.getAllStreetsByCitySync(trimmedCity)
            } else {
                streetCatalogDao.getAllStreetsSync()
            }

            val normQuery = AddressNormalizer.normalize(trimmedQuery)
            val querySigWords = AddressNormalizer.extractStreetSignificantWords(trimmedQuery)

            allInCity.filter { street ->
                val normStreet = AddressNormalizer.normalize(street.nomeCompleto)
                val normBairro = AddressNormalizer.normalize(street.bairro)

                if (normStreet.contains(normQuery) || normBairro.contains(normQuery)) {
                    return@filter true
                }

                if (querySigWords.isNotEmpty()) {
                    val streetWords = AddressNormalizer.extractStreetSignificantWords(street.nomeCompleto)
                    if (querySigWords.all { qWord -> streetWords.any { sWord -> sWord.contains(qWord) || AddressNormalizer.isSimilarWord(qWord, sWord) } }) {
                        return@filter true
                    }
                }

                street.nome.contains(trimmedQuery, ignoreCase = true) ||
                street.bairro.contains(trimmedQuery, ignoreCase = true)
            }.take(limit)
        }
    }

    override fun getStreetsByCity(city: String): Flow<List<StreetCatalog>> {
        return streetCatalogDao.getStreetsByCity(city.trim())
    }

    override fun getAvailableCities(): Flow<List<String>> {
        return streetCatalogDao.getAvailableCities().map { dbCities ->
            val set = linkedSetOf("Monte Santo de Minas", "Itamogi", "Arceburgo", "Mococa")
            set.addAll(dbCities)
            set.toList()
        }
    }

    override suspend fun getSelectedCity(): String {
        return withContext(Dispatchers.IO) {
            settingsRepository.getSelectedCity()
        }
    }

    override suspend fun setSelectedCity(city: String) {
        val clean = city.trim().split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                if (word.lowercase() in listOf("de", "do", "da", "dos", "das", "e")) word.lowercase()
                else word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        withContext(Dispatchers.IO) {
            settingsRepository.setSelectedCity(clean)
            if (clean.equals(RegionalStreetData.ITAMOGI_CITY, ignoreCase = true) &&
                streetCatalogDao.countStreetsByCity(clean) == 0) {
                streetCatalogDao.insertAll(RegionalStreetData.getItamogiStreets())
            } else if (clean.equals(RegionalStreetData.ARCEBURGO_CITY, ignoreCase = true) &&
                streetCatalogDao.countStreetsByCity(clean) == 0) {
                streetCatalogDao.insertAll(RegionalStreetData.getArceburgoStreets())
            } else if (clean.equals(RegionalStreetData.MOCOCA_CITY, ignoreCase = true) &&
                streetCatalogDao.countStreetsByCity(clean) == 0) {
                streetCatalogDao.insertAll(RegionalStreetData.getMococaStreets())
            }
        }
    }

    override suspend fun addStreet(street: StreetCatalog): Long {
        return withContext(Dispatchers.IO) {
            streetCatalogDao.insert(street)
        }
    }

    override suspend fun deleteStreet(street: StreetCatalog) {
        withContext(Dispatchers.IO) {
            streetCatalogDao.delete(street)
        }
    }

    override suspend fun ensureDefaultStreetsLoaded() {
        withContext(Dispatchers.IO) {
            if (streetCatalogDao.countStreetsByCity(MonteSantoStreetData.DEFAULT_CITY) == 0) {
                streetCatalogDao.insertAll(MonteSantoStreetData.getInitialStreets())
            }
            if (streetCatalogDao.countStreetsByCity(RegionalStreetData.ITAMOGI_CITY) == 0) {
                streetCatalogDao.insertAll(RegionalStreetData.getItamogiStreets())
            }
            if (streetCatalogDao.countStreetsByCity(RegionalStreetData.ARCEBURGO_CITY) == 0) {
                streetCatalogDao.insertAll(RegionalStreetData.getArceburgoStreets())
            }
            if (streetCatalogDao.countStreetsByCity(RegionalStreetData.MOCOCA_CITY) == 0) {
                streetCatalogDao.insertAll(RegionalStreetData.getMococaStreets())
            }
        }
    }

    override suspend fun importStreets(city: String, uf: String, streets: List<StreetCatalog>): Int {
        return withContext(Dispatchers.IO) {
            val cleanList = streets.map { 
                it.copy(
                    cidade = if (it.cidade.isBlank()) city else it.cidade,
                    uf = if (it.uf.isBlank()) uf else it.uf
                )
            }
            streetCatalogDao.insertAll(cleanList)
            cleanList.size
        }
    }
}
