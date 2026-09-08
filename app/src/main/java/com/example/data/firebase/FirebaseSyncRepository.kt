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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
                val app = FirebaseApp.initializeApp(appContext)
                if (app == null) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:946779143583:android:ea254adf31519413b591aa")
                        .setApiKey("AIzaSyDKSJOuGg8nDr4HTiF9SXZcdYrl6FSeB0w")
                        .setProjectId("assistente-de-entregas")
                        .setStorageBucket("assistente-de-entregas.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(appContext, options)
                }
            }
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w("FirebaseSync", "Firestore not available: ${e.message}")
            try {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApplicationId("1:946779143583:android:ea254adf31519413b591aa")
                    .setApiKey("AIzaSyDKSJOuGg8nDr4HTiF9SXZcdYrl6FSeB0w")
                    .setProjectId("assistente-de-entregas")
                    .setStorageBucket("assistente-de-entregas.firebasestorage.app")
                    .build()
                val app = FirebaseApp.initializeApp(appContext, options, "fallback_firestore_app")
                FirebaseFirestore.getInstance(app)
            } catch (e2: Exception) {
                Log.e("FirebaseSync", "Firestore complete failure: ${e2.message}")
                null
            }
        }
    }

    private suspend fun resolveUserId(): String {
        return try {
            kotlinx.coroutines.withTimeoutOrNull(3000L) {
                val auth = FirebaseAuth.getInstance()
                val user = auth.currentUser
                if (user != null && user.uid.isNotBlank()) {
                    user.uid
                } else {
                    try {
                        val anon = auth.signInAnonymously().await()
                        anon.user?.uid ?: fallbackLocalUserId()
                    } catch (e: Exception) {
                        fallbackLocalUserId()
                    }
                }
            } ?: fallbackLocalUserId()
        } catch (_: Exception) {
            fallbackLocalUserId()
        }
    }

    private fun fallbackLocalUserId(): String {
        val prefs = appContext.getSharedPreferences("app_delivery_auth_prefs", Context.MODE_PRIVATE)
        val localEmail = prefs.getString("logged_email", null)
        return if (!localEmail.isNullOrBlank()) {
            "user_${localEmail.replace(".", "_").replace("@", "_")}"
        } else {
            "local_device"
        }
    }

    private fun getUserId(): String {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null && user.uid.isNotBlank()) {
                user.uid
            } else {
                fallbackLocalUserId()
            }
        } catch (_: Exception) {
            "local_device"
        }
    }

    override val isFirestoreAvailable: Boolean
        get() = getFirestore() != null

    override suspend fun syncAllToCloud(): SyncState = withContext(Dispatchers.IO) {
        val firestore = getFirestore()
            ?: return@withContext SyncState.Error("Firebase Firestore não pôde ser inicializado no dispositivo.")

        _syncState.value = SyncState.Syncing(
            progress = 0.05f,
            stepMessage = "Conectando ao banco de dados...",
            processedCount = 0,
            totalCount = 0
        )
        try {
            // 1. Unifica/Consolida destinatários que compartilham o mesmo endereço antes de subir
            try {
                com.example.DeliveryApp.instance.personRepository.consolidateDuplicateAddressPersons()
            } catch (e: Throwable) {
                Log.w("FirebaseSync", "Aviso consolidação: ${e.message}")
            }

            val userId = resolveUserId()
            val persons = personDao.getAllPersonsDirect()
            if (persons.isEmpty()) {
                val result = SyncState.Success("Nenhum cadastro local para enviar.")
                _syncState.value = result
                return@withContext result
            }

            val totalPersons = persons.size
            _syncState.value = SyncState.Syncing(
                progress = 0.1f,
                stepMessage = "Preparando $totalPersons cadastros para envio...",
                processedCount = 0,
                totalCount = totalPersons
            )

            val userPersonsRef = firestore.collection("users")
                .document(userId)
                .collection("pessoas")

            // 2. Envia os cadastros locais em lotes de 100 itens por lote (suporta até 500 no Firestore)
            val batchPersonsChunks = persons.chunked(100)
            var totalUploaded = 0

            for ((index, chunk) in batchPersonsChunks.withIndex()) {
                val startProgress = 0.1f + (totalUploaded.toFloat() / totalPersons.toFloat()) * 0.85f
                _syncState.value = SyncState.Syncing(
                    progress = startProgress,
                    stepMessage = "Enviando dados: $totalUploaded de $totalPersons cadastros (${(startProgress * 100).toInt()}%)...",
                    processedCount = totalUploaded,
                    totalCount = totalPersons
                )

                try {
                    withTimeout(25000L) {
                        val b = firestore.batch()
                        for (p in chunk) {
                            val docRef = userPersonsRef.document(p.id.toString())
                            val fbPerson = FirebasePerson(
                                id = p.id.toString(),
                                localId = p.id,
                                userId = userId,
                                nome = p.nome,
                                documento = p.documento,
                                endereco = p.endereco,
                                numero = p.numero,
                                complemento = p.complemento,
                                bairro = p.bairro,
                                cidade = p.cidade,
                                uf = p.uf,
                                observacao = p.observacao,
                                assinatura = p.assinatura,
                                coRecebedoresJson = p.coRecebedoresJson,
                                dataCriacao = p.dataCriacao,
                                dataAtualizacao = p.dataAtualizacao
                            )
                            b.set(docRef, fbPerson, SetOptions.merge())
                        }
                        b.commit().await()
                    }
                    totalUploaded += chunk.size
                } catch (e: Exception) {
                    Log.w("FirebaseSync", "Aviso no lote $index de envio: ${e.message}")
                    // Se falhar por tamanho individual, envia em sub-lotes de 20
                    if (chunk.size > 20) {
                        for (subChunk in chunk.chunked(20)) {
                            try {
                                withTimeout(15000L) {
                                    val subBatch = firestore.batch()
                                    for (p in subChunk) {
                                        val docRef = userPersonsRef.document(p.id.toString())
                                        val fbPerson = FirebasePerson(
                                            id = p.id.toString(),
                                            localId = p.id,
                                            userId = userId,
                                            nome = p.nome,
                                            documento = p.documento,
                                            endereco = p.endereco,
                                            numero = p.numero,
                                            complemento = p.complemento,
                                            bairro = p.bairro,
                                            cidade = p.cidade,
                                            uf = p.uf,
                                            observacao = p.observacao,
                                            assinatura = p.assinatura,
                                            coRecebedoresJson = p.coRecebedoresJson,
                                            dataCriacao = p.dataCriacao,
                                            dataAtualizacao = p.dataAtualizacao
                                        )
                                        subBatch.set(docRef, fbPerson, SetOptions.merge())
                                    }
                                    subBatch.commit().await()
                                }
                                totalUploaded += subChunk.size
                            } catch (subErr: Exception) {
                                Log.e("FirebaseSync", "Falha no sub-lote: ${subErr.message}")
                            }
                        }
                    }
                }

                val currentProgress = 0.1f + (totalUploaded.toFloat() / totalPersons.toFloat()) * 0.85f
                _syncState.value = SyncState.Syncing(
                    progress = currentProgress.coerceAtMost(0.98f),
                    stepMessage = "Enviando dados: $totalUploaded de $totalPersons cadastros (${(currentProgress * 100).toInt().coerceAtMost(98)}%)...",
                    processedCount = totalUploaded,
                    totalCount = totalPersons
                )
            }

            _syncState.value = SyncState.Syncing(
                progress = 1.0f,
                stepMessage = "Finalizando sincronização...",
                processedCount = totalUploaded,
                totalCount = totalPersons
            )

            val result = SyncState.Success("Nuvem atualizada: $totalUploaded endereço(s) e recebedores sincronizados com sucesso!")
            _syncState.value = result
            result
        } catch (e: TimeoutCancellationException) {
            val error = SyncState.Error("Conexão com o Firebase expirou. Os dados continuam salvos localmente no dispositivo.")
            _syncState.value = error
            error
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: "Erro desconhecido"
            val friendlyMsg = if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                "Permissão negada no Firebase: as regras do Firestore precisam permitir leitura/escrita."
            } else if (msg.contains("UNAVAILABLE", ignoreCase = true)) {
                "Sem conexão com os servidores do Firebase. Os dados estão salvos localmente com segurança."
            } else {
                "Erro ao sincronizar na nuvem: $msg"
            }
            val error = SyncState.Error(friendlyMsg)
            _syncState.value = error
            error
        }
    }

    override suspend fun fetchAllFromCloud(): SyncState = withContext(Dispatchers.IO) {
        val firestore = getFirestore()
            ?: return@withContext SyncState.Error("Firebase Firestore não pôde ser inicializado.")

        _syncState.value = SyncState.Syncing(
            progress = 0.08f,
            stepMessage = "Conectando ao Firebase...",
            processedCount = 0,
            totalCount = 0
        )
        try {
            val userId = resolveUserId()

            _syncState.value = SyncState.Syncing(
                progress = 0.2f,
                stepMessage = "Buscando dados na nuvem...",
                processedCount = 0,
                totalCount = 0
            )

            // Baixar Pessoas da nuvem com timeout amplo (60s)
            val personsSnapshot = withTimeout(60000L) {
                firestore.collection("users")
                    .document(userId)
                    .collection("pessoas")
                    .get()
                    .await()
            }

            val totalDocs = personsSnapshot.documents.size
            _syncState.value = SyncState.Syncing(
                progress = 0.5f,
                stepMessage = "Processando $totalDocs cadastros baixados...",
                processedCount = 0,
                totalCount = totalDocs
            )

            val cloudPersons = mutableListOf<Person>()
            for (doc in personsSnapshot.documents) {
                val it = doc.toObject(FirebasePerson::class.java)
                if (it != null) {
                    val safeRecebedores = if (it.coRecebedoresJson.isNotBlank()) {
                        it.coRecebedoresJson
                    } else {
                        doc.getString("coRecebedoresJson") ?: ""
                    }
                    val primaryPerson = Person(
                        id = if (it.localId > 0) it.localId else 0,
                        nome = it.nome,
                        documento = it.documento,
                        endereco = it.endereco,
                        numero = it.numero,
                        complemento = it.complemento,
                        bairro = it.bairro,
                        cidade = it.cidade,
                        uf = it.uf,
                        observacao = it.observacao,
                        assinatura = it.assinatura,
                        coRecebedoresJson = safeRecebedores,
                        dataCriacao = it.dataCriacao,
                        dataAtualizacao = it.dataAtualizacao
                    )
                    cloudPersons.add(primaryPerson)
                }
            }

            if (cloudPersons.isNotEmpty()) {
                val totalToSave = cloudPersons.size
                var savedCount = 0
                cloudPersons.chunked(100).forEach { chunk ->
                    personDao.insertAll(chunk)
                    savedCount += chunk.size
                    val saveProgress = 0.5f + (savedCount.toFloat() / totalToSave.toFloat()) * 0.45f
                    _syncState.value = SyncState.Syncing(
                        progress = saveProgress.coerceAtMost(0.98f),
                        stepMessage = "Salvando localmente: $savedCount de $totalToSave (${(saveProgress * 100).toInt()}%)...",
                        processedCount = savedCount,
                        totalCount = totalToSave
                    )
                }
                // Unifica automaticamente quaisquer destinatários antigos de mesmo endereço baixados da nuvem
                try {
                    com.example.DeliveryApp.instance.personRepository.consolidateDuplicateAddressPersons()
                } catch (_: Throwable) {}
            }

            _syncState.value = SyncState.Syncing(
                progress = 1.0f,
                stepMessage = "Download finalizado com sucesso!",
                processedCount = cloudPersons.size,
                totalCount = cloudPersons.size
            )

            val finalCount = personDao.countPersons()
            val result = SyncState.Success("Download concluído: $finalCount endereço(s) carregados com sucesso.")
            _syncState.value = result
            result
        } catch (e: TimeoutCancellationException) {
            val error = SyncState.Error("O download do Firebase excedeu o tempo limite. Verifique sua internet.")
            _syncState.value = error
            error
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: "Erro desconhecido"
            val friendlyMsg = if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                "Permissão negada no Firebase: verifique se as regras do Firestore permitem leitura/escrita."
            } else if (msg.contains("UNAVAILABLE", ignoreCase = true)) {
                "Sem conexão com os servidores do Firebase. Verifique sua internet."
            } else {
                "Erro ao baixar da nuvem: $msg"
            }
            val error = SyncState.Error(friendlyMsg)
            _syncState.value = error
            error
        }
    }

    override suspend fun uploadPerson(person: Person): Boolean = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext false
        try {
            withTimeout(4000L) {
                val userId = resolveUserId()
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
                    coRecebedoresJson = person.coRecebedoresJson,
                    dataCriacao = person.dataCriacao,
                    dataAtualizacao = person.dataAtualizacao
                )
                docRef.set(fbPerson, SetOptions.merge()).await()
            }
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Failed to upload person: ${e.message}")
            false
        }
    }

    override suspend fun uploadDelivery(delivery: Delivery): Boolean = withContext(Dispatchers.IO) {
        val firestore = getFirestore() ?: return@withContext false
        try {
            withTimeout(4000L) {
                val userId = resolveUserId()
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
            }
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Failed to upload delivery: ${e.message}")
            false
        }
    }
}
