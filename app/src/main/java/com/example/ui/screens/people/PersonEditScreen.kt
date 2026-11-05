package com.example.ui.screens.people

import android.widget.Toast
import com.example.util.ToastHelper
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.DeliveryApp
import com.example.data.remote.CpfLookupService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.blur
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
import com.example.ui.components.DialogBlurEffect
import com.example.ui.components.SignatureCanvas
import com.example.ui.components.VoiceInputIconButton
import com.example.util.AddressNormalizer
import com.example.util.FeedbackHelper
import com.example.util.SpeechHelper
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
    val screenOpenTime = remember { android.os.SystemClock.elapsedRealtime() }
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    var address by remember { mutableStateOf(initialAddress) }
    var number by remember { mutableStateOf("") }
    var complement by remember { mutableStateOf("") }
    var neighborhood by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf(false) }

    var streetSuggestions by remember { mutableStateOf<List<com.example.data.local.entity.StreetCatalog>>(emptyList()) }
    LaunchedEffect(address) {
        val trimmed = address.trim()
        if (trimmed.length >= 2) {
            delay(200)
            streetSuggestions = viewModel.getStreetSuggestions(trimmed)
        } else {
            streetSuggestions = emptyList()
        }
    }

    var recebedoresList by remember { mutableStateOf<List<Recebedor>>(emptyList()) }

    // Rastreamento para identificar alterações não salvas na tela inteira
    var initialAddressLoaded by remember { mutableStateOf(initialAddress) }
    var initialNumberLoaded by remember { mutableStateOf("") }
    var initialComplementLoaded by remember { mutableStateOf("") }
    var initialNeighborhoodLoaded by remember { mutableStateOf("") }
    var initialRecebedoresLoaded by remember { mutableStateOf<List<Recebedor>>(emptyList()) }
    var showDiscardScreenConfirmDialog by remember { mutableStateOf(false) }

    val hasUnsavedScreenChanges by remember {
        androidx.compose.runtime.derivedStateOf {
            address.trim() != initialAddressLoaded.trim() ||
                    number.trim() != initialNumberLoaded.trim() ||
                    complement.trim() != initialComplementLoaded.trim() ||
                    neighborhood.trim() != initialNeighborhoodLoaded.trim() ||
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
                DialogBlurEffect()
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
                        com.example.util.RecipientDraftManager.clearScreenDraft(context, personId)
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
    var recebedorToDelete by remember { mutableStateOf<Pair<Int, Recebedor>?>(null) }
    var loadedPerson by remember { mutableStateOf<Person?>(null) }
    var moveCopyTargetRecebedor by remember { mutableStateOf<Recebedor?>(null) }

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
                loadedPerson = existing
                var cleanStreet = existing.endereco
                var cleanComp = existing.complemento

                if (cleanComp.contains("ABALHADORES", ignoreCase = true)) {
                    cleanComp = cleanComp.replace(Regex("""(?i)\b(?:torre\s+)?abalhadores\b"""), "").trim()
                    if (cleanStreet.trim().equals("Rua dos", ignoreCase = true) || cleanStreet.trim().equals("Trabalhadores", ignoreCase = true)) {
                        cleanStreet = "Rua dos Trabalhadores"
                    }
                }

                address = cleanStreet
                number = existing.numero
                complement = cleanComp
                neighborhood = existing.bairro

                initialAddressLoaded = cleanStreet
                initialNumberLoaded = existing.numero
                initialComplementLoaded = cleanComp
                initialNeighborhoodLoaded = existing.bairro

                val mainReceiver = Recebedor(
                    id = "main",
                    nome = existing.nome,
                    documento = existing.documento,
                    assinatura = existing.assinatura
                )
                val extraReceivers = Recebedor.listFromJson(existing.coRecebedoresJson)
                val loadedList = listOf(mainReceiver) + extraReceivers
                recebedoresList = loadedList
                initialRecebedoresLoaded = loadedList
            }
        } else {
            if (initialAddress.isNotBlank() && address.isBlank()) {
                val parsed = AddressNormalizer.parseAddressComponents(initialAddress)
                address = parsed.street
                number = parsed.number
                complement = parsed.complement
                neighborhood = parsed.neighborhood

                initialAddressLoaded = parsed.street
                initialNumberLoaded = parsed.number
                initialComplementLoaded = parsed.complement
                initialNeighborhoodLoaded = parsed.neighborhood
            }
            if (recebedoresList.isEmpty()) {
                val initialList = listOf(
                    Recebedor(id = "main", nome = "", documento = "", assinatura = "")
                )
                recebedoresList = initialList
                initialRecebedoresLoaded = initialList
            }
        }

        // Recupera rascunho não salvo localmente se o usuário fechou o app
        val screenDraft = com.example.util.RecipientDraftManager.getScreenDraft(context, personId)
        if (screenDraft != null) {
            if (screenDraft.address.isNotBlank()) address = screenDraft.address
            if (screenDraft.number.isNotBlank()) number = screenDraft.number
            if (screenDraft.complement.isNotBlank()) complement = screenDraft.complement
            if (screenDraft.neighborhood.isNotBlank()) neighborhood = screenDraft.neighborhood
            if (screenDraft.recebedoresJson.isNotBlank()) {
                val draftList = Recebedor.listFromJson(screenDraft.recebedoresJson)
                if (draftList.isNotEmpty()) {
                    recebedoresList = draftList
                }
            }
        }
    }

    // Salva automaticamente no storage local a cada alteração feita nos campos da tela (com debounce para fluidez)
    LaunchedEffect(address, number, complement, neighborhood, recebedoresList) {
        delay(600)
        com.example.util.RecipientDraftManager.saveScreenDraft(
            context = context,
            personId = personId,
            address = address,
            number = number,
            complement = complement,
            neighborhood = neighborhood,
            recebedoresJson = Recebedor.listToJson(recebedoresList)
        )
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
        val isAnyModalOrDialogOpen = showDiscardScreenConfirmDialog || editingRecebedor != null || isSignatureModalOpen || recebedorToDelete != null
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .blur(if (isAnyModalOrDialogOpen) 12.dp else 0.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 700.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // SEÇÃO DE ENDEREÇO E UNIDADE (CONDOMÍNIOS / BLOCOS / APTOS)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Endereço & Unidade",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Logradouro / Rua
                        OutlinedTextField(
                            value = address,
                            onValueChange = { newVal ->
                                // Se for colagem de texto longo com endereço completo em um formulário com número ainda vazio
                                if (newVal.length - address.length > 5 && (newVal.contains(",") || newVal.contains("-")) && number.isBlank()) {
                                    val parsed = AddressNormalizer.parseAddressComponents(newVal)
                                    if (parsed.street.isNotBlank()) {
                                        address = parsed.street
                                        if (parsed.number.isNotBlank()) number = parsed.number
                                        if (parsed.complement.isNotBlank() && complement.isBlank()) complement = parsed.complement
                                        if (parsed.neighborhood.isNotBlank() && neighborhood.isBlank()) neighborhood = parsed.neighborhood
                                    } else {
                                        address = newVal
                                    }
                                } else {
                                    address = newVal
                                }
                                addressError = address.isBlank()
                            },
                            label = { Text("Logradouro / Rua / Avenida *") },
                            placeholder = { Text("Ex: Rua das Flores") },
                            isError = addressError,
                            supportingText = { if (addressError) Text("O logradouro é obrigatório") },
                            trailingIcon = {
                                VoiceInputIconButton(
                                    hintPrompt = "Fale o nome da rua...",
                                    onResult = { spoken ->
                                        val parsed = AddressNormalizer.parseAddressComponents(spoken)
                                        if (parsed.street.isNotBlank() && (parsed.number.isNotBlank() || parsed.complement.isNotBlank() || parsed.neighborhood.isNotBlank())) {
                                            address = parsed.street
                                            if (parsed.number.isNotBlank()) number = parsed.number
                                            if (parsed.complement.isNotBlank() && complement.isBlank()) complement = parsed.complement
                                            if (parsed.neighborhood.isNotBlank() && neighborhood.isBlank()) neighborhood = parsed.neighborhood
                                        } else {
                                            val streetOnly = SpeechHelper.processSpokenStreet(spoken)
                                            if (streetOnly.isNotBlank()) {
                                                address = streetOnly
                                            }
                                        }
                                        addressError = address.isBlank()
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("person_address_input"),
                            singleLine = true
                        )

                        // Sugestões de Ruas (Catálogo da Cidade)
                        if (streetSuggestions.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                streetSuggestions.forEach { suggestion ->
                                    SuggestionChip(
                                        onClick = {
                                            address = suggestion.nomeCompleto
                                            if (neighborhood.isBlank() && suggestion.bairro.isNotBlank()) {
                                                neighborhood = suggestion.bairro
                                            }
                                            streetSuggestions = emptyList()
                                        },
                                        label = {
                                            Column {
                                                Text(
                                                    text = suggestion.nomeCompleto,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp
                                                )
                                                if (suggestion.bairro.isNotBlank()) {
                                                    Text(
                                                        text = suggestion.bairro,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.LocationCity,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Linha com Número e Complemento / Unidade
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = number,
                                onValueChange = { number = it.replace(".", "") },
                                label = { Text("Número") },
                                placeholder = { Text("Ex: 100") },
                                trailingIcon = {
                                    VoiceInputIconButton(
                                        hintPrompt = "Fale o número...",
                                        onResult = { spoken ->
                                            val num = SpeechHelper.processSpokenNumber(spoken)
                                            if (num.isNotBlank()) {
                                                number = num
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("person_number_input"),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = complement,
                                onValueChange = { complement = it },
                                label = { Text("Complemento / Unidade") },
                                placeholder = { Text("Ex: Bloco B Apto 24") },
                                trailingIcon = {
                                    VoiceInputIconButton(
                                        hintPrompt = "Fale o complemento...",
                                        onResult = { spoken ->
                                            val comp = SpeechHelper.processSpokenComplement(spoken)
                                            if (comp.isNotBlank()) {
                                                complement = comp
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .weight(1.7f)
                                    .testTag("person_complement_input"),
                                singleLine = true
                            )
                        }

                        // Chips rápidos para complemento
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Adicionar complemento rápido:",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val quickChips = listOf(
                                    "Apto ", "Bloco ", "Casa ", "Torre ", "Sala ", "Fundos", "Sobrado", "Lote ", "Quadra "
                                )
                                quickChips.forEach { chip ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.clickable {
                                            complement = if (complement.isBlank()) chip else "${complement.trim()} $chip"
                                        }
                                    ) {
                                        Text(
                                            text = "+ $chip",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Campo de Bairro
                        OutlinedTextField(
                            value = neighborhood,
                            onValueChange = { neighborhood = it },
                            label = { Text("Bairro (Opcional)") },
                            placeholder = { Text("Ex: Jardim das Rosas") },
                            trailingIcon = {
                                VoiceInputIconButton(
                                    hintPrompt = "Fale o bairro...",
                                    onResult = { spoken ->
                                        val br = SpeechHelper.processSpokenNeighborhood(spoken)
                                        if (br.isNotBlank()) {
                                            neighborhood = br
                                        }
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("person_neighborhood_input"),
                            singleLine = true
                        )

                        // Preview visual do endereço cadastrado
                        if (address.isNotBlank() || number.isNotBlank() || complement.isNotBlank()) {
                            val parsedPreview = remember(address, number, complement, neighborhood) {
                                AddressNormalizer.parseAddressComponents(address, number, complement, neighborhood)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Pré-visualização da identificação:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = parsedPreview.street.ifBlank { "Sem logradouro" },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (parsedPreview.number.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Nº ${parsedPreview.number}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (parsedPreview.hasComplement) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer
                                            ) {
                                                Text(
                                                    text = parsedPreview.unitBadgeText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

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
                                            val rDigits = rec.documento.filter { it.isDigit() }
                                            val isCpf = rDigits.length == 11
                                            val isVerified = isCpf && com.example.data.remote.CpfVerificationTracker.isCpfVerified(rDigits)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = if (isCpf) "CPF: ${rec.documento}" else "Doc: ${rec.documento}",
                                                    fontSize = 12.sp,
                                                    color = Color.Gray
                                                )
                                                if (isVerified) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFFE8F5E9)
                                                    ) {
                                                        Text(
                                                            text = "✓ Verificado na API",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF2E7D32),
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
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
                                                moveCopyTargetRecebedor = rec
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Mover ou Copiar Morador",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
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
                                                    recebedorToDelete = Pair(index, rec)
                                                } else {
                                                    ToastHelper.show(context, "É necessário manter pelo menos um recebedor!")
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
                        val trimmedAddress = address.trim()
                        if (trimmedAddress.isBlank()) {
                            addressError = true
                            ToastHelper.show(context, "Informe o endereço completo (rua e número)!")
                            return@Button
                        }

                        val parsedAddr = AddressNormalizer.parseAddressComponents(trimmedAddress, number.trim(), complement.trim(), neighborhood.trim())
                        val finalStreet = if (parsedAddr.street.isNotBlank()) parsedAddr.street else trimmedAddress
                        val finalNum = if (number.trim().isNotBlank()) number.trim() else parsedAddr.number

                        val stripped = AddressNormalizer.stripStreetType(finalStreet)
                        if (finalStreet.isBlank() || stripped.isBlank() || stripped.all { it.isDigit() || it == '-' || it == ',' || it == '.' }) {
                            addressError = true
                            ToastHelper.show(context, "Informe o nome da rua junto com o número!")
                            return@Button
                        }

                        if (finalNum.isBlank()) {
                            ToastHelper.show(context, "Informe o número do imóvel junto com o nome da rua!")
                            return@Button
                        }

                        if (recebedoresList.isEmpty()) {
                            ToastHelper.show(context, "Adicione pelo menos um recebedor!")
                            return@Button
                        }

                        val formattedRecebedores = recebedoresList.map {
                            it.copy(nome = AddressNormalizer.capitalizeWords(it.nome.trim()))
                        }
                        val primaryRecebedor = formattedRecebedores.first()
                        if (primaryRecebedor.nome.isBlank()) {
                            ToastHelper.show(context, "O primeiro recebedor deve ter um nome válido!")
                            return@Button
                        }

                        // Filtrar se algum recebedor ficou com o nome em branco
                        val invalid = formattedRecebedores.any { it.nome.isBlank() }
                        if (invalid) {
                            ToastHelper.show(context, "Todos os recebedores devem ter nomes preenchidos!")
                            return@Button
                        }

                        val extraList = if (formattedRecebedores.size > 1) {
                            formattedRecebedores.subList(1, formattedRecebedores.size)
                        } else {
                            emptyList()
                        }

                        val rawComp = if (parsedAddr.complement.isNotBlank() && complement.isBlank()) parsedAddr.complement else complement.trim()
                        val finalComp = AddressNormalizer.formatComplementToken(rawComp).ifBlank { rawComp }
                        val finalBairro = if (parsedAddr.neighborhood.isNotBlank() && neighborhood.isBlank()) parsedAddr.neighborhood else neighborhood.trim()

                        val person = Person(
                            id = personId,
                            nome = primaryRecebedor.nome,
                            documento = primaryRecebedor.documento.trim(),
                            endereco = finalStreet,
                            numero = finalNum,
                            complemento = finalComp,
                            bairro = finalBairro,
                            cidade = "",
                            uf = "",
                            observacao = "",
                            assinatura = primaryRecebedor.assinatura,
                            coRecebedoresJson = Recebedor.listToJson(extraList)
                        )

                        val formDuration = android.os.SystemClock.elapsedRealtime() - screenOpenTime
                        viewModel.savePerson(person, durationMs = formDuration) {
                            com.example.util.RecipientDraftManager.clearScreenDraft(context, personId)
                            FeedbackHelper.triggerSuccess(context)
                            ToastHelper.show(context, "Destinatário salvo com sucesso!")
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

    // Diálogo de confirmação para remoção de recebedor dentro do endereço
    if (recebedorToDelete != null) {
        val (targetIndex, targetRec) = recebedorToDelete!!
        AlertDialog(
            onDismissRequest = { recebedorToDelete = null },
            title = {
                Text(
                    text = "Remover Recebedor",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                        text = "Deseja realmente remover este recebedor do endereço?",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "👤 ${targetRec.nome.ifBlank { "Sem nome" }}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (targetRec.documento.isNotBlank()) {
                                Text(
                                    text = "Doc: ${targetRec.documento}",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "✓ O endereço e os outros ${recebedoresList.size - 1} recebedor(es) serão mantidos normalmente.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
            confirmButton = {
                Button(
                    onClick = {
                        recebedoresList = recebedoresList.filterIndexed { i, _ -> i != targetIndex }
                        recebedorToDelete = null
                        ToastHelper.show(context, "Recebedor removido.")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("REMOVER", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { recebedorToDelete = null }) {
                    Text("CANCELAR")
                }
            }
        )
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
                },
                onMoveOrCopy = { targetRec ->
                    moveCopyTargetRecebedor = targetRec
                }
            )
        }
    }

    // Modal de Mover / Copiar Recebedor para outro endereço
    if (moveCopyTargetRecebedor != null) {
        MoveOrCopyReceiverDialog(
            receiver = moveCopyTargetRecebedor!!,
            sourcePerson = loadedPerson,
            currentAddress = address,
            viewModel = viewModel,
            onDismiss = { moveCopyTargetRecebedor = null },
            onSuccess = { isMove ->
                if (isMove) {
                    val targetId = moveCopyTargetRecebedor?.id
                    recebedoresList = recebedoresList.filter { it.id != targetId }
                }
                moveCopyTargetRecebedor = null
                editingRecebedor = null
            }
        )
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
                isDarkTheme = false,
                onSignatureConfirmed = { signature ->
                    val sigJson = signature.toJson()
                    editingRecebedor = editingRecebedor?.copy(assinatura = sigJson)
                    isSignatureModalOpen = false
                    signatureTargetId = null
                    ToastHelper.show(context, "Assinatura gravada!")
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
    onClearSignature: () -> Unit,
    onMoveOrCopy: ((Recebedor) -> Unit)? = null
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

    val dialogScope = rememberCoroutineScope()
    var isLookingUpCpfOnline by remember { mutableStateOf(false) }
    var cpfLookupMessage by remember { mutableStateOf<String?>(null) }
    var lastAutoLookedUpCpf by remember { mutableStateOf("") }
    var savedRecipientFoundByName by remember { mutableStateOf<com.example.util.SavedRecipientInfo?>(null) }
    var apiNameCorrectionSuggestion by remember { mutableStateOf<String?>(null) }
    var officialApiName by remember { mutableStateOf<String?>(null) }

    val performCpfLookup: (String) -> Unit = { rawDoc ->
        val digits = rawDoc.filter { it.isDigit() }
        if (digits.length == 11) {
            isLookingUpCpfOnline = true
            cpfLookupMessage = "Consultando CPF na API..."
            dialogScope.launch(Dispatchers.IO) {
                // 1º PRIORIDADE ABSOLUTA: Consulta direta na API oficial do CPF
                val result = CpfLookupService.lookupCpf(digits)
                val localSaved = com.example.util.SavedRecipientLookupHelper.findByDocument(rawDoc)

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    isLookingUpCpfOnline = false

                    if (result.success && result.nome.isNotBlank()) {
                        // API encontrou com sucesso -> Prioridade máxima para o nome oficial da API
                        val apiNome = result.nome
                        officialApiName = apiNome
                        lastAutoLookedUpCpf = digits
                        com.example.data.remote.CpfVerificationTracker.markCpfVerified(digits, apiNome)

                        var pulledSig = false
                        if (localAssinatura.isBlank() && localSaved != null && localSaved.assinatura.isNotBlank()) {
                            localAssinatura = localSaved.assinatura
                            pulledSig = true
                        }

                        val currentTyped = nome.trim()
                        val isDiff = currentTyped.isNotBlank() &&
                                com.example.util.AddressNormalizer.normalize(currentTyped) != com.example.util.AddressNormalizer.normalize(apiNome)

                        if (isDiff) {
                            apiNameCorrectionSuggestion = apiNome
                            cpfLookupMessage = "⚠️ Divergência: Nome na API é '$apiNome'"
                            ToastHelper.showLong(context, "Divergência com a base oficial! Sugestão disponível para correção.")
                        } else {
                            nome = apiNome
                            nomeError = false
                            apiNameCorrectionSuggestion = null
                            cpfLookupMessage = buildString {
                                append("✓ Nome obtido via API: $apiNome")
                                if (pulledSig) append(" + Assinatura do banco")
                            }
                            ToastHelper.show(context, "Nome obtido pela API: $apiNome")
                        }
                    } else {
                        // 2º PRIORIDADE: FALLBACK NO BANCO LOCAL (apenas se a API falhar ou não encontrar)
                        if (localSaved != null && localSaved.nome.isNotBlank()) {
                            nome = localSaved.nome
                            nomeError = false
                            lastAutoLookedUpCpf = digits
                            var pulledSig = false
                            if (localAssinatura.isBlank() && localSaved.assinatura.isNotBlank()) {
                                localAssinatura = localSaved.assinatura
                                pulledSig = true
                            }
                            cpfLookupMessage = buildString {
                                append("✓ Puxado do banco local: ${localSaved.nome}")
                                if (pulledSig) append(" + Assinatura")
                            }
                            ToastHelper.show(context, "Nome recuperado do banco local: ${localSaved.nome}")
                        } else {
                            val err = result.errorMessage ?: "CPF não localizado na API"
                            cpfLookupMessage = "⚠️ $err"
                            ToastHelper.show(context, err)
                        }
                    }
                }
            }
        } else if (rawDoc.trim().length >= 5) {
            // Documento com formato de RG (ou outro doc) -> Busca recebedor no banco local em background
            dialogScope.launch(Dispatchers.IO) {
                val localSaved = com.example.util.SavedRecipientLookupHelper.findByDocument(rawDoc)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (localSaved != null && localSaved.nome.isNotBlank()) {
                        nome = localSaved.nome
                        nomeError = false
                        lastAutoLookedUpCpf = rawDoc.trim()
                        var pulledSig = false
                        if (localAssinatura.isBlank() && localSaved.assinatura.isNotBlank()) {
                            localAssinatura = localSaved.assinatura
                            pulledSig = true
                        }
                        cpfLookupMessage = buildString {
                            append("✓ Puxado do banco local via RG: ${localSaved.nome}")
                            if (pulledSig) append(" + Assinatura")
                        }
                        ToastHelper.show(context, "Recebedor localizado no banco pelo RG!")
                    }
                }
            }
        } else {
            ToastHelper.show(context, "Informe o documento para pesquisar")
        }
    }

    // Ao abrir para editar recebedor que já possui CPF (ou quando o documento for carregado),
    // consulta automaticamente a API para verificar se o nome cadastrado diverge do oficial
    LaunchedEffect(recebedor.id, recebedor.documento) {
        officialApiName = null
        apiNameCorrectionSuggestion = null
        cpfLookupMessage = null
        lastAutoLookedUpCpf = ""
        val digits = documento.filter { it.isDigit() }
        if (digits.length == 11) {
            performCpfLookup(digits)
        }
    }

    // Mantém a sugestão de correção ativa apenas enquanto o documento corresponder exatamente ao CPF consultado
    LaunchedEffect(nome, officialApiName, documento) {
        val currentCpfDigits = documento.filter { it.isDigit() }
        val offName = officialApiName
        if (offName != null && offName.isNotBlank() && currentCpfDigits == lastAutoLookedUpCpf && currentCpfDigits.length == 11) {
            val isDiff = nome.trim().isNotBlank() &&
                    com.example.util.AddressNormalizer.normalize(nome.trim()) != com.example.util.AddressNormalizer.normalize(offName)
            if (isDiff) {
                apiNameCorrectionSuggestion = offName
                cpfLookupMessage = "⚠️ Divergência: Nome na API é '$offName'"
            } else {
                apiNameCorrectionSuggestion = null
                if (nome.trim().isNotBlank()) {
                    cpfLookupMessage = "✓ Nome verificado na API: $offName"
                }
            }
        } else {
            apiNameCorrectionSuggestion = null
            if (currentCpfDigits != lastAutoLookedUpCpf) {
                officialApiName = null
                if (cpfLookupMessage?.startsWith("✓") == true || cpfLookupMessage?.startsWith("⚠️") == true) {
                    cpfLookupMessage = null
                }
            }
        }
    }

    LaunchedEffect(nome) {
        val q = nome.trim()
        // Evita disparar busca redundante e congelamento quando o documento já foi informado/encontrado
        if (q.length >= 3 && documento.isBlank() && lastAutoLookedUpCpf.isBlank()) {
            delay(350)
            try {
                val match = com.example.util.SavedRecipientLookupHelper.findByName(q)
                if (match != null && (match.documento.isNotBlank() || match.assinatura.isNotBlank())) {
                    savedRecipientFoundByName = match
                    val isExact = com.example.util.AddressNormalizer.normalize(match.nome) == com.example.util.AddressNormalizer.normalize(q)
                    if (isExact) {
                        if (documento.isBlank() && match.documento.isNotBlank()) {
                            documento = match.documento
                        }
                        if (localAssinatura.isBlank() && match.assinatura.isNotBlank()) {
                            localAssinatura = match.assinatura
                        }
                    }
                } else {
                    savedRecipientFoundByName = null
                }
            } catch (_: Throwable) {
                savedRecipientFoundByName = null
            }
        } else if (documento.isNotBlank()) {
            savedRecipientFoundByName = null
        } else {
            savedRecipientFoundByName = null
        }
    }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingAction?.invoke()
        } else {
            ToastHelper.show(context, "Permissão de microfone necessária para comando de voz.")
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
                DialogBlurEffect()
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
                DialogBlurEffect()
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
            DialogBlurEffect()
            Text(
                text = if (isNew) "Adicionar Recebedor" else "Editar Recebedor",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            SelectionContainer {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = {
                        nome = it
                        nomeError = it.isBlank()
                        if (apiNameCorrectionSuggestion != null && it.trim().equals(apiNameCorrectionSuggestion!!.trim(), ignoreCase = true)) {
                            apiNameCorrectionSuggestion = null
                        }
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
                                            onReady = { ToastHelper.show(context, "Fale o nome...") },
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
                                                ToastHelper.show(context, err)
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

                if (apiNameCorrectionSuggestion != null) {
                    val officialName = apiNameCorrectionSuggestion!!
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF8E1),
                        border = BorderStroke(1.dp, Color(0xFFFFB300)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                nome = officialName
                                nomeError = false
                                apiNameCorrectionSuggestion = null
                                ToastHelper.show(context, "Nome corrigido para o oficial!")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "⚠️ Divergência com a base oficial da API",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB71C1C)
                                )
                                Text(
                                    text = "Cadastrado / Digitado: '${nome}'",
                                    fontSize = 10.sp,
                                    color = Color(0xFF5D4037)
                                )
                                Text(
                                    text = "Oficial na API: '${officialName}'",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                                Text(
                                    text = "👉 Toque aqui para corrigir para o nome oficial",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                    }
                }

                if (savedRecipientFoundByName != null) {
                    val match = savedRecipientFoundByName!!
                    val hasDoc = match.documento.isNotBlank()
                    val hasSig = match.assinatura.isNotBlank()
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                nome = match.nome
                                nomeError = false
                                if (hasDoc) {
                                    documento = match.documento
                                }
                                if (hasSig) {
                                    localAssinatura = match.assinatura
                                }
                                savedRecipientFoundByName = null
                                ToastHelper.show(context, "Dados recuperados do banco!")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Recebedor salvo: ${match.nome}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                                Text(
                                    text = buildString {
                                        if (hasDoc) append("Doc: ${match.documento} ")
                                        if (hasSig) append("• ✓ Assinatura disponível ")
                                        append("(Toque para puxar)")
                                    },
                                    fontSize = 10.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }

                com.example.ui.components.CpfApiQuotaCard()

                val isDocValidState = remember(documento) {
                    if (documento.isBlank()) null else com.example.util.SpeechHelper.isValidDocument(documento)
                }

                OutlinedTextField(
                    value = documento,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '/' || it == ' ' }.uppercase(java.util.Locale.ROOT)
                        documento = filtered
                        val digits = filtered.filter { it.isDigit() }
                        // Prioridade máxima da API: pesquisa automaticamente assim que atinge 11 dígitos de CPF
                        if (digits.length == 11 && digits != lastAutoLookedUpCpf) {
                            performCpfLookup(digits)
                        } else if (filtered.any { it.isLetter() } && filtered.length >= 6 && filtered != lastAutoLookedUpCpf) {
                            // Documento com letras (ex: RG com sigla estadual MG-12345678)
                            performCpfLookup(filtered)
                        }
                    },
                    label = { Text("Documento (CPF / RG)") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Text
                    ),
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
                        if (cpfLookupMessage != null) {
                            Text(
                                text = cpfLookupMessage!!,
                                color = if (cpfLookupMessage!!.startsWith("✓")) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            val formDocDigits = documento.filter { it.isDigit() }
                            if (formDocDigits.length == 11 && com.example.data.remote.CpfVerificationTracker.isCpfVerified(formDocDigits)) {
                                Text("✓ CPF verificado e validado na base oficial da API", color = Color(0xFF2E7D32), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                when (isDocValidState) {
                                    true -> Text("Documento válido", color = Color(0xFF2E7D32), fontSize = 11.sp)
                                    false -> Text("Documento inválido", color = Color(0xFFD32F2F), fontSize = 11.sp)
                                    null -> null
                                }
                            }
                        }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLookingUpCpfOnline) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp).padding(end = 4.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                val digits = documento.filter { it.isDigit() }
                                if (digits.length == 11 || documento.trim().length >= 5) {
                                    IconButton(
                                        onClick = { performCpfLookup(documento) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Consultar Documento",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (!isListeningDoc) {
                                        runWithMicPermission {
                                            isListeningDoc = true
                                            com.example.util.SpeechHelper.startListening(
                                                context = context,
                                                onReady = { ToastHelper.show(context, "Fale os números do documento...") },
                                                onResult = { result ->
                                                    isListeningDoc = false
                                                    val processed = com.example.util.SpeechHelper.processSpokenDocument(result)
                                                    if (processed.isNotBlank()) {
                                                        val upper = processed.uppercase(java.util.Locale.ROOT)
                                                        documento = upper
                                                        val digits = upper.filter { it.isDigit() }
                                                        if (digits.length == 11 && digits != lastAutoLookedUpCpf) {
                                                            performCpfLookup(digits)
                                                        }
                                                    }
                                                },
                                                onError = { err ->
                                                    isListeningDoc = false
                                                    ToastHelper.show(context, err)
                                                }
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Falar Documento",
                                    tint = if (isListeningDoc) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Botão para copiar texto do morador para área de transferência
                OutlinedButton(
                    onClick = {
                        val textToCopy = buildString {
                            append("Nome: ").append(nome.ifBlank { "Sem nome" })
                            if (documento.isNotBlank()) append("\nDocumento: ").append(documento)
                        }
                        com.example.util.ClipboardHelper.copyToClipboard(context, "Morador", textToCopy)
                        ToastHelper.show(context, "Dados do morador copiados!")
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar Nome / Documento", fontSize = 12.5.sp)
                }

                // Botão para Mover ou Copiar Morador para outro endereço
                if (onMoveOrCopy != null) {
                    OutlinedButton(
                        onClick = {
                            val currentRec = recebedor.copy(
                                nome = AddressNormalizer.capitalizeWords(nome.trim()),
                                documento = documento.trim(),
                                assinatura = localAssinatura
                            )
                            onMoveOrCopy(currentRec)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mover / Copiar para Outro Endereço", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveOrCopyReceiverDialog(
    receiver: Recebedor,
    sourcePerson: Person?,
    currentAddress: String,
    viewModel: PeopleViewModel,
    onDismiss: () -> Unit,
    onSuccess: (isMove: Boolean) -> Unit
) {
    val context = LocalContext.current
    var isMove by remember { mutableStateOf(true) } // true = Mover, false = Copiar
    var targetStreet by remember { mutableStateOf("") }
    var targetNumber by remember { mutableStateOf("") }
    var targetComplement by remember { mutableStateOf("") }
    var targetBairro by remember { mutableStateOf("") }
    var streetError by remember { mutableStateOf(false) }

    val allPersons by viewModel.persons.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val suggestedPersons = remember(allPersons, searchQuery) {
        if (searchQuery.isBlank()) {
            allPersons.take(4)
        } else {
            allPersons.filter { p ->
                p.endereco.contains(searchQuery, ignoreCase = true) ||
                        p.numero.contains(searchQuery, ignoreCase = true) ||
                        p.bairro.contains(searchQuery, ignoreCase = true) ||
                        p.nome.contains(searchQuery, ignoreCase = true)
            }.take(5)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogBlurEffect()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMove) "Mover Morador" else "Copiar Morador",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info do morador
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "👤 ${receiver.nome.ifBlank { "Sem nome" }}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (receiver.documento.isNotBlank()) {
                            Text(
                                text = "Doc: ${receiver.documento}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // Opção de Ação (Mover vs Copiar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isMove,
                        onClick = { isMove = true },
                        label = { Text("🚚 Mover (Transferir)", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isMove,
                        onClick = { isMove = false },
                        label = { Text("📋 Copiar (Duplicar)", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = if (isMove)
                        "Ao mover, este morador será transferido para o novo endereço."
                    else
                        "Ao copiar, este morador será duplicado para o novo endereço, mantendo também o cadastro original.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Text(
                    text = "Endereço de Destino",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Busca rápida de endereços existentes
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar Endereço Salvo...") },
                    placeholder = { Text("Digite rua, número ou nome...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (suggestedPersons.isNotEmpty()) {
                    Text(
                        text = "Toque em um endereço para selecionar:",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        suggestedPersons.forEach { person ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        targetStreet = person.endereco
                                        targetNumber = person.numero
                                        targetComplement = person.complemento
                                        targetBairro = person.bairro
                                        searchQuery = ""
                                        streetError = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "${person.endereco}${if (person.numero.isNotBlank()) ", ${person.numero}" else ""}",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (person.bairro.isNotBlank() || person.complemento.isNotBlank()) {
                                            Text(
                                                text = "${person.complemento} ${person.bairro}".trim(),
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Preenchimento manual do endereço de destino
                OutlinedTextField(
                    value = targetStreet,
                    onValueChange = {
                        targetStreet = it
                        streetError = it.isBlank()
                    },
                    label = { Text("Rua / Logradouro de Destino *") },
                    isError = streetError,
                    supportingText = { if (streetError) Text("Informe a rua de destino") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = targetNumber,
                        onValueChange = { targetNumber = it },
                        label = { Text("Número") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetComplement,
                        onValueChange = { targetComplement = it },
                        label = { Text("Complemento") },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = targetBairro,
                    onValueChange = { targetBairro = it },
                    label = { Text("Bairro") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (targetStreet.isBlank()) {
                        streetError = true
                        return@Button
                    }
                    viewModel.moveOrCopyReceiver(
                        sourcePerson = sourcePerson,
                        receiver = receiver,
                        isMove = isMove,
                        targetAddress = targetStreet,
                        targetNumber = targetNumber,
                        targetComplement = targetComplement,
                        targetBairro = targetBairro,
                        onComplete = {
                            FeedbackHelper.triggerSuccess(context)
                            val actionText = if (isMove) "movido" else "copiado"
                            ToastHelper.show(context, "Morador $actionText com sucesso!")
                            onSuccess(isMove)
                        }
                    )
                }
            ) {
                Text(if (isMove) "Mover Morador" else "Copiar Morador", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
