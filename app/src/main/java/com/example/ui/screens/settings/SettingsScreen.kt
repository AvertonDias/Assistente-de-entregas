package com.example.ui.screens.settings

import com.example.util.ToastHelper

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import com.example.data.local.BrazilianLocationsData
import com.example.data.local.BrazilianState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import com.example.DeliveryApp
import com.example.data.local.entity.StreetCatalog
import com.example.util.FeedbackHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    firebaseSyncRepository: FirebaseSyncRepository? = null,
    viewModel: SettingsViewModel? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by (viewModel?.settings ?: settingsRepository.getSettings()).collectAsState(initial = AppSettings())
    val syncStateFlow = viewModel?.syncState ?: firebaseSyncRepository?.syncState
    val syncState by (syncStateFlow?.collectAsState(initial = SyncState.Idle) ?: remember { mutableStateOf(SyncState.Idle) })

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showAppSelector by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var importJsonText by remember { mutableStateOf("") }
    var mergeImport by remember { mutableStateOf(true) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfoAvailable by remember { mutableStateOf<AppUpdateInfo?>(null) }

    var showChangeCityDialog by remember { mutableStateOf(false) }
    var showStatePickerDialog by remember { mutableStateOf(false) }
    var selectedStateUf by remember { mutableStateOf("MG") }
    var citySearchInput by remember { mutableStateOf("") }
    var showAddStreetDialog by remember { mutableStateOf(false) }
    var newStreetTipo by remember { mutableStateOf("Rua") }
    var newStreetNome by remember { mutableStateOf("") }
    var newStreetBairro by remember { mutableStateOf("") }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val dismissChangeCityDialog = {
        keyboardController?.hide()
        focusManager.clearFocus()
        showChangeCityDialog = false
    }

    val dismissAddStreetDialog = {
        keyboardController?.hide()
        focusManager.clearFocus()
        showAddStreetDialog = false
    }

    val streetRepo = remember { DeliveryApp.instance.streetCatalogRepository }
    val availableCities by streetRepo.getAvailableCities().collectAsState(initial = listOf("Monte Santo de Minas"))

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

                        // Status da última sincronização ou barra de progresso em andamento
                        when (val state = syncState) {
                            is SyncState.Syncing -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFD3E3FD),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = state.stepMessage,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF041E49),
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (state.progress >= 0f) {
                                                Text(
                                                    text = "${(state.progress * 100).toInt()}%",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF041E49)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (state.progress >= 0f) {
                                            val animatedProgress by animateFloatAsState(
                                                targetValue = state.progress.coerceIn(0f, 1f),
                                                label = "sync_progress"
                                            )
                                            LinearProgressIndicator(
                                                progress = { animatedProgress },
                                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                                color = Color(0xFF1967D2),
                                                trackColor = Color.White
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                                color = Color(0xFF1967D2),
                                                trackColor = Color.White
                                            )
                                        }
                                    }
                                }
                            }
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
                            val isSyncing = syncState is SyncState.Syncing

                            Button(
                                onClick = {
                                    if (viewModel != null) {
                                        viewModel.exportRoomToFirestore { res ->
                                            if (res is SyncState.Success) {
                                                ToastHelper.show(context, res.message)
                                            } else if (res is SyncState.Error) {
                                                ToastHelper.showLong(context, res.message)
                                            }
                                        }
                                    } else {
                                        scope.launch {
                                            val res = firebaseSyncRepository?.syncAllToCloud()
                                            if (res is SyncState.Success) {
                                                ToastHelper.show(context, res.message)
                                            } else if (res is SyncState.Error) {
                                                ToastHelper.showLong(context, res.message)
                                            }
                                        }
                                    }
                                },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1967D2))
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ENVIANDO...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ENVIAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    if (viewModel != null) {
                                        viewModel.importFromFirestore { res ->
                                            if (res is SyncState.Success) {
                                                ToastHelper.show(context, res.message)
                                            } else if (res is SyncState.Error) {
                                                ToastHelper.showLong(context, res.message)
                                            }
                                        }
                                    } else {
                                        scope.launch {
                                            val res = firebaseSyncRepository?.fetchAllFromCloud()
                                            if (res is SyncState.Success) {
                                                ToastHelper.show(context, res.message)
                                            } else if (res is SyncState.Error) {
                                                ToastHelper.showLong(context, res.message)
                                            }
                                        }
                                    }
                                },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF1967D2), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("BAIXANDO...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("BAIXAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
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
                            ToastHelper.show(context, "Feedback testado com sucesso!")
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

            // Seção de Cidade & Catálogo de Ruas
            Card(
                modifier = Modifier.fillMaxWidth().testTag("city_street_catalog_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationCity,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CIDADE & RUAS OFFLINE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cidade Ativa", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = settings.selectedCity,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val activeUf = BrazilianLocationsData.findStateForCity(settings.selectedCity)?.uf ?: "MG"
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = activeUf,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                selectedStateUf = BrazilianLocationsData.findStateForCity(settings.selectedCity)?.uf ?: "MG"
                                citySearchInput = ""
                                showChangeCityDialog = true
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trocar Cidade", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Catálogo local de logradouros para autocompletar nomes de ruas sem precisar de internet.\n• Se pesquisar ou cadastrar um endereço que não está no catálogo, o aplicativo aceita e continua normalmente.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            newStreetNome = ""
                            newStreetBairro = ""
                            newStreetTipo = "Rua"
                            showAddStreetDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cadastrar Nova Rua na Cidade", fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                                    ToastHelper.show(context, "Seu app já está atualizado na versão mais recente (v${BuildConfig.VERSION_NAME})!")
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
                            ToastHelper.show(context, "Backup copiado para a Área de Transferência!")
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
                                ToastHelper.showLong(context, result.message)
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
                                            ToastHelper.show(context, "✅ Área alvo configurada para ${selectedApp.name}!")
                                        }
                                    },
                                    onDismiss = {}
                                )
                                selector.show()
                            }
                        } else {
                            ToastHelper.show(context, "Não foi possível abrir ${selectedApp.name}")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ToastHelper.show(context, "Erro ao abrir app: ${e.message}")
                    }
                },
                onDirectDraw = {
                    showAppSelector = false
                    val selector = AreaSelectorOverlay(
                        context = context,
                        onAreaSelected = { left, top, right, bottom ->
                            scope.launch {
                                settingsRepository.setCustomScanArea(left, top, right, bottom)
                                ToastHelper.show(context, "✅ Área alvo desenhada e salva!")
                            }
                        },
                        onDismiss = {}
                    )
                    selector.show()
                }
            )
        }

        // Diálogo para Selecionar o Estado (UF)
        if (showStatePickerDialog) {
            AlertDialog(
                onDismissRequest = { showStatePickerDialog = false },
                title = { Text("Selecione o Estado (UF)", fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(BrazilianLocationsData.STATES) { state ->
                            val isCurrentState = state.uf == selectedStateUf
                            Surface(
                                onClick = {
                                    selectedStateUf = state.uf
                                    showStatePickerDialog = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentState) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isCurrentState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = state.uf,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isCurrentState) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = state.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Text(text = "Região ${state.region}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isCurrentState) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showStatePickerDialog = false }) {
                        Text("FECHAR")
                    }
                }
            )
        }

        // Diálogo para Trocar ou Adicionar Cidade (com escolha de Estado e busca preditiva)
        if (showChangeCityDialog) {
            val currentState = remember(selectedStateUf) {
                BrazilianLocationsData.STATES.firstOrNull { it.uf == selectedStateUf }
            }
            val filteredCities = remember(selectedStateUf, citySearchInput, availableCities) {
                BrazilianLocationsData.searchCities(selectedStateUf, citySearchInput, availableCities)
            }

            AlertDialog(
                onDismissRequest = dismissChangeCityDialog,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationCity, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Trocar Cidade", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Passo: Escolha de Estado
                        Text(
                            text = "1. Estado (UF):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            onClick = { showStatePickerDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = selectedStateUf,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = currentState?.name ?: selectedStateUf,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("Mudar", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Atalhos de Estados
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("MG", "SP", "RJ", "PR", "GO", "BA").forEach { uf ->
                                val isSelectedUf = selectedStateUf == uf
                                Surface(
                                    onClick = { selectedStateUf = uf },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelectedUf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = uf,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelectedUf) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelectedUf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        // 2. Passo: Digitação da Cidade
                        Text(
                            text = "2. Digite a Cidade (sugestões em tempo real):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = citySearchInput,
                            onValueChange = { citySearchInput = it },
                            label = { Text("Nome da Cidade ($selectedStateUf)") },
                            placeholder = { Text("Ex: Monte Santo, Mococa, Passos...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (citySearchInput.isNotBlank()) {
                                    IconButton(onClick = { citySearchInput = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpar", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // 3. Passo: Lista de cidades que surgem conforme digita
                        val isCustomCityNeeded = citySearchInput.isNotBlank() &&
                            filteredCities.none { it.equals(citySearchInput.trim(), ignoreCase = true) }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 210.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isCustomCityNeeded) {
                                item {
                                    val formattedCustom = citySearchInput.trim().split(" ")
                                        .filter { it.isNotBlank() }
                                        .joinToString(" ") { word ->
                                            if (word.lowercase() in listOf("de", "do", "da", "dos", "das", "e")) word.lowercase()
                                            else word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                        }
                                    Surface(
                                        onClick = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            scope.launch {
                                                streetRepo.setSelectedCity(formattedCustom)
                                                ToastHelper.show(context, "Cidade alterada para: $formattedCustom ($selectedStateUf)")
                                                showChangeCityDialog = false
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.AddLocation,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Usar: \"$formattedCustom\"",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Text(
                                                    text = "Cadastrar e selecionar esta cidade em $selectedStateUf",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            items(filteredCities) { city ->
                                val isSelected = city.equals(settings.selectedCity, ignoreCase = true)
                                Surface(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        scope.launch {
                                            streetRepo.setSelectedCity(city)
                                            ToastHelper.show(context, "Cidade alterada para: $city ($selectedStateUf)")
                                            showChangeCityDialog = false
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.LocationCity,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = city,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Text(
                                                text = selectedStateUf,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Ativa",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = dismissChangeCityDialog) {
                        Text("CANCELAR")
                    }
                }
            )
        }

        // Diálogo para Adicionar Nova Rua Manualmente
        if (showAddStreetDialog) {
            AlertDialog(
                onDismissRequest = dismissAddStreetDialog,
                title = { Text("Cadastrar Rua na Cidade", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Cidade atual: ${settings.selectedCity}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = newStreetTipo,
                            onValueChange = { newStreetTipo = it },
                            label = { Text("Tipo (Rua, Av, Praça, Travessa)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = newStreetNome,
                            onValueChange = { newStreetNome = it },
                            label = { Text("Nome do Logradouro *") },
                            placeholder = { Text("Ex: Cel. Antônio Paulino") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = newStreetBairro,
                            onValueChange = { newStreetBairro = it },
                            label = { Text("Bairro") },
                            placeholder = { Text("Ex: Centro, Vila Nova") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmedNome = newStreetNome.trim()
                            if (trimmedNome.isNotBlank()) {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                scope.launch {
                                    streetRepo.addStreet(
                                        StreetCatalog(
                                            nome = trimmedNome,
                                            tipo = newStreetTipo.trim().ifBlank { "Rua" },
                                            bairro = newStreetBairro.trim(),
                                            cidade = settings.selectedCity,
                                            uf = BrazilianLocationsData.findStateForCity(settings.selectedCity)?.uf ?: selectedStateUf
                                        )
                                    )
                                    ToastHelper.show(context, "Rua cadastrada no catálogo offline!")
                                    showAddStreetDialog = false
                                }
                            }
                        },
                        enabled = newStreetNome.isNotBlank()
                    ) {
                        Text("SALVAR RUA")
                    }
                },
                dismissButton = {
                    TextButton(onClick = dismissAddStreetDialog) {
                        Text("CANCELAR")
                    }
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
