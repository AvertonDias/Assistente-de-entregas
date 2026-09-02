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
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Info
import com.example.util.FeedbackHelper
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
                            onCheckedChange = { scope.launch { settingsRepository.setCompactMode(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = Color(0xFF475569),
                                uncheckedTrackColor = Color(0xFFE2E8F0),
                                uncheckedBorderColor = Color(0xFF64748B)
                            )
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
                }
            }

            // Seção de Aparência & Tema (Dark Mode)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("theme_settings_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "APARÊNCIA & TEMA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "O aplicativo está configurado exclusivamente para o Modo Claro.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { scope.launch { settingsRepository.setThemeMode("LIGHT") } },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Modo Claro Ativo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Seção de Feedback Tátil & Sonoro
            Card(
                modifier = Modifier.fillMaxWidth().testTag("feedback_settings_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FEEDBACK TÁTIL & SONORO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Receba confirmação imediata de sucesso ao preencher dados e ao assinar sem precisar desviar o olhar da rua.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Vibração
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Feedback Háptico (Vibração)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text("Vibração suave de confirmação ao preencher ou assinar", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.vibrationEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setVibrationEnabled(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = Color(0xFF475569),
                                uncheckedTrackColor = Color(0xFFE2E8F0),
                                uncheckedBorderColor = Color(0xFF64748B)
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    // Som
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Feedback Sonoro Sutil", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text("Som suave de confirmação de sucesso de preenchimento", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.soundEnabled,
                            onCheckedChange = { scope.launch { settingsRepository.setSoundEnabled(it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = Color(0xFF475569),
                                uncheckedTrackColor = Color(0xFFE2E8F0),
                                uncheckedBorderColor = Color(0xFF64748B)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Botão de Teste de Feedback
                    OutlinedButton(
                        onClick = {
                            FeedbackHelper.triggerSuccess(
                                context = context,
                                vibrationEnabled = settings.vibrationEnabled,
                                soundEnabled = settings.soundEnabled
                            )
                            Toast.makeText(context, "Feedback testado com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testar Feedback de Sucesso Agora", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Seção de Atualização do Aplicativo (Automática)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ATUALIZAÇÃO DO APLICATIVO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Versão Instalada: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Canal de atualização automática conectado. O aplicativo verifica novas versões diretamente do repositório oficial.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            scope.launch {
                                val info = UpdateChecker.checkForUpdates(context)
                                isCheckingUpdate = false
                                if (info != null) {
                                    updateInfoAvailable = info
                                } else {
                                    Toast.makeText(context, "Seu app já está atualizado na versão mais recente (v${BuildConfig.VERSION_NAME})!", Toast.LENGTH_SHORT).show()
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
                            Text("VERIFICANDO ATUALIZAÇÃO...")
                        } else {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VERIFICAR ATUALIZAÇÃO")
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
    }
}
