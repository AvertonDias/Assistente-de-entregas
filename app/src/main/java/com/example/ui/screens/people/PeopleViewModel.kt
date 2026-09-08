package com.example.ui.screens.people

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.Person
import com.example.data.model.Recebedor
import com.example.data.model.SignatureData
import com.example.data.repository.PersonRepository
import com.example.util.AddressNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.DeliveryApp
import com.example.data.firebase.FirebaseSyncRepository
import com.example.data.firebase.SyncState

class PeopleViewModel(
    private val personRepository: PersonRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _tempSignature = MutableStateFlow<SignatureData?>(null)
    val tempSignature: StateFlow<SignatureData?> = _tempSignature.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                personRepository.sanitizeCorruptedAddressRecords()
            } catch (e: Exception) {
                // Log and continue
            }
            try {
                personRepository.cleanupInactiveReceiversOlderThan5Years()
            } catch (e: Exception) {
                // Log and continue
            }
            try {
                personRepository.consolidateDuplicateAddressPersons()
            } catch (e: Exception) {
                // Log and continue
            }
        }
    }

    fun setTempSignature(signature: SignatureData?) {
        _tempSignature.value = signature
    }

    val persons: StateFlow<List<Person>> = _searchQuery
        .flatMapLatest { query: String ->
            personRepository.searchPersons(query)
                .catch { e: Throwable ->
                    Log.e("PeopleViewModel", "Erro ao carregar pessoas: ${e.message}", e)
                    emit(emptyList<Person>())
                }
        }
        .catch { e: Throwable ->
            Log.e("PeopleViewModel", "Erro crítico no fluxo de pessoas: ${e.message}", e)
            emit(emptyList<Person>())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch {
            personRepository.deletePerson(person)
        }
    }

    /**
     * Remove um recebedor específico dentro de um endereço.
     * Se o recebedor for o primeiro (principal), o próximo recebedor da lista assume como principal.
     * Se não sobrar nenhum recebedor, o cadastro do endereço é removido.
     */
    fun removeReceiverFromPerson(person: Person, receiverIndex: Int, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val allRecebedores = mutableListOf<Recebedor>()
            if (person.nome.isNotBlank() || person.documento.isNotBlank()) {
                allRecebedores.add(
                    Recebedor(
                        id = "main",
                        nome = person.nome,
                        documento = person.documento,
                        assinatura = person.assinatura
                    )
                )
            }
            if (person.coRecebedoresJson.isNotBlank()) {
                try {
                    allRecebedores.addAll(Recebedor.listFromJson(person.coRecebedoresJson))
                } catch (_: Throwable) {}
            }

            if (receiverIndex in allRecebedores.indices) {
                val updatedList = allRecebedores.filterIndexed { i, _ -> i != receiverIndex }
                if (updatedList.isEmpty()) {
                    personRepository.deletePerson(person)
                } else {
                    val newPrimary = updatedList.first()
                    val newExtras = updatedList.drop(1)
                    val updatedPerson = person.copy(
                        nome = newPrimary.nome,
                        documento = newPrimary.documento,
                        assinatura = newPrimary.assinatura,
                        coRecebedoresJson = Recebedor.listToJson(newExtras),
                        dataAtualizacao = System.currentTimeMillis()
                    )
                    personRepository.updatePerson(updatedPerson)
                }
            }
            onComplete?.invoke()
        }
    }

    suspend fun getPersonById(id: Long): Person? {
        return personRepository.getPersonByIdDirect(id)
    }

    fun savePerson(person: Person, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (person.id > 0) {
                personRepository.updatePerson(person.copy(dataAtualizacao = System.currentTimeMillis()))
            } else {
                val existingList = personRepository.findPersonsByAddress(person.endereco)
                if (existingList.isNotEmpty() && person.nome.isNotBlank() && existingList.first().nome.isNotBlank()) {
                    val basePerson = existingList.first()
                    val oldPrimary = Recebedor(
                        id = "co_${System.currentTimeMillis().toString().takeLast(6)}",
                        nome = basePerson.nome,
                        documento = basePerson.documento,
                        assinatura = basePerson.assinatura,
                        dataUso = basePerson.dataAtualizacao
                    )
                    val existingExtras = Recebedor.listFromJson(basePerson.coRecebedoresJson).toMutableList()
                    val newExtras = mutableListOf<Recebedor>()
                    if (oldPrimary.nome.isNotBlank()) {
                        newExtras.add(oldPrimary)
                    }
                    newExtras.addAll(existingExtras)
                    val extraFromNew = Recebedor.listFromJson(person.coRecebedoresJson)
                    newExtras.addAll(extraFromNew)
                    val updated = basePerson.copy(
                        nome = person.nome,
                        documento = person.documento,
                        assinatura = person.assinatura,
                        coRecebedoresJson = Recebedor.listToJson(newExtras.distinctBy { "${it.nome.trim().lowercase()}_${it.documento.trim()}" }),
                        dataAtualizacao = System.currentTimeMillis()
                    )
                    personRepository.updatePerson(updated)
                } else {
                    personRepository.insertPerson(person)
                }
            }
            try {
                personRepository.consolidateDuplicateAddressPersons()
            } catch (_: Throwable) {}
            onComplete()
        }
    }

    fun consolidateDuplicateAddresses(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = personRepository.consolidateDuplicateAddressPersons()
            onResult(count)
        }
    }

    /**
     * Exporta os dados locais do Room para o Firestore.
     * Trata a migração de versões antigas e valida se o campo de múltiplos destinatários existe
     * e está íntegro antes de enviar para a nuvem para compatibilidade entre versões.
     */
    fun exportRoomToFirestore(
        syncRepository: FirebaseSyncRepository = DeliveryApp.instance.firebaseSyncRepository,
        onResult: ((SyncState) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                // 1. Trata migração de registros legados (mescla endereços unificados)
                personRepository.consolidateDuplicateAddressPersons()

                // 2. Valida se o campo de múltiplos destinatários existe e está íntegro
                val allPersons = personRepository.getAllPersonsDirect()
                for (person in allPersons) {
                    val rawRecebedores = person.coRecebedoresJson
                    if (!rawRecebedores.isNullOrBlank()) {
                        try {
                            Recebedor.listFromJson(rawRecebedores)
                        } catch (e: Exception) {
                            Log.w("PeopleViewModel", "Corrigindo campo de múltiplos destinatários inválido no ID ${person.id}")
                            personRepository.updatePerson(person.copy(coRecebedoresJson = ""))
                        }
                    }
                }

                // 3. Executa exportação para o Firestore
                val result = syncRepository.syncAllToCloud()
                onResult?.invoke(result)
            } catch (e: Exception) {
                val error = SyncState.Error("Falha na exportação para o Firestore: ${e.message}")
                onResult?.invoke(error)
            }
        }
    }

    class Factory(
        private val personRepository: PersonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PeopleViewModel(personRepository) as T
        }
    }
}
