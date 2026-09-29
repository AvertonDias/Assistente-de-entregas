package com.example.ui.screens.diagnostic

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityAutomationEngine
import com.example.util.CrashReporter
import com.example.util.DeviceInfoHelper
import com.example.util.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val autoState by AccessibilityAutomationEngine.state.collectAsState()
    val hasOverlay = PermissionUtils.hasOverlayPermission(context)
    val hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(context) || autoState.isServiceActive

    // Estado local para atualizar após limpeza de crash/logs
    var lastCrashReport by remember { mutableStateOf(CrashReporter.getLastCrashReport()) }
    var refreshKey by remember { mutableStateOf(0) }
    val deviceSummary = remember(refreshKey) { DeviceInfoHelper.getDeviceSummary(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico & Telemetria", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        refreshKey++
                        lastCrashReport = CrashReporter.getLastCrashReport()
                        Toast.makeText(context, "Dados atualizados!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar Dados", tint = Color.White)
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
                // AÇÕES RÁPIDAS DE EXPORTAÇÃO DO RELATÓRIO COMPLETO
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "RELATÓRIO TÉCNICO COMPLETO",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Gera um relatório minucioso com todas as especificações do aparelho, versão do Android, memória, bateria, estado da tela, permissões e histórico de falhas para facilitar melhorias e correção de bugs.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 17.sp
                            )

                            // Botões de ação rápida
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Copiar
                                Button(
                                    onClick = {
                                        val fullReport = DeviceInfoHelper.generateFullDiagnosticReport(
                                            context = context,
                                            autoState = autoState,
                                            includeLogs = true
                                        )
                                        DeviceInfoHelper.copyToClipboard(context, "Relatório Diagnóstico", fullReport)
                                        Toast.makeText(context, "Relatório completo copiado para a área de transferência!", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copiar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // E-mail
                                Button(
                                    onClick = {
                                        val fullReport = DeviceInfoHelper.generateFullDiagnosticReport(
                                            context = context,
                                            autoState = autoState,
                                            includeLogs = true
                                        )
                                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:verton3@gmail.com")
                                            putExtra(Intent.EXTRA_EMAIL, arrayOf("verton3@gmail.com"))
                                            putExtra(Intent.EXTRA_SUBJECT, "Relatório Completo de Diagnóstico - ${deviceSummary.manufacturer} ${deviceSummary.model}")
                                            putExtra(Intent.EXTRA_TEXT, fullReport)
                                        }
                                        try {
                                            context.startActivity(Intent.createChooser(emailIntent, "Enviar Relatório por E-mail"))
                                        } catch (_: Exception) {
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_EMAIL, arrayOf("verton3@gmail.com"))
                                                putExtra(Intent.EXTRA_SUBJECT, "Relatório Completo de Diagnóstico")
                                                putExtra(Intent.EXTRA_TEXT, fullReport)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Enviar Relatório"))
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("E-mail", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // Compartilhar (WhatsApp, etc.)
                                OutlinedButton(
                                    onClick = {
                                        val fullReport = DeviceInfoHelper.generateFullDiagnosticReport(
                                            context = context,
                                            autoState = autoState,
                                            includeLogs = true
                                        )
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, fullReport)
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Compartilhar Relatório")
                                        context.startActivity(shareIntent)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // DADOS DO DISPOSITIVO & HARDWARE
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "DADOS DO DISPOSITIVO & HARDWARE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            InfoValueRow("Aparelho / Modelo", "${deviceSummary.manufacturer} ${deviceSummary.model}")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Marca / Produto", "${deviceSummary.brand} (${deviceSummary.product})")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Android / SDK", "Android ${deviceSummary.androidVersion} (API ${deviceSummary.sdkInt})")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Patch de Segurança", deviceSummary.securityPatch)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Resolução & Densidade", "${deviceSummary.screenResolution} • ${deviceSummary.screenDensityDpi} DPI")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Orientação da Tela", deviceSummary.screenOrientation)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Arquitetura CPU (ABIs)", deviceSummary.supportedAbis)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Versão do App", "v${deviceSummary.appVersionName} (Build ${deviceSummary.appVersionCode})")
                        }
                    }
                }

                // RECURSOS DO SISTEMA: MEMÓRIA & BATERIA
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "MEMÓRIA, ENERGIA & ARMAZENAMENTO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            InfoValueRow("Memória RAM Livre", "${deviceSummary.availRamFormatted} de ${deviceSummary.totalRamFormatted}")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Alerta Pouca Memória", if (deviceSummary.isLowMemory) "⚠️ SIM (Crítico)" else "NÃO (Estável)")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Heap do App (JVM)", "${deviceSummary.jvmHeapUsedMb} MB usados de ${deviceSummary.jvmHeapMaxMb} MB")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Armazenamento Livre", "${deviceSummary.internalStorageFree} livres de ${deviceSummary.internalStorageTotal}")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Nível da Bateria", "${if (deviceSummary.batteryLevelPct >= 0) "${deviceSummary.batteryLevelPct}%" else "N/A"} (${deviceSummary.batteryStatus})")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Economia de Bateria", if (deviceSummary.isPowerSaveMode) "⚠️ Ativado" else "Desativado")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow(
                                "Isenção Bateria (Fundo)",
                                if (deviceSummary.isIgnoringBatteryOptimizations) "✓ Isento (Ideal)" else "⚠️ Padrão SO (Pode fechar)"
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            InfoValueRow("Conexão de Rede", deviceSummary.networkType)
                        }
                    }
                }

                // STATUS DOS SUBSISTEMAS DO ASSISTENTE
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "STATUS DOS SUBSISTEMAS E PERMISSÕES",
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
                            DiagnosticItemRow("Isenção Otimização de Bateria", deviceSummary.isIgnoringBatteryOptimizations)
                        }
                    }
                }

                // ESTADO DO APLICATIVO EM FOCO (ACCESSIBILITY ENGINE)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "ESTADO DA JANELA ATIVA / AUTOMAÇÃO",
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

                // ÚLTIMO CRASH / ERRO CRÍTICO SALVO
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (lastCrashReport != null) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (lastCrashReport != null) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (lastCrashReport != null) Color(0xFFC62828) else Color(0xFF2E7D32),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ÚLTIMO ERRO CRÍTICO / CRASH",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (lastCrashReport != null) Color(0xFFC62828) else MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (lastCrashReport != null) {
                                    IconButton(
                                        onClick = {
                                            CrashReporter.clearCrashReport()
                                            lastCrashReport = null
                                            Toast.makeText(context, "Registro de crash excluído.", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Limpar Crash", tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            if (lastCrashReport != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = lastCrashReport ?: "",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFB71C1C),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Nenhum erro crítico ou crash registrado no armazenamento. O aplicativo está executando de forma estável.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }

                // FERRAMENTAS DE TESTE E LIMPEZA
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                CrashReporter.simulateDiagnosticTestWarning()
                                lastCrashReport = CrashReporter.getLastCrashReport()
                                Toast.makeText(context, "Erro de teste registrado para análise!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simular Erro de Teste", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                CrashReporter.clearLogs()
                                refreshKey++
                                Toast.makeText(context, "Logs internos limpos.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpar Logs", fontSize = 11.sp)
                        }
                    }
                }

                // HISTÓRICO DE LOGS DE TELEMETRIA
                item {
                    Text(
                        text = "LOGS RECENTES DE TELEMETRIA E AUTOMAÇÃO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (autoState.logs.isEmpty() && CrashReporter.getRecentLogs().isEmpty()) {
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
                    // Logs de automação
                    items(autoState.logs.takeLast(20)) { log ->
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

                    // Logs do CrashReporter
                    items(CrashReporter.getRecentLogs().takeLast(15)) { crashLog ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = crashLog,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
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

