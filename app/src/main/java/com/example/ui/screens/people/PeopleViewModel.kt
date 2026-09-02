package com.example.ui.screens.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.Person
import com.example.data.model.Recebedor
import com.example.data.model.SignatureData
import com.example.data.repository.PersonRepository
import com.example.util.AddressNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(
    private val personRepository: PersonRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _tempSignature = MutableStateFlow<SignatureData?>(null)
    val tempSignature: StateFlow<SignatureData?> = _tempSignature.asStateFlow()

    init {
        viewModelScope.launch {
            personRepository.cleanupInactiveReceiversOlderThan5Years()
        }
    }

    fun setTempSignature(signature: SignatureData?) {
        _tempSignature.value = signature
    }

    val persons: StateFlow<List<Person>> = _searchQuery
        .flatMapLatest { query -> personRepository.searchPersons(query) }
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

    suspend fun getPersonById(id: Long): Person? {
        return personRepository.getPersonByIdDirect(id)
    }

    fun savePerson(person: Person, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (person.id > 0) {
                personRepository.updatePerson(person.copy(dataAtualizacao = System.currentTimeMillis()))
            } else {
                val existingList = personRepository.findPersonsByAddress(person.endereco)
                if (existingList.isNotEmpty()) {
                    val basePerson = existingList.first()
                    val existingExtras = Recebedor.listFromJson(basePerson.coRecebedoresJson).toMutableList()
                    val newRec = Recebedor(
                        id = "co_${System.currentTimeMillis().toString().takeLast(6)}",
                        nome = person.nome,
                        documento = person.documento,
                        assinatura = person.assinatura
                    )
                    existingExtras.add(newRec)
                    val extraFromNew = Recebedor.listFromJson(person.coRecebedoresJson)
                    existingExtras.addAll(extraFromNew)
                    val updated = basePerson.copy(
                        coRecebedoresJson = Recebedor.listToJson(existingExtras.distinctBy { "${it.nome.trim().lowercase()}_${it.documento.trim()}" }),
                        dataAtualizacao = System.currentTimeMillis()
                    )
                    personRepository.updatePerson(updated)
                } else {
                    personRepository.insertPerson(person)
                }
            }
            onComplete()
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
