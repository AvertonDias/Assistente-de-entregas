package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.FirebaseSyncRepository
import com.example.data.firebase.SyncState
import com.example.data.repository.AppSettings
import com.example.data.repository.SettingsRepository
import com.example.service.AreaSelectorOverlay
import com.example.ui.components.AppSelectorDialog
import com.example.util.ClipboardHelper
import com.example.util.PermissionUtils
import com.example.util.UpdateChecker
import com.example.util.AppUpdateInfo
import com.example.ui.components.UpdateDialog
import com.example.BuildConfig
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Info
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    firebaseSyncRepository: FirebaseSyncRepository? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.getSettings().collectAsState(initial = AppSettings())
    val syncState by (firebaseSyncRepository?.syncState?.collectAsState(initial = SyncState.Idle)
        ?: remember { mutableStateOf(SyncState.Idle) })

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showAppSelector by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var importJsonText by remember { mutableStateOf("") }
    var mergeImport by remember { mutableStateOf(true) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfoAvailable by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showJsonExampleDialog by remember { mutableStateOf(false) }
    var updateUrlInput by remember(settings.updateJsonUrl) { mutableStateOf(settings.updateJsonUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações & Backup", fontWeight = FontWeight.Bold, color = Color.White) },
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
            // Seção Firebase Cloud Sync
            if (firebaseSyncRepository != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("firebase_cloud_sync_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FE)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBFDBFE)))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = Color(0xFF1967D2),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SINCRONIZAÇÃO EM NUVEM",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1967D2)
                                )
                            }
                            if (syncState is SyncState.Syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF1967D2)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sincronize pessoas e entregas com o banco de dados Firebase Firestore em tempo real.",
                            fontSize = 12.sp,
                            color = Color(0xFF3C4043)
                        )

                        // Status da última sincronização
                        when (val state = syncState) {
                            is SyncState.Success -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "✓ ${state.message}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF137333),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            is SyncState.Error -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "✕ ${state.message}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC5221F),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            else -> {}
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val res = firebaseSyncRepository.syncAllToCloud()
                                        if (res is SyncState.Success) {
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = syncState !is SyncState.Syncing,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1967D2))
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ENVIAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val res = firebaseSyncRepository.fetchAllFromCloud()
                                        if (res is SyncState.Success) {
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = syncState !is SyncState.Syncing,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("BAIXAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            // Seção do Balão Flutuante
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BALÃO FLUTUANTE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modo Compacto por Padrão", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Abre o painel em versão reduzida", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = settings.isCompactMode,
                            onCheckedChange = { scope.launch { settingsRepository.setCompactMode(it) } }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    Text("Tamanho da Bolha: ${settings.bubbleSizeDp} dp", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = settings.bubbleSizeDp.toFloat(),
                        onValueChange = { scope.launch { settingsRepository.setBubbleSize(it.toInt()) } },
                        valueRange = 44f..72f,
                        steps = 6
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    Text("Área Alvo de Leitura na Tela", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Escolha onde o balão deve capturar o endereço para consulta", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isFocusMode = settings.scanTargetMode == "NEXT_DELIVERY"
                        val isCustomMode = settings.scanTargetMode == "CUSTOM_RECT" || settings.scanTargetMode == "CUSTOM_AREA"
                        Button(
                            onClick = { scope.launch { settingsRepository.setScanTargetMode("NEXT_DELIVERY") } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFocusMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isFocusMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("🎯 Foco: Próxima", fontSize = 12.sp, fontWeight = if (isFocusMode) FontWeight.Bold else FontWeight.Normal)
                        }

                        Button(
                            onClick = { scope.launch { settingsRepository.setScanTargetMode("ALL_SCREEN") } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isFocusMode && !isCustomMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (!isFocusMode && !isCustomMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("📱 Tela Toda", fontSize = 12.sp, fontWeight = if (!isFocusMode && !isCustomMode) FontWeight.Bold else FontWeight.Normal)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            if (PermissionUtils.hasOverlayPermission(context)) {
                                showAppSelector = true
                            } else {
                                Toast.makeText(context, "Conceda a permissão de sobreposição primeiro.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📐 Desenhar Área Alvo em um App", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (settings.scanTargetMode == "CUSTOM_RECT" || settings.scanTargetMode == "CUSTOM_AREA") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "✓ Área customizada ativa: L=${(settings.scanAreaLeft * 100).toInt()}% T=${(settings.scanAreaTop * 100).toInt()}% R=${(settings.scanAreaRight * 100).toInt()}% B=${(settings.scanAreaBottom * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = Color(0xFF137333),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Seção de Feedback & Confirmações
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FEEDBACK & SEGURANÇA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibração ao Preencher", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Feedback tátil durante ações", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = settings.vibrationEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setVibrationEnabled(it) } }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Confirmar antes de Enviar Assinatura", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Evita envio acidental de gestos", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = settings.confirmBeforeSendSignature,
                            onCheckedChange = { scope.launch { settingsRepository.setConfirmBeforeSendSignature(it) } }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    Text("Velocidade do Desenho da Assinatura", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Desenho ultra suave, detalhado e cadenciado no aplicativo de entrega", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🐢 Ultra Lenta (Fixa & Precisa)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Seção de Atualização do Aplicativo (OTA)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ATUALIZAÇÃO DO APLICATIVO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showJsonExampleDialog = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Info, contentDescription = "Como configurar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Versão instalada: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = updateUrlInput,
                        onValueChange = {
                            updateUrlInput = it
                            scope.launch { settingsRepository.setUpdateJsonUrl(it) }
                        },
                        label = { Text("URL do arquivo version.json") },
                        placeholder = { Text("https://raw.githubusercontent.com/usuario/repo/main/version.json") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (updateUrlInput.isBlank()) {
                                Toast.makeText(context, "Insira a URL do arquivo version.json acima", Toast.LENGTH_SHORT).show()
                            } else {
                                isCheckingUpdate = true
                                scope.launch {
                                    val info = UpdateChecker.checkForUpdates(context, updateUrlInput)
                                    isCheckingUpdate = false
                                    if (info != null) {
                                        updateInfoAvailable = info
                                    } else {
                                        Toast.makeText(context, "Seu app já está atualizado (v${BuildConfig.VERSION_NAME})!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("VERIFICANDO...")
                        } else {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VERIFICAR ATUALIZAÇÃO")
                        }
                    }
                }
            }

            // Seção de Backup & Migração de Dados
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BACKUP & MIGRAÇÃO (JSON)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Exporte todos os destinatários, históricos e configurações para backup ou transferência para outro aparelho.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val json = settingsRepository.exportAllDataJson()
                                    exportedJsonText = json
                                    showExportDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EXPORTAR")
                        }

                        OutlinedButton(
                            onClick = {
                                importJsonText = ""
                                showImportDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("IMPORTAR")
                        }
                    }
                }
            }
        }
    }

        // Diálogo de Exportação
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Exportação de Dados") },
                text = {
                    Column {
                        Text("Dados exportados em formato JSON estruturado:", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = exportedJsonText,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            ClipboardHelper.copyToClipboard(context, "Backup JSON", exportedJsonText)
                            Toast.makeText(context, "Backup copiado para a Área de Transferência!", Toast.LENGTH_SHORT).show()
                            showExportDialog = false
                        }
                    ) {
                        Text("COPIAR JSON")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("FECHAR")
                    }
                }
            )
        }

        // Diálogo de Importação
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Importar Dados") },
                text = {
                    Column {
                        Text("Cole o JSON de backup abaixo:", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = importJsonText,
                            onValueChange = { importJsonText = it },
                            placeholder = { Text("Cole aqui o JSON exportado...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                val result = settingsRepository.importDataJson(importJsonText, merge = mergeImport)
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                if (result.success) {
                                    showImportDialog = false
                                }
                            }
                        },
                        enabled = importJsonText.isNotBlank()
                    ) {
                        Text("IMPORTAR AGORA")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("CANCELAR")
                    }
                }
            )
        }

        // Diálogo para escolher app antes de desenhar a área alvo
        if (showAppSelector) {
            AppSelectorDialog(
                onDismiss = { showAppSelector = false },
                onAppSelected = { selectedApp ->
                    showAppSelector = false
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedApp.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            scope.launch {
                                delay(1000)
                                val selector = AreaSelectorOverlay(
                                    context = context,
                                    onAreaSelected = { left, top, right, bottom ->
                                        scope.launch {
                                            settingsRepository.setCustomScanArea(left, top, right, bottom)
                                            Toast.makeText(context, "✅ Área alvo configurada para ${selectedApp.name}!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onDismiss = {}
                                )
                                selector.show()
                            }
                        } else {
                            Toast.makeText(context, "Não foi possível abrir ${selectedApp.name}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Erro ao abrir app: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onDirectDraw = {
                    showAppSelector = false
                    val selector = AreaSelectorOverlay(
                        context = context,
                        onAreaSelected = { left, top, right, bottom ->
                            scope.launch {
                                settingsRepository.setCustomScanArea(left, top, right, bottom)
                                Toast.makeText(context, "✅ Área alvo desenhada e salva!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDismiss = {}
                    )
                    selector.show()
                }
            )
        }

        // Diálogo de Atualização Disponível
        updateInfoAvailable?.let { info ->
            UpdateDialog(
                updateInfo = info,
                onDismiss = { updateInfoAvailable = null }
            )
        }

        // Diálogo de Ajuda / Exemplo do Json de Atualização
        if (showJsonExampleDialog) {
            val sampleJson = """
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "apkUrl": "https://github.com/seu-usuario/seu-repo/releases/download/v1.1.0/app-release.apk",
  "changelog": "- Adicionado suporte a microfone inteligente\n- Correções na validação de CPF\n- Atualização direta de APK",
  "forceUpdate": false
}
            """.trimIndent()

            AlertDialog(
                onDismissRequest = { showJsonExampleDialog = false },
                title = { Text("Como criar o version.json", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Hospede um arquivo chamado 'version.json' no seu GitHub Releases, GitHub Gist ou servidor web com a seguinte estrutura:",
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = sampleJson,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Quando você publicar um novo APK, altere 'versionCode' para um número maior (ex: 2) e cole a URL do arquivo bruto acima.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            ClipboardHelper.copyToClipboard(context, "Exemplo version.json", sampleJson)
                            Toast.makeText(context, "Modelo copiado!", Toast.LENGTH_SHORT).show()
                            showJsonExampleDialog = false
                        }
                    ) {
                        Text("COPIAR MODELO")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJsonExampleDialog = false }) {
                        Text("FECHAR")
                    }
                }
            )
        }
    }
}
