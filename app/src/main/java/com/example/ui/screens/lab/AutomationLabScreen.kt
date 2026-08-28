package com.example.ui.screens.lab

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityAutomationEngine
import com.example.data.model.Point
import com.example.data.model.SignatureData
import com.example.data.model.Stroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationLabScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val autoState by AccessibilityAutomationEngine.state.collectAsState()

    // Campos simulados do laboratório
    var labReceiverName by remember { mutableStateOf("") }
    var labDocument by remember { mutableStateOf("") }
    var testReportMessage by remember { mutableStateOf("Nenhum teste executado ainda.") }
    var testSuccess by remember { mutableStateOf<Boolean?>(null) }
    var selectedLabSpeed by remember { mutableStateOf("ULTRA_SLOW") }

    // Simulação de assinatura de teste para automação
    val mockTestSignature = remember {
        SignatureData(
            strokes = listOf(
                Stroke(
                    points = listOf(
                        Point(100f, 150f, 1000L),
                        Point(200f, 120f, 1050L),
                        Point(350f, 220f, 1100L),
                        Point(500f, 100f, 1150L)
                    ),
                    strokeWidth = 6f,
                    color = 0xFF1565C0.toInt()
                ),
                Stroke(
                    points = listOf(
                        Point(120f, 260f, 1200L),
                        Point(450f, 260f, 1250L)
                    ),
                    strokeWidth = 4f,
                    color = 0xFF1565C0.toInt()
                )
            ),
            canvasWidth = 600f,
            canvasHeight = 400f
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laboratório de Automação", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Cartão de Explicação
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Ambiente de Teste Local",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Simula a estrutura de formulários de apps de entrega para validar a detecção de nós e o preenchimento.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Status do Motor de Acessibilidade
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "STATUS DO MOTOR DE ACESSIBILIDADE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (autoState.isServiceActive) Color(0xFF2E7D32) else Color(0xFFC62828), RoundedCornerShape(5.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (autoState.isServiceActive) "Serviço Ativo e Conectado ao Android" else "Serviço Desativado no Sistema",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (autoState.isServiceActive) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }

            // FORMULÁRIO SIMULADO
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FORMULÁRIO SIMULADO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Campo de Nome Simulado com semântica de formulário externo
                    OutlinedTextField(
                        value = labReceiverName,
                        onValueChange = { labReceiverName = it },
                        label = { Text("Nome do recebedor") },
                        placeholder = { Text("Ex: Carlos Mendes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "nome recebedor destinatario" }
                            .testTag("lab_name_field"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Campo de Documento Simulado
                    OutlinedTextField(
                        value = labDocument,
                        onValueChange = { labDocument = it },
                        label = { Text("CPF / Documento") },
                        placeholder = { Text("Ex: 987.654.321-99") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "documento cpf rg recebedor" }
                            .testTag("lab_document_field"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Área Simulada de Assinatura
                    Text(text = "Área de Assinatura Simulada", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                            .semantics { contentDescription = "assinatura area assine aqui signature canvas" }
                            .testTag("lab_signature_area"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ ÁREA DE ASSINATURA SIMULADA ]",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Ações de Teste
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "EXECUTAR TESTES DE AUTOMAÇÃO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val result = AccessibilityAutomationEngine.fillFields(
                                "Carlos Mendes (Teste)",
                                "987.654.321-99"
                            )
                            testReportMessage = result.message
                            testSuccess = result.nameFilled || result.documentFilled
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("lab_test_fill_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TESTAR PREENCHIMENTO AUTOMÁTICO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Seleção de velocidade para teste
                    Text(text = "Velocidade do Desenho para Teste:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ULTRA_SLOW" to "🐢 Ultra Lenta",
                            "SLOW" to "🚶 Lenta",
                            "NORMAL" to "⚡ Normal"
                        ).forEach { (mode, label) ->
                            val isSel = selectedLabSpeed == mode
                            Button(
                                onClick = { selectedLabSpeed = mode },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(label, fontSize = 10.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                AccessibilityAutomationEngine.dispatchSignatureGestures(mockTestSignature, speedMode = selectedLabSpeed) { ok, msg ->
                                    testReportMessage = msg
                                    testSuccess = ok
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Testar Gestos", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                labReceiverName = ""
                                labDocument = ""
                                testReportMessage = "Campos limpos com sucesso."
                                testSuccess = null
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpar Campos", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Relatório de Diagnóstico do Teste
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "RELATÓRIO DO TESTE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (testSuccess) {
                            true -> Color(0xFFE8F5E9)
                            false -> Color(0xFFFFEBEE)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (testSuccess) {
                                    true -> Icons.Default.CheckCircle
                                    false -> Icons.Default.Warning
                                    else -> Icons.Default.Science
                                },
                                contentDescription = null,
                                tint = when (testSuccess) {
                                    true -> Color(0xFF2E7D32)
                                    false -> Color(0xFFC62828)
                                    else -> Color.Gray
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = testReportMessage,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = when (testSuccess) {
                                    true -> Color(0xFF1B5E20)
                                    false -> Color(0xFFB71C1C)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}
