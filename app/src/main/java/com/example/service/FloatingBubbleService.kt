package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.DeliveryApp
import com.example.MainActivity
import com.example.accessibility.AccessibilityAutomationEngine
import com.example.data.local.entity.Person
import com.example.data.model.Recebedor
import com.example.data.model.SignatureData
import com.example.ui.components.SignatureCanvas
import com.example.util.AddressNormalizer
import com.example.util.ClipboardHelper
import com.example.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var floatingView: ComposeView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var lastBubbleX: Int = 20
    private var lastBubbleY: Int = 200

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun triggerHapticFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (_: Exception) {}
    }

    private fun executeSignatureDrawing(signatureJson: String) {
        if (signatureJson.isBlank()) {
            Toast.makeText(this@FloatingBubbleService, "Nenhuma assinatura cadastrada para este recebedor.", Toast.LENGTH_SHORT).show()
            return
        }
        val sigData = SignatureData.fromJson(signatureJson)
        if (sigData == null || sigData.strokes.isEmpty()) {
            Toast.makeText(this@FloatingBubbleService, "Assinatura vazia ou sem traços salvos.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Recolher temporariamente o painel para desobstruir a tela do app de entregas
        updateWindowLayoutMode(OverlayMode.BUBBLE)
        Toast.makeText(this@FloatingBubbleService, "✍️ Desenhando assinatura...", Toast.LENGTH_SHORT).show()

        // 2. Obter a velocidade configurada pelo usuário nas preferências
        serviceScope.launch {
            val speedMode = try {
                val app = applicationContext as? DeliveryApp
                app?.settingsRepository?.getSettings()?.first()?.signatureSpeedMode ?: "ULTRA_SLOW"
            } catch (e: Exception) {
                "ULTRA_SLOW"
            }

            // 3. Aguardar breve intervalo para liberação de foco da janela
            mainHandler.postDelayed({
                AccessibilityAutomationEngine.dispatchSignatureGestures(sigData, speedMode = speedMode) { success, msg ->
                    Toast.makeText(this@FloatingBubbleService, msg, Toast.LENGTH_SHORT).show()
                }
            }, 200L)
        }
    }

    private fun getScreenBounds(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager?.currentWindowMetrics
            val bounds = windowMetrics?.bounds
            if (bounds != null) {
                Pair(bounds.width(), bounds.height())
            } else {
                val dm = resources.displayMetrics
                Pair(dm.widthPixels, dm.heightPixels)
            }
        } else {
            val dm = resources.displayMetrics
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private enum class OverlayMode { BUBBLE, PANEL, MODAL, FULLSCREEN_SIGNATURE }

    private fun updateWindowLayoutMode(mode: OverlayMode) {
        val params = windowLayoutParams ?: return
        val view = floatingView ?: return

        when (mode) {
            OverlayMode.BUBBLE, OverlayMode.PANEL -> {
                val (screenWidth, screenHeight) = getScreenBounds()
                val density = resources.displayMetrics.density
                val estimatedWidth = if (mode == OverlayMode.PANEL) (330 * density).toInt() else (64 * density).toInt()
                val estimatedHeight = if (mode == OverlayMode.PANEL) (400 * density).toInt() else (64 * density).toInt()
                val viewWidth = view.width.takeIf { it > 0 } ?: estimatedWidth
                val viewHeight = view.height.takeIf { it > 0 } ?: estimatedHeight

                val maxX = (screenWidth - viewWidth).coerceAtLeast(0)
                val maxY = (screenHeight - viewHeight).coerceAtLeast(0)

                lastBubbleX = lastBubbleX.coerceIn(0, maxX)
                lastBubbleY = lastBubbleY.coerceIn(0, maxY)

                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                params.gravity = Gravity.TOP or Gravity.START
                params.x = lastBubbleX
                params.y = lastBubbleY
                params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }
            OverlayMode.MODAL -> {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.CENTER
                params.x = 0
                params.y = 0
                params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            }
            OverlayMode.FULLSCREEN_SIGNATURE -> {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.CENTER
                params.x = 0
                params.y = 0
                params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            }
        }

        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "floating_bubble_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Assistente Flutuante de Entregas",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação ativa do assistente flutuante de preenchimento"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Assistente de Entregas Ativo")
            .setContentText("Balão flutuante em execução. Toque para abrir o app.")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createFloatingBubble() {
        if (!PermissionUtils.hasOverlayPermission(this)) {
            Toast.makeText(this, "Permissão de sobreposição necessária", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        windowLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)

            setContent {
                FloatingBubbleUI(
                    onDrag = { dx, dy ->
                        windowLayoutParams?.let { params ->
                            val (screenWidth, screenHeight) = getScreenBounds()
                            val viewWidth = this@apply.width.takeIf { it > 0 } ?: (56 * resources.displayMetrics.density).toInt()
                            val viewHeight = this@apply.height.takeIf { it > 0 } ?: (56 * resources.displayMetrics.density).toInt()

                            val maxX = (screenWidth - viewWidth).coerceAtLeast(0)
                            val maxY = (screenHeight - viewHeight).coerceAtLeast(0)

                            val newX = (params.x + dx.toInt()).coerceIn(0, maxX)
                            val newY = (params.y + dy.toInt()).coerceIn(0, maxY)

                            params.x = newX
                            params.y = newY
                            lastBubbleX = newX
                            lastBubbleY = newY
                            try {
                                windowManager?.updateViewLayout(this@apply, params)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    onCloseService = {
                        stopSelf()
                    },
                    onOpenApp = {
                        val intent = Intent(this@FloatingBubbleService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                    }
                )
            }
        }

        try {
            windowManager?.addView(floatingView, windowLayoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    @Composable
    private fun FloatingBubbleUI(
        onDrag: (Float, Float) -> Unit,
        onCloseService: () -> Unit,
        onOpenApp: () -> Unit
    ) {
        val appSettings by DeliveryApp.instance.settingsRepository.getSettings().collectAsState(initial = com.example.data.repository.AppSettings())
        var isExpanded by remember { mutableStateOf(false) }
        var isCompactMode by remember { mutableStateOf(false) }

        androidx.compose.runtime.LaunchedEffect(appSettings.isCompactMode) {
            isCompactMode = appSettings.isCompactMode
        }
        
        // Modals state
        var isEditModalOpen by remember { mutableStateOf(false) }
        var isSearchModalOpen by remember { mutableStateOf(false) }
        var isMultipleRecipientsModalOpen by remember { mutableStateOf(false) }
        var searchModalQuery by remember { mutableStateOf("") }
        var isSignatureFullScreen by remember { mutableStateOf(false) }

        // Form Fields for Editing / Registering in the Assistant
        var editingPersonId by remember { mutableStateOf<Long?>(null) }
        var editingRecebedorId by remember { mutableStateOf<String?>(null) }
        var editedAddress by remember { mutableStateOf("") }
        var recipientName by remember { mutableStateOf("") }
        var recipientDocument by remember { mutableStateOf("") }
        var collectedSignatureData by remember { mutableStateOf<SignatureData?>(null) }

        var isListeningName by remember { mutableStateOf(false) }
        var isListeningDoc by remember { mutableStateOf(false) }
        val serviceContext = this@FloatingBubbleService

        // Rastreamento dos valores iniciais para trava de segurança contra fechamento acidental
        var initialEditedAddress by remember { mutableStateOf("") }
        var initialRecipientName by remember { mutableStateOf("") }
        var initialRecipientDocument by remember { mutableStateOf("") }
        var initialCollectedSigJson by remember { mutableStateOf("") }
        var showDiscardEditConfirmDialog by remember { mutableStateOf(false) }
        var showBubbleClearSigConfirmDialog by remember { mutableStateOf(false) }

        val hasUnsavedEditChanges by remember {
            androidx.compose.runtime.derivedStateOf {
                editedAddress.trim() != initialEditedAddress.trim() ||
                        recipientName.trim() != initialRecipientName.trim() ||
                        recipientDocument.trim() != initialRecipientDocument.trim() ||
                        (collectedSignatureData?.toJson() ?: "") != initialCollectedSigJson
            }
        }

        val handleAttemptCloseEditModal: () -> Unit = {
            if (hasUnsavedEditChanges) {
                showDiscardEditConfirmDialog = true
            } else {
                isEditModalOpen = false
                updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
            }
        }

        val automationState by AccessibilityAutomationEngine.state.collectAsState()

        val person = automationState.selectedRecebedor
        val availableRecebedores = automationState.availableRecebedores
        val address = automationState.detectedAddressText.ifBlank { "Nenhum endereço detectado" }

        // Automatically open the selection modal when multiple recipients are detected for a new address
        androidx.compose.runtime.LaunchedEffect(availableRecebedores) {
            if (availableRecebedores.size > 1) {
                isMultipleRecipientsModalOpen = true
                updateWindowLayoutMode(OverlayMode.MODAL)
            }
        }

        // Helper to open Edit/Complete modal for current recipient
        val openEditForCurrent: (focusDoc: Boolean) -> Unit = { _ ->
            val matchedP = automationState.matchedPerson
            val initialAddr = matchedP?.endereco ?: AddressNormalizer.extractStreetAndNumber(address)
            val initialNm = person?.nome ?: matchedP?.nome ?: ""
            val initialDoc = person?.documento ?: matchedP?.documento ?: ""
            val sigStr = person?.assinatura ?: matchedP?.assinatura ?: ""

            editingPersonId = matchedP?.id
            editingRecebedorId = person?.id
            editedAddress = initialAddr
            recipientName = initialNm
            recipientDocument = initialDoc
            collectedSignatureData = if (sigStr.isNotBlank()) SignatureData.fromJson(sigStr) else null

            initialEditedAddress = initialAddr
            initialRecipientName = initialNm
            initialRecipientDocument = initialDoc
            initialCollectedSigJson = sigStr

            isEditModalOpen = true
            updateWindowLayoutMode(OverlayMode.MODAL)
        }

        // Helper to open Register modal for an additional resident at the current address
        val openRegisterNewResidentForAddress: (targetPerson: Person?) -> Unit = { targetPerson ->
            val initialAddr = targetPerson?.endereco ?: AddressNormalizer.extractStreetAndNumber(address)
            editingPersonId = targetPerson?.id
            editingRecebedorId = "new_co"
            editedAddress = initialAddr
            recipientName = ""
            recipientDocument = ""
            collectedSignatureData = null

            initialEditedAddress = initialAddr
            initialRecipientName = ""
            initialRecipientDocument = ""
            initialCollectedSigJson = ""

            isSearchModalOpen = false
            isEditModalOpen = true
            updateWindowLayoutMode(OverlayMode.MODAL)
        }

        Box(
            modifier = if (isSignatureFullScreen) Modifier.fillMaxSize() else Modifier.padding(8.dp)
        ) {
            if (isSignatureFullScreen) {
                // TELA TODA DE ASSINATURA HORIZONTAL MÁXIMA
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A))
                ) {
                    SignatureCanvas(
                        modifier = Modifier.fillMaxSize(),
                        initialSignature = collectedSignatureData,
                        isDarkTheme = true,
                        onSignatureConfirmed = { signature ->
                            collectedSignatureData = signature
                            isSignatureFullScreen = false
                            updateWindowLayoutMode(OverlayMode.MODAL)
                            Toast.makeText(this@FloatingBubbleService, "Assinatura gravada!", Toast.LENGTH_SHORT).show()
                        },
                        onCancel = {
                            isSignatureFullScreen = false
                            updateWindowLayoutMode(OverlayMode.MODAL)
                        }
                    )
                }
            } else if (isSearchModalOpen) {
                // MODAL DE PESQUISA DE DESTINATÁRIOS SALVOS
                val searchResults by remember(searchModalQuery) {
                    DeliveryApp.instance.personRepository.searchPersons(searchModalQuery)
                }.collectAsState(initial = emptyList())

                Card(
                    modifier = Modifier
                        .width(340.dp)
                        .shadow(16.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cabeçalho do Modal de Pesquisa
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF1976D2),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pesquisar Destinatários",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1976D2)
                                )
                            }
                            IconButton(
                                onClick = {
                                    isSearchModalOpen = false
                                    updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE0E0E0))

                        // Campo de Busca
                        OutlinedTextField(
                            value = searchModalQuery,
                            onValueChange = { searchModalQuery = it },
                            placeholder = { Text("Nome, documento ou endereço...", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray)
                            },
                            trailingIcon = {
                                if (searchModalQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchModalQuery = "" }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.Gray)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Lista de Resultados
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (searchResults.isEmpty()) {
                                item {
                                    Text(
                                        text = if (searchModalQuery.isBlank()) "Nenhum destinatário cadastrado." else "Nenhum resultado para \"$searchModalQuery\".",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            } else {
                                items(searchResults, key = { it.id }) { itemPerson ->
                                    val hasDoc = itemPerson.documento.isNotBlank()
                                    val hasSig = itemPerson.assinatura.isNotBlank()
                                    val isIncomplete = !hasDoc || !hasSig

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isIncomplete) Color(0xFFFFFDE7) else Color(0xFFF0F4F8),
                                        border = if (isIncomplete) BorderStroke(1.dp, Color(0xFFFFD54F)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                AccessibilityAutomationEngine.setMatchedPersonDirect(itemPerson)
                                                isSearchModalOpen = false
                                                updateWindowLayoutMode(OverlayMode.PANEL)
                                                Toast.makeText(
                                                    this@FloatingBubbleService,
                                                    "Selecionado: ${itemPerson.nome}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    // Endereço em primeiro lugar e com destaque
                                                    Text(
                                                        text = if (itemPerson.endereco.isNotBlank()) itemPerson.endereco else "Sem endereço cadastrado",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1A237E),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Destinatário: ${itemPerson.nome}",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF263238),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (hasDoc) {
                                                        Text(
                                                            text = "Doc: ${itemPerson.documento}",
                                                            fontSize = 10.5.sp,
                                                            color = Color(0xFF455A64)
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "⚠️ Sem Documento",
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFD84315)
                                                        )
                                                    }
                                                }

                                                // Status Badge
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                            if (!hasSig) Color(0xFFFFEBEE)
                                                            else Color(0xFFE8F5E9)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (hasSig) "✓ Assinatura" else "⚠️ Sem Assinatura",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (hasSig) Color(0xFF2E7D32) else Color(0xFFC62828)
                                                    )
                                                }
                                            }

                                            // Botões de Ação do Item de Busca
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // Botão Selecionar / Usar
                                                Button(
                                                    onClick = {
                                                        AccessibilityAutomationEngine.setMatchedPersonDirect(itemPerson)
                                                        isSearchModalOpen = false
                                                        updateWindowLayoutMode(OverlayMode.PANEL)
                                                        Toast.makeText(
                                                            this@FloatingBubbleService,
                                                            "Selecionado: ${itemPerson.nome}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("Selecionar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }

                                                // Botão Preencher
                                                Button(
                                                    onClick = {
                                                        AccessibilityAutomationEngine.setMatchedPersonDirect(itemPerson)
                                                        val res = AccessibilityAutomationEngine.fillFields(
                                                            itemPerson.nome,
                                                            itemPerson.documento
                                                        )
                                                        Toast.makeText(this@FloatingBubbleService, res.message, Toast.LENGTH_SHORT).show()
                                                        isSearchModalOpen = false
                                                        updateWindowLayoutMode(OverlayMode.PANEL)
                                                    },
                                                    modifier = Modifier.weight(1.1f).height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                                ) {
                                                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("Preencher", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }

                                                 // Botão Editar / Completar Cadastro Direto no Assistente
                                                OutlinedButton(
                                                    onClick = {
                                                        val initialAddr = itemPerson.endereco
                                                        val initialNm = itemPerson.nome
                                                        val initialDoc = itemPerson.documento
                                                        val sigStr = itemPerson.assinatura

                                                        editingPersonId = itemPerson.id
                                                        editingRecebedorId = "main"
                                                        editedAddress = initialAddr
                                                        recipientName = initialNm
                                                        recipientDocument = initialDoc
                                                        collectedSignatureData = if (sigStr.isNotBlank()) {
                                                            SignatureData.fromJson(sigStr)
                                                        } else null

                                                        initialEditedAddress = initialAddr
                                                        initialRecipientName = initialNm
                                                        initialRecipientDocument = initialDoc
                                                        initialCollectedSigJson = sigStr

                                                        isSearchModalOpen = false
                                                        isEditModalOpen = true
                                                        updateWindowLayoutMode(OverlayMode.MODAL)
                                                    },
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = if (isIncomplete) Color(0xFFE65100) else Color(0xFF0D47A1)
                                                    )
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = if (isIncomplete) "Completar" else "Editar",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                // Botão + Outra Pessoa / Morador neste Endereço
                                                OutlinedButton(
                                                    onClick = {
                                                        openRegisterNewResidentForAddress(itemPerson)
                                                    },
                                                    modifier = Modifier.weight(1.1f).height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = Color(0xFF6A1B9A)
                                                    )
                                                ) {
                                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = "+ Pessoa",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (isEditModalOpen) {
                // MODAL DE EDIÇÃO / CADASTRO DIRETO NO ASSISTENTE
                val isAddingNewResident = editingRecebedorId == "new_co"
                val isEditingExisting = editingPersonId != null && editingPersonId!! > 0 && !isAddingNewResident
                val isMissingDocInForm = recipientDocument.isBlank()
                val isMissingSigInForm = collectedSignatureData == null

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(330.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cabeçalho do Modal
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isEditingExisting) Icons.Default.Edit else Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = if (isEditingExisting) Color(0xFF0288D1) else if (isAddingNewResident) Color(0xFF6A1B9A) else Color(0xFF0D47A1),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEditingExisting) "Editar / Completar Dados" else if (isAddingNewResident) "Adicionar Morador no Endereço" else "Cadastrar Destinatário",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = if (isEditingExisting) Color(0xFF0288D1) else if (isAddingNewResident) Color(0xFF6A1B9A) else Color(0xFF0D47A1)
                                )
                            }
                            IconButton(
                                onClick = handleAttemptCloseEditModal,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE0E0E0))

                        // Alerta se faltar algo no formulário
                        if (isEditingExisting && (isMissingDocInForm || isMissingSigInForm)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFF3E0),
                                border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = buildString {
                                            append("Faltando: ")
                                            if (isMissingDocInForm && isMissingSigInForm) append("Documento e Assinatura")
                                            else if (isMissingDocInForm) append("Documento (CPF/RG)")
                                            else append("Assinatura")
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                }
                            }
                        }

                        // Endereço
                        Text(
                            text = "Endereço (Rua e Número):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF424242)
                        )
                        OutlinedTextField(
                            value = editedAddress,
                            onValueChange = { editedAddress = it },
                            placeholder = { Text("Ex: Rua das Flores, 123", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Nome do Recebedor
                        Text(
                            text = "Nome do Recebedor *:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF424242)
                        )
                        OutlinedTextField(
                            value = recipientName,
                            onValueChange = { recipientName = it },
                            placeholder = { Text("Ex: Maria da Silva", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (!isListeningName) {
                                            isListeningName = true
                                            com.example.util.SpeechHelper.startListening(
                                                context = serviceContext,
                                                onReady = { Toast.makeText(serviceContext, "Fale o nome...", Toast.LENGTH_SHORT).show() },
                                                onResult = { result ->
                                                    isListeningName = false
                                                    val processed = com.example.util.SpeechHelper.processSpokenName(result)
                                                    if (processed.isNotBlank()) {
                                                        recipientName = processed
                                                    }
                                                },
                                                onError = { err ->
                                                    isListeningName = false
                                                    Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                }
                                            )
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

                        // Documento do Recebedor (numérico)
                        Text(
                            text = "Documento do Recebedor (CPF / RG):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF424242)
                        )
                        val isDocValidState = remember(recipientDocument) {
                            if (recipientDocument.isBlank()) null else com.example.util.SpeechHelper.isValidDocument(recipientDocument)
                        }

                        OutlinedTextField(
                            value = recipientDocument,
                            onValueChange = { input ->
                                recipientDocument = input.filter { it.isLetterOrDigit() || it == '.' || it == '-' }
                            },
                            placeholder = { Text("Ex: 123.456.789-00", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
                                            isListeningDoc = true
                                            com.example.util.SpeechHelper.startListening(
                                                context = serviceContext,
                                                onReady = { Toast.makeText(serviceContext, "Fale os números do documento...", Toast.LENGTH_SHORT).show() },
                                                onResult = { result ->
                                                    isListeningDoc = false
                                                    val processed = com.example.util.SpeechHelper.processSpokenDocument(result)
                                                    if (processed.isNotBlank()) {
                                                        recipientDocument = processed
                                                    }
                                                },
                                                onError = { err ->
                                                    isListeningDoc = false
                                                    Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                }
                                            )
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

                        // Assinatura
                        Text(
                            text = "Assinatura do Recebedor:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF424242)
                        )

                        if (collectedSignatureData != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE8F5E9),
                                border = BorderStroke(1.dp, Color(0xFF81C784)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Assinatura gravada", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                isSignatureFullScreen = true
                                                updateWindowLayoutMode(OverlayMode.FULLSCREEN_SIGNATURE)
                                            },
                                            modifier = Modifier.height(30.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Refazer", fontSize = 10.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { showBubbleClearSigConfirmDialog = true },
                                            modifier = Modifier.height(30.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Limpar", fontSize = 10.sp, color = Color.Red)
                                        }
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    isSignatureFullScreen = true
                                    updateWindowLayoutMode(OverlayMode.FULLSCREEN_SIGNATURE)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) {
                                Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "✍️ COLETAR ASSINATURA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Botões Salvar / Cancelar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = handleAttemptCloseEditModal,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("CANCELAR", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    if (recipientName.isBlank()) {
                                        Toast.makeText(this@FloatingBubbleService, "Informe o nome do recebedor!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    val sigJson = collectedSignatureData?.toJson() ?: ""
                                    val formattedRecipientName = AddressNormalizer.capitalizeWords(recipientName.trim())

                                    if (isAddingNewResident && editingPersonId != null) {
                                        // Adicionar novo co-recebedor / morador para o mesmo endereço
                                        serviceScope.launch(Dispatchers.IO) {
                                            val existing = DeliveryApp.instance.personRepository.getPersonByIdDirect(editingPersonId!!)
                                            if (existing != null) {
                                                val extras = Recebedor.listFromJson(existing.coRecebedoresJson).toMutableList()
                                                val newRecebedor = Recebedor(
                                                    id = java.util.UUID.randomUUID().toString().take(8),
                                                    nome = formattedRecipientName,
                                                    documento = recipientDocument.trim(),
                                                    assinatura = sigJson
                                                )
                                                extras.add(newRecebedor)
                                                val updated = existing.copy(
                                                    coRecebedoresJson = Recebedor.listToJson(extras),
                                                    dataAtualizacao = System.currentTimeMillis()
                                                )
                                                DeliveryApp.instance.personRepository.updatePerson(updated)
                                                withContext(Dispatchers.Main) {
                                                    AccessibilityAutomationEngine.setMatchedPersonDirect(updated)
                                                    // Selecionar o novo recebedor recém criado
                                                    val allRecs = AccessibilityAutomationEngine.extractAllRecebedores(listOf(updated))
                                                    val createdRec = allRecs.firstOrNull { it.id.endsWith(newRecebedor.id) }
                                                    if (createdRec != null) {
                                                        AccessibilityAutomationEngine.selectRecebedor(createdRec)
                                                    }
                                                    Toast.makeText(
                                                        this@FloatingBubbleService,
                                                        "Novo morador adicionado ao endereço com sucesso!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    isEditModalOpen = false
                                                    updateWindowLayoutMode(OverlayMode.PANEL)
                                                }
                                            }
                                        }
                                    } else if (isEditingExisting && editingPersonId != null) {
                                        // Atualizar cadastro existente
                                        serviceScope.launch(Dispatchers.IO) {
                                            val existing = DeliveryApp.instance.personRepository.getPersonByIdDirect(editingPersonId!!)
                                            if (existing != null) {
                                                val updated = if (editingRecebedorId == null || editingRecebedorId == "main" || editingRecebedorId?.startsWith("p_${existing.id}_main") == true) {
                                                    existing.copy(
                                                        nome = formattedRecipientName,
                                                        documento = recipientDocument.trim(),
                                                        endereco = editedAddress.trim().ifBlank { existing.endereco },
                                                        assinatura = sigJson
                                                    )
                                                } else {
                                                    val extras = Recebedor.listFromJson(existing.coRecebedoresJson).toMutableList()
                                                    val cleanId = editingRecebedorId!!.removePrefix("p_${existing.id}_co_")
                                                    val idx = extras.indexOfFirst { it.id == cleanId || it.id == editingRecebedorId }
                                                    if (idx >= 0) {
                                                        extras[idx] = extras[idx].copy(
                                                            nome = formattedRecipientName,
                                                            documento = recipientDocument.trim(),
                                                            assinatura = sigJson
                                                        )
                                                    }
                                                    existing.copy(
                                                        endereco = editedAddress.trim().ifBlank { existing.endereco },
                                                        coRecebedoresJson = Recebedor.listToJson(extras)
                                                    )
                                                }
                                                DeliveryApp.instance.personRepository.updatePerson(updated)
                                                withContext(Dispatchers.Main) {
                                                    AccessibilityAutomationEngine.setMatchedPersonDirect(updated)
                                                    Toast.makeText(
                                                        this@FloatingBubbleService,
                                                        "Destinatário atualizado com sucesso!",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    isEditModalOpen = false
                                                    updateWindowLayoutMode(OverlayMode.PANEL)
                                                }
                                            }
                                        }
                                    } else {
                                        // Inserir novo cadastro
                                        if (editedAddress.isBlank()) {
                                            Toast.makeText(this@FloatingBubbleService, "Informe a rua e número do endereço!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        val newPerson = Person(
                                            nome = formattedRecipientName,
                                            documento = recipientDocument.trim(),
                                            endereco = editedAddress.trim(),
                                            assinatura = sigJson
                                        )

                                        serviceScope.launch(Dispatchers.IO) {
                                            val newId = DeliveryApp.instance.personRepository.insertPerson(newPerson)
                                            val saved = newPerson.copy(id = newId)
                                            withContext(Dispatchers.Main) {
                                                AccessibilityAutomationEngine.setMatchedPersonDirect(saved)
                                                Toast.makeText(
                                                    this@FloatingBubbleService,
                                                    "Destinatário cadastrado com sucesso!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                isEditModalOpen = false
                                                updateWindowLayoutMode(OverlayMode.PANEL)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isEditingExisting) Color(0xFF0288D1) else if (isAddingNewResident) Color(0xFF6A1B9A) else Color(0xFFE65100)
                                )
                            ) {
                                Icon(
                                    imageVector = if (isEditingExisting) Icons.Default.Check else Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEditingExisting) "SALVAR ALTERAÇÃO" else if (isAddingNewResident) "SALVAR MORADOR" else "SALVAR NOVO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (showDiscardEditConfirmDialog) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(0.96f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Descartar alterações?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    text = "Você preencheu ou alterou informações que ainda não foram salvas. Tem certeza que deseja sair e perder as alterações?",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showDiscardEditConfirmDialog = false },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Continuar",
                                            maxLines = 1,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            showDiscardEditConfirmDialog = false
                                            isEditModalOpen = false
                                            updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Descartar",
                                            color = Color.White,
                                            maxLines = 1,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showBubbleClearSigConfirmDialog) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(0.96f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Limpar assinatura?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    text = "A assinatura gravada para este formulário será removida. Deseja continuar?",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showBubbleClearSigConfirmDialog = false },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Cancelar",
                                            maxLines = 1,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            collectedSignatureData = null
                                            showBubbleClearSigConfirmDialog = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Sim, Limpar",
                                            color = Color.White,
                                            maxLines = 1,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } else if (isMultipleRecipientsModalOpen && availableRecebedores.size > 1) {
                // MODAL DE SELEÇÃO DE MÚLTIPLOS RECEBEDORES
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(330.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cabeçalho
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = Color(0xFF3F51B5),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Múltiplos Recebedores",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1A237E)
                                )
                            }
                            IconButton(
                                onClick = {
                                    isMultipleRecipientsModalOpen = false
                                    updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE0E0E0))

                        Text(
                            text = "Este endereço possui mais de um destinatário cadastrado. Escolha o recebedor ativo:",
                            fontSize = 11.sp,
                            color = Color(0xFF424242)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableRecebedores) { r ->
                                val isSel = r.id == person?.id
                                val hasSig = r.assinatura.isNotBlank()
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) Color(0xFFE8EAF6) else Color(0xFFF5F5F5),
                                    border = BorderStroke(1.dp, if (isSel) Color(0xFF3F51B5) else Color(0xFFE0E0E0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            AccessibilityAutomationEngine.selectRecebedor(r)
                                            isMultipleRecipientsModalOpen = false
                                            updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                                            Toast.makeText(
                                                this@FloatingBubbleService,
                                                "Selecionado: ${r.nome}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = r.nome,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color(0xFF1A237E) else Color(0xFF212121),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (r.documento.isNotBlank()) "Doc: ${r.documento}" else "Sem documento",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF757575)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (hasSig) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (hasSig) "✓ Assinado" else "⚠️ Sem Assinatura",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hasSig) Color(0xFF2E7D32) else Color(0xFFC62828)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Button(
                            onClick = {
                                isMultipleRecipientsModalOpen = false
                                updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
                        ) {
                            Text("MANTER ATUAL", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            } else if (automationState.isDrawingSignature) {
                // Indicador Flutuante Compacto exibido enquanto a automação está desenhando a assinatura
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0D47A1),
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.5.dp, Color.White),
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✍️ Assinando...",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else if (!isExpanded) {
                // Bolha Pequena Fechada (tamanho configurável)
                val bubbleSize = appSettings.bubbleSizeDp.coerceIn(44, 72).dp
                val iconSize = (appSettings.bubbleSizeDp.coerceIn(44, 72) * 0.5f).dp
                val indicatorSize = (appSettings.bubbleSizeDp.coerceIn(44, 72) * 0.25f).dp.coerceAtLeast(12.dp)

                Box(
                    modifier = Modifier
                        .size(bubbleSize)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1976D2), Color(0xFF0D47A1))
                            )
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        }
                        .clickable {
                            isCompactMode = appSettings.isCompactMode
                            isExpanded = true
                            updateWindowLayoutMode(OverlayMode.PANEL)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = "Abrir Assistente de Entregas",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )

                    // Indicador de status de detecção
                    if (person != null) {
                        Box(
                            modifier = Modifier
                                .size(indicatorSize)
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                    } else if (address.isNotBlank() && address != "Nenhum endereço detectado.") {
                        Box(
                            modifier = Modifier
                                .size(indicatorSize)
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .background(Color(0xFFFF9800), CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                    }
                }
            } else {
                // Painel Compacto ou Completo Flutuante
                Card(
                    modifier = Modifier
                        .width(if (isCompactMode) 280.dp else 330.dp)
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFDFF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Cabeçalho do Painel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            if (person != null) Color(0xFF4CAF50)
                                            else if (address.isNotBlank() && address != "Nenhum endereço detectado.") Color(0xFFFF9800)
                                            else Color.Gray,
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ASSISTENTE DE ENTREGA",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF0D47A1)
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = { isCompactMode = !isCompactMode },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCompactMode) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Alternar Modo",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { isExpanded = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Minimizar",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color(0xFFE0E0E0)
                        )

                        // BADGE DE STATUS DO ENDEREÇO (SALVO vs NÃO CADASTRADO vs NENHUM)
                        val hasDetectedAddress = address.isNotBlank() && address != "Nenhum endereço detectado."
                        if (hasDetectedAddress) {
                            if (person != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFE8F5E9),
                                    border = BorderStroke(1.dp, Color(0xFF81C784)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "✓ ENDEREÇO SALVO NO BANCO",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1B5E20)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFFF3E0),
                                    border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFE65100),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "⚠️ ENDEREÇO NÃO CADASTRADO",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            // Alerta de Múltiplos Recebedores
                            if (availableRecebedores.size > 1) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFE8EAF6),
                                    border = BorderStroke(1.dp, Color(0xFFC5CAE9)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isMultipleRecipientsModalOpen = true
                                            updateWindowLayoutMode(OverlayMode.MODAL)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Default.People,
                                                contentDescription = null,
                                                tint = Color(0xFF3F51B5),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = "MÚLTIPLOS RECEBEDORES",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1A237E)
                                                )
                                                Text(
                                                    text = "Este endereço possui ${availableRecebedores.size} destinatários.",
                                                    fontSize = 9.5.sp,
                                                    color = Color(0xFF283593)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "ALTERAR ➔",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF3F51B5)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        // Endereço Identificado na tela
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Endereço na tela:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF555555)
                                )
                                if (hasDetectedAddress) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFEDE7F6),
                                        border = BorderStroke(0.5.dp, Color(0xFFD1C4E9))
                                    ) {
                                        Text(
                                            text = "🔒 Travado",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF512DA8),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                } else if (automationState.isPausedScanning) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFFFF3E0),
                                        border = BorderStroke(0.5.dp, Color(0xFFFFB74D))
                                    ) {
                                        Text(
                                            text = "⏸️ Pausado",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (hasDetectedAddress) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFFFEBEE),
                                        border = BorderStroke(0.5.dp, Color(0xFFFFCDD2)),
                                        modifier = Modifier
                                            .clickable {
                                                AccessibilityAutomationEngine.clearDetectedAddress()
                                                Toast.makeText(this@FloatingBubbleService, "Pesquisa pausada. Clique nas setas 🔄 para buscar novamente.", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Limpar endereço",
                                                tint = Color(0xFFD32F2F),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "Limpar",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD32F2F)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                IconButton(
                                    onClick = {
                                        AccessibilityAutomationEngine.rescanCurrentScreen(forceUnlock = true)
                                        Toast.makeText(this@FloatingBubbleService, "Buscando endereço na tela...", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reescanear tela",
                                        tint = Color(0xFF1976D2),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (automationState.isPausedScanning && address.isBlank()) {
                                "Pesquisa pausada. Clique em 🔄 para pesquisar o endereço."
                            } else {
                                address.ifBlank { "Nenhum endereço detectado na tela." }
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (address.isBlank()) Color.Gray else Color(0xFF212121),
                            maxLines = if (isCompactMode) 1 else 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Pessoa Encontrada ou Estado Não Salvo
                        if (person != null) {
                            val isDocMissing = person.documento.isBlank()
                            val isSigMissing = person.assinatura.isBlank()
                            val isIncomplete = isDocMissing || isSigMissing

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isIncomplete) Color(0xFFFFFDE7) else Color(0xFFE8F5E9),
                                border = if (isIncomplete) BorderStroke(1.dp, Color(0xFFFFD54F)) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "✓ Recebedor Selecionado:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isIncomplete) Color(0xFFE65100) else Color(0xFF2E7D32)
                                        )
                                        IconButton(
                                            onClick = { openEditForCurrent(false) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Editar Recebedor",
                                                tint = Color(0xFF0288D1),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = person.nome,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncomplete) Color(0xFFE65100) else Color(0xFF1B5E20)
                                    )
                                    if (person.documento.isNotBlank()) {
                                        Text(
                                            text = "Doc: ${person.documento}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                    } else {
                                        Text(
                                            text = "⚠️ Documento não cadastrado",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD84315)
                                        )
                                    }
                                }
                            }

                            // BANNER DE SUGESTÃO DE COMPLETAR CADASTRO SE ESTIVER FALTANDO ALGO
                            if (isIncomplete) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFF3E0),
                                    border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = buildString {
                                                    append("Falta: ")
                                                    if (isDocMissing && isSigMissing) append("Documento e Assinatura")
                                                    else if (isDocMissing) append("Documento")
                                                    else append("Assinatura")
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE65100)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isSigMissing) {
                                                Button(
                                                    onClick = { openEditForCurrent(false) },
                                                    modifier = Modifier.weight(1.2f).height(30.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                                ) {
                                                    Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("+ Assinatura", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            if (isDocMissing) {
                                                Button(
                                                    onClick = { openEditForCurrent(true) },
                                                    modifier = Modifier.weight(1.2f).height(30.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("+ Documento", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { openEditForCurrent(false) },
                                                modifier = Modifier.weight(1f).height(30.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp)
                                            ) {
                                                Text("Editar", fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            if (availableRecebedores.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (availableRecebedores.size > 1) "Moradores neste endereço:" else "Morador:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF555555)
                                    )
                                    // Botão para cadastrar mais um morador/pessoa neste endereço
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFF3E5F5),
                                        border = BorderStroke(1.dp, Color(0xFFCE93D8)),
                                        modifier = Modifier.clickable {
                                            openRegisterNewResidentForAddress(automationState.matchedPerson)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("+ Outro Morador", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B9A))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    availableRecebedores.forEach { r ->
                                        val isSel = r.id == person?.id
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) Color(0xFF2E7D32) else Color(0xFFE0E0E0))
                                                .clickable { AccessibilityAutomationEngine.selectRecebedor(r) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = r.nome,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSel) Color.White else Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (hasDetectedAddress) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFF8E1),
                                border = BorderStroke(1.dp, Color(0xFFFFE082)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Nenhum destinatário encontrado para este endereço.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFD84315)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Cadastre agora para salvar o nome, documento e assinatura deste local.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF616161)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF5F5F5),
                                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Aguardando detecção de endereço na tela.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Abra o aplicativo de entregas ou selecione a área do endereço.",
                                        fontSize = 10.sp,
                                        color = Color(0xFF888888)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Botões de Ação
                        if (person != null) {
                            val hasSignature = person.assinatura.isNotBlank()

                            // Botão 1: Preencher Nome e Doc no App
                            Button(
                                onClick = {
                                    if (appSettings.vibrationEnabled) {
                                        triggerHapticFeedback()
                                    }
                                    val res = AccessibilityAutomationEngine.fillFields(
                                        person.nome,
                                        person.documento
                                    )
                                    Toast.makeText(this@FloatingBubbleService, res.message, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "⚡ PREENCHER NOME E DOC",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Botão 2: Desenhar Assinatura na Tela (Dedicado)
                            if (hasSignature) {
                                Button(
                                    onClick = {
                                        if (appSettings.vibrationEnabled) {
                                            triggerHapticFeedback()
                                        }
                                        isExpanded = false
                                        executeSignatureDrawing(person.assinatura)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                ) {
                                    Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "✍️ DESENHAR ASSINATURA NA TELA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Linha de Atalhos: Copiar Nome, Copiar Doc e Preencher+Assinar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (appSettings.vibrationEnabled) {
                                            triggerHapticFeedback()
                                        }
                                        ClipboardHelper.copyToClipboard(
                                            this@FloatingBubbleService,
                                            "Nome",
                                            person.nome
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("Copiar Nome", fontSize = 9.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (appSettings.vibrationEnabled) {
                                            triggerHapticFeedback()
                                        }
                                        ClipboardHelper.copyToClipboard(
                                            this@FloatingBubbleService,
                                            "Documento",
                                            person.documento
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("Copiar Doc", fontSize = 9.sp, maxLines = 1)
                                }

                                if (hasSignature) {
                                    OutlinedButton(
                                        onClick = {
                                            if (appSettings.vibrationEnabled) {
                                                triggerHapticFeedback()
                                            }
                                            isExpanded = false
                                            val res = AccessibilityAutomationEngine.fillFields(
                                                person.nome,
                                                person.documento
                                            )
                                            Toast.makeText(this@FloatingBubbleService, res.message, Toast.LENGTH_SHORT).show()
                                            executeSignatureDrawing(person.assinatura)
                                        },
                                        modifier = Modifier.weight(1.3f).height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text("Preencher+Assinar", fontSize = 8.5.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    val extracted = AddressNormalizer.extractStreetAndNumber(address)
                                    editingPersonId = null
                                    editingRecebedorId = null
                                    editedAddress = extracted
                                    recipientName = ""
                                    recipientDocument = ""
                                    collectedSignatureData = null

                                    initialEditedAddress = extracted
                                    initialRecipientName = ""
                                    initialRecipientDocument = ""
                                    initialCollectedSigJson = ""

                                    isEditModalOpen = true
                                    updateWindowLayoutMode(OverlayMode.MODAL)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CADASTRAR DESTINATÁRIO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!isCompactMode) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isExpanded = false
                                        val app = application as? DeliveryApp
                                        val selector = AreaSelectorOverlay(
                                            context = this@FloatingBubbleService,
                                            onAreaSelected = { left, top, right, bottom ->
                                                serviceScope.launch {
                                                    app?.settingsRepository?.setCustomScanArea(left, top, right, bottom)
                                                    Toast.makeText(this@FloatingBubbleService, "Área alvo calibrada!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onDismiss = {}
                                        )
                                        selector.show()
                                    },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Calibrar", fontSize = 10.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        isSearchModalOpen = true
                                        searchModalQuery = ""
                                        updateWindowLayoutMode(OverlayMode.MODAL)
                                    },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pesquisar", fontSize = 10.sp)
                                }

                                OutlinedButton(
                                    onClick = { onCloseService() },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Fechar", fontSize = 10.sp, color = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                DeliveryApp.instance.settingsRepository.setBubbleEnabled(false)
            } catch (_: Exception) {}
        }

        if (floatingView != null && windowManager != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.service.STOP_BUBBLE"
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
        fun isServiceRunning(): Boolean = _isRunning.value
    }
}
