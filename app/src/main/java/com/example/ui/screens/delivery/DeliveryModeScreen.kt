package com.example.ui.screens.delivery

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryModeScreen(
    viewModel: DeliveryModeViewModel,
    onNavigateBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var searchDialogOpen by remember { mutableStateOf(false) }

    var isListeningAddress by remember { mutableStateOf(false) }
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingMicAction?.invoke()
        } else {
            Toast.makeText(context, "Permissão do microfone necessária para falar o endereço.", Toast.LENGTH_SHORT).show()
        }
        pendingMicAction = null
    }

    fun runWithMicPermission(action: () -> Unit) {
        if (com.example.util.PermissionUtils.hasRecordAudioPermission(context)) {
            action()
        } else {
            pendingMicAction = action
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MODO DE ENTREGA",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Cartão de Endereço Atual
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ENDEREÇO ATUAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.currentAddress,
                            onValueChange = { viewModel.updateAddress(it) },
                            placeholder = { Text("Ex: Cel. Francisco Paulino da Costa, 630") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delivery_address_input"),
                            trailingIcon = {
                                Row {
                                    IconButton(
                                        onClick = {
                                            if (!isListeningAddress) {
                                                runWithMicPermission {
                                                    isListeningAddress = true
                                                    com.example.util.SpeechHelper.startListening(
                                                        context = context,
                                                        onReady = { Toast.makeText(context, "Fale o endereço...", Toast.LENGTH_SHORT).show() },
                                                        onResult = { result ->
                                                            isListeningAddress = false
                                                            if (result.isNotBlank()) {
                                                                viewModel.updateAddress(result)
                                                                viewModel.searchPersonsForAddress(result)
                                                            }
                                                        },
                                                        onError = { err ->
                                                            isListeningAddress = false
                                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Falar Endereço",
                                            tint = if (isListeningAddress) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    }
                                    IconButton(onClick = { viewModel.searchPersonsForAddress(state.currentAddress) }) {
                                        Icon(Icons.Default.Search, contentDescription = "Pesquisar")
                                    }
                                }
                            },
                            singleLine = true
                        )
                    }
                }
            }

            // Resultados de múltiplos candidatos se houver
            if (state.candidatePersons.size > 1) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${state.candidatePersons.size} destinatários compatíveis encontrados:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            state.candidatePersons.forEach { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectPerson(p) }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${p.nome} (${p.documento})",
                                        fontSize = 13.sp,
                                        fontWeight = if (p.id == state.selectedPerson?.id) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (p.id == state.selectedPerson?.id) {
                                        Text("Selecionado", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Cartão de Destinatário Identificado
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DESTINATÁRIO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (state.selectedPerson != null) {
                                Text(
                                    text = "✓ DESTINATÁRIO ENCONTRADO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (state.selectedPerson != null) {
                            val person = state.selectedPerson!!
                            Text(
                                text = person.nome,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Documento: ${person.documento.ifBlank { "Não informado" }}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Nenhum destinatário selecionado para este endereço.",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { onNavigate(Screen.PersonEdit.createRoute(0)) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CADASTRAR NOVO DESTINATÁRIO", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Status da Assinatura Coletada
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "ASSINATURA DIGITAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (state.currentSignature != null) "✓ Assinatura coletada" else "Pendente de coleta",
                                fontSize = 13.sp,
                                color = if (state.currentSignature != null) Color(0xFF2E7D32) else Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = { onNavigate(Screen.Signature.createRoute(0, state.selectedPerson?.id ?: 0)) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (state.currentSignature != null) "Refazer" else "Coletar")
                        }
                    }
                }
            }

            // Botões de Ação Rápida
            item {
                Text(
                    text = "AÇÕES DE ENTREGA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Button(
                    onClick = {
                        val msg = viewModel.fillFields()
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    enabled = state.selectedPerson != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("fill_fields_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🚀 PREENCHER NO APP", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            state.selectedPerson?.let {
                                viewModel.copyData(context, "Nome", it.nome)
                            }
                        },
                        enabled = state.selectedPerson != null,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar Nome", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            state.selectedPerson?.let {
                                viewModel.copyData(context, "Documento", it.documento)
                            }
                        },
                        enabled = state.selectedPerson != null && state.selectedPerson!!.documento.isNotBlank(),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar Doc", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (state.currentAddress.isNotBlank()) {
                                viewModel.copyData(context, "Endereço", state.currentAddress)
                            }
                        },
                        enabled = state.currentAddress.isNotBlank(),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar End", fontSize = 11.sp)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        viewModel.finishDelivery {
                            Toast.makeText(context, "Entrega finalizada com sucesso!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("finish_delivery_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("✓ FINALIZAR ENTREGA", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
    }
}
}
