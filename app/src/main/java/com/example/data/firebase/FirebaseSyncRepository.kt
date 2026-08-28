package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.local.dao.DeliveryDao
import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.Delivery
import com.example.data.local.entity.Person
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

interface FirebaseSyncRepository {
    val syncState: Flow<SyncState>
    val isFirestoreAvailable: Boolean

    suspend fun syncAllToCloud(): SyncState
    suspend fun fetchAllFromCloud(): SyncState
    suspend fun uploadPerson(person: Person): Boolean
    suspend fun uploadDelivery(delivery: Delivery): Boolean
}

class FirebaseSyncRepositoryImpl(
    private val appContext: Context,
    private val personDao: PersonDao,
    private val deliveryDao: DeliveryDao
) : FirebaseSyncRepository {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: Flow<SyncState> = _syncState.asStateFlow()

    private fun getFirestore(): FirebaseFirestore? {
        return try {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w("FirebaseSync", "Firestore not available: ${e.message}")
            null
        }
    }

    private fun getUserId(): String {
        return try {
            FirebaseAuth.getInstance().currentUser?.uid ?: "local_device"
        } catch (_: Exception) {
            "local_device"
        }
    }

    override val isFirestoreAvailable: Boolean
        get() = getFirestore() != null

    override suspend fun syncAllToCloud(): SyncState = withContext(Dispatchers.IO) {
        val firestore = getFirestore()
            ?: return@withContext SyncState.Error("Firebase Firestore não inicializado.")

        _syncState.value = SyncState.Syncing
        try {
            val userId = getUserId()
            val persons = personDao.getAllPersons().first()

            val batch = firestore.batch()

            // Salvar Pessoas na coleção 'users/{userId}/pessoas'
            persons.forEach { p ->
                val docRef = firestore.collection("users")
                    .document(userId)
                    .collection("pessoas")
                    .document(p.id.toString())

                val fbPerson = FirebasePerson(
                    id = p.id.toString(),
                    localId = p.id,
                    userId = userId,
                    nome = p.nome,
                    documento = p.documento,
                    endereco = p.endereco,
                    numero = p.numero,
                    complemento = "",
                    bairro = "",
                    cidade = "",
                    uf = "",
                    observacao = "",
                    assinatura = p.assinatura,
                    dataCriacao = p.dataCriacao,
                    dataAtualizacao = p.dataAtualizacao
                )
                batch.set(docRef, fbPerson, SetOptions.merge())
            }

            batch.commit().await()

            val result = SyncState.Success("Nuvem atualizada: ${persons.size} destinatários sincronizados.")
            _syncState.value = result
            result
        } catch (e: Exception) {
            val error = SyncState.Error("Erro ao sincronizar na nuvem: ${e.localizedMessage ?: e.message}")
            _syncState.value = error
            error
        }
    }

    override suspend fun fetchAllFromCloud(): SyncState = withContext(Dispatchers.IO) {
        val firestore = getFirestore()
            ?: return@withContext SyncState.Error("Firebase Firestore não inicializado.")

        _syncState.value = SyncState.Syncing
        try {
            val userId = getUserId()

            // Baixar Pessoas
            val personsSnapshot = firestore.collection("users")
                .document(userId)
                .collection("pessoas")
                .get()
                .await()

            val cloudPersons = personsSnapshot.documents.mapNotNull { doc ->
                val p = doc.toObject(FirebasePerson::class.java)
                p?.let {
                    Person(
                        id = if (it.localId > 0) it.localId else 0,
                        nome = it.nome,
                        documento = it.documento,
                        endereco = it.endereco,
                        numero = it.numero,
                        complemento = "",
                        bairro = "",
                        cidade = "",
                        uf = "",
                        observacao = "",
                        assinatura = it.assinatura,
                        dataCriacao = it.dataCriacao,
                        dataAtualizacao = it.dataAtualizacao
                    )
                }
            }

            if (cloudPersons.isNotEmpty()) {
                personDao.insertAll(cloudPersons)
            }

            val result = SyncState.Success("Download concluído: ${cloudPersons.size} destinatários importados da nuvem.")
            _syncState.value = result
            result
        } catch (e: Exception) {
            val error = SyncState.Error("Erro ao baixar da nuvem: ${e.localizedMessage ?: e.message}")
            _syncState.value = error
            error
        }
    }

    override suspend fun uploadPerson(person: Person): Boolean = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext false
        try {
            val userId = getUserId()
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("pessoas")
                .document(person.id.toString())

            val fbPerson = FirebasePerson(
                id = person.id.toString(),
                localId = person.id,
                userId = userId,
                nome = person.nome,
                documento = person.documento,
                endereco = person.endereco,
                numero = person.numero,
                complemento = person.complemento,
                bairro = person.bairro,
                cidade = person.cidade,
                uf = person.uf,
                observacao = person.observacao,
                assinatura = person.assinatura,
                dataCriacao = person.dataCriacao,
                dataAtualizacao = person.dataAtualizacao
            )
            docRef.set(fbPerson, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Failed to upload person: ${e.message}")
            false
        }
    }

    override suspend fun uploadDelivery(delivery: Delivery): Boolean = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext false
        try {
            val userId = getUserId()
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("entregas")
                .document(delivery.id.toString())

            val fbDelivery = FirebaseDelivery(
                id = delivery.id.toString(),
                localId = delivery.id,
                userId = userId,
                pessoaId = delivery.pessoaId,
                nomeDestinatario = delivery.nomeDestinatario,
                documentoDestinatario = delivery.documentoDestinatario,
                endereco = delivery.endereco,
                dataHora = delivery.dataHora,
                observacao = delivery.observacao,
                assinatura = delivery.assinatura,
                status = delivery.status,
                nomePreenchido = delivery.nomePreenchido,
                documentoPreenchido = delivery.documentoPreenchido,
                assinaturaAplicada = delivery.assinaturaAplicada,
                syncTimestamp = System.currentTimeMillis()
            )
            docRef.set(fbDelivery, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Failed to upload delivery: ${e.message}")
            false
        }
    }
}
