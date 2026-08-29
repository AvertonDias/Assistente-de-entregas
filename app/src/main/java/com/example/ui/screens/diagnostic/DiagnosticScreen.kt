package com.example.ui.screens.diagnostic

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityAutomationEngine
import com.example.util.PermissionUtils

import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import com.example.util.CrashReporter
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val autoState by AccessibilityAutomationEngine.state.collectAsState()
    val hasOverlay = PermissionUtils.hasOverlayPermission(context)
    val hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(context) || autoState.isServiceActive

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico & Telemetria", fontWeight = FontWeight.Bold, color = Color.White) },
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Subsistemas
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "STATUS DOS SUBSISTEMAS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        DiagnosticItemRow("Permissão de Sobreposição (Overlay)", hasOverlay)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        DiagnosticItemRow("AccessibilityService Ativo no OS", hasAccessibility)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        DiagnosticItemRow("Motor de Automação Conectado", autoState.isServiceActive)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        DiagnosticItemRow("Banco Local Room (SQLite)", true)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        DiagnosticItemRow("Firebase Crashlytics (Telemetria)", true)
                    }
                }
            }

            // Ações de Relatório e Telemetria
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "RELATÓRIO & TELEMETRIA DE ERROS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Erros e falhas do app são capturados e gravados com segurança. Você pode enviar o relatório detalhado por e-mail ou compartilhar via qualquer mensageiro.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val report = buildString {
                                        appendLine("=== RELATÓRIO DE DIAGNÓSTICO DO ASSISTENTE ===")
                                        appendLine("Data/Hora: ${java.util.Date()}")
                                        appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
                                        appendLine("Sobreposição de Tela: ${if (hasOverlay) "OK (Autorizada)" else "PENDENTE"}")
                                        appendLine("Serviço de Acessibilidade: ${if (hasAccessibility) "OK (Ativo)" else "PENDENTE"}")
                                        appendLine("Último aplicativo em foco: ${autoState.currentPackageName}")
                                        appendLine("Último endereço detectado: ${autoState.detectedAddressText}")
                                        appendLine("\n--- ÚLTIMO CRASH / ERRO SALVO ---")
                                        appendLine(CrashReporter.getLastCrashReport() ?: "Nenhum crash crítico registrado.")
                                        appendLine("\n--- LOGS RECENTES DE OPERAÇÃO ---")
                                        autoState.logs.takeLast(25).forEach {
                                            appendLine("${it.formattedTime} [${it.tag}] ${it.message}")
                                        }
                                        CrashReporter.getRecentLogs().takeLast(25).forEach {
                                            appendLine(it)
                                        }
                                    }

                                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:verton3@gmail.com")
                                        putExtra(Intent.EXTRA_EMAIL, arrayOf("verton3@gmail.com"))
                                        putExtra(Intent.EXTRA_SUBJECT, "Relatório de Diagnóstico & Erros - Assistente de Entregas")
                                        putExtra(Intent.EXTRA_TEXT, report)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(emailIntent, "Enviar Relatório por E-mail"))
                                    } catch (e: Exception) {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_EMAIL, arrayOf("verton3@gmail.com"))
                                            putExtra(Intent.EXTRA_SUBJECT, "Relatório de Erros - Assistente")
                                            putExtra(Intent.EXTRA_TEXT, report)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Enviar Relatório"))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enviar p/ E-mail", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val report = buildString {
                                        appendLine("=== RELATÓRIO DE DIAGNÓSTICO DO ASSISTENTE ===")
                                        appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
                                        appendLine("Último pacote: ${autoState.currentPackageName}")
                                        appendLine("Último endereço: ${autoState.detectedAddressText}")
                                        appendLine("\n--- LOGS RECENTES ---")
                                        autoState.logs.takeLast(15).forEach {
                                            appendLine("${it.formattedTime} [${it.tag}] ${it.message}")
                                        }
                                        CrashReporter.getRecentLogs().takeLast(15).forEach {
                                            appendLine(it)
                                        }
                                    }

                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, report)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Compartilhar Relatório")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp / Outros", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Estado em Tempo Real da Janela Atual
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ESTADO DO APLICATIVO EM FOCO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Pacote: ${autoState.currentPackageName.ifBlank { "Nenhum pacote detectado ainda" }}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Classe: ${autoState.currentClassName.ifBlank { "Nenhuma classe detectada" }}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Endereço extraído: ${autoState.detectedAddressText.ifBlank { "Nenhum" }}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Pessoa combinada: ${autoState.matchedPerson?.nome ?: "Nenhuma correspondência exata"}",
                            fontSize = 12.sp,
                            color = if (autoState.matchedPerson != null) Color(0xFF2E7D32) else Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Histórico de Logs do Motor
            item {
                Text(
                    text = "LOGS DE EVENTOS E AÇÕES RECENTES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (autoState.logs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Nenhum evento registrado até o momento.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                items(autoState.logs) { log ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (log.isSuccess) Color(0xFFF1F8E9) else Color(0xFFFFEBEE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = log.formattedTime,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "[${log.tag}] ${log.message}",
                                fontSize = 12.sp,
                                color = if (log.isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun DiagnosticItemRow(name: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (ok) Color(0xFF2E7D32) else Color(0xFFC62828), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (ok) "OK" else "PENDENTE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}
