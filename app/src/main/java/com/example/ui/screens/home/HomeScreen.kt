package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DeliveryApp
import com.example.service.AreaSelectorOverlay
import com.example.ui.components.AppSelectorDialog
import com.example.util.UpdateChecker
import com.example.util.AppUpdateInfo
import com.example.ui.components.UpdateDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.ui.navigation.Screen
import com.example.ui.theme.BentoBackgroundLight
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoBorderMuted
import com.example.ui.theme.BentoOnPrimaryContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoPrimaryContainer
import com.example.ui.theme.BentoPrimaryDark
import com.example.ui.theme.BentoSurfaceCard
import com.example.ui.theme.BentoSurfaceLight
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTextTertiary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenDark

import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.IconButton
import com.example.ui.screens.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    authViewModel: AuthViewModel? = null,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val stats by viewModel.deliveryStats.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val authState by authViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) }
    var showAppSelector by remember { mutableStateOf(false) }
    var updateInfoAvailable by remember { mutableStateOf<AppUpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions(context)
    }

    LaunchedEffect(settings.updateJsonUrl) {
        if (settings.updateJsonUrl.isNotBlank()) {
            val info = UpdateChecker.checkForUpdates(context, settings.updateJsonUrl)
            if (info != null) {
                updateInfoAvailable = info
            }
        }
    }

    Scaffold(
        containerColor = BentoBackgroundLight,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Assistente de Entregas",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = BentoPrimaryDark
                        )
                        Text(
                            text = authState?.currentUser?.let { "👤 Conectado: ${it.displayName ?: it.email}" }
                                ?: "Painel Bento • Automação e Rotas",
                            fontSize = 12.sp,
                            color = BentoTextSecondary
                        )
                    }
                },
                actions = {
                    if (authState?.currentUser != null) {
                        IconButton(
                            onClick = {
                                authViewModel?.signOut(context)
                                onNavigate(Screen.Auth.route)
                            },
                            modifier = Modifier.testTag("btn_logout_topbar")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Sair da conta",
                                tint = BentoPrimaryDark
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { onNavigate(Screen.Auth.route) },
                            modifier = Modifier.testTag("btn_login_topbar")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Fazer Login",
                                tint = BentoPrimaryDark
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BentoBackgroundLight
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BentoBackgroundLight),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Bento Hero 1: Status dos Serviços Ativos (Pills container)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("services_status_bento_card"),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = BentoPrimaryContainer),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBFDBFE)))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (uiState.isAccessibilityEnabled) SuccessGreen else ErrorRed, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SERVIÇOS & AUTOMAÇÃO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = BentoOnPrimaryContainer
                                )
                            }
                            // Toggle rápido da bolha
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (uiState.isBubbleServiceRunning) "Balão ON" else "Balão OFF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoOnPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = uiState.isBubbleServiceRunning,
                                    onCheckedChange = { checked ->
                                        if (checked && !uiState.isOverlayGranted) {
                                            onNavigate(Screen.OverlaySettings.route)
                                        } else {
                                            viewModel.toggleFloatingBubble(context, checked)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BentoPrimary,
                                        checkedTrackColor = Color.White
                                    )
                                )
                            }
                        }

                        // Pills dos serviços no estilo Bento
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BentoPillChip(
                                modifier = Modifier.weight(1f),
                                text = if (uiState.isAccessibilityEnabled) "● Acessibilidade" else "○ Acessibilidade",
                                isActive = uiState.isAccessibilityEnabled,
                                onClick = { onNavigate(Screen.AccessibilitySettings.route) }
                            )
                            BentoPillChip(
                                modifier = Modifier.weight(1f),
                                text = if (uiState.isOverlayGranted) "● Sobreposição" else "○ Sobreposição",
                                isActive = uiState.isOverlayGranted,
                                onClick = { onNavigate(Screen.OverlaySettings.route) }
                            )
                        }

                        // Acesso direto para Calibrar / Desenhar Área Alvo
                        OutlinedButton(
                            onClick = {
                                if (uiState.isOverlayGranted) {
                                    showAppSelector = true
                                } else {
                                    onNavigate(Screen.OverlaySettings.route)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White.copy(alpha = 0.8f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CropFree,
                                contentDescription = null,
                                tint = BentoPrimaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Desenhar Área Alvo no App",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoPrimaryDark
                            )
                        }
                    }
                }
            }

            // Bento Hero 3: Grande Botão de Ação "INICIAR MODO ENTREGA"
            item {
                Button(
                    onClick = { onNavigate(Screen.DeliveryMode.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("start_delivery_mode_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BentoPrimary,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "INICIAR MODO ENTREGA",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Bento Grid 4: Módulos do Sistema
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoModuleCard(
                        modifier = Modifier.weight(1f),
                        emoji = "👥",
                        title = "Destinatários",
                        subtitle = "Gerenciar pessoas",
                        onClick = { onNavigate(Screen.PeopleList.route) }
                    )
                    BentoModuleCard(
                        modifier = Modifier.weight(1f),
                        emoji = "🧪",
                        title = "Laboratório",
                        subtitle = "Testar automação",
                        onClick = { onNavigate(Screen.AutomationLab.route) }
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onNavigate(Screen.Diagnostic.route) }
                        .testTag("bento_card_diagnostic"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = BentoSurfaceLight),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🩺", fontSize = 26.sp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Diagnóstico & Telemetria",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPrimary
                            )
                            Text(
                                text = "Verifique o status dos serviços e logs de automação",
                                fontSize = 12.sp,
                                color = BentoTextSecondary
                            )
                        }
                    }
                }
            }

            // Bento Grid 5: Configurações & Backup
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onNavigate(Screen.Settings.route) }
                        .testTag("menu_tile_settings"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = BentoSurfaceLight),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "⚙️", fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Configurações & Backup",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPrimary
                            )
                            Text(
                                text = "Tamanho do balão, feedback tátil e exportação JSON",
                                fontSize = 12.sp,
                                color = BentoTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

        if (showAppSelector) {
            val app = context.applicationContext as? DeliveryApp
            AppSelectorDialog(
                onDismiss = { showAppSelector = false },
                onAppSelected = { selectedApp ->
                    showAppSelector = false
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedApp.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            // Aguarda 1 segundo para o app abrir em tela cheia e então aciona o desenhador
                            scope.launch {
                                delay(1000)
                                val selector = AreaSelectorOverlay(
                                    context = context,
                                    onAreaSelected = { left, top, right, bottom ->
                                        scope.launch {
                                            app?.settingsRepository?.setCustomScanArea(left, top, right, bottom)
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
                                app?.settingsRepository?.setCustomScanArea(left, top, right, bottom)
                                Toast.makeText(context, "✅ Área alvo desenhada e salva!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDismiss = {}
                    )
                    selector.show()
                }
            )
        }

        updateInfoAvailable?.let { info ->
            UpdateDialog(
                updateInfo = info,
                onDismiss = { updateInfoAvailable = null }
            )
        }
    }
}

@Composable
private fun BentoPillChip(
    modifier: Modifier = Modifier,
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(99.dp),
        color = Color.White.copy(alpha = 0.65f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) BentoPrimaryDark else BentoTextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun BentoModuleCard(
    modifier: Modifier = Modifier,
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .testTag("bento_card_${title.lowercase()}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoSurfaceLight),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = emoji, fontSize = 24.sp)
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = BentoTextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
