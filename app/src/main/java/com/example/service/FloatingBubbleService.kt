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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.example.data.repository.AppSettings
import com.example.ui.components.SignatureCanvas
import com.example.util.AddressNormalizer
import com.example.util.ClipboardHelper
import com.example.util.FeedbackHelper
import com.example.util.PermissionUtils
import com.example.util.RecipientDraftManager
import com.example.util.RecipientDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SearchAddressGroup(
    val addressKey: String,
    val street: String,
    val number: String,
    val neighborhood: String,
    val complements: List<String>,
    val personsByComplement: Map<String, List<Person>>,
    val personsWithoutComplement: List<Person>,
    val allPersons: List<Person>
)

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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun triggerHapticFeedback() {
        FeedbackHelper.triggerClick(this@FloatingBubbleService, vibrationEnabled = true)
    }

    private fun triggerSuccessFeedback() {
        serviceScope.launch {
            val app = applicationContext as? DeliveryApp
            val settings = app?.settingsRepository?.getSettings()?.first() ?: AppSettings()
            FeedbackHelper.triggerSuccess(
                this@FloatingBubbleService,
                vibrationEnabled = settings.vibrationEnabled,
                soundEnabled = settings.soundEnabled
            )
        }
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
        val sigStartTime = System.currentTimeMillis()
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
                    val sigDuration = System.currentTimeMillis() - sigStartTime
                    Toast.makeText(this@FloatingBubbleService, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        com.example.util.AppActivityTracker.logAction(
                            actionType = "SIGNATURE_APPLIED",
                            title = "Assinatura Aplicada na Tela",
                            details = "Traços desenhados via acessibilidade.",
                            category = "Automação",
                            durationMs = sigDuration,
                            incrementSignature = true
                        )
                        triggerSuccessFeedback()
                        mainHandler.postDelayed({
                            AccessibilityAutomationEngine.resetAndClearAfterSignature(5000L)
                        }, 300L)
                    }
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
                AccessibilityAutomationEngine.setModalActive(false)
                val (screenWidth, screenHeight) = getScreenBounds()
                val density = resources.displayMetrics.density
                val estimatedWidth = if (mode == OverlayMode.PANEL) (360 * density).toInt() else (64 * density).toInt()
                val estimatedHeight = if (mode == OverlayMode.PANEL) (480 * density).toInt() else (64 * density).toInt()
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
                params.dimAmount = 0f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                    params.blurBehindRadius = 0
                }
            }
            OverlayMode.MODAL -> {
                AccessibilityAutomationEngine.setModalActive(true)
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.CENTER
                params.x = 0
                params.y = 0
                params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                params.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                params.dimAmount = 0.55f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    val density = resources.displayMetrics.density
                    params.blurBehindRadius = (25 * density).toInt()
                }
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            }
            OverlayMode.FULLSCREEN_SIGNATURE -> {
                AccessibilityAutomationEngine.setModalActive(true)
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.CENTER
                params.x = 0
                params.y = 0
                params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                params.dimAmount = 0f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                    params.blurBehindRadius = 0
                }
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

        com.example.util.AppActivityTracker.onFloatingAssistantStateChanged(true)

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
        var editModalTab by remember { mutableStateOf("RESIDENT") } // "ADDRESS" or "RESIDENT"
        var editingPersonId by remember { mutableStateOf<Long?>(null) }
        var editingRecebedorId by remember { mutableStateOf<String?>(null) }
        var editedStreet by remember { mutableStateOf("") }
        var editedNumber by remember { mutableStateOf("") }
        var editedComplement by remember { mutableStateOf("") }
        var editedNeighborhood by remember { mutableStateOf("") }
        var recipientName by remember { mutableStateOf("") }
        var recipientDocument by remember { mutableStateOf("") }
        var collectedSignatureData by remember { mutableStateOf<SignatureData?>(null) }
        var isSavingRecipient by remember { mutableStateOf(false) }

        var isListeningName by remember { mutableStateOf(false) }
        var isListeningDoc by remember { mutableStateOf(false) }
        var isListeningStreet by remember { mutableStateOf(false) }
        var isListeningNumber by remember { mutableStateOf(false) }
        var isListeningComplement by remember { mutableStateOf(false) }
        var isListeningNeighborhood by remember { mutableStateOf(false) }
        var isListeningSearch by remember { mutableStateOf(false) }
        val serviceContext = this@FloatingBubbleService

        // Rastreamento dos valores iniciais para trava de segurança contra fechamento acidental
        var initialEditedStreet by remember { mutableStateOf("") }
        var initialEditedNumber by remember { mutableStateOf("") }
        var initialEditedComplement by remember { mutableStateOf("") }
        var initialEditedNeighborhood by remember { mutableStateOf("") }
        var initialRecipientName by remember { mutableStateOf("") }
        var initialRecipientDocument by remember { mutableStateOf("") }
        var initialCollectedSigJson by remember { mutableStateOf("") }
        var showDiscardEditConfirmDialog by remember { mutableStateOf(false) }
        var showBubbleClearSigConfirmDialog by remember { mutableStateOf(false) }

        // Diálogo de Adicionar Complemento no Endereço Detectado
        var showAddComplementDialog by remember { mutableStateOf(false) }
        var newComplementInput by remember { mutableStateOf("") }
        var isListeningNewComp by remember { mutableStateOf(false) }
        var searchModalSelectedComplements by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        // Diálogo de Mover / Copiar Morador no balão
        var showMoveOrCopyDialog by remember { mutableStateOf(false) }
        var isMoveAction by remember { mutableStateOf(true) } // true = Mover, false = Copiar
        var moveOrCopyTargetStreet by remember { mutableStateOf("") }
        var moveOrCopyTargetNumber by remember { mutableStateOf("") }
        var moveOrCopyTargetComplement by remember { mutableStateOf("") }
        var moveOrCopyTargetBairro by remember { mutableStateOf("") }
        var moveOrCopySearchQuery by remember { mutableStateOf("") }
        var moveOrCopyStreetError by remember { mutableStateOf(false) }
        var isListeningMoveStreet by remember { mutableStateOf(false) }
        var isListeningMoveNumber by remember { mutableStateOf(false) }
        var isListeningMoveComp by remember { mutableStateOf(false) }
        var isListeningMoveBairro by remember { mutableStateOf(false) }
        var isExecutingMoveOrCopy by remember { mutableStateOf(false) }
        var moveOrCopyTargetReceiver by remember { mutableStateOf<Recebedor?>(null) }
        var moveOrCopySourcePerson by remember { mutableStateOf<Person?>(null) }
        var draftAvailable by remember { mutableStateOf<RecipientDraft?>(null) }

        // Diálogo para apagar recebedor do endereço
        var showDeleteReceiverConfirmDialog by remember { mutableStateOf(false) }
        var receiverToDelete by remember { mutableStateOf<Pair<Recebedor, Person?>?>(null) }
        var isDeletingReceiver by remember { mutableStateOf(false) }

        // Diálogo para alterar complemento de morador específico no endereço
        var showChangeComplementDialog by remember { mutableStateOf(false) }
        var receiverToChangeComplement by remember { mutableStateOf<Pair<Recebedor, Person?>?>(null) }
        var targetNewComplementInput by remember { mutableStateOf("") }
        var isListeningChangeComp by remember { mutableStateOf(false) }
        var isExecutingChangeComplement by remember { mutableStateOf(false) }

        // Proteção e recuperação automática de rascunho não salvo
        androidx.compose.runtime.LaunchedEffect(isEditModalOpen) {
            if (isEditModalOpen) {
                val draft = RecipientDraftManager.getDraft(this@FloatingBubbleService)
                if (draft != null && recipientName.isBlank() && recipientDocument.isBlank() && collectedSignatureData == null) {
                    draftAvailable = draft
                } else {
                    draftAvailable = null
                }
            } else {
                draftAvailable = null
            }
        }

        // Salva rascunho automaticamente conforme o usuário preenche dados
        androidx.compose.runtime.LaunchedEffect(
            isEditModalOpen, editedStreet, editedNumber, editedComplement, editedNeighborhood,
            recipientName, recipientDocument, collectedSignatureData
        ) {
            if (isEditModalOpen && (recipientName.isNotBlank() || recipientDocument.isNotBlank() || collectedSignatureData != null)) {
                RecipientDraftManager.saveDraft(
                    context = this@FloatingBubbleService,
                    street = editedStreet,
                    number = editedNumber,
                    complement = editedComplement,
                    neighborhood = editedNeighborhood,
                    name = recipientName,
                    document = recipientDocument,
                    signatureJson = collectedSignatureData?.toJson() ?: ""
                )
            }
        }

        val hasUnsavedEditChanges by remember {
            androidx.compose.runtime.derivedStateOf {
                editedStreet.trim() != initialEditedStreet.trim() ||
                        editedNumber.trim() != initialEditedNumber.trim() ||
                        editedComplement.trim() != initialEditedComplement.trim() ||
                        editedNeighborhood.trim() != initialEditedNeighborhood.trim() ||
                        recipientName.trim() != initialRecipientName.trim() ||
                        recipientDocument.trim() != initialRecipientDocument.trim() ||
                        (collectedSignatureData?.toJson() ?: "") != initialCollectedSigJson
            }
        }

        val handleAttemptCloseEditModal: () -> Unit = {
            if (!isSavingRecipient) {
                if (hasUnsavedEditChanges) {
                    showDiscardEditConfirmDialog = true
                } else {
                    isEditModalOpen = false
                    updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                }
            }
        }

        val automationState by AccessibilityAutomationEngine.state.collectAsState()

        val person = automationState.selectedRecebedor
        val availableRecebedores = automationState.availableRecebedores
        val address = automationState.detectedAddressText.ifBlank { "Nenhum endereço detectado" }
        val hasDetectedAddress = automationState.detectedAddressText.isNotBlank()

        // Keep multiple recipients state synced without forcing intrusive popup modal
        androidx.compose.runtime.LaunchedEffect(availableRecebedores) {
            // Selected recebedor is maintained by AccessibilityAutomationEngine
        }

        // Helper to open Edit/Complete modal for a specific recipient or address
        val openEditForReceiver: (targetRec: Recebedor?, targetPerson: Person?, tab: String, focusDoc: Boolean) -> Unit = { targetRec, targetPerson, tab, _ ->
            val detected = automationState.detectedAddressText.trim()
            val parsed = AddressNormalizer.parseAddressComponents(detected)

            // Determinar o recebedor alvo com fallback para o selecionado ou primeiro disponível
            val effectiveRec = targetRec ?: person ?: availableRecebedores.firstOrNull()

            // Descobrir a entidade Person do banco de dados (seja por targetPerson explícito,
            // personId contido no id do recebedor ou matchedPerson atual)
            val personIdFromRec = effectiveRec?.id?.removePrefix("p_")?.substringBefore("_")?.toLongOrNull()
            val resolvedPerson = targetPerson
                ?: (if (personIdFromRec != null) automationState.candidatePersons.firstOrNull { it.id == personIdFromRec } else null)
                ?: automationState.matchedPerson

            // Carregar o endereço: priorizar a pessoa cadastrada; fallback para o texto parseado da tela
            val st = resolvedPerson?.endereco?.ifBlank { parsed.street } ?: parsed.street
            val num = resolvedPerson?.numero?.ifBlank { parsed.number } ?: parsed.number
            val comp = resolvedPerson?.complemento?.ifBlank { parsed.complement } ?: parsed.complement
            val br = resolvedPerson?.bairro?.ifBlank { parsed.neighborhood } ?: parsed.neighborhood

            // Carregar dados do morador com prioridade para o recebedor escolhido, depois a pessoa cadastrada
            val initialNm = effectiveRec?.nome?.ifBlank { resolvedPerson?.nome.orEmpty() } ?: resolvedPerson?.nome.orEmpty()
            val initialDoc = effectiveRec?.documento?.ifBlank { resolvedPerson?.documento.orEmpty() } ?: resolvedPerson?.documento.orEmpty()
            val sigStr = effectiveRec?.assinatura?.ifBlank { resolvedPerson?.assinatura.orEmpty() } ?: resolvedPerson?.assinatura.orEmpty()

            editingPersonId = resolvedPerson?.id
            editingRecebedorId = effectiveRec?.id ?: "main"

            editedStreet = st
            editedNumber = num
            editedComplement = comp
            editedNeighborhood = br
            recipientName = initialNm
            recipientDocument = initialDoc
            collectedSignatureData = if (sigStr.isNotBlank()) SignatureData.fromJson(sigStr) else null

            initialEditedStreet = st
            initialEditedNumber = num
            initialEditedComplement = comp
            initialEditedNeighborhood = br
            initialRecipientName = initialNm
            initialRecipientDocument = initialDoc
            initialCollectedSigJson = sigStr

            editModalTab = tab
            isMultipleRecipientsModalOpen = false
            isEditModalOpen = true
            updateWindowLayoutMode(OverlayMode.MODAL)
        }

        // Helper to open Edit/Complete modal for current recipient or address
        val openEditForCurrent: (tab: String, focusDoc: Boolean) -> Unit = { tab, focusDoc ->
            val activeRec = person ?: availableRecebedores.firstOrNull()
            openEditForReceiver(activeRec, automationState.matchedPerson, tab, focusDoc)
        }

        // Helper to open Register modal for an additional resident at the current address
        val openRegisterNewResidentForAddress: (targetPerson: Person?) -> Unit = { targetPerson ->
            val st: String
            val num: String
            val comp: String
            val br: String

            if (targetPerson != null) {
                st = targetPerson.endereco
                num = targetPerson.numero
                comp = targetPerson.complemento
                br = targetPerson.bairro
            } else {
                val detected = automationState.detectedAddressText.trim()
                val parsed = AddressNormalizer.parseAddressComponents(detected)
                st = parsed.street
                num = parsed.number
                comp = parsed.complement
                br = parsed.neighborhood
            }
            editingPersonId = targetPerson?.id
            editingRecebedorId = "new_co"
            editedStreet = st
            editedNumber = num
            editedComplement = comp
            editedNeighborhood = br
            recipientName = ""
            recipientDocument = ""
            collectedSignatureData = null

            initialEditedStreet = st
            initialEditedNumber = num
            initialEditedComplement = comp
            initialEditedNeighborhood = br
            initialRecipientName = ""
            initialRecipientDocument = ""
            initialCollectedSigJson = ""

            editModalTab = "RESIDENT"
            isSearchModalOpen = false
            isEditModalOpen = true
            updateWindowLayoutMode(OverlayMode.MODAL)
        }

        val openMoveOrCopyForReceiver: (Recebedor, Person?) -> Unit = { rec, sourcePerson ->
            moveOrCopyTargetReceiver = rec
            moveOrCopySourcePerson = sourcePerson
            moveOrCopyTargetStreet = ""
            moveOrCopyTargetNumber = ""
            moveOrCopyTargetComplement = ""
            moveOrCopyTargetBairro = ""
            moveOrCopySearchQuery = ""
            moveOrCopyStreetError = false
            isMoveAction = true
            showMoveOrCopyDialog = true
            updateWindowLayoutMode(OverlayMode.MODAL)
        }

        val openMoveOrCopyForCurrentEditing: () -> Unit = {
            val sigJson = collectedSignatureData?.toJson() ?: ""
            val targetRec = Recebedor(
                id = editingRecebedorId ?: "main",
                nome = recipientName.trim().ifBlank { "Morador" },
                documento = recipientDocument.trim(),
                assinatura = sigJson
            )
            val sourceP = if (editingPersonId != null && editingPersonId!! > 0) {
                automationState.matchedPerson?.takeIf { it.id == editingPersonId }
                    ?: Person(
                        id = editingPersonId!!,
                        nome = recipientName.trim(),
                        documento = recipientDocument.trim(),
                        endereco = editedStreet,
                        numero = editedNumber,
                        complemento = editedComplement,
                        bairro = editedNeighborhood,
                        assinatura = sigJson
                    )
            } else {
                automationState.matchedPerson
            }
            openMoveOrCopyForReceiver(targetRec, sourceP)
        }

        val openAddComplementDialog: () -> Unit = {
            newComplementInput = ""
            showAddComplementDialog = true
            updateWindowLayoutMode(OverlayMode.MODAL)
        }

        val closeAddComplementDialog: () -> Unit = {
            showAddComplementDialog = false
            if (!isEditModalOpen && !isSearchModalOpen && !isMultipleRecipientsModalOpen && !showMoveOrCopyDialog) {
                updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
            }
        }

        val isAnyModalOpen = isSignatureFullScreen || isSearchModalOpen || isEditModalOpen || isMultipleRecipientsModalOpen || showMoveOrCopyDialog || showAddComplementDialog || showDeleteReceiverConfirmDialog || showChangeComplementDialog

        Box(
            modifier = if (isAnyModalOpen) Modifier.fillMaxSize() else Modifier.padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            // Fundo escurecido e desfocado com suporte a fechar ao tocar fora do modal
            if (isAnyModalOpen && !isSignatureFullScreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.52f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            if (showDeleteReceiverConfirmDialog) {
                                if (!isDeletingReceiver) {
                                    showDeleteReceiverConfirmDialog = false
                                    receiverToDelete = null
                                    if (!isMultipleRecipientsModalOpen && !isEditModalOpen && !isSearchModalOpen && !showMoveOrCopyDialog) {
                                        updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                                    }
                                }
                            } else if (showChangeComplementDialog) {
                                if (!isExecutingChangeComplement) {
                                    showChangeComplementDialog = false
                                    receiverToChangeComplement = null
                                    if (!isMultipleRecipientsModalOpen && !isEditModalOpen && !isSearchModalOpen && !showMoveOrCopyDialog) {
                                        updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                                    }
                                }
                            } else if (showAddComplementDialog) {
                                closeAddComplementDialog()
                            } else if (showMoveOrCopyDialog) {
                                showMoveOrCopyDialog = false
                                if (!isEditModalOpen && !isSearchModalOpen && !isMultipleRecipientsModalOpen) {
                                    updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                                }
                            } else if (isSearchModalOpen) {
                                isSearchModalOpen = false
                                updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                            } else if (isEditModalOpen) {
                                if (!isSavingRecipient) {
                                    handleAttemptCloseEditModal()
                                }
                            } else if (isMultipleRecipientsModalOpen) {
                                isMultipleRecipientsModalOpen = false
                                updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                            }
                        }
                )
            }

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
                        isDarkTheme = false,
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
                var executedQuery by remember { mutableStateOf("") }
                var isSearching by remember { mutableStateOf(false) }
                val searchScope = rememberCoroutineScope()

                val searchResults by remember(executedQuery) {
                    if (executedQuery.isBlank()) {
                        kotlinx.coroutines.flow.flowOf(emptyList<Person>())
                    } else {
                        DeliveryApp.instance.personRepository.searchPersons(executedQuery)
                    }
                }.collectAsState(initial = emptyList())

                val performSearch: () -> Unit = {
                    val trimmed = searchModalQuery.trim()
                    if (trimmed.isNotBlank()) {
                        isSearching = true
                        executedQuery = trimmed
                        searchScope.launch {
                            kotlinx.coroutines.delay(120)
                            isSearching = false
                        }
                    } else {
                        executedQuery = ""
                        isSearching = false
                    }
                }

                Card(
                    modifier = Modifier
                        .width(356.dp)
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

                        // Campo de Busca com disparo na Lupa ou Enter
                        OutlinedTextField(
                            value = searchModalQuery,
                            onValueChange = {
                                searchModalQuery = it
                                if (it.isBlank()) {
                                    executedQuery = ""
                                    isSearching = false
                                }
                            },
                            placeholder = { Text("Nome, documento ou endereço...", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search,
                                keyboardType = KeyboardType.Text
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { performSearch() }
                            ),
                            leadingIcon = {
                                IconButton(
                                    onClick = { performSearch() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Buscar",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (searchModalQuery.isNotBlank()) Color(0xFF1976D2) else Color.Gray
                                    )
                                }
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchModalQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                searchModalQuery = ""
                                                executedQuery = ""
                                                isSearching = false
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            if (!isListeningSearch) {
                                                isListeningSearch = true
                                                com.example.util.SpeechHelper.startListening(
                                                    context = serviceContext,
                                                    onReady = { Toast.makeText(serviceContext, "Fale o nome, documento ou rua...", Toast.LENGTH_SHORT).show() },
                                                    onResult = { result ->
                                                        isListeningSearch = false
                                                        val clean = com.example.util.SpeechHelper.processSpokenSearch(result)
                                                        if (clean.isNotBlank()) {
                                                            searchModalQuery = clean
                                                            performSearch()
                                                        }
                                                    },
                                                    onError = { err ->
                                                        isListeningSearch = false
                                                        Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Falar Busca",
                                            tint = if (isListeningSearch) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { performSearch() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (searchModalQuery.isNotBlank()) Color(0xFF1976D2) else Color(0xFFECEFF1),
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = "Pesquisar",
                                                    tint = if (searchModalQuery.isNotBlank()) Color.White else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Barra de Ação Rápida de Cadastro na Busca
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSearching) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF1976D2)
                                    )
                                    Text(
                                        text = "Buscando...",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1976D2)
                                    )
                                }
                            } else if (executedQuery.isBlank()) {
                                Text(
                                    text = if (searchModalQuery.isNotBlank()) "Clique na lupa para pesquisar" else "Aguardando pesquisa",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            } else if (searchResults.isEmpty()) {
                                Text(
                                    text = "Destinatário não cadastrado",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD32F2F)
                                )
                            } else {
                                Text(
                                    text = "${searchResults.size} encontrado(s)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1565C0)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE8F5E9),
                                border = BorderStroke(1.dp, Color(0xFF81C784)),
                                modifier = Modifier.clickable {
                                    val initialAddr = searchModalQuery.trim()
                                    val parsed = AddressNormalizer.parseAddressComponents(initialAddr)
                                    editingPersonId = null
                                    editingRecebedorId = null
                                    editedStreet = parsed.street
                                    editedNumber = parsed.number
                                    editedComplement = parsed.complement
                                    editedNeighborhood = parsed.neighborhood
                                    recipientName = ""
                                    recipientDocument = ""
                                    collectedSignatureData = null

                                    initialEditedStreet = parsed.street
                                    initialEditedNumber = parsed.number
                                    initialEditedComplement = parsed.complement
                                    initialEditedNeighborhood = parsed.neighborhood
                                    initialRecipientName = ""
                                    initialRecipientDocument = ""
                                    initialCollectedSigJson = ""

                                    editModalTab = if (parsed.street.isNotBlank()) "RESIDENT" else "ADDRESS"
                                    isSearchModalOpen = false
                                    isEditModalOpen = true
                                    updateWindowLayoutMode(OverlayMode.MODAL)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (searchModalQuery.isNotBlank()) "Cadastrar neste Endereço" else "Novo Destinatário",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }

                        // Agrupamento dos resultados de busca por endereço e complemento
                        val groupedSearchResults = remember(searchResults) {
                            val groups = linkedMapOf<String, MutableList<Person>>()
                            for (p in searchResults) {
                                val parsed = AddressNormalizer.parseAddressComponents(p.endereco, p.numero, p.complemento, p.bairro)
                                val streetKey = AddressNormalizer.normalize(parsed.street.ifBlank { p.endereco })
                                val numKey = parsed.number.ifBlank { p.numero }.trim()
                                val key = if (streetKey.isNotBlank()) "$streetKey|$numKey" else "p_${p.id}"
                                groups.getOrPut(key) { mutableListOf() }.add(p)
                            }
                            groups.values.map { persons ->
                                val first = persons.first()
                                val parsed = AddressNormalizer.parseAddressComponents(first.endereco, first.numero, first.complemento, first.bairro)
                                val street = parsed.street.ifBlank { first.endereco }
                                val number = parsed.number.ifBlank { first.numero }
                                val neighborhood = parsed.neighborhood.ifBlank { first.bairro }
                                val comps = persons.map { it.complemento.trim() }.filter { it.isNotBlank() }.distinct()
                                val byComp = linkedMapOf<String, MutableList<Person>>()
                                val withoutComp = mutableListOf<Person>()
                                for (p in persons) {
                                    val c = p.complemento.trim()
                                    if (c.isNotBlank()) {
                                        byComp.getOrPut(c) { mutableListOf() }.add(p)
                                    } else {
                                        withoutComp.add(p)
                                    }
                                }
                                val streetKey = AddressNormalizer.normalize(street)
                                val numKey = number.trim()
                                val addrKey = if (streetKey.isNotBlank()) "$streetKey|$numKey" else "p_${first.id}"
                                SearchAddressGroup(
                                    addressKey = addrKey,
                                    street = street,
                                    number = number,
                                    neighborhood = neighborhood,
                                    complements = comps,
                                    personsByComplement = byComp,
                                    personsWithoutComplement = withoutComp,
                                    allPersons = persons
                                )
                            }
                        }

                        // Lista de Resultados
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSearching) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.5.dp,
                                            color = Color(0xFF1976D2)
                                        )
                                        Text(
                                            text = "Buscando...",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF1976D2)
                                        )
                                    }
                                }
                            } else if (executedQuery.isBlank()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = { performSearch() },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Buscar",
                                                tint = if (searchModalQuery.isNotBlank()) Color(0xFF1976D2) else Color(0xFF90A4AE),
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Text(
                                            text = if (searchModalQuery.isNotBlank()) "Clique na lupa para pesquisar" else "Digite e clique na lupa",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF455A64),
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Pesquise por nome do morador, documento, rua ou número.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF78909C),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else if (searchResults.isEmpty()) {
                                item {
                                    // AVISO DESTACADO DE DESTINATÁRIO NÃO CADASTRADO
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                            border = BorderStroke(1.dp, Color(0xFFFFB74D))
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = Color(0xFFE65100),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Destinatário não cadastrado",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFD84315)
                                                    )
                                                }
                                                Text(
                                                    text = "Não encontramos nenhum destinatário ou endereço para \"$executedQuery\".",
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFF5D4037),
                                                    textAlign = TextAlign.Center
                                                )
                                                Button(
                                                    onClick = {
                                                        val initialAddr = (if (executedQuery.isNotBlank()) executedQuery else searchModalQuery).trim()
                                                        val parsed = AddressNormalizer.parseAddressComponents(initialAddr)
                                                        editingPersonId = null
                                                        editingRecebedorId = null
                                                        editedStreet = parsed.street
                                                        editedNumber = parsed.number
                                                        editedComplement = parsed.complement
                                                        editedNeighborhood = parsed.neighborhood
                                                        recipientName = ""
                                                        recipientDocument = ""
                                                        collectedSignatureData = null

                                                        initialEditedStreet = parsed.street
                                                        initialEditedNumber = parsed.number
                                                        initialEditedComplement = parsed.complement
                                                        initialEditedNeighborhood = parsed.neighborhood
                                                        initialRecipientName = ""
                                                        initialRecipientDocument = ""
                                                        initialCollectedSigJson = ""

                                                        editModalTab = if (parsed.street.isNotBlank()) "RESIDENT" else "ADDRESS"
                                                        isSearchModalOpen = false
                                                        isEditModalOpen = true
                                                        updateWindowLayoutMode(OverlayMode.MODAL)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Cadastrar Destinatário neste Endereço", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                            } else {
                                items(groupedSearchResults, key = { it.addressKey }) { group ->
                                    val hasComplements = group.complements.isNotEmpty()
                                    if (hasComplements) {
                                        // ENDEREÇO COM COMPLEMENTO SALVO:
                                        // Mostra primeiro o complemento para selecionar, depois os moradores relacionados a esse complemento!
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFBFE)),
                                            border = BorderStroke(1.dp, Color(0xFFD1C4E9)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Cabeçalho do Endereço
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = group.street.ifBlank { "Sem logradouro" },
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF1A237E),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Row(
                                                            modifier = Modifier.padding(top = 2.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            if (group.number.isNotBlank()) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .background(Color(0xFFE3F2FD))
                                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Nº ${group.number}",
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color(0xFF1565C0)
                                                                    )
                                                                }
                                                            }
                                                            if (group.neighborhood.isNotBlank()) {
                                                                Text(
                                                                    text = "• ${group.neighborhood}",
                                                                    fontSize = 10.5.sp,
                                                                    color = Color(0xFF555555),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFFEDE7F6))
                                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(
                                                            text = "🏢 ${group.complements.size} compl.",
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF4A148C)
                                                        )
                                                    }
                                                }

                                                // 1. PRIMEIRO: SELEÇÃO DE COMPLEMENTO
                                                val activeComp = searchModalSelectedComplements[group.addressKey] ?: group.complements.firstOrNull().orEmpty()
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Home,
                                                            contentDescription = null,
                                                            tint = Color(0xFF6A1B9A),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = "Selecione o complemento:",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF4A148C)
                                                        )
                                                        Spacer(modifier = Modifier.weight(1f))
                                                        Text(
                                                            text = "+ Novo Complemento",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF1976D2),
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .clickable {
                                                                    openRegisterNewResidentForAddress(group.allPersons.firstOrNull())
                                                                }
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    LazyRow(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        items(group.complements) { comp ->
                                                            val isSelected = comp.equals(activeComp, ignoreCase = true)
                                                            val count = group.personsByComplement[comp]?.size ?: 0
                                                            Surface(
                                                                shape = RoundedCornerShape(12.dp),
                                                                color = if (isSelected) Color(0xFF6A1B9A) else Color(0xFFF3E5F5),
                                                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF4A148C) else Color(0xFFCE93D8)),
                                                                modifier = Modifier.clickable {
                                                                    searchModalSelectedComplements = searchModalSelectedComplements + (group.addressKey to comp)
                                                                }
                                                            ) {
                                                                Text(
                                                                    text = "🏠 $comp ($count)",
                                                                    fontSize = 10.5.sp,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                    color = if (isSelected) Color.White else Color(0xFF4A148C),
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                                )
                                                            }
                                                        }
                                                        if (group.personsWithoutComplement.isNotEmpty()) {
                                                            item {
                                                                val isSelected = activeComp == "__NONE__"
                                                                Surface(
                                                                    shape = RoundedCornerShape(12.dp),
                                                                    color = if (isSelected) Color(0xFF6A1B9A) else Color(0xFFF5F5F5),
                                                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF4A148C) else Color(0xFFBDBDBD)),
                                                                    modifier = Modifier.clickable {
                                                                        searchModalSelectedComplements = searchModalSelectedComplements + (group.addressKey to "__NONE__")
                                                                    }
                                                                ) {
                                                                    Text(
                                                                        text = "Principal (${group.personsWithoutComplement.size})",
                                                                        fontSize = 10.5.sp,
                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                        color = if (isSelected) Color.White else Color(0xFF424242),
                                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                HorizontalDivider(color = Color(0xFFE0E0E0))

                                                // 2. DEPOIS: MORADORES RELACIONADOS A ESSE COMPLEMENTO
                                                val filteredPersons = if (activeComp == "__NONE__") {
                                                    group.personsWithoutComplement
                                                } else {
                                                    group.personsByComplement[activeComp] ?: emptyList()
                                                }

                                                if (filteredPersons.isEmpty()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Nenhum morador neste complemento.",
                                                            fontSize = 11.sp,
                                                            color = Color.Gray
                                                        )
                                                        Button(
                                                            onClick = {
                                                                val base = group.allPersons.firstOrNull()
                                                                editingPersonId = base?.id
                                                                editingRecebedorId = "new_co"
                                                                editedStreet = group.street
                                                                editedNumber = group.number
                                                                editedComplement = activeComp
                                                                editedNeighborhood = group.neighborhood
                                                                recipientName = ""
                                                                recipientDocument = ""
                                                                collectedSignatureData = null
                                                                initialEditedStreet = group.street
                                                                initialEditedNumber = group.number
                                                                initialEditedComplement = activeComp
                                                                initialEditedNeighborhood = group.neighborhood
                                                                initialRecipientName = ""
                                                                initialRecipientDocument = ""
                                                                initialCollectedSigJson = ""
                                                                editModalTab = "RESIDENT"
                                                                isSearchModalOpen = false
                                                                isEditModalOpen = true
                                                                updateWindowLayoutMode(OverlayMode.MODAL)
                                                            },
                                                            modifier = Modifier.height(28.dp),
                                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                                            shape = RoundedCornerShape(6.dp),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                                        ) {
                                                            Text("+ Cadastrar morador", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                } else {
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        for (itemPerson in filteredPersons) {
                                                            val hasDoc = itemPerson.documento.isNotBlank()
                                                            val hasSig = itemPerson.assinatura.isNotBlank()
                                                            val isIncomplete = !hasDoc || !hasSig

                                                            val allRecs = remember(itemPerson) {
                                                                val list = mutableListOf<Recebedor>()
                                                                if (itemPerson.nome.isNotBlank()) {
                                                                    list.add(
                                                                        Recebedor(
                                                                            id = "p_${itemPerson.id}_main",
                                                                            nome = itemPerson.nome,
                                                                            documento = itemPerson.documento,
                                                                            assinatura = itemPerson.assinatura
                                                                        )
                                                                    )
                                                                }
                                                                val extras = Recebedor.listFromJson(itemPerson.coRecebedoresJson)
                                                                for (r in extras) {
                                                                    if (r.nome.isNotBlank()) {
                                                                        list.add(
                                                                            Recebedor(
                                                                                id = "p_${itemPerson.id}_co_${r.id}",
                                                                                nome = r.nome,
                                                                                documento = r.documento,
                                                                                assinatura = r.assinatura
                                                                            )
                                                                        )
                                                                    }
                                                                }
                                                                list
                                                            }

                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = if (isIncomplete) Color(0xFFFFFDE7) else Color.White,
                                                                border = if (isIncomplete) BorderStroke(1.dp, Color(0xFFFFD54F)) else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(8.dp),
                                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.Top
                                                                    ) {
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(
                                                                                text = "Destinatário: ${itemPerson.nome}",
                                                                                fontSize = 12.sp,
                                                                                fontWeight = FontWeight.Bold,
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

                                                                        if (allRecs.size > 1) {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .clip(RoundedCornerShape(6.dp))
                                                                                    .background(Color(0xFFEDE7F6))
                                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                            ) {
                                                                                Text(
                                                                                    text = "👥 ${allRecs.size} moradores",
                                                                                    fontSize = 9.sp,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    color = Color(0xFF4A148C)
                                                                                )
                                                                            }
                                                                        } else {
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
                                                                    }

                                                                    // Chips de múltiplos co-recebedores
                                                                    if (allRecs.size > 1) {
                                                                        Row(
                                                                            modifier = Modifier
                                                                                .fillMaxWidth()
                                                                                .horizontalScroll(rememberScrollState()),
                                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                        ) {
                                                                            allRecs.forEach { r ->
                                                                                Surface(
                                                                                    shape = RoundedCornerShape(12.dp),
                                                                                    color = Color(0xFFE8F5E9),
                                                                                    border = BorderStroke(1.dp, Color(0xFF81C784)),
                                                                                    modifier = Modifier.clickable {
                                                                                        AccessibilityAutomationEngine.setMatchedPersonDirect(itemPerson)
                                                                                        AccessibilityAutomationEngine.selectRecebedor(r)
                                                                                        isSearchModalOpen = false
                                                                                        updateWindowLayoutMode(OverlayMode.PANEL)
                                                                                        Toast.makeText(
                                                                                            serviceContext,
                                                                                            "Selecionado: ${r.nome}",
                                                                                            Toast.LENGTH_SHORT
                                                                                        ).show()
                                                                                    }
                                                                                ) {
                                                                                    Row(
                                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                                                        verticalAlignment = Alignment.CenterVertically
                                                                                    ) {
                                                                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(11.dp))
                                                                                        Spacer(modifier = Modifier.width(2.dp))
                                                                                        Text(r.nome, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20), maxLines = 1)
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }

                                                                    // Botões de Ação
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                    ) {
                                                                        Button(
                                                                            onClick = {
                                                                                AccessibilityAutomationEngine.setMatchedPersonDirect(itemPerson)
                                                                                if (allRecs.isNotEmpty()) {
                                                                                    val selRec = allRecs.first()
                                                                                    AccessibilityAutomationEngine.selectRecebedor(selRec)
                                                                                    AccessibilityAutomationEngine.prioritizeRecebedor(selRec)
                                                                                }
                                                                                isSearchModalOpen = false
                                                                                updateWindowLayoutMode(OverlayMode.PANEL)
                                                                                Toast.makeText(
                                                                                    serviceContext,
                                                                                    "Selecionado: ${itemPerson.nome}",
                                                                                    Toast.LENGTH_SHORT
                                                                                ).show()
                                                                            },
                                                                            modifier = Modifier.weight(1.05f).height(32.dp),
                                                                            contentPadding = PaddingValues(horizontal = 2.dp),
                                                                            shape = RoundedCornerShape(6.dp),
                                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                                                        ) {
                                                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                            Spacer(modifier = Modifier.width(2.dp))
                                                                            Text("Selecionar", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                                        }

                                                                        Button(
                                                                            onClick = {
                                                                                AccessibilityAutomationEngine.setMatchedPersonDirect(itemPerson)
                                                                                if (allRecs.isNotEmpty()) {
                                                                                    val selRec = allRecs.first()
                                                                                    AccessibilityAutomationEngine.selectRecebedor(selRec)
                                                                                    AccessibilityAutomationEngine.prioritizeRecebedor(selRec)
                                                                                }
                                                                                val res = AccessibilityAutomationEngine.fillFields(itemPerson.nome, itemPerson.documento)
                                                                                Toast.makeText(serviceContext, res.message, Toast.LENGTH_SHORT).show()
                                                                                if (res.nameFilled || res.documentFilled) {
                                                                                    triggerSuccessFeedback()
                                                                                }
                                                                                isSearchModalOpen = false
                                                                                updateWindowLayoutMode(OverlayMode.PANEL)
                                                                            },
                                                                            modifier = Modifier.weight(1.05f).height(32.dp),
                                                                            contentPadding = PaddingValues(horizontal = 2.dp),
                                                                            shape = RoundedCornerShape(6.dp),
                                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                                                        ) {
                                                                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                            Spacer(modifier = Modifier.width(2.dp))
                                                                            Text("Preencher", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                                        }

                                                                        OutlinedButton(
                                                                            onClick = {
                                                                                val initialNm = itemPerson.nome
                                                                                val initialDoc = itemPerson.documento
                                                                                val sigStr = itemPerson.assinatura

                                                                                editingPersonId = itemPerson.id
                                                                                editingRecebedorId = "main"
                                                                                editedStreet = itemPerson.endereco
                                                                                editedNumber = itemPerson.numero
                                                                                editedComplement = itemPerson.complemento
                                                                                editedNeighborhood = itemPerson.bairro
                                                                                recipientName = initialNm
                                                                                recipientDocument = initialDoc
                                                                                collectedSignatureData = if (sigStr.isNotBlank()) SignatureData.fromJson(sigStr) else null

                                                                                initialEditedStreet = itemPerson.endereco
                                                                                initialEditedNumber = itemPerson.numero
                                                                                initialEditedComplement = itemPerson.complemento
                                                                                initialEditedNeighborhood = itemPerson.bairro
                                                                                initialRecipientName = initialNm
                                                                                initialRecipientDocument = initialDoc
                                                                                initialCollectedSigJson = sigStr

                                                                                editModalTab = "RESIDENT"
                                                                                isSearchModalOpen = false
                                                                                isEditModalOpen = true
                                                                                updateWindowLayoutMode(OverlayMode.MODAL)
                                                                            },
                                                                            modifier = Modifier.weight(1.05f).height(32.dp),
                                                                            contentPadding = PaddingValues(horizontal = 2.dp),
                                                                            shape = RoundedCornerShape(6.dp),
                                                                            colors = ButtonDefaults.outlinedButtonColors(
                                                                                contentColor = if (isIncomplete) Color(0xFFE65100) else Color(0xFF0D47A1)
                                                                            )
                                                                        ) {
                                                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                            Spacer(modifier = Modifier.width(2.dp))
                                                                            Text(if (isIncomplete) "Completar" else "Editar", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                                        }

                                                                        OutlinedButton(
                                                                            onClick = {
                                                                                openRegisterNewResidentForAddress(itemPerson)
                                                                            },
                                                                            modifier = Modifier.weight(0.95f).height(32.dp),
                                                                            contentPadding = PaddingValues(horizontal = 2.dp),
                                                                            shape = RoundedCornerShape(6.dp),
                                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6A1B9A))
                                                                        ) {
                                                                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                            Spacer(modifier = Modifier.width(2.dp))
                                                                            Text("Morador", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // ENDEREÇO SEM COMPLEMENTO SALVO:
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF0F4F8),
                                            border = BorderStroke(1.dp, Color(0xFFCFD8DC)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // Endereço e botão para adicionar complemento
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = group.street.ifBlank { "Sem logradouro" },
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF1A237E),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Row(
                                                            modifier = Modifier.padding(top = 2.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            if (group.number.isNotBlank()) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .background(Color(0xFFE3F2FD))
                                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Nº ${group.number}",
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color(0xFF1565C0)
                                                                    )
                                                                }
                                                            }
                                                            if (group.neighborhood.isNotBlank()) {
                                                                Text(
                                                                    text = "• ${group.neighborhood}",
                                                                    fontSize = 10.5.sp,
                                                                    color = Color(0xFF555555),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFFEDE7F6),
                                                        modifier = Modifier.clickable {
                                                            openRegisterNewResidentForAddress(group.allPersons.firstOrNull())
                                                        }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(11.dp))
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text(
                                                                text = "Adicionar Complemento",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF6A1B9A)
                                                            )
                                                        }
                                                    }
                                                }

                                                for (itemPerson in group.allPersons) {
                                                    val hasDoc = itemPerson.documento.isNotBlank()
                                                    val hasSig = itemPerson.assinatura.isNotBlank()
                                                    val isIncomplete = !hasDoc || !hasSig

                                                    val allRecs = remember(itemPerson) {
                                                        val list = mutableListOf<Recebedor>()
                                                        if (itemPerson.nome.isNotBlank()) {
                                                            list.add(
                                                                Recebedor(
                                                                    id = "p_${itemPerson.id}_main",
                                                                    nome = itemPerson.nome,
                                                                    documento = itemPerson.documento,
                                                                    assinatura = itemPerson.assinatura
                                                                )
                                                            )
                                                        }
                                                        val extras = Recebedor.listFromJson(itemPerson.coRecebedoresJson)
                                                        for (r in extras) {
                                                            if (r.nome.isNotBlank()) {
                                                                list.add(
                                                                    Recebedor(
                                                                        id = "p_${itemPerson.id}_co_${r.id}",
                                                                        nome = r.nome,
                                                                        documento = r.documento,
                                                                        assinatura = r.assinatura
                                                                    )
                                                                )
                                                            }
                                                        }
                                                        list
                                                    }

                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isIncomplete) Color(0xFFFFFDE7) else Color.White,
                                                        border = if (isIncomplete) BorderStroke(1.dp, Color(0xFFFFD54F)) else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(8.dp),
                                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.Top
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        text = "Destinatário: ${itemPerson.nome}",
                                                                        fontSize = 12.sp,
                                                                        fontWeight = FontWeight.Bold,
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

                                                                if (allRecs.size > 1) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .clip(RoundedCornerShape(6.dp))
                                                                            .background(Color(0xFFEDE7F6))
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = "👥 ${allRecs.size} moradores",
                                                                            fontSize = 9.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = Color(0xFF4A148C)
                                                                        )
                                                                    }
                                                                } else {
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
                                                            }

                                                            // Chips de múltiplos co-recebedores
                                                            if (allRecs.size > 1) {
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .horizontalScroll(rememberScrollState()),
                                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                ) {
                                                                    allRecs.forEach { r ->
                                                                        Surface(
                                                                            shape = RoundedCornerShape(12.dp),
                                                                            color = Color(0xFFE8F5E9),
                                                                            border = BorderStroke(1.dp, Color(0xFF81C784)),
                                                                            modifier = Modifier.clickable {
                                                                                AccessibilityAutomationEngine.setMatchedPersonDirect(itemPerson)
                                                                                AccessibilityAutomationEngine.selectRecebedor(r)
                                                                                isSearchModalOpen = false
                                                                                updateWindowLayoutMode(OverlayMode.PANEL)
                                                                                Toast.makeText(
                                                                                    serviceContext,
                                                                                    "Selecionado: ${r.nome}",
                                                                                    Toast.LENGTH_SHORT
                                                                                ).show()
                                                                            }
                                                                        ) {
                                                                            Row(
                                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                                                verticalAlignment = Alignment.CenterVertically
                                                                            ) {
                                                                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(11.dp))
                                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                                Text(r.nome, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20), maxLines = 1)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            // Botões de Ação
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Button(
                                                                    onClick = {
                                                                        AccessibilityAutomationEngine.setMatchedPersonDirect(itemPerson)
                                                                        if (allRecs.isNotEmpty()) {
                                                                            val selRec = allRecs.first()
                                                                            AccessibilityAutomationEngine.selectRecebedor(selRec)
                                                                            AccessibilityAutomationEngine.prioritizeRecebedor(selRec)
                                                                        }
                                                                        isSearchModalOpen = false
                                                                        updateWindowLayoutMode(OverlayMode.PANEL)
                                                                        Toast.makeText(
                                                                            serviceContext,
                                                                            "Selecionado: ${itemPerson.nome}",
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    },
                                                                    modifier = Modifier.weight(1.05f).height(32.dp),
                                                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                                                ) {
                                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(2.dp))
                                                                    Text("Selecionar", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                                }

                                                                Button(
                                                                    onClick = {
                                                                        AccessibilityAutomationEngine.setMatchedPersonDirect(itemPerson)
                                                                        if (allRecs.isNotEmpty()) {
                                                                            val selRec = allRecs.first()
                                                                            AccessibilityAutomationEngine.selectRecebedor(selRec)
                                                                            AccessibilityAutomationEngine.prioritizeRecebedor(selRec)
                                                                        }
                                                                        val res = AccessibilityAutomationEngine.fillFields(itemPerson.nome, itemPerson.documento)
                                                                        Toast.makeText(serviceContext, res.message, Toast.LENGTH_SHORT).show()
                                                                        if (res.nameFilled || res.documentFilled) {
                                                                            triggerSuccessFeedback()
                                                                        }
                                                                        isSearchModalOpen = false
                                                                        updateWindowLayoutMode(OverlayMode.PANEL)
                                                                    },
                                                                    modifier = Modifier.weight(1.05f).height(32.dp),
                                                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                                                ) {
                                                                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(2.dp))
                                                                    Text("Preencher", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                                }

                                                                OutlinedButton(
                                                                    onClick = {
                                                                        val initialNm = itemPerson.nome
                                                                        val initialDoc = itemPerson.documento
                                                                        val sigStr = itemPerson.assinatura

                                                                        editingPersonId = itemPerson.id
                                                                        editingRecebedorId = "main"
                                                                        editedStreet = itemPerson.endereco
                                                                        editedNumber = itemPerson.numero
                                                                        editedComplement = itemPerson.complemento
                                                                        editedNeighborhood = itemPerson.bairro
                                                                        recipientName = initialNm
                                                                        recipientDocument = initialDoc
                                                                        collectedSignatureData = if (sigStr.isNotBlank()) SignatureData.fromJson(sigStr) else null

                                                                        initialEditedStreet = itemPerson.endereco
                                                                        initialEditedNumber = itemPerson.numero
                                                                        initialEditedComplement = itemPerson.complemento
                                                                        initialEditedNeighborhood = itemPerson.bairro
                                                                        initialRecipientName = initialNm
                                                                        initialRecipientDocument = initialDoc
                                                                        initialCollectedSigJson = sigStr

                                                                        editModalTab = "RESIDENT"
                                                                        isSearchModalOpen = false
                                                                        isEditModalOpen = true
                                                                        updateWindowLayoutMode(OverlayMode.MODAL)
                                                                    },
                                                                    modifier = Modifier.weight(1.05f).height(32.dp),
                                                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                                        contentColor = if (isIncomplete) Color(0xFFE65100) else Color(0xFF0D47A1)
                                                                    )
                                                                ) {
                                                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(2.dp))
                                                                    Text(if (isIncomplete) "Completar" else "Editar", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                                }

                                                                OutlinedButton(
                                                                    onClick = {
                                                                        openRegisterNewResidentForAddress(itemPerson)
                                                                    },
                                                                    modifier = Modifier.weight(0.95f).height(32.dp),
                                                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6A1B9A))
                                                                ) {
                                                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(2.dp))
                                                                    Text("Morador", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                                }
                                                            }
                                                        }
                                                    }
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
                            .blur(if (showDiscardEditConfirmDialog || showBubbleClearSigConfirmDialog || showMoveOrCopyDialog || showDeleteReceiverConfirmDialog || showChangeComplementDialog) 12.dp else 0.dp)
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
                                    imageVector = if (editModalTab == "ADDRESS") Icons.Default.LocationOn else if (isEditingExisting) Icons.Default.Edit else Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = if (editModalTab == "ADDRESS") Color(0xFFE65100) else if (isEditingExisting) Color(0xFF0288D1) else if (isAddingNewResident) Color(0xFF6A1B9A) else Color(0xFF0D47A1),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (editModalTab == "ADDRESS") {
                                        "Editar Endereço Completo"
                                    } else {
                                        if (isEditingExisting) "Editar Dados do Morador" else if (isAddingNewResident) "Adicionar Morador no Endereço" else "Cadastrar Destinatário"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = if (editModalTab == "ADDRESS") Color(0xFFE65100) else if (isEditingExisting) Color(0xFF0288D1) else if (isAddingNewResident) Color(0xFF6A1B9A) else Color(0xFF0D47A1)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (!isSavingRecipient) {
                                        handleAttemptCloseEditModal()
                                    }
                                },
                                enabled = !isSavingRecipient,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = if (isSavingRecipient) Color.LightGray else Color.Gray)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE0E0E0))

                        // Notificação de restauração de rascunho não salvo
                        if (draftAvailable != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                                border = BorderStroke(1.dp, Color(0xFFFFB300))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Rascunho anterior encontrado",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF795548)
                                        )
                                        Text(
                                            text = draftAvailable!!.name.ifBlank { "Destinatário com documento/assinatura" },
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF5D4037),
                                            maxLines = 1
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = {
                                                val draft = draftAvailable!!
                                                if (draft.street.isNotBlank()) editedStreet = draft.street
                                                if (draft.number.isNotBlank()) editedNumber = draft.number
                                                if (draft.complement.isNotBlank()) editedComplement = draft.complement
                                                if (draft.neighborhood.isNotBlank()) editedNeighborhood = draft.neighborhood
                                                if (draft.name.isNotBlank()) recipientName = draft.name
                                                if (draft.document.isNotBlank()) recipientDocument = draft.document
                                                if (draft.signatureJson.isNotBlank()) {
                                                    collectedSignatureData = SignatureData.fromJson(draft.signatureJson)
                                                }
                                                draftAvailable = null
                                                Toast.makeText(this@FloatingBubbleService, "Rascunho recuperado!", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("RESTAURAR", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                        }
                                        IconButton(
                                            onClick = {
                                                RecipientDraftManager.clearDraft(this@FloatingBubbleService)
                                                draftAvailable = null
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Dispensar", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Seletor de Abas Separadas: [📍 Endereço] e [👤 Morador]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Aba 1: Endereço
                            val isAddressTab = editModalTab == "ADDRESS"
                            Surface(
                                onClick = { editModalTab = "ADDRESS" },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isAddressTab) Color.White else Color.Transparent,
                                shadowElevation = if (isAddressTab) 2.dp else 0.dp,
                                border = if (isAddressTab) BorderStroke(1.dp, Color(0xFFCBD5E1)) else null
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = if (isAddressTab) Color(0xFFE65100) else Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Endereço",
                                        fontSize = 12.sp,
                                        fontWeight = if (isAddressTab) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isAddressTab) Color(0xFF0F172A) else Color(0xFF64748B)
                                    )
                                }
                            }

                            // Aba 2: Morador
                            val isResidentTab = editModalTab == "RESIDENT"
                            Surface(
                                onClick = { editModalTab = "RESIDENT" },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isResidentTab) Color.White else Color.Transparent,
                                shadowElevation = if (isResidentTab) 2.dp else 0.dp,
                                border = if (isResidentTab) BorderStroke(1.dp, Color(0xFFCBD5E1)) else null
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isResidentTab) Color(0xFF0288D1) else Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Morador",
                                        fontSize = 12.sp,
                                        fontWeight = if (isResidentTab) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isResidentTab) Color(0xFF0F172A) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        // CONTEÚDO DA ABA SELECIONADA
                        if (editModalTab == "ADDRESS") {
                            // --- ABA 1: EDITAR ENDEREÇO COMPLETO ---
                            Text(
                                text = "Logradouro / Rua / Avenida *:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF424242)
                            )
                            OutlinedTextField(
                                value = editedStreet,
                                onValueChange = { input ->
                                    if (input.contains(",") || input.contains(" - ") || input.contains("\n")) {
                                        val parsed = AddressNormalizer.parseAddressComponents(input)
                                        if (parsed.street.isNotBlank() && (parsed.number.isNotBlank() || parsed.complement.isNotBlank() || parsed.neighborhood.isNotBlank())) {
                                            editedStreet = parsed.street
                                            if (parsed.number.isNotBlank()) editedNumber = parsed.number
                                            if (parsed.complement.isNotBlank()) editedComplement = parsed.complement
                                            if (parsed.neighborhood.isNotBlank()) editedNeighborhood = parsed.neighborhood
                                            return@OutlinedTextField
                                        }
                                    }
                                    editedStreet = input
                                },
                                placeholder = { Text("Ex: Rua das Flores", fontSize = 12.sp) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (!isListeningStreet) {
                                                isListeningStreet = true
                                                com.example.util.SpeechHelper.startListening(
                                                    context = serviceContext,
                                                    onReady = { Toast.makeText(serviceContext, "Fale a rua...", Toast.LENGTH_SHORT).show() },
                                                    onResult = { result ->
                                                        isListeningStreet = false
                                                        val parsed = AddressNormalizer.parseAddressComponents(result)
                                                        if (parsed.street.isNotBlank() && (parsed.number.isNotBlank() || parsed.complement.isNotBlank() || parsed.neighborhood.isNotBlank())) {
                                                            editedStreet = parsed.street
                                                            if (parsed.number.isNotBlank()) editedNumber = parsed.number
                                                            if (parsed.complement.isNotBlank()) editedComplement = parsed.complement
                                                            if (parsed.neighborhood.isNotBlank()) editedNeighborhood = parsed.neighborhood
                                                        } else {
                                                            val st = com.example.util.SpeechHelper.processSpokenStreet(result)
                                                            if (st.isNotBlank()) editedStreet = st
                                                        }
                                                    },
                                                    onError = { err ->
                                                        isListeningStreet = false
                                                        Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Falar Rua",
                                            tint = if (isListeningStreet) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Número:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF424242)
                                    )
                                    OutlinedTextField(
                                        value = editedNumber,
                                        onValueChange = { editedNumber = it },
                                        placeholder = { Text("Ex: 123", fontSize = 12.sp) },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    if (!isListeningNumber) {
                                                        isListeningNumber = true
                                                        com.example.util.SpeechHelper.startListening(
                                                            context = serviceContext,
                                                            onReady = { Toast.makeText(serviceContext, "Fale o número...", Toast.LENGTH_SHORT).show() },
                                                            onResult = { result ->
                                                                isListeningNumber = false
                                                                val num = com.example.util.SpeechHelper.processSpokenNumber(result)
                                                                if (num.isNotBlank()) editedNumber = num
                                                            },
                                                            onError = { err ->
                                                                isListeningNumber = false
                                                                Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = "Falar Número",
                                                    tint = if (isListeningNumber) MaterialTheme.colorScheme.primary else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                }
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text(
                                        text = "Complemento / Unidade:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF424242)
                                    )
                                    OutlinedTextField(
                                        value = editedComplement,
                                        onValueChange = { editedComplement = it },
                                        placeholder = { Text("Ex: Apto 101 Bloco B", fontSize = 12.sp) },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    if (!isListeningComplement) {
                                                        isListeningComplement = true
                                                        com.example.util.SpeechHelper.startListening(
                                                            context = serviceContext,
                                                            onReady = { Toast.makeText(serviceContext, "Fale o complemento...", Toast.LENGTH_SHORT).show() },
                                                            onResult = { result ->
                                                                isListeningComplement = false
                                                                val comp = com.example.util.SpeechHelper.processSpokenComplement(result)
                                                                if (comp.isNotBlank()) editedComplement = comp
                                                            },
                                                            onError = { err ->
                                                                isListeningComplement = false
                                                                Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = "Falar Complemento",
                                                    tint = if (isListeningComplement) MaterialTheme.colorScheme.primary else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }

                            // Atalhos rápidos de complemento
                            Text(
                                text = "Atalhos rápidos de complemento:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF616161)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Apto", "Bloco", "Casa", "Torre", "Sala", "Fundos", "Sobrado", "Lote", "Quadra").forEach { chip ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF5F5F5),
                                        border = BorderStroke(0.5.dp, Color(0xFFE0E0E0)),
                                        modifier = Modifier.clickable {
                                            editedComplement = if (editedComplement.isBlank()) chip else "$editedComplement $chip"
                                        }
                                    ) {
                                        Text(
                                            text = "+ $chip",
                                            fontSize = 9.5.sp,
                                            color = Color(0xFF424242),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Bairro (Opcional):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF424242)
                            )
                            OutlinedTextField(
                                value = editedNeighborhood,
                                onValueChange = { editedNeighborhood = it },
                                placeholder = { Text("Ex: Centro", fontSize = 12.sp) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (!isListeningNeighborhood) {
                                                isListeningNeighborhood = true
                                                com.example.util.SpeechHelper.startListening(
                                                    context = serviceContext,
                                                    onReady = { Toast.makeText(serviceContext, "Fale o bairro...", Toast.LENGTH_SHORT).show() },
                                                    onResult = { result ->
                                                        isListeningNeighborhood = false
                                                        val br = com.example.util.SpeechHelper.processSpokenNeighborhood(result)
                                                        if (br.isNotBlank()) editedNeighborhood = br
                                                    },
                                                    onError = { err ->
                                                        isListeningNeighborhood = false
                                                        Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Falar Bairro",
                                            tint = if (isListeningNeighborhood) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Pré-visualização do endereço formatado
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFF8E1),
                                border = BorderStroke(1.dp, Color(0xFFFFE082)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "PRÉ-VISUALIZAÇÃO DO ENDEREÇO:",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                    val fullAddrPreview = buildString {
                                        append(editedStreet.ifBlank { "Rua não informada" })
                                        if (editedNumber.isNotBlank()) append(", nº $editedNumber")
                                        if (editedComplement.isNotBlank()) append(" - $editedComplement")
                                        if (editedNeighborhood.isNotBlank()) append(" ($editedNeighborhood)")
                                    }
                                    Text(
                                        text = fullAddrPreview,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF212121)
                                    )
                                }
                            }

                            // Botão para avançar para a aba do morador
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { editModalTab = "RESIDENT" }) {
                                    Text(
                                        text = "Ir para dados do morador ➔",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF0288D1),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            // --- ABA 2: EDITAR DADOS DO MORADOR ---
                            // Resumo do endereço vinculado com atalho para editar endereço
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE3F2FD),
                                border = BorderStroke(1.dp, Color(0xFF90CAF9)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editModalTab = "ADDRESS" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color(0xFF0D47A1),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        val summary = buildString {
                                            append(editedStreet.ifBlank { "Endereço não definido" })
                                            if (editedNumber.isNotBlank()) append(", $editedNumber")
                                            if (editedComplement.isNotBlank()) append(" ($editedComplement)")
                                        }
                                        Text(
                                            text = summary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF0D47A1),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Editar Endereço ➔",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0D47A1)
                                    )
                                }
                            }

                            // Alerta se faltar algo no formulário do morador
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

                            // Botão de ação rápida: Mover / Copiar Morador
                            if (recipientName.isNotBlank() || isEditingExisting) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF3E5F5),
                                    border = BorderStroke(1.dp, Color(0xFFCE93D8)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openMoveOrCopyForCurrentEditing() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                tint = Color(0xFF6A1B9A),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Mover / Copiar Morador",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4A148C)
                                            )
                                        }
                                        Text(
                                            text = "Outro Endereço ➔",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF6A1B9A)
                                        )
                                    }
                                }
                            }

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

                            // Complemento do Morador neste endereço (Apto, Bloco, Casa...)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Complemento / Unidade deste Morador:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF424242)
                                    )
                                    if (editedComplement.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFEDE7F6)
                                        ) {
                                            Text(
                                                text = "🏠 $editedComplement",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4A148C),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = editedComplement,
                                    onValueChange = { editedComplement = it },
                                    placeholder = { Text("Ex: Apto 101, Bloco B, Fundos", fontSize = 12.sp) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                if (!isListeningComplement) {
                                                    isListeningComplement = true
                                                    com.example.util.SpeechHelper.startListening(
                                                        context = serviceContext,
                                                        onReady = { Toast.makeText(serviceContext, "Fale o complemento...", Toast.LENGTH_SHORT).show() },
                                                        onResult = { result ->
                                                            isListeningComplement = false
                                                            val comp = com.example.util.SpeechHelper.processSpokenComplement(result)
                                                            if (comp.isNotBlank()) editedComplement = comp
                                                        },
                                                        onError = { err ->
                                                            isListeningComplement = false
                                                            Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Falar Complemento",
                                                tint = if (isListeningComplement) MaterialTheme.colorScheme.primary else Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                val knownComps = automationState.candidatePersons
                                    .map { it.complemento.trim() }
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                if (knownComps.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(knownComps) { comp ->
                                            val isSelComp = editedComplement.trim().equals(comp, ignoreCase = true)
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelComp) Color(0xFF6A1B9A) else Color(0xFFF3E5F5),
                                                border = BorderStroke(1.dp, if (isSelComp) Color(0xFF4A148C) else Color(0xFFCE93D8)),
                                                modifier = Modifier.clickable { editedComplement = comp }
                                            ) {
                                                Text(
                                                    text = "🏠 $comp",
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelComp) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelComp) Color.White else Color(0xFF4A148C),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

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

                            // Botões auxiliares da aba morador: Mover/Copiar e avançar para endereço
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (recipientName.isNotBlank() || isEditingExisting) {
                                    TextButton(
                                        onClick = { openMoveOrCopyForCurrentEditing() },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            tint = Color(0xFF6A1B9A),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Mover / Copiar",
                                            fontSize = 11.sp,
                                            color = Color(0xFF6A1B9A),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                TextButton(onClick = { editModalTab = "ADDRESS" }) {
                                    Text(
                                        text = "Editar endereço completo ➔",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (isEditingExisting) {
                                OutlinedButton(
                                    onClick = {
                                        val targetP = if (editingPersonId != null) {
                                            automationState.candidatePersons.firstOrNull { it.id == editingPersonId }
                                                ?: automationState.matchedPerson
                                        } else {
                                            automationState.matchedPerson
                                        }
                                        val targetR = Recebedor(
                                            id = editingRecebedorId ?: "main",
                                            nome = recipientName.trim(),
                                            documento = recipientDocument.trim(),
                                            assinatura = collectedSignatureData?.toJson() ?: ""
                                        )
                                        receiverToDelete = Pair(targetR, targetP)
                                        showDeleteReceiverConfirmDialog = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = Color(0xFFD32F2F)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Apagar este recebedor do endereço",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD32F2F)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // BOTÕES SALVAR / CANCELAR (Visíveis em ambas as abas)
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
                                enabled = !isSavingRecipient,
                                onClick = {
                                    if (isSavingRecipient) return@Button

                                    // Validação da rua/logradouro
                                    if (editedStreet.trim().isBlank()) {
                                        Toast.makeText(this@FloatingBubbleService, "Informe o logradouro / rua na aba Endereço!", Toast.LENGTH_SHORT).show()
                                        editModalTab = "ADDRESS"
                                        return@Button
                                    }

                                    // Validação do nome do morador
                                    if (recipientName.trim().isBlank()) {
                                        Toast.makeText(this@FloatingBubbleService, "Informe o nome do morador na aba Morador!", Toast.LENGTH_SHORT).show()
                                        editModalTab = "RESIDENT"
                                        return@Button
                                    }

                                    isSavingRecipient = true
                                    val finalStreet = AddressNormalizer.capitalizeWords(editedStreet.trim())
                                    val finalNum = editedNumber.trim()
                                    val finalComp = AddressNormalizer.formatComplementToken(editedComplement.trim()).ifBlank { editedComplement.trim() }
                                    val finalBairro = AddressNormalizer.capitalizeWords(editedNeighborhood.trim())
                                    val formattedRecipientName = AddressNormalizer.capitalizeWords(recipientName.trim())
                                    val sigJson = collectedSignatureData?.toJson() ?: ""

                                    if (isAddingNewResident && editingPersonId != null) {
                                        // Adicionar novo co-recebedor / morador para o endereço
                                        serviceScope.launch(Dispatchers.IO) {
                                            try {
                                                val existing = DeliveryApp.instance.personRepository.getPersonByIdDirect(editingPersonId!!)
                                                if (existing != null) {
                                                    val oldPrimary = Recebedor(
                                                        id = java.util.UUID.randomUUID().toString().take(8),
                                                        nome = existing.nome,
                                                        documento = existing.documento,
                                                        assinatura = existing.assinatura,
                                                        dataUso = existing.dataAtualizacao
                                                    )
                                                    val existingExtras = Recebedor.listFromJson(existing.coRecebedoresJson).toMutableList()
                                                    val newExtras = mutableListOf<Recebedor>()
                                                    if (oldPrimary.nome.isNotBlank()) {
                                                        newExtras.add(oldPrimary)
                                                    }
                                                    newExtras.addAll(existingExtras)

                                                    val updated = existing.copy(
                                                        nome = formattedRecipientName,
                                                        documento = recipientDocument.trim(),
                                                        endereco = finalStreet,
                                                        numero = finalNum,
                                                        complemento = finalComp,
                                                        bairro = finalBairro,
                                                        assinatura = sigJson,
                                                        coRecebedoresJson = Recebedor.listToJson(newExtras),
                                                        dataAtualizacao = System.currentTimeMillis()
                                                    )
                                                    DeliveryApp.instance.personRepository.updatePerson(updated)
                                                    withContext(Dispatchers.Main) {
                                                        AccessibilityAutomationEngine.setMatchedPersonDirect(updated)
                                                        val allRecs = AccessibilityAutomationEngine.extractAllRecebedores(listOf(updated))
                                                        val newMainRec = allRecs.firstOrNull { it.id == "p_${updated.id}_main" } ?: allRecs.firstOrNull()
                                                        if (newMainRec != null) {
                                                            AccessibilityAutomationEngine.selectRecebedor(newMainRec)
                                                        }
                                                        Toast.makeText(
                                                            this@FloatingBubbleService,
                                                            "Novo morador e endereço salvos!",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        RecipientDraftManager.clearDraft(this@FloatingBubbleService)
                                                        draftAvailable = null
                                                        isSavingRecipient = false
                                                        isEditModalOpen = false
                                                        updateWindowLayoutMode(OverlayMode.PANEL)
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) { isSavingRecipient = false }
                                                }
                                            } catch (e: Throwable) {
                                                com.example.util.CrashReporter.recordException(e, "SaveResidentExtra")
                                                withContext(Dispatchers.Main) {
                                                    isSavingRecipient = false
                                                    Toast.makeText(this@FloatingBubbleService, "Erro ao salvar morador: ${e.message ?: "Tente novamente"}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    } else if (isEditingExisting && editingPersonId != null) {
                                        // Atualizar cadastro existente (endereço + dados do morador)
                                        serviceScope.launch(Dispatchers.IO) {
                                            try {
                                                val existing = DeliveryApp.instance.personRepository.getPersonByIdDirect(editingPersonId!!)
                                                if (existing != null) {
                                                    val isChangingCompForMultiReceivers = finalComp != existing.complemento &&
                                                            (existing.coRecebedoresJson.isNotBlank() || (editingRecebedorId != null && !editingRecebedorId!!.startsWith("p_${existing.id}_main") && editingRecebedorId != "main"))

                                                    if (isChangingCompForMultiReceivers) {
                                                        val targetRec = Recebedor(
                                                            id = editingRecebedorId ?: "main",
                                                            nome = formattedRecipientName,
                                                            documento = recipientDocument.trim(),
                                                            assinatura = sigJson
                                                        )
                                                        DeliveryApp.instance.personRepository.moveOrCopyReceiver(
                                                            sourcePerson = existing,
                                                            receiver = targetRec,
                                                            isMove = true,
                                                            targetAddress = finalStreet,
                                                            targetNumber = finalNum,
                                                            targetComplement = finalComp,
                                                            targetBairro = finalBairro
                                                        )
                                                    } else {
                                                        val updated = if (editingRecebedorId == null || editingRecebedorId == "main" || editingRecebedorId?.startsWith("p_${existing.id}_main") == true) {
                                                            existing.copy(
                                                                nome = formattedRecipientName,
                                                                documento = recipientDocument.trim(),
                                                                endereco = finalStreet,
                                                                numero = finalNum,
                                                                complemento = finalComp,
                                                                bairro = finalBairro,
                                                                assinatura = sigJson,
                                                                dataAtualizacao = System.currentTimeMillis()
                                                            )
                                                        } else {
                                                            val extras = Recebedor.listFromJson(existing.coRecebedoresJson)
                                                            val cleanId = editingRecebedorId!!.removePrefix("p_${existing.id}_co_")
                                                            val otherExtras = extras.filterNot { it.id == cleanId || it.id == editingRecebedorId }
                                                            val oldPrimary = Recebedor(
                                                                id = java.util.UUID.randomUUID().toString().take(8),
                                                                nome = existing.nome,
                                                                documento = existing.documento,
                                                                assinatura = existing.assinatura,
                                                                dataUso = existing.dataAtualizacao
                                                            )
                                                            val newExtras = mutableListOf<Recebedor>()
                                                            if (oldPrimary.nome.isNotBlank()) {
                                                                newExtras.add(oldPrimary)
                                                            }
                                                            newExtras.addAll(otherExtras)

                                                            existing.copy(
                                                                nome = formattedRecipientName,
                                                                documento = recipientDocument.trim(),
                                                                endereco = finalStreet,
                                                                numero = finalNum,
                                                                complemento = finalComp,
                                                                bairro = finalBairro,
                                                                assinatura = sigJson,
                                                                coRecebedoresJson = Recebedor.listToJson(newExtras),
                                                                dataAtualizacao = System.currentTimeMillis()
                                                            )
                                                        }
                                                        DeliveryApp.instance.personRepository.updatePerson(updated)
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        AccessibilityAutomationEngine.refreshCurrentAddress()
                                                        Toast.makeText(
                                                            this@FloatingBubbleService,
                                                            "Dados atualizados com sucesso!",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        RecipientDraftManager.clearDraft(this@FloatingBubbleService)
                                                        draftAvailable = null
                                                        isSavingRecipient = false
                                                        isEditModalOpen = false
                                                        updateWindowLayoutMode(OverlayMode.PANEL)
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) { isSavingRecipient = false }
                                                }
                                            } catch (e: Throwable) {
                                                com.example.util.CrashReporter.recordException(e, "UpdateResident")
                                                withContext(Dispatchers.Main) {
                                                    isSavingRecipient = false
                                                    Toast.makeText(this@FloatingBubbleService, "Erro ao atualizar: ${e.message ?: "Tente novamente"}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    } else {
                                        // Inserir novo cadastro
                                        serviceScope.launch(Dispatchers.IO) {
                                            try {
                                                val searchKey = if (finalNum.isNotBlank()) "$finalStreet, $finalNum" else finalStreet
                                                val existingList = DeliveryApp.instance.personRepository.findPersonsByAddress(searchKey)
                                                val matchingHouse = existingList.firstOrNull {
                                                    AddressNormalizer.areNumbersMatching(it.numero, finalNum) &&
                                                    (finalComp.isBlank() || it.complemento.isBlank() || it.complemento.equals(finalComp, ignoreCase = true))
                                                }
                                                if (matchingHouse != null) {
                                                    val oldPrimary = Recebedor(
                                                        id = java.util.UUID.randomUUID().toString().take(8),
                                                        nome = matchingHouse.nome,
                                                        documento = matchingHouse.documento,
                                                        assinatura = matchingHouse.assinatura,
                                                        dataUso = matchingHouse.dataAtualizacao
                                                    )
                                                    val existingExtras = Recebedor.listFromJson(matchingHouse.coRecebedoresJson).toMutableList()
                                                    val newExtras = mutableListOf<Recebedor>()
                                                    if (oldPrimary.nome.isNotBlank()) {
                                                        newExtras.add(oldPrimary)
                                                    }
                                                    newExtras.addAll(existingExtras)

                                                    val updated = matchingHouse.copy(
                                                        nome = formattedRecipientName,
                                                        documento = recipientDocument.trim(),
                                                        endereco = finalStreet,
                                                        numero = finalNum,
                                                        complemento = finalComp.ifBlank { matchingHouse.complemento },
                                                        bairro = finalBairro.ifBlank { matchingHouse.bairro },
                                                        assinatura = sigJson,
                                                        coRecebedoresJson = Recebedor.listToJson(newExtras),
                                                        dataAtualizacao = System.currentTimeMillis()
                                                    )
                                                    DeliveryApp.instance.personRepository.updatePerson(updated)
                                                    withContext(Dispatchers.Main) {
                                                        AccessibilityAutomationEngine.setMatchedPersonDirect(updated)
                                                        val allRecs = AccessibilityAutomationEngine.extractAllRecebedores(listOf(updated))
                                                        val newMainRec = allRecs.firstOrNull { it.id == "p_${updated.id}_main" } ?: allRecs.firstOrNull()
                                                        if (newMainRec != null) {
                                                            AccessibilityAutomationEngine.selectRecebedor(newMainRec)
                                                        }
                                                        Toast.makeText(
                                                            this@FloatingBubbleService,
                                                            "Destinatário adicionado e selecionado!",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        RecipientDraftManager.clearDraft(this@FloatingBubbleService)
                                                        draftAvailable = null
                                                        isSavingRecipient = false
                                                        isEditModalOpen = false
                                                        updateWindowLayoutMode(OverlayMode.PANEL)
                                                    }
                                                } else {
                                                    val newPerson = Person(
                                                        nome = formattedRecipientName,
                                                        documento = recipientDocument.trim(),
                                                        endereco = finalStreet,
                                                        numero = finalNum,
                                                        complemento = finalComp,
                                                        bairro = finalBairro,
                                                        assinatura = sigJson
                                                    )
                                                    val newId = DeliveryApp.instance.personRepository.insertPerson(newPerson)
                                                    val saved = newPerson.copy(id = newId)
                                                    withContext(Dispatchers.Main) {
                                                        AccessibilityAutomationEngine.setMatchedPersonDirect(saved)
                                                        val allRecs = AccessibilityAutomationEngine.extractAllRecebedores(listOf(saved))
                                                        if (allRecs.isNotEmpty()) {
                                                            AccessibilityAutomationEngine.selectRecebedor(allRecs.first())
                                                        }
                                                        Toast.makeText(
                                                            this@FloatingBubbleService,
                                                            "Destinatário cadastrado com sucesso!",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        RecipientDraftManager.clearDraft(this@FloatingBubbleService)
                                                        draftAvailable = null
                                                        isSavingRecipient = false
                                                        isEditModalOpen = false
                                                        updateWindowLayoutMode(OverlayMode.PANEL)
                                                    }
                                                }
                                            } catch (e: Throwable) {
                                                com.example.util.CrashReporter.recordException(e, "InsertNewRecipient")
                                                withContext(Dispatchers.Main) {
                                                    isSavingRecipient = false
                                                    Toast.makeText(this@FloatingBubbleService, "Erro ao cadastrar: ${e.message ?: "Tente novamente"}", Toast.LENGTH_LONG).show()
                                                }
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
                                if (isSavingRecipient) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SALVANDO...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
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
                                            RecipientDraftManager.clearDraft(this@FloatingBubbleService)
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
                            .blur(if (showDeleteReceiverConfirmDialog || showChangeComplementDialog) 12.dp else 0.dp)
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

                        val distinctCompsInModal = automationState.candidatePersons
                            .map { it.complemento.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                        val hasMultipleCompsInModal = distinctCompsInModal.size > 1

                        Text(
                            text = if (hasMultipleCompsInModal) {
                                "Este endereço possui múltiplos complementos cadastrados (${distinctCompsInModal.joinToString(", ")}). Escolha a casa ou morador correto:"
                            } else {
                                "Este endereço possui mais de um destinatário cadastrado. Escolha o recebedor ativo:"
                            },
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
                                val rPersonId = r.id.removePrefix("p_").substringBefore("_").toLongOrNull()
                                val rPerson = automationState.candidatePersons.firstOrNull { it.id == rPersonId }
                                val rComp = rPerson?.complemento?.trim().orEmpty()

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) Color(0xFFE8EAF6) else Color(0xFFF5F5F5),
                                    border = BorderStroke(1.dp, if (isSel) Color(0xFF3F51B5) else Color(0xFFE0E0E0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            AccessibilityAutomationEngine.selectRecebedor(r)
                                            AccessibilityAutomationEngine.prioritizeRecebedor(r)
                                            isMultipleRecipientsModalOpen = false
                                            updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                                            Toast.makeText(
                                                this@FloatingBubbleService,
                                                "Selecionado: ${r.nome}${if (rComp.isNotBlank()) " ($rComp)" else ""}",
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = r.nome,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSel) Color(0xFF1A237E) else Color(0xFF212121),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (rComp.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isSel) Color(0xFFC5CAE9) else Color(0xFFE0E0E0)
                                                    ) {
                                                        Text(
                                                            text = "🏠 $rComp",
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSel) Color(0xFF1A237E) else Color(0xFF424242),
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (rPerson != null && (rPerson.endereco.isNotBlank() || rComp.isNotBlank())) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${rPerson.endereco}, ${rPerson.numero}${if (rComp.isNotBlank()) " - $rComp" else ""}",
                                                    fontSize = 9.5.sp,
                                                    color = Color(0xFF616161),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (r.documento.isNotBlank()) "Doc: ${r.documento}" else "Sem documento",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF757575)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                            Spacer(modifier = Modifier.width(3.dp))
                                            val effectivePerson = rPerson ?: automationState.matchedPerson
                                            IconButton(
                                                onClick = {
                                                    openEditForReceiver(r, effectivePerson, "RESIDENT", false)
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Editar Morador",
                                                    tint = Color(0xFF0288D1),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(2.dp))
                                            IconButton(
                                                onClick = {
                                                    receiverToChangeComplement = Pair(r, effectivePerson)
                                                    targetNewComplementInput = rComp
                                                    showChangeComplementDialog = true
                                                    updateWindowLayoutMode(OverlayMode.MODAL)
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Home,
                                                    contentDescription = "Mudar Complemento deste Morador",
                                                    tint = Color(0xFF6A1B9A),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(2.dp))
                                            IconButton(
                                                onClick = {
                                                    openMoveOrCopyForReceiver(r, effectivePerson)
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Mover ou Copiar Morador",
                                                    tint = Color(0xFF455A64),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(2.dp))
                                            IconButton(
                                                onClick = {
                                                    receiverToDelete = Pair(r, effectivePerson)
                                                    showDeleteReceiverConfirmDialog = true
                                                    updateWindowLayoutMode(OverlayMode.MODAL)
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Apagar Recebedor deste Endereço",
                                                    tint = Color(0xFFD32F2F),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
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

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                        if (automationState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(bubbleSize - 2.dp),
                                strokeWidth = 2.5.dp,
                                color = Color(0xFF80D8FF)
                            )
                            Box(
                                modifier = Modifier
                                    .size(indicatorSize)
                                    .align(Alignment.TopEnd)
                                    .padding(1.dp)
                                    .background(Color(0xFF0288D1), CircleShape)
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Procurando endereço",
                                    tint = Color.White,
                                    modifier = Modifier.size(indicatorSize * 0.7f)
                                )
                            }
                        } else if (person != null) {
                            Box(
                                modifier = Modifier
                                    .size(indicatorSize)
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                                    .border(1.dp, Color.White, CircleShape)
                            )
                        } else if (automationState.detectedAddressText.isNotBlank()) {
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

                    // Aviso quando o assistente estiver procurando um endereço na tela
                    if (automationState.isScanning) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xF00D47A1),
                            border = BorderStroke(1.dp, Color(0xFF80D8FF)),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(9.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Procurando...",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                // Painel Compacto ou Completo Flutuante
                Card(
                    modifier = Modifier
                        .width(if (isCompactMode) 290.dp else 350.dp)
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
                                            if (automationState.isScanning) Color(0xFF2196F3)
                                            else if (person != null) Color(0xFF4CAF50)
                                            else if (automationState.detectedAddressText.isNotBlank()) Color(0xFFFF9800)
                                            else Color(0xFFBDBDBD),
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

                        // BADGE DE STATUS DO ENDEREÇO (PROCURANDO vs SALVO vs NÃO CADASTRADO vs NENHUM)
                        if (automationState.isScanning) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE3F2FD),
                                border = BorderStroke(1.dp, Color(0xFF90CAF9)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF1976D2)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "🔍 PROCURANDO ENDEREÇO NA TELA...",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0D47A1)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        } else if (hasDetectedAddress) {
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
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF5F5F5),
                                border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFF757575),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (automationState.isPausedScanning) "⏸️ PESQUISA PAUSADA (CLIQUE EM 🔄)" else "AGUARDANDO ENDEREÇO NA TELA",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF616161)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                            // Alerta de Múltiplos Recebedores / Complementos
                            if (availableRecebedores.size > 1) {
                                val distinctComps = automationState.candidatePersons
                                    .map { it.complemento.trim() }
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                val hasMultipleComps = distinctComps.size > 1

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (hasMultipleComps) Color(0xFFEDE7F6) else Color(0xFFE8EAF6),
                                    border = BorderStroke(1.dp, if (hasMultipleComps) Color(0xFFD1C4E9) else Color(0xFFC5CAE9)),
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
                                                tint = if (hasMultipleComps) Color(0xFF512DA8) else Color(0xFF3F51B5),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = if (hasMultipleComps) "MÚLTIPLOS COMPLEMENTOS (${distinctComps.size} UNIDADES)" else "MÚLTIPLOS RECEBEDORES",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (hasMultipleComps) Color(0xFF311B92) else Color(0xFF1A237E)
                                                )
                                                Text(
                                                    text = if (hasMultipleComps) "Unidades: ${distinctComps.joinToString(", ")}. Toque para escolher:" else "Este endereço possui ${availableRecebedores.size} destinatários.",
                                                    fontSize = 9.sp,
                                                    color = if (hasMultipleComps) Color(0xFF4527A0) else Color(0xFF283593),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (hasMultipleComps) "ESCOLHER ➔" else "ALTERAR ➔",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasMultipleComps) Color(0xFF512DA8) else Color(0xFF3F51B5)
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
                                if (automationState.isScanning) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFE3F2FD),
                                        border = BorderStroke(0.5.dp, Color(0xFF90CAF9))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(9.dp),
                                                strokeWidth = 1.5.dp,
                                                color = Color(0xFF1565C0)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Buscando...",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1565C0)
                                            )
                                        }
                                    }
                                } else if (hasDetectedAddress && automationState.isAddressLocked) {
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
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFE3F2FD),
                                    border = BorderStroke(0.5.dp, Color(0xFF90CAF9)),
                                    modifier = Modifier
                                        .clickable {
                                            openEditForCurrent("ADDRESS", false)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar Endereço",
                                            tint = Color(0xFF0D47A1),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Editar",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0D47A1)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
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
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    if (automationState.isScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF1976D2)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reescanear tela",
                                            tint = Color(0xFF1976D2),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        val activeRecForDisplay = person ?: availableRecebedores.firstOrNull()
                        val activeRecPersonId = activeRecForDisplay?.id?.removePrefix("p_")?.substringBefore("_")?.toLongOrNull()
                        val activePersonForDisplay = if (activeRecPersonId != null) automationState.candidatePersons.firstOrNull { it.id == activeRecPersonId } else automationState.matchedPerson
                        val activePersonComp = activePersonForDisplay?.complemento?.trim().orEmpty()
                        val displayAddress = if (activePersonComp.isNotBlank() && address.isNotBlank() && !address.contains(activePersonComp, ignoreCase = true)) {
                            "$address ($activePersonComp)"
                        } else {
                            address
                        }

                        if (automationState.isScanning) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE3F2FD),
                                border = BorderStroke(1.dp, Color(0xFFBBDEFB)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.8.dp,
                                        color = Color(0xFF1976D2)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Procurando endereço na tela...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1565C0)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = if (automationState.isPausedScanning && automationState.detectedAddressText.isBlank()) {
                                    "Pesquisa pausada. Clique em 🔄 para pesquisar o endereço."
                                } else {
                                    displayAddress.ifBlank { "Nenhum endereço detectado na tela." }
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (automationState.detectedAddressText.isBlank()) Color.Gray else Color(0xFF212121),
                                maxLines = if (isCompactMode) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable {
                                    openEditForCurrent("ADDRESS", false)
                                }
                            )
                        }

                        // SELETOR E ADIÇÃO DE COMPLEMENTO NO BALÃO
                        if (!automationState.isScanning && automationState.detectedAddressText.isNotBlank()) {
                            if (automationState.availableComplements.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = null,
                                            tint = Color(0xFF6A1B9A),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Complementos salvos:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4A148C)
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFEDE7F6),
                                            modifier = Modifier.clickable { openAddComplementDialog() }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = Color(0xFF6A1B9A),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "Adicionar",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF6A1B9A)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(automationState.availableComplements) { comp ->
                                            val isSelected = comp.equals(automationState.selectedComplement, ignoreCase = true)
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) Color(0xFF6A1B9A) else Color(0xFFF3E5F5),
                                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF4A148C) else Color(0xFFCE93D8)),
                                                modifier = Modifier.clickable {
                                                    AccessibilityAutomationEngine.selectComplement(comp)
                                                }
                                            ) {
                                                Text(
                                                    text = comp,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color.White else Color(0xFF4A148C),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Quando não há complementos cadastrados ainda, permitir adicionar facilmente
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFF5F5F5),
                                        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { openAddComplementDialog() }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = Color(0xFF1976D2),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Adicionar Complemento (Apto, Bloco, Casa...)",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF1976D2)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Pessoa Encontrada ou Estado Não Salvo
                        if (availableRecebedores.isNotEmpty()) {
                            val activeRec = person ?: availableRecebedores.first()
                            val isDocMissing = activeRec.documento.isBlank()
                            val isSigMissing = activeRec.assinatura.isBlank()
                            val isIncomplete = isDocMissing || isSigMissing

                            // Card do Recebedor Selecionado
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE8F5E9),
                                border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
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
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val activeRecPersonId = activeRec.id.removePrefix("p_").substringBefore("_").toLongOrNull()
                                            val activeRecPerson = if (activeRecPersonId != null) automationState.candidatePersons.firstOrNull { it.id == activeRecPersonId } else automationState.matchedPerson
                                            val activeComp = activeRecPerson?.complemento?.trim().orEmpty()

                                            IconButton(
                                                onClick = {
                                                    val targetP = activeRecPerson ?: automationState.matchedPerson
                                                    receiverToChangeComplement = Pair(activeRec, targetP)
                                                    targetNewComplementInput = activeComp
                                                    showChangeComplementDialog = true
                                                    updateWindowLayoutMode(OverlayMode.MODAL)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Home,
                                                    contentDescription = "Mudar Complemento deste Morador",
                                                    tint = Color(0xFF6A1B9A),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { openEditForReceiver(activeRec, activeRecPerson ?: automationState.matchedPerson, "RESIDENT", false) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Editar Morador",
                                                    tint = Color(0xFF0288D1),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    openMoveOrCopyForReceiver(activeRec, activeRecPerson ?: automationState.matchedPerson)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Mover ou Copiar Morador",
                                                    tint = Color(0xFF455A64),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    val targetP = activeRecPerson ?: automationState.matchedPerson
                                                    receiverToDelete = Pair(activeRec, targetP)
                                                    showDeleteReceiverConfirmDialog = true
                                                    updateWindowLayoutMode(OverlayMode.MODAL)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Apagar Recebedor deste Endereço",
                                                    tint = Color(0xFFD32F2F),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = activeRec.nome.ifBlank { "Sem nome" },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1B5E20),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        val recPersonId = activeRec.id.removePrefix("p_").substringBefore("_").toLongOrNull()
                                        val recPerson = automationState.candidatePersons.firstOrNull { it.id == recPersonId } ?: automationState.matchedPerson
                                        val recComp = recPerson?.complemento?.trim().orEmpty()
                                        if (recComp.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFC8E6C9),
                                                border = BorderStroke(1.dp, Color(0xFF81C784))
                                            ) {
                                                Text(
                                                    text = "🏠 $recComp",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1B5E20),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (activeRec.documento.isNotBlank()) {
                                        Text(
                                            text = "Doc: ${activeRec.documento}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF455A64)
                                        )
                                    } else {
                                        Text(
                                            text = "⚠️ Sem Documento",
                                            fontSize = 11.sp,
                                            color = Color(0xFFD84315),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Seção "Moradores neste endereço:" e "+ + Outro Morador"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Moradores neste endereço:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF424242)
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF3E5F5),
                                    border = BorderStroke(1.dp, Color(0xFFCE93D8)),
                                    modifier = Modifier.clickable {
                                        openRegisterNewResidentForAddress(automationState.matchedPerson)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Outro Morador", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B9A))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Chips Horizontais dos Moradores
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableRecebedores.forEach { r ->
                                    val isSelected = r.id == activeRec.id
                                    val rPersonId = r.id.removePrefix("p_").substringBefore("_").toLongOrNull()
                                    val rPerson = automationState.candidatePersons.firstOrNull { it.id == rPersonId }
                                    val rComp = rPerson?.complemento?.trim().orEmpty()
                                    val chipLabel = if (rComp.isNotBlank()) {
                                        "${r.nome.ifBlank { "Sem nome" }} ($rComp)"
                                    } else {
                                        r.nome.ifBlank { "Sem nome" }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) Color(0xFF2E7D32) else Color(0xFFECEFF1),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFF1B5E20) else Color(0xFFCFD8DC)
                                        ),
                                        modifier = Modifier.clickable {
                                            AccessibilityAutomationEngine.selectRecebedor(r)
                                            AccessibilityAutomationEngine.prioritizeRecebedor(r)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = chipLabel,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFF263238),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // BANNER DE SUGESTÃO DE COMPLETAR CADASTRO SE ESTIVER FALTANDO ALGO NO ATIVO
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
                                                    append("Falta para ${activeRec.nome}: ")
                                                    if (isDocMissing && isSigMissing) append("Doc e Assinatura")
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
                                                    onClick = { openEditForReceiver(activeRec, automationState.matchedPerson, "RESIDENT", false) },
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
                                                    onClick = { openEditForReceiver(activeRec, automationState.matchedPerson, "RESIDENT", true) },
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
                                                onClick = { openEditForReceiver(activeRec, automationState.matchedPerson, "RESIDENT", false) },
                                                modifier = Modifier.weight(1f).height(30.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 2.dp)
                                            ) {
                                                Text("Morador", fontSize = 8.5.sp, maxLines = 1)
                                            }
                                            OutlinedButton(
                                                onClick = { openEditForReceiver(activeRec, automationState.matchedPerson, "ADDRESS", false) },
                                                modifier = Modifier.weight(1f).height(30.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 2.dp)
                                            ) {
                                                Text("Endereço", fontSize = 8.5.sp, maxLines = 1)
                                            }
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
                                    AccessibilityAutomationEngine.prioritizeRecebedor(person)
                                    if (appSettings.vibrationEnabled) {
                                        triggerHapticFeedback()
                                    }
                                    val res = AccessibilityAutomationEngine.fillFields(
                                        person.nome,
                                        person.documento
                                    )
                                    Toast.makeText(this@FloatingBubbleService, res.message, Toast.LENGTH_SHORT).show()
                                    if (res.nameFilled || res.documentFilled) {
                                        triggerSuccessFeedback()
                                    }
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
                                        AccessibilityAutomationEngine.prioritizeRecebedor(person)
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
                                        AccessibilityAutomationEngine.prioritizeRecebedor(person)
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
                                        AccessibilityAutomationEngine.prioritizeRecebedor(person)
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
                                            AccessibilityAutomationEngine.prioritizeRecebedor(person)
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
                                    val detected = automationState.detectedAddressText.trim()
                                    val parsed = AddressNormalizer.parseAddressComponents(detected)
                                    editingPersonId = null
                                    editingRecebedorId = null
                                    editedStreet = parsed.street
                                    editedNumber = parsed.number
                                    editedComplement = parsed.complement
                                    editedNeighborhood = parsed.neighborhood
                                    recipientName = ""
                                    recipientDocument = ""
                                    collectedSignatureData = null

                                    initialEditedStreet = parsed.street
                                    initialEditedNumber = parsed.number
                                    initialEditedComplement = parsed.complement
                                    initialEditedNeighborhood = parsed.neighborhood
                                    initialRecipientName = ""
                                    initialRecipientDocument = ""
                                    initialCollectedSigJson = ""

                                    editModalTab = if (parsed.street.isNotBlank()) "RESIDENT" else "ADDRESS"
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
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Calibrar", fontSize = 10.5.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        isSearchModalOpen = true
                                        searchModalQuery = ""
                                        updateWindowLayoutMode(OverlayMode.MODAL)
                                    },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Pesquisar", fontSize = 10.5.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { onCloseService() },
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                ) {
                                    Text("Fechar", fontSize = 10.5.sp, color = Color.Red, maxLines = 1)
                                }
                            }
                        }
                    }
                }

            // DIÁLOGO DE MOVER / COPIAR MORADOR (Exibido no topo de qualquer modal ou painel)
            if (showMoveOrCopyDialog) {
                val moveOrCopySearchResults by remember(moveOrCopySearchQuery) {
                    if (moveOrCopySearchQuery.isBlank()) {
                        DeliveryApp.instance.personRepository.getAllPersons()
                    } else {
                        DeliveryApp.instance.personRepository.searchPersons(moveOrCopySearchQuery)
                    }
                }.collectAsState(initial = emptyList())

                val distinctTargetSuggestions = remember(moveOrCopySearchResults) {
                    moveOrCopySearchResults.distinctBy {
                        "${AddressNormalizer.normalize(it.endereco)}_${AddressNormalizer.normalize(it.numero)}_${AddressNormalizer.normalize(it.complemento)}"
                    }.take(5)
                }

                val onConfirmMoveOrCopy: () -> Unit = onConfirm@{
                    if (moveOrCopyTargetStreet.trim().isBlank()) {
                        moveOrCopyStreetError = true
                        Toast.makeText(serviceContext, "Informe o endereço de destino!", Toast.LENGTH_SHORT).show()
                        return@onConfirm
                    }
                    val rec = moveOrCopyTargetReceiver ?: return@onConfirm
                    isExecutingMoveOrCopy = true

                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val success = DeliveryApp.instance.personRepository.moveOrCopyReceiver(
                                sourcePerson = moveOrCopySourcePerson,
                                receiver = rec,
                                isMove = isMoveAction,
                                targetAddress = moveOrCopyTargetStreet.trim(),
                                targetNumber = moveOrCopyTargetNumber.trim(),
                                targetComplement = moveOrCopyTargetComplement.trim(),
                                targetBairro = moveOrCopyTargetBairro.trim()
                            )

                            withContext(Dispatchers.Main) {
                                isExecutingMoveOrCopy = false
                                showMoveOrCopyDialog = false
                                val actionName = if (isMoveAction) "movido" else "copiado"
                                if (success) {
                                    FeedbackHelper.triggerSuccess(serviceContext)
                                    Toast.makeText(serviceContext, "Morador $actionName com sucesso!", Toast.LENGTH_SHORT).show()

                                    if (isMoveAction) {
                                        isEditModalOpen = false
                                    }

                                    AccessibilityAutomationEngine.rescanCurrentScreen(forceUnlock = true)
                                    updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                                } else {
                                    Toast.makeText(serviceContext, "Erro ao realizar operação.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isExecutingMoveOrCopy = false
                                Toast.makeText(serviceContext, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(344.dp)
                            .heightIn(max = 540.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState()),
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
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = Color(0xFF6A1B9A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isMoveAction) "Mover Morador" else "Copiar Morador",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF4A148C)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showMoveOrCopyDialog = false
                                        if (!isEditModalOpen && !isSearchModalOpen && !isMultipleRecipientsModalOpen) {
                                            updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE0E0E0))

                            // Card com dados do morador sendo transferido
                            val targetRec = moveOrCopyTargetReceiver
                            val hasSig = targetRec?.assinatura?.isNotBlank() == true || collectedSignatureData != null
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF3E5F5),
                                border = BorderStroke(1.dp, Color(0xFFE1BEE7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = targetRec?.nome?.ifBlank { "Morador" } ?: "Morador",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF4A148C)
                                        )
                                    }
                                    if (!targetRec?.documento.isNullOrBlank()) {
                                        Text(
                                            text = "Documento: ${targetRec?.documento}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF616161)
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (hasSig) "✓ Assinatura incluída (será mantida)" else "ℹ️ Sem assinatura cadastrada",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (hasSig) Color(0xFF2E7D32) else Color(0xFF757575)
                                        )
                                    }
                                }
                            }

                            // Seletor de Modo: MOVER vs COPIAR
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isMoveAction) Color(0xFF6A1B9A) else Color(0xFFF5F5F5),
                                    border = BorderStroke(1.dp, if (isMoveAction) Color(0xFF6A1B9A) else Color(0xFFE0E0E0)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { isMoveAction = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = if (isMoveAction) Color.White else Color(0xFF616161),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Mover",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMoveAction) Color.White else Color(0xFF424242)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (!isMoveAction) Color(0xFF1565C0) else Color(0xFFF5F5F5),
                                    border = BorderStroke(1.dp, if (!isMoveAction) Color(0xFF1565C0) else Color(0xFFE0E0E0)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { isMoveAction = false }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            tint = if (!isMoveAction) Color.White else Color(0xFF616161),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Copiar",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!isMoveAction) Color.White else Color(0xFF424242)
                                        )
                                    }
                                }
                            }

                            // Explicação da ação
                            Text(
                                text = if (isMoveAction)
                                    "• Mover: Transfere o morador e sua assinatura para o novo endereço, removendo do endereço de origem."
                                else
                                    "• Copiar: Duplica o morador e sua assinatura no novo endereço, mantendo também o cadastro de origem.",
                                fontSize = 10.sp,
                                color = Color(0xFF616161),
                                lineHeight = 13.sp
                            )

                            HorizontalDivider(color = Color(0xFFEEEEEE))

                            // Título Endereço de Destino
                            Text(
                                text = "Endereço de Destino:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1A237E)
                            )

                            // Campo de Busca Rápida de Endereços Salvos
                            OutlinedTextField(
                                value = moveOrCopySearchQuery,
                                onValueChange = { moveOrCopySearchQuery = it },
                                placeholder = { Text("Buscar endereço existente...", fontSize = 11.5.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (moveOrCopySearchQuery.isNotBlank()) {
                                        IconButton(onClick = { moveOrCopySearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Limpar", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Lista rápida de sugestões de endereços existentes
                            if (distinctTargetSuggestions.isNotEmpty() && moveOrCopySearchQuery.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 130.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    distinctTargetSuggestions.forEach { p ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFF5F5F5),
                                            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    moveOrCopyTargetStreet = p.endereco
                                                    moveOrCopyTargetNumber = p.numero
                                                    moveOrCopyTargetComplement = p.complemento
                                                    moveOrCopyTargetBairro = p.bairro
                                                    moveOrCopySearchQuery = ""
                                                    moveOrCopyStreetError = false
                                                    Toast.makeText(serviceContext, "Endereço selecionado!", Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF3F51B5), modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${p.endereco}, ${p.numero.ifBlank { "S/N" }}",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF212121),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (p.complemento.isNotBlank() || p.bairro.isNotBlank()) {
                                                        Text(
                                                            text = listOf(p.complemento, p.bairro).filter { it.isNotBlank() }.joinToString(" • "),
                                                            fontSize = 9.5.sp,
                                                            color = Color(0xFF757575),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Campos Manuais de Endereço de Destino
                            Text(
                                text = "Rua / Logradouro *:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (moveOrCopyStreetError) Color(0xFFD32F2F) else Color(0xFF424242)
                            )
                            OutlinedTextField(
                                value = moveOrCopyTargetStreet,
                                onValueChange = {
                                    moveOrCopyTargetStreet = it
                                    if (it.isNotBlank()) moveOrCopyStreetError = false
                                },
                                isError = moveOrCopyStreetError,
                                placeholder = { Text("Ex: Rua das Flores", fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (!isListeningMoveStreet) {
                                                isListeningMoveStreet = true
                                                com.example.util.SpeechHelper.startListening(
                                                    context = serviceContext,
                                                    onReady = { Toast.makeText(serviceContext, "Fale o endereço...", Toast.LENGTH_SHORT).show() },
                                                    onResult = { result ->
                                                        isListeningMoveStreet = false
                                                        val parsed = AddressNormalizer.parseAddressComponents(result)
                                                        moveOrCopyTargetStreet = parsed.street.ifBlank { com.example.util.SpeechHelper.processSpokenStreet(result) }
                                                        if (parsed.number.isNotBlank()) moveOrCopyTargetNumber = parsed.number
                                                        if (parsed.complement.isNotBlank()) moveOrCopyTargetComplement = parsed.complement
                                                        if (parsed.neighborhood.isNotBlank()) moveOrCopyTargetBairro = parsed.neighborhood
                                                        moveOrCopyStreetError = false
                                                    },
                                                    onError = { err ->
                                                        isListeningMoveStreet = false
                                                        Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Falar Rua", tint = if (isListeningMoveStreet) MaterialTheme.colorScheme.primary else Color.Gray)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Número e Complemento
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Número:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF424242))
                                    OutlinedTextField(
                                        value = moveOrCopyTargetNumber,
                                        onValueChange = { moveOrCopyTargetNumber = it },
                                        placeholder = { Text("Ex: 120", fontSize = 12.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    if (!isListeningMoveNumber) {
                                                        isListeningMoveNumber = true
                                                        com.example.util.SpeechHelper.startListening(
                                                            context = serviceContext,
                                                            onReady = { Toast.makeText(serviceContext, "Fale o número...", Toast.LENGTH_SHORT).show() },
                                                            onResult = { result ->
                                                                isListeningMoveNumber = false
                                                                val num = com.example.util.SpeechHelper.processSpokenNumber(result)
                                                                moveOrCopyTargetNumber = if (num.isNotBlank()) num else result
                                                            },
                                                            onError = { err ->
                                                                isListeningMoveNumber = false
                                                                Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Mic, contentDescription = "Falar Número", tint = if (isListeningMoveNumber) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(18.dp))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }

                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text(text = "Complemento:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF424242))
                                    OutlinedTextField(
                                        value = moveOrCopyTargetComplement,
                                        onValueChange = { moveOrCopyTargetComplement = it },
                                        placeholder = { Text("Ex: Apto 101", fontSize = 12.sp) },
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    if (!isListeningMoveComp) {
                                                        isListeningMoveComp = true
                                                        com.example.util.SpeechHelper.startListening(
                                                            context = serviceContext,
                                                            onReady = { Toast.makeText(serviceContext, "Fale o complemento...", Toast.LENGTH_SHORT).show() },
                                                            onResult = { result ->
                                                                isListeningMoveComp = false
                                                                val comp = com.example.util.SpeechHelper.processSpokenComplement(result)
                                                                moveOrCopyTargetComplement = if (comp.isNotBlank()) comp else result
                                                            },
                                                            onError = { err ->
                                                                isListeningMoveComp = false
                                                                Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Mic, contentDescription = "Falar Complemento", tint = if (isListeningMoveComp) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(18.dp))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }

                            // Bairro
                            Text(text = "Bairro:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF424242))
                            OutlinedTextField(
                                value = moveOrCopyTargetBairro,
                                onValueChange = { moveOrCopyTargetBairro = it },
                                placeholder = { Text("Ex: Centro", fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (!isListeningMoveBairro) {
                                                isListeningMoveBairro = true
                                                com.example.util.SpeechHelper.startListening(
                                                    context = serviceContext,
                                                    onReady = { Toast.makeText(serviceContext, "Fale o bairro...", Toast.LENGTH_SHORT).show() },
                                                    onResult = { result ->
                                                        isListeningMoveBairro = false
                                                        val br = com.example.util.SpeechHelper.processSpokenNeighborhood(result)
                                                        moveOrCopyTargetBairro = if (br.isNotBlank()) br else result
                                                    },
                                                    onError = { err ->
                                                        isListeningMoveBairro = false
                                                        Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Falar Bairro", tint = if (isListeningMoveBairro) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(18.dp))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Botões de Ação do Diálogo
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showMoveOrCopyDialog = false
                                        if (!isEditModalOpen && !isSearchModalOpen && !isMultipleRecipientsModalOpen) {
                                            updateWindowLayoutMode(if (isExpanded) OverlayMode.PANEL else OverlayMode.BUBBLE)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancelar", fontSize = 12.sp, maxLines = 1)
                                }

                                Button(
                                    onClick = onConfirmMoveOrCopy,
                                    enabled = !isExecutingMoveOrCopy,
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isMoveAction) Color(0xFF6A1B9A) else Color(0xFF1565C0)
                                    )
                                ) {
                                    if (isExecutingMoveOrCopy) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = if (isMoveAction) Icons.Default.LocationOn else Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isMoveAction) "Mover Morador" else "Copiar Morador",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showAddComplementDialog) {
                // DIÁLOGO DE ADICIONAR COMPLEMENTO AO ENDEREÇO
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(320.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Título do Diálogo
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = null,
                                        tint = Color(0xFF6A1B9A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Adicionar Complemento",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                IconButton(
                                    onClick = { closeAddComplementDialog() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }

                            // Exibição do Endereço Atual
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF3E5F5),
                                border = BorderStroke(1.dp, Color(0xFFE1BEE7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Endereço atual:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF6A1B9A)
                                    )
                                    Text(
                                        text = automationState.detectedAddressText.ifBlank { "Endereço detectado" },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF311B92),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Campo de Entrada do Complemento
                            Text(
                                text = "Informe o complemento (Apto, Bloco, Casa...):",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF424242)
                            )

                            OutlinedTextField(
                                value = newComplementInput,
                                onValueChange = { newComplementInput = it },
                                placeholder = { Text("Ex: Apto 101, Bloco B, Fundos", fontSize = 12.sp) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (!isListeningNewComp) {
                                                isListeningNewComp = true
                                                com.example.util.SpeechHelper.startListening(
                                                    context = serviceContext,
                                                    onReady = { Toast.makeText(serviceContext, "Fale o complemento...", Toast.LENGTH_SHORT).show() },
                                                    onResult = { result ->
                                                        isListeningNewComp = false
                                                        val comp = com.example.util.SpeechHelper.processSpokenComplement(result)
                                                        newComplementInput = if (comp.isNotBlank()) comp else result
                                                    },
                                                    onError = { err ->
                                                        isListeningNewComp = false
                                                        Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Falar Complemento",
                                            tint = if (isListeningNewComp) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Sugestões Rápidas de Complementos Comuns
                            Text(
                                text = "Sugestões rápidas:",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF757575)
                            )
                            val commonSuggestions = listOf("Apto 1", "Apto 2", "Apto 101", "Apto 102", "Casa 1", "Casa 2", "Fundos", "Sobrado", "Bloco A", "Bloco B")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(commonSuggestions) { suggestion ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF5F5F5),
                                        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                        modifier = Modifier.clickable {
                                            newComplementInput = suggestion
                                        }
                                    ) {
                                        Text(
                                            text = suggestion,
                                            fontSize = 10.sp,
                                            color = Color(0xFF424242),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Botões de Ação
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { closeAddComplementDialog() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancelar", fontSize = 12.sp, maxLines = 1)
                                }

                                Button(
                                    onClick = {
                                        val trimmedComp = newComplementInput.trim()
                                        if (trimmedComp.isBlank()) {
                                            Toast.makeText(serviceContext, "Digite ou fale o complemento.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val formatted = AddressNormalizer.formatComplementToken(trimmedComp).ifBlank { trimmedComp }
                                        AccessibilityAutomationEngine.setComplementForDetectedAddress(formatted)
                                        closeAddComplementDialog()
                                        Toast.makeText(serviceContext, "Complemento \"$formatted\" adicionado!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Adicionar",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showDeleteReceiverConfirmDialog && receiverToDelete != null) {
                // DIÁLOGO DE CONFIRMAÇÃO PARA APAGAR RECEBEDOR DO ENDEREÇO
                val (delRec, delSourcePerson) = receiverToDelete!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(320.dp)
                            .padding(16.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFEBEE),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Apagar Recebedor?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFFB71C1C),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                text = "Deseja realmente apagar o recebedor \"${delRec.nome}\" deste endereço?",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF212121),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (delSourcePerson != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF5F5F5),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "Endereço:",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = "${delSourcePerson.endereco}, ${delSourcePerson.numero}${if (delSourcePerson.complemento.isNotBlank()) " - ${delSourcePerson.complemento}" else ""}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF424242)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "⚠️ Esta ação removerá o morador e sua assinatura deste endereço.",
                                fontSize = 10.5.sp,
                                color = Color(0xFF757575),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isDeletingReceiver) {
                                            showDeleteReceiverConfirmDialog = false
                                            receiverToDelete = null
                                        }
                                    },
                                    enabled = !isDeletingReceiver,
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancelar", fontSize = 11.5.sp, maxLines = 1)
                                }
                                Button(
                                    onClick = {
                                        isDeletingReceiver = true
                                        serviceScope.launch(Dispatchers.IO) {
                                            try {
                                                val personId = delSourcePerson?.id
                                                    ?: delRec.id.removePrefix("p_").substringBefore("_").toLongOrNull()
                                                    ?: -1L
                                                val ok = DeliveryApp.instance.personRepository.deleteReceiverFromAddress(personId, delRec)
                                                withContext(Dispatchers.Main) {
                                                    isDeletingReceiver = false
                                                    showDeleteReceiverConfirmDialog = false
                                                    receiverToDelete = null
                                                    if (ok) {
                                                        Toast.makeText(this@FloatingBubbleService, "Recebedor \"${delRec.nome}\" apagado com sucesso!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(this@FloatingBubbleService, "Recebedor removido.", Toast.LENGTH_SHORT).show()
                                                    }
                                                    AccessibilityAutomationEngine.refreshCurrentAddress()
                                                    if (!isExpanded && !isMultipleRecipientsModalOpen && !isEditModalOpen) {
                                                        updateWindowLayoutMode(OverlayMode.PANEL)
                                                    }
                                                }
                                            } catch (e: Throwable) {
                                                withContext(Dispatchers.Main) {
                                                    isDeletingReceiver = false
                                                    showDeleteReceiverConfirmDialog = false
                                                    receiverToDelete = null
                                                    Toast.makeText(this@FloatingBubbleService, "Erro ao apagar: ${e.message ?: "Tente novamente"}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isDeletingReceiver,
                                    modifier = Modifier.weight(1.2f).height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isDeletingReceiver) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sim, Apagar", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showChangeComplementDialog && receiverToChangeComplement != null) {
                // DIÁLOGO PARA MUDAR COMPLEMENTO DE MORADOR ESPECÍFICO NESTE ENDEREÇO
                val (recToChange, sourcePersonToChange) = receiverToChangeComplement!!
                val st = sourcePersonToChange?.endereco ?: AddressNormalizer.parseAddressComponents(automationState.detectedAddressText).street
                val num = sourcePersonToChange?.numero ?: AddressNormalizer.parseAddressComponents(automationState.detectedAddressText).number
                val currentComp = sourcePersonToChange?.complemento?.trim().orEmpty()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(330.dp)
                            .padding(14.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
                                        imageVector = Icons.Default.Home,
                                        contentDescription = null,
                                        tint = Color(0xFF6A1B9A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Mudar Complemento",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF4A148C)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (!isExecutingChangeComplement) {
                                            showChangeComplementDialog = false
                                            receiverToChangeComplement = null
                                        }
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }

                            // Card com identificação do Morador e Endereço
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF3E5F5),
                                border = BorderStroke(1.dp, Color(0xFFCE93D8)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = recToChange.nome.ifBlank { "Morador" },
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4A148C),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Endereço: $st, $num",
                                        fontSize = 11.sp,
                                        color = Color(0xFF333333)
                                    )
                                    Text(
                                        text = "Complemento atual: ${if (currentComp.isNotBlank()) currentComp else "Principal (Sem complemento)"}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF7B1FA2)
                                    )
                                }
                            }

                            Text(
                                text = "Novo Complemento / Unidade:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF424242)
                            )

                            OutlinedTextField(
                                value = targetNewComplementInput,
                                onValueChange = { targetNewComplementInput = it },
                                placeholder = { Text("Ex: Apto 102, Casa 2, Fundos", fontSize = 12.sp) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (!isListeningChangeComp) {
                                                isListeningChangeComp = true
                                                com.example.util.SpeechHelper.startListening(
                                                    context = serviceContext,
                                                    onReady = { Toast.makeText(serviceContext, "Fale o complemento...", Toast.LENGTH_SHORT).show() },
                                                    onResult = { result ->
                                                        isListeningChangeComp = false
                                                        val comp = com.example.util.SpeechHelper.processSpokenComplement(result)
                                                        targetNewComplementInput = if (comp.isNotBlank()) comp else result
                                                    },
                                                    onError = { err ->
                                                        isListeningChangeComp = false
                                                        Toast.makeText(serviceContext, err, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Falar Complemento",
                                            tint = if (isListeningChangeComp) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Sugestões de complementos já existentes no endereço
                            val distinctComps = automationState.candidatePersons
                                .map { it.complemento.trim() }
                                .filter { it.isNotBlank() }
                                .distinct()

                            if (distinctComps.isNotEmpty()) {
                                Text(
                                    text = "Complementos já cadastrados neste endereço:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF6A1B9A)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(distinctComps) { comp ->
                                        val isSel = targetNewComplementInput.trim().equals(comp, ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSel) Color(0xFF6A1B9A) else Color(0xFFEDE7F6),
                                            border = BorderStroke(1.dp, if (isSel) Color(0xFF4A148C) else Color(0xFFCE93D8)),
                                            modifier = Modifier.clickable { targetNewComplementInput = comp }
                                        ) {
                                            Text(
                                                text = "🏠 $comp",
                                                fontSize = 10.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSel) Color.White else Color(0xFF4A148C),
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Atalhos rápidos comuns
                            Text(
                                text = "Atalhos rápidos:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF757575)
                            )
                            val quickComps = listOf("Apto 1", "Apto 2", "Apto 101", "Apto 102", "Casa 1", "Casa 2", "Fundos", "Sobrado", "Bloco A", "Bloco B")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(quickComps) { s ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFF5F5F5),
                                        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                        modifier = Modifier.clickable { targetNewComplementInput = s }
                                    ) {
                                        Text(
                                            text = s,
                                            fontSize = 9.5.sp,
                                            color = Color(0xFF424242),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "💡 O morador será transferido para o novo complemento preservando nome, documento e assinatura.",
                                fontSize = 9.5.sp,
                                color = Color(0xFF555555),
                                lineHeight = 13.sp
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Botões Cancelar / Salvar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isExecutingChangeComplement) {
                                            showChangeComplementDialog = false
                                            receiverToChangeComplement = null
                                        }
                                    },
                                    enabled = !isExecutingChangeComplement,
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancelar", fontSize = 11.5.sp, maxLines = 1)
                                }
                                Button(
                                    onClick = {
                                        val trimmedInput = targetNewComplementInput.trim()
                                        if (trimmedInput.isBlank()) {
                                            Toast.makeText(serviceContext, "Digite ou selecione o complemento.", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val formatted = AddressNormalizer.formatComplementToken(trimmedInput).ifBlank { trimmedInput }
                                        isExecutingChangeComplement = true
                                        serviceScope.launch(Dispatchers.IO) {
                                            try {
                                                val targetBairro = sourcePersonToChange?.bairro ?: AddressNormalizer.parseAddressComponents(automationState.detectedAddressText).neighborhood
                                                val ok = DeliveryApp.instance.personRepository.moveOrCopyReceiver(
                                                    sourcePerson = sourcePersonToChange,
                                                    receiver = recToChange,
                                                    isMove = true,
                                                    targetAddress = st,
                                                    targetNumber = num,
                                                    targetComplement = formatted,
                                                    targetBairro = targetBairro
                                                )
                                                withContext(Dispatchers.Main) {
                                                    isExecutingChangeComplement = false
                                                    showChangeComplementDialog = false
                                                    receiverToChangeComplement = null
                                                    if (ok) {
                                                        Toast.makeText(this@FloatingBubbleService, "Morador \"${recToChange.nome}\" movido para $formatted!", Toast.LENGTH_SHORT).show()
                                                        AccessibilityAutomationEngine.setComplementForDetectedAddress(formatted)
                                                    } else {
                                                        Toast.makeText(this@FloatingBubbleService, "Não foi possível mover o morador.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Throwable) {
                                                withContext(Dispatchers.Main) {
                                                    isExecutingChangeComplement = false
                                                    showChangeComplementDialog = false
                                                    receiverToChangeComplement = null
                                                    Toast.makeText(this@FloatingBubbleService, "Erro: ${e.message ?: "Tente novamente"}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isExecutingChangeComplement,
                                    modifier = Modifier.weight(1.3f).height(40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                                ) {
                                    if (isExecutingChangeComplement) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Salvar", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
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

        com.example.util.AppActivityTracker.onFloatingAssistantStateChanged(false)

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
