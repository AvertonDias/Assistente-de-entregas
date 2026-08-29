package com.example.ui.screens.people

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.Person
import com.example.data.model.Recebedor
import com.example.data.model.SignatureData
import com.example.ui.components.SignatureCanvas
import com.example.util.AddressNormalizer
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonEditScreen(
    personId: Long,
    initialAddress: String = "",
    viewModel: PeopleViewModel,
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    var address by remember { mutableStateOf(initialAddress) }
    var addressError by remember { mutableStateOf(false) }

    var recebedoresList by remember { mutableStateOf<List<Recebedor>>(emptyList()) }

    // Rastreamento para identificar alterações não salvas na tela inteira
    var initialAddressLoaded by remember { mutableStateOf(initialAddress) }
    var initialRecebedoresLoaded by remember { mutableStateOf<List<Recebedor>>(emptyList()) }
    var showDiscardScreenConfirmDialog by remember { mutableStateOf(false) }

    val hasUnsavedScreenChanges by remember {
        androidx.compose.runtime.derivedStateOf {
            address.trim() != initialAddressLoaded.trim() ||
                    recebedoresList != initialRecebedoresLoaded
        }
    }

    val handleAttemptNavigateBack: () -> Unit = {
        if (hasUnsavedScreenChanges) {
            showDiscardScreenConfirmDialog = true
        } else {
            onNavigateBack()
        }
    }

    // Interceptar o botão voltar físico/gestual do Android
    BackHandler(enabled = hasUnsavedScreenChanges) {
        showDiscardScreenConfirmDialog = true
    }

    if (showDiscardScreenConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardScreenConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Descartar alterações?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Você fez alterações nos dados do endereço ou recebedores que ainda não foram salvas. Deseja realmente sair?",
                    fontSize = 13.5.sp,
                    color = Color(0xFF424242)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardScreenConfirmDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Descartar e Sair", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDiscardScreenConfirmDialog = false }
                ) {
                    Text("Continuar Editando")
                }
            }
        )
    }

    // Dialog control for adding/editing a specific receiver
    var editingRecebedor by remember { mutableStateOf<Recebedor?>(null) }
    var isNewRecebedor by remember { mutableStateOf(false) }

    // Signature Fullscreen Modal state
    var isSignatureModalOpen by remember { mutableStateOf(false) }
    var signatureTargetId by remember { mutableStateOf<String?>(null) }

    // Temp signature from shared ViewModels/Drawing events (if any, as in original template)
    val tempSignature by viewModel.tempSignature.collectAsStateWithLifecycle()
    LaunchedEffect(tempSignature) {
        tempSignature?.let { sig ->
            if (isSignatureModalOpen && signatureTargetId != null) {
                // Update signature for the receiver that is target of drawing
                if (editingRecebedor?.id == signatureTargetId) {
                    editingRecebedor = editingRecebedor?.copy(assinatura = sig.toJson())
                }
                isSignatureModalOpen = false
                signatureTargetId = null
            }
            viewModel.setTempSignature(null)
        }
    }

    LaunchedEffect(personId, initialAddress) {
        if (personId > 0) {
            val existing = viewModel.getPersonById(personId)
            if (existing != null) {
                address = existing.endereco
                val mainReceiver = Recebedor(
                    id = "main",
                    nome = existing.nome,
                    documento = existing.documento,
                    assinatura = existing.assinatura
                )
                val extraReceivers = Recebedor.listFromJson(existing.coRecebedoresJson)
                val loadedList = listOf(mainReceiver) + extraReceivers
                recebedoresList = loadedList
                initialAddressLoaded = existing.endereco
                initialRecebedoresLoaded = loadedList
            }
        } else {
            if (initialAddress.isNotBlank() && address.isBlank()) {
                address = initialAddress
                initialAddressLoaded = initialAddress
            }
            if (recebedoresList.isEmpty()) {
                val initialList = listOf(
                    Recebedor(id = "main", nome = "", documento = "", assinatura = "")
                )
                recebedoresList = initialList
                initialRecebedoresLoaded = initialList
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (personId > 0) "Editar Destinatário" else "Novo Destinatário",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleAttemptNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Endereço
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                    addressError = it.isBlank()
                },
                label = { Text("Endereço (Rua e Número) *") },
                placeholder = { Text("Ex: Rua das Flores, 123") },
                isError = addressError,
                supportingText = { if (addressError) Text("O endereço é obrigatório") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("person_address_input"),
                singleLine = true
            )

            // Seção de Recebedores
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recebedores Autorizados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = {
                                editingRecebedor = Recebedor(nome = "", documento = "", assinatura = "")
                                isNewRecebedor = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Adicionar", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (recebedoresList.isEmpty()) {
                        Text(
                            text = "Nenhum recebedor cadastrado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        recebedoresList.forEachIndexed { index, rec ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (rec.nome.isBlank()) "(Sem nome - Toque em editar)" else rec.nome,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (rec.documento.isNotBlank()) {
                                            Text(
                                                text = "Doc: ${rec.documento}",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            val hasSig = rec.assinatura.isNotBlank()
                                            Icon(
                                                imageVector = if (hasSig) Icons.Default.Check else Icons.Default.Draw,
                                                contentDescription = null,
                                                tint = if (hasSig) Color(0xFF2E7D32) else Color.Gray,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (hasSig) "Assinatura registrada" else "Sem assinatura",
                                                fontSize = 11.sp,
                                                color = if (hasSig) Color(0xFF2E7D32) else Color.Gray
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                editingRecebedor = rec
                                                isNewRecebedor = false
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Editar",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                // Permitir exclusão se não for o último recebedor
                                                if (recebedoresList.size > 1) {
                                                    recebedoresList = recebedoresList.filter { it.id != rec.id }
                                                } else {
                                                    Toast.makeText(context, "É necessário ter pelo menos um recebedor!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            enabled = recebedoresList.size > 1,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Excluir",
                                                tint = if (recebedoresList.size > 1) MaterialTheme.colorScheme.error else Color.Gray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botões de Ação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = handleAttemptNavigateBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("CANCELAR")
                }

                Button(
                    onClick = {
                        if (address.isBlank()) {
                            addressError = true
                            return@Button
                        }

                        if (recebedoresList.isEmpty()) {
                            Toast.makeText(context, "Adicione pelo menos um recebedor!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val first = recebedoresList.first()
                        if (first.nome.isBlank()) {
                            Toast.makeText(context, "O primeiro recebedor deve ter um nome válido!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Filtrar se algum recebedor ficou com o nome em branco
                        val invalid = recebedoresList.any { it.nome.isBlank() }
                        if (invalid) {
                            Toast.makeText(context, "Todos os recebedores devem ter nomes preenchidos!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val extraList = if (recebedoresList.size > 1) {
                            recebedoresList.subList(1, recebedoresList.size)
                        } else {
                            emptyList()
                        }

                        val person = Person(
                            id = personId,
                            nome = first.nome.trim(),
                            documento = first.documento.trim(),
                            endereco = address.trim(),
                            numero = "",
                            complemento = "",
                            bairro = "",
                            cidade = "",
                            uf = "",
                            observacao = "",
                            assinatura = first.assinatura,
                            coRecebedoresJson = Recebedor.listToJson(extraList)
                        )

                        viewModel.savePerson(person) {
                            Toast.makeText(context, "Destinatário salvo com sucesso!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                        .testTag("save_person_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SALVAR", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

    // Modal de Edição de Recebedor (apenas quando não estiver desenhando a assinatura)
    if (!isSignatureModalOpen) {
        editingRecebedor?.let { rec ->
            RecebedorDialog(
                recebedor = rec,
                isNew = isNewRecebedor,
                onDismiss = { editingRecebedor = null },
                onSave = { savedRec ->
                    if (isNewRecebedor) {
                        recebedoresList = recebedoresList + savedRec
                    } else {
                        recebedoresList = recebedoresList.map { if (it.id == savedRec.id) savedRec else it }
                    }
                    editingRecebedor = null
                },
                onCollectSignature = { id, currentNome, currentDocumento ->
                    editingRecebedor = editingRecebedor?.copy(
                        id = id,
                        nome = currentNome,
                        documento = currentDocumento
                    )
                    signatureTargetId = id
                    isSignatureModalOpen = true
                },
                onClearSignature = {
                    editingRecebedor = editingRecebedor?.copy(assinatura = "")
                }
            )
        }
    }

    // TELA TODA DE ASSINATURA HORIZONTAL MÁXIMA
    if (isSignatureModalOpen && signatureTargetId != null) {
        val currentSigData = remember(signatureTargetId, editingRecebedor?.assinatura) {
            val raw = editingRecebedor?.assinatura ?: ""
            if (raw.isNotBlank()) SignatureData.fromJson(raw) else null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
        ) {
            SignatureCanvas(
                modifier = Modifier.fillMaxSize(),
                initialSignature = currentSigData,
                isDarkTheme = true,
                onSignatureConfirmed = { signature ->
                    val sigJson = signature.toJson()
                    editingRecebedor = editingRecebedor?.copy(assinatura = sigJson)
                    isSignatureModalOpen = false
                    signatureTargetId = null
                    Toast.makeText(context, "Assinatura gravada!", Toast.LENGTH_SHORT).show()
                },
                onCancel = {
                    isSignatureModalOpen = false
                    signatureTargetId = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecebedorDialog(
    recebedor: Recebedor,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Recebedor) -> Unit,
    onCollectSignature: (String, String, String) -> Unit,
    onClearSignature: () -> Unit
) {
    var nome by remember { mutableStateOf(recebedor.nome) }
    var documento by remember { mutableStateOf(recebedor.documento) }
    var localAssinatura by remember { mutableStateOf(recebedor.assinatura) }
    var nomeError by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var showClearSigConfirmDialog by remember { mutableStateOf(false) }

    var isListeningName by remember { mutableStateOf(false) }
    var isListeningDoc by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingAction?.invoke()
        } else {
            Toast.makeText(context, "Permissão de microfone necessária para comando de voz.", Toast.LENGTH_SHORT).show()
        }
        pendingAction = null
    }

    fun runWithMicPermission(action: () -> Unit) {
        if (com.example.util.PermissionUtils.hasRecordAudioPermission(context)) {
            action()
        } else {
            pendingAction = action
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    val hasUnsavedDialogChanges = remember(nome, documento, localAssinatura, recebedor) {
        nome.trim() != recebedor.nome.trim() ||
                documento.trim() != recebedor.documento.trim() ||
                localAssinatura != recebedor.assinatura
    }

    val handleAttemptDismiss: () -> Unit = {
        if (hasUnsavedDialogChanges) {
            showDiscardConfirmDialog = true
        } else {
            onDismiss()
        }
    }

    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Descartar alterações?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Você preencheu ou alterou dados do recebedor que ainda não foram confirmados. Deseja realmente fechar?",
                    fontSize = 13.5.sp,
                    color = Color(0xFF424242)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Descartar e Fechar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDiscardConfirmDialog = false }
                ) {
                    Text("Continuar Editando")
                }
            }
        )
    }

    if (showClearSigConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearSigConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Limpar assinatura salva?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "A assinatura cadastrada para este recebedor será removida. Deseja continuar?",
                    fontSize = 13.5.sp,
                    color = Color(0xFF424242)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        localAssinatura = ""
                        onClearSignature()
                        showClearSigConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Sim, Limpar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearSigConfirmDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    LaunchedEffect(recebedor.id) {
        nome = recebedor.nome
        documento = recebedor.documento
    }

    LaunchedEffect(recebedor.assinatura) {
        localAssinatura = recebedor.assinatura
    }

    AlertDialog(
        onDismissRequest = handleAttemptDismiss,
        title = {
            Text(
                text = if (isNew) "Adicionar Recebedor" else "Editar Recebedor",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = {
                        nome = it
                        nomeError = it.isBlank()
                    },
                    label = { Text("Nome do Recebedor *") },
                    placeholder = { Text("Ex: Maria da Silva") },
                    isError = nomeError,
                    supportingText = { if (nomeError) Text("Nome é obrigatório") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (!isListeningName) {
                                    runWithMicPermission {
                                        isListeningName = true
                                        com.example.util.SpeechHelper.startListening(
                                            context = context,
                                            onReady = { Toast.makeText(context, "Fale o nome...", Toast.LENGTH_SHORT).show() },
                                            onResult = { result ->
                                                isListeningName = false
                                                val processed = com.example.util.SpeechHelper.processSpokenName(result)
                                                if (processed.isNotBlank()) {
                                                    nome = processed
                                                    nomeError = false
                                                }
                                            },
                                            onError = { err ->
                                                isListeningName = false
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Falar Nome",
                                tint = if (isListeningName) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                val isDocValidState = remember(documento) {
                    if (documento.isBlank()) null else com.example.util.SpeechHelper.isValidDocument(documento)
                }

                OutlinedTextField(
                    value = documento,
                    onValueChange = { documento = it },
                    label = { Text("Documento (CPF / RG)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = when (isDocValidState) {
                            true -> Color(0xFF2E7D32)
                            false -> Color(0xFFD32F2F)
                            null -> MaterialTheme.colorScheme.primary
                        },
                        unfocusedBorderColor = when (isDocValidState) {
                            true -> Color(0xFF4CAF50)
                            false -> Color(0xFFE53935)
                            null -> MaterialTheme.colorScheme.outline
                        }
                    ),
                    supportingText = {
                        when (isDocValidState) {
                            true -> Text("Documento válido", color = Color(0xFF2E7D32), fontSize = 11.sp)
                            false -> Text("Documento inválido", color = Color(0xFFD32F2F), fontSize = 11.sp)
                            null -> null
                        }
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (!isListeningDoc) {
                                    runWithMicPermission {
                                        isListeningDoc = true
                                        com.example.util.SpeechHelper.startListening(
                                            context = context,
                                            onReady = { Toast.makeText(context, "Fale os números do documento...", Toast.LENGTH_SHORT).show() },
                                            onResult = { result ->
                                                isListeningDoc = false
                                                val processed = com.example.util.SpeechHelper.processSpokenDocument(result)
                                                if (processed.isNotBlank()) {
                                                    documento = processed
                                                }
                                            },
                                            onError = { err ->
                                                isListeningDoc = false
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Falar Documento",
                                tint = if (isListeningDoc) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Assinatura do Recebedor",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (localAssinatura.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                Text("Assinatura salva", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onCollectSignature(recebedor.id, nome, documento) },
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Refazer", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        showClearSigConfirmDialog = true
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Limpar", fontSize = 11.sp, color = Color.Red)
                                }
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { onCollectSignature(recebedor.id, nome, documento) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Coletar Assinatura")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nome.isBlank()) {
                        nomeError = true
                        return@Button
                    }
                    onSave(
                        recebedor.copy(
                            nome = AddressNormalizer.capitalizeWords(nome.trim()),
                            documento = documento.trim(),
                            assinatura = localAssinatura
                        )
                    )
                }
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = handleAttemptDismiss) {
                Text("Cancelar")
            }
        }
    )
}
