package com.example.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebasePerson
import com.example.data.firebase.FirebaseSyncRepository
import com.example.data.firebase.SyncState
import com.example.data.local.entity.Person
import com.example.data.model.Recebedor
import com.example.data.repository.AppSettings
import com.example.data.repository.PersonRepository
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel responsável pelas configurações do aplicativo e pela exportação/sincronização
 * de dados locais do Room com o Firebase Firestore, incluindo rotinas de migração e garantia
 * de compatibilidade entre versões de esquemas de dados.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val firebaseSyncRepository: FirebaseSyncRepository,
    private val personRepository: PersonRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        viewModelScope.launch {
            firebaseSyncRepository.syncState.collect { state ->
                _syncState.value = state
            }
        }
    }

    /**
     * Exporta os dados do Room para o Firestore.
     * Trata a migração de dados legados e verifica se o campo de múltiplos destinatários existe
     * antes de salvar na nuvem para garantir total compatibilidade entre diferentes versões do app.
     */
    fun exportRoomToFirestore(onResult: ((SyncState) -> Unit)? = null) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing(
                progress = 0.02f,
                stepMessage = "Verificando cadastros locais...",
                processedCount = 0,
                totalCount = 0
            )

            val result = withContext(Dispatchers.IO) {
                try {
                    // 1. Migração de dados legados do Room:
                    // Consolida endereços duplicados (em versões antigas, destinatários extras eram cadastrados como pessoas separadas)
                    try {
                        val consolidatedCount = personRepository.consolidateDuplicateAddressPersons()
                        if (consolidatedCount > 0) {
                            Log.d("SettingsViewModel", "Migração de versões antigas: $consolidatedCount registros unificados com múltiplos destinatários.")
                        }
                    } catch (e: Exception) {
                        Log.w("SettingsViewModel", "Aviso na consolidação de endereços: ${e.message}")
                    }

                    // 2. Carrega todos os registros do Room
                    val allPersons = personRepository.getAllPersonsDirect()
                    if (allPersons.isEmpty()) {
                        return@withContext SyncState.Success("Nenhum cadastro local para exportar.")
                    }

                    // 3. Verifica e valida o campo de múltiplos destinatários de cada registro para compatibilidade
                    var migratedCount = 0
                    val normalizedList = allPersons.map { person ->
                        val migrated = migrateAndValidateRecipientsField(person)
                        if (migrated != person) {
                            migratedCount++
                            personRepository.updatePerson(migrated)
                        }
                        migrated
                    }

                    if (migratedCount > 0) {
                        Log.d("SettingsViewModel", "Migração de campos concluída em $migratedCount cadastros locais.")
                    }

                    // 4. Salva os dados no Firestore através do repositório
                    val uploadResult = firebaseSyncRepository.syncAllToCloud()
                    uploadResult
                } catch (e: Exception) {
                    val errorMsg = "Erro na exportação para o Firestore: ${e.localizedMessage ?: e.message}"
                    Log.e("SettingsViewModel", errorMsg, e)
                    SyncState.Error(errorMsg)
                }
            }

            _syncState.value = result
            onResult?.invoke(result)
        }
    }

    /**
     * Baixa os dados da nuvem Firestore e os importa para o Room local.
     */
    fun importFromFirestore(onResult: ((SyncState) -> Unit)? = null) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing(
                progress = 0.05f,
                stepMessage = "Iniciando download da nuvem...",
                processedCount = 0,
                totalCount = 0
            )
            val result = withContext(Dispatchers.IO) {
                firebaseSyncRepository.fetchAllFromCloud()
            }
            _syncState.value = result
            onResult?.invoke(result)
        }
    }

    /**
     * Atualiza as preferências do usuário.
     */
    fun saveSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings { newSettings }
        }
    }

    /**
     * Verifica e trata a existência do campo de múltiplos destinatários (coRecebedoresJson):
     * - Se ausente ou vazio (dados criados em versões antigas), inicializa como lista vazia de forma segura.
     * - Se possuir dados, valida a integridade do JSON de recebedores.
     * - Preserva os campos clássicos (nome, documento, assinatura) para compatibilidade retroativa com versões antigas.
     */
    private fun migrateAndValidateRecipientsField(person: Person): Person {
        var primaryNome = person.nome.trim()
        var primaryDoc = person.documento.trim()
        var primaryAssinatura = person.assinatura.trim()
        val rawCoRecebedores = person.coRecebedoresJson

        // Verifica se o campo de múltiplos destinatários existe e contém dados
        val hasMultipleRecipients = !rawCoRecebedores.isNullOrBlank()

        val recebedoresList: MutableList<Recebedor> = if (hasMultipleRecipients) {
            try {
                // Tenta deserializar o campo de múltiplos destinatários
                Recebedor.listFromJson(rawCoRecebedores).toMutableList()
            } catch (e: Exception) {
                Log.w("SettingsViewModel", "Formato incompatível no campo de múltiplos destinatários (ID ${person.id}): ${e.message}")
                mutableListOf()
            }
        } else {
            // Em versões antigas onde o campo não existia ou estava vazio
            mutableListOf()
        }

        // Se o titular estiver vazio mas existirem co-recebedores, promove o primeiro a titular para compatibilidade com versões legadas
        if (primaryNome.isBlank() && recebedoresList.isNotEmpty()) {
            val first = recebedoresList.removeAt(0)
            primaryNome = first.nome.trim()
            primaryDoc = first.documento.trim()
            if (primaryAssinatura.isBlank()) {
                primaryAssinatura = first.assinatura.trim()
            }
        }

        // Garante remoção de duplicados na lista de destinatários extras
        val sanitizedExtras = recebedoresList.distinctBy {
            "${it.nome.trim().lowercase()}_${it.documento.trim()}"
        }

        val finalCoRecebedoresJson = if (sanitizedExtras.isNotEmpty()) {
            Recebedor.listToJson(sanitizedExtras)
        } else {
            ""
        }

        return person.copy(
            nome = primaryNome,
            documento = primaryDoc,
            assinatura = primaryAssinatura,
            coRecebedoresJson = finalCoRecebedoresJson,
            dataAtualizacao = System.currentTimeMillis()
        )
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val firebaseSyncRepository: FirebaseSyncRepository,
        private val personRepository: PersonRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, firebaseSyncRepository, personRepository) as T
        }
    }
}
