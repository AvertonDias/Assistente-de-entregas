package com.example.data.firebase

data class FirebasePerson(
    val id: String = "",
    val localId: Long = 0,
    val userId: String = "",
    val nome: String = "",
    val documento: String = "",
    val endereco: String = "",
    val numero: String = "",
    val complemento: String = "",
    val bairro: String = "",
    val cidade: String = "",
    val uf: String = "MG",
    val observacao: String = "",
    val assinatura: String = "",
    val dataCriacao: Long = System.currentTimeMillis(),
    val dataAtualizacao: Long = System.currentTimeMillis()
)

data class FirebaseDelivery(
    val id: String = "",
    val localId: Long = 0,
    val userId: String = "",
    val pessoaId: Long? = null,
    val nomeDestinatario: String = "",
    val documentoDestinatario: String = "",
    val endereco: String = "",
    val dataHora: Long = System.currentTimeMillis(),
    val observacao: String = "",
    val assinatura: String = "",
    val status: String = "CONCLUÍDA",
    val nomePreenchido: Boolean = false,
    val documentoPreenchido: Boolean = false,
    val assinaturaAplicada: Boolean = false,
    val syncTimestamp: Long = System.currentTimeMillis()
)

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}
