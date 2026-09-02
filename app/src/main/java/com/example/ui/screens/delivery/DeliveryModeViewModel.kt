package com.example.ui.screens.delivery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.accessibility.AccessibilityAutomationEngine
import com.example.data.local.entity.Delivery
import com.example.data.local.entity.Person
import com.example.data.model.Recebedor
import com.example.data.model.SignatureData
import com.example.data.repository.DeliveryRepository
import com.example.data.repository.PersonRepository
import com.example.util.ClipboardHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeliveryModeState(
    val currentAddress: String = "",
    val selectedPerson: Person? = null,
    val candidatePersons: List<Person> = emptyList(),
    val availableRecebedores: List<Recebedor> = emptyList(),
    val selectedRecebedor: Recebedor? = null,
    val currentSignature: SignatureData? = null,
    val isSearching: Boolean = false,
    val nameFilled: Boolean = false,
    val documentFilled: Boolean = false,
    val signatureApplied: Boolean = false,
    val observation: String = "",
    val statusMessage: String = ""
)

class DeliveryModeViewModel(
    private val personRepository: PersonRepository,
    private val deliveryRepository: DeliveryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DeliveryModeState())
    val state: StateFlow<DeliveryModeState> = _state.asStateFlow()

    init {
        // Observar mudanças detectadas pelo AccessibilityAutomationEngine
        viewModelScope.launch {
            AccessibilityAutomationEngine.state.collect { autoState ->
                if (autoState.detectedAddressText.isNotBlank() && _state.value.currentAddress.isBlank()) {
                    _state.value = _state.value.copy(currentAddress = autoState.detectedAddressText)
                }
                if (autoState.matchedPerson != null && _state.value.selectedPerson == null) {
                    val recebedores = autoState.availableRecebedores.ifEmpty {
                        AccessibilityAutomationEngine.extractAllRecebedores(autoState.candidatePersons)
                    }
                    val currentSig = autoState.selectedRecebedor?.assinatura?.let {
                        if (it.isNotBlank()) SignatureData.fromJson(it) else null
                    } ?: autoState.matchedPerson?.assinatura?.let {
                        if (it.isNotBlank()) SignatureData.fromJson(it) else null
                    }
                    _state.value = _state.value.copy(
                        selectedPerson = autoState.matchedPerson,
                        candidatePersons = autoState.candidatePersons,
                        availableRecebedores = recebedores,
                        selectedRecebedor = autoState.selectedRecebedor ?: recebedores.firstOrNull(),
                        currentSignature = currentSig
                    )
                }
            }
        }
    }

    fun updateAddress(address: String) {
        _state.value = _state.value.copy(currentAddress = address)
        searchPersonsForAddress(address)
    }

    fun searchPersonsForAddress(address: String) {
        viewModelScope.launch {
            if (address.isBlank()) {
                _state.value = _state.value.copy(
                    candidatePersons = emptyList(),
                    availableRecebedores = emptyList(),
                    selectedPerson = null,
                    selectedRecebedor = null,
                    currentSignature = null
                )
                return@launch
            }
            _state.value = _state.value.copy(isSearching = true)
            val results = personRepository.findPersonsByAddress(address)
            val recebedores = AccessibilityAutomationEngine.extractAllRecebedores(results)
            val firstRec = recebedores.firstOrNull()
            val firstPerson = results.firstOrNull()
            val sig = firstRec?.assinatura?.let {
                if (it.isNotBlank()) SignatureData.fromJson(it) else null
            } ?: firstPerson?.assinatura?.let {
                if (it.isNotBlank()) SignatureData.fromJson(it) else null
            }

            _state.value = _state.value.copy(
                isSearching = false,
                candidatePersons = results,
                availableRecebedores = recebedores,
                selectedPerson = firstPerson,
                selectedRecebedor = firstRec,
                currentSignature = sig
            )
        }
    }

    fun selectPerson(person: Person) {
        val recebedores = AccessibilityAutomationEngine.extractAllRecebedores(listOf(person))
        val rec = recebedores.firstOrNull()
        val sig = rec?.assinatura?.let {
            if (it.isNotBlank()) SignatureData.fromJson(it) else null
        } ?: person.assinatura.let {
            if (it.isNotBlank()) SignatureData.fromJson(it) else null
        }
        _state.value = _state.value.copy(
            selectedPerson = person,
            selectedRecebedor = rec,
            currentSignature = sig,
            currentAddress = if (_state.value.currentAddress.isBlank()) "${person.endereco}, ${person.numero}" else _state.value.currentAddress
        )
    }

    fun selectRecebedor(recebedor: Recebedor) {
        val personId = recebedor.id.removePrefix("p_").substringBefore("_").toLongOrNull()
        val parentPerson = if (personId != null) {
            _state.value.candidatePersons.firstOrNull { it.id == personId } ?: _state.value.selectedPerson
        } else {
            _state.value.selectedPerson
        }
        val sig = if (recebedor.assinatura.isNotBlank()) {
            SignatureData.fromJson(recebedor.assinatura)
        } else if (parentPerson?.assinatura?.isNotBlank() == true) {
            SignatureData.fromJson(parentPerson.assinatura)
        } else null

        _state.value = _state.value.copy(
            selectedRecebedor = recebedor,
            selectedPerson = parentPerson,
            currentSignature = sig
        )
        AccessibilityAutomationEngine.selectRecebedor(recebedor)
    }

    fun fillFields(): String {
        val activeName = _state.value.selectedRecebedor?.nome ?: _state.value.selectedPerson?.nome ?: return "Nenhuma pessoa selecionada para preenchimento."
        val activeDoc = _state.value.selectedRecebedor?.documento ?: _state.value.selectedPerson?.documento ?: ""
        val result = AccessibilityAutomationEngine.fillFields(activeName, activeDoc)
        _state.value = _state.value.copy(
            nameFilled = result.nameFilled || _state.value.nameFilled,
            documentFilled = result.documentFilled || _state.value.documentFilled,
            statusMessage = result.message
        )
        return result.message
    }

    fun copyData(context: Context, label: String, text: String) {
        ClipboardHelper.copyToClipboard(context, label, text)
    }

    fun setSignature(signature: SignatureData) {
        _state.value = _state.value.copy(
            currentSignature = signature,
            signatureApplied = true,
            statusMessage = "Assinatura coletada com sucesso."
        )
    }

    fun finishDelivery(onFinished: (Long) -> Unit) {
        val current = _state.value
        val person = current.selectedPerson
        val activeRec = current.selectedRecebedor
        val delivery = Delivery(
            pessoaId = person?.id,
            nomeDestinatario = activeRec?.nome ?: person?.nome ?: "Destinatário Direto",
            documentoDestinatario = activeRec?.documento ?: person?.documento ?: "",
            endereco = current.currentAddress.ifBlank { person?.let { "${it.endereco}, ${it.numero}" } ?: "Endereço não informado" },
            dataHora = System.currentTimeMillis(),
            observacao = current.observation,
            assinatura = current.currentSignature?.toJson() ?: "",
            status = "CONCLUÍDA",
            nomePreenchido = current.nameFilled,
            documentoPreenchido = current.documentFilled,
            assinaturaAplicada = current.signatureApplied
        )

        viewModelScope.launch {
            val deliveryId = deliveryRepository.insertDelivery(delivery)
            // Resetar formulário de entrega rápida
            _state.value = DeliveryModeState()
            onFinished(deliveryId)
        }
    }

    class Factory(
        private val personRepository: PersonRepository,
        private val deliveryRepository: DeliveryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeliveryModeViewModel(personRepository, deliveryRepository) as T
        }
    }
}
