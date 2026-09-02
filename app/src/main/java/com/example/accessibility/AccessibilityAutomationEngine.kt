package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.local.entity.Person
import com.example.data.model.ExternalAppProfile
import com.example.data.model.SignatureData
import com.example.data.model.Recebedor
import com.example.data.model.Point
import com.example.data.repository.PersonRepository
import com.example.data.repository.SettingsRepository
import com.example.util.AddressNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val isSuccess: Boolean = true
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

data class FillResult(
    val nameFilled: Boolean,
    val documentFilled: Boolean,
    val message: String
)

data class AutomationState(
    val isServiceActive: Boolean = false,
    val currentPackageName: String = "",
    val currentClassName: String = "",
    val detectedAddressText: String = "",
    val isAddressLocked: Boolean = false,
    val isPausedScanning: Boolean = false,
    val matchedPerson: Person? = null,
    val candidatePersons: List<Person> = emptyList(),
    val selectedRecebedor: Recebedor? = null,
    val availableRecebedores: List<Recebedor> = emptyList(),
    val lastAction: String = "Nenhuma",
    val lastActionResult: Boolean? = null,
    val lastFoundField: String = "",
    val isDrawingSignature: Boolean = false,
    val logs: List<DiagnosticLogEntry> = emptyList()
)

object AccessibilityAutomationEngine {

    private var activeService: DeliveryAccessibilityService? = null
    private var personRepository: PersonRepository? = null
    private var settingsRepository: SettingsRepository? = null
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    private var scanJob: kotlinx.coroutines.Job? = null
    @Volatile private var cachedSettings: com.example.data.repository.AppSettings? = null
    @Volatile private var lastScannedHash: Int = 0

    private val _state = MutableStateFlow(AutomationState())
    val state = _state.asStateFlow()

    fun init(repository: PersonRepository, settingsRepo: SettingsRepository? = null) {
        this.personRepository = repository
        this.settingsRepository = settingsRepo
        if (settingsRepo != null) {
            scope.launch {
                settingsRepo.getSettings().collect { s ->
                    cachedSettings = s
                }
            }
        }
    }

    fun registerService(service: DeliveryAccessibilityService) {
        activeService = service
        _state.value = _state.value.copy(
            isServiceActive = true,
            logs = addLog("Serviço de acessibilidade ativado com sucesso.", true)
        )
    }

    fun unregisterService() {
        activeService = null
        _state.value = _state.value.copy(
            isServiceActive = false,
            logs = addLog("Serviço de acessibilidade desconectado.", false)
        )
    }

    fun isServiceActive(): Boolean = activeService != null

    private fun addLog(message: String, isSuccess: Boolean, tag: String = "Automação"): List<DiagnosticLogEntry> {
        val current = _state.value.logs
        val newEntry = DiagnosticLogEntry(tag = tag, message = message, isSuccess = isSuccess)
        return (listOf(newEntry) + current).take(50)
    }

    fun onWindowOrContentChanged(packageName: String, className: String, rootNode: AccessibilityNodeInfo?) {
        _state.value = _state.value.copy(
            currentPackageName = packageName,
            currentClassName = className
        )

        // Se o usuário clicou em Limpar, a pesquisa automática fica pausada até clicar no botão de atualizar
        if (_state.value.isPausedScanning) {
            return
        }

        // Se já foi detectado um endereço e está travado nele, não altera automaticamente durante rolagem ou eventos
        if (_state.value.isAddressLocked && _state.value.detectedAddressText.isNotBlank()) {
            return
        }

        if (rootNode != null) {
            // Cancela varredura anterior se ainda pendente para processar o estado mais recente imediatamente
            scanJob?.cancel()
            scanJob = scope.launch {
                scanAndExtractScreenDataInternal(rootNode, packageName)
            }
        }
    }

    fun clearDetectedAddress() {
        scanJob?.cancel()
        lastScannedHash = 0
        _state.value = _state.value.copy(
            detectedAddressText = "",
            isAddressLocked = false,
            isPausedScanning = true,
            matchedPerson = null,
            candidatePersons = emptyList(),
            selectedRecebedor = null,
            availableRecebedores = emptyList(),
            logs = addLog("Pesquisa pausada. Clique nas setas para atualizar e buscar novamente.", true)
        )
    }

    fun rescanCurrentScreen(forceUnlock: Boolean = true) {
        scanJob?.cancel()
        _state.value = _state.value.copy(
            isPausedScanning = false,
            isAddressLocked = if (forceUnlock) false else _state.value.isAddressLocked
        )
        lastScannedHash = 0
        val service = activeService
        if (service == null) {
            _state.value = _state.value.copy(
                logs = addLog("Reescaneamento falhou: Serviço de acessibilidade inativo.", false)
            )
            return
        }

        var targetRootNode: AccessibilityNodeInfo? = null
        var targetPkg = _state.value.currentPackageName

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val windows = service.windows
                if (!windows.isNullOrEmpty()) {
                    for (window in windows) {
                        if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                            val root = window.root
                            if (root != null) {
                                val pkg = root.packageName?.toString() ?: ""
                                if (pkg.isNotBlank() && pkg != service.packageName) {
                                    targetRootNode = root
                                    targetPkg = pkg
                                    break
                                } else if (targetRootNode == null) {
                                    targetRootNode = root
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (targetRootNode == null) {
            targetRootNode = service.rootInActiveWindow
        }

        if (targetRootNode == null) {
            _state.value = _state.value.copy(
                logs = addLog("Reescaneamento falhou: Nenhuma janela ativa encontrada.", false)
            )
            return
        }

        scanJob?.cancel()
        scanJob = scope.launch {
            scanAndExtractScreenDataInternal(targetRootNode, targetPkg)
        }
    }

    /**
     * Varre a árvore de nós para extrair endereço e sugerir pessoas cadastradas
     */
    fun scanAndExtractScreenData(rootNode: AccessibilityNodeInfo, packageName: String) {
        scanJob?.cancel()
        scanJob = scope.launch {
            scanAndExtractScreenDataInternal(rootNode, packageName)
        }
    }

    private suspend fun scanAndExtractScreenDataInternal(rootNode: AccessibilityNodeInfo, packageName: String) {
        val settings = cachedSettings ?: try {
            settingsRepository?.getSettings()?.first()
        } catch (e: Exception) {
            null
        }

        val scanMode = settings?.scanTargetMode ?: "NEXT_DELIVERY"
        var detectedAddress = ""

        val metrics = activeService?.resources?.displayMetrics
        val screenW = metrics?.widthPixels?.toFloat() ?: 1080f
        val screenH = metrics?.heightPixels?.toFloat() ?: 1920f

        if (scanMode == "CUSTOM_RECT" || scanMode == "CUSTOM_AREA") {
            val left = (settings?.scanAreaLeft ?: 0f) * screenW
            val top = (settings?.scanAreaTop ?: 0f) * screenH
            val right = (settings?.scanAreaRight ?: 1f) * screenW
            val bottom = (settings?.scanAreaBottom ?: 1f) * screenH

            val customRectTexts = mutableListOf<String>()
            collectTextsInRect(rootNode, left, top, right, bottom, customRectTexts, screenW, screenH)

            val uniqueTexts = customRectTexts.distinct().filter { 
                it.length > 2 && 
                !it.equals("ASSISTENTE", ignoreCase = true) &&
                !it.startsWith("Próxima", ignoreCase = true)
            }

            if (uniqueTexts.isNotEmpty()) {
                detectedAddress = cleanAddressText(uniqueTexts.joinToString(" "))
            }
        }

        val allTexts = mutableListOf<String>()
        if (detectedAddress.isBlank()) {
            collectAllTexts(rootNode, allTexts)

            if (allTexts.isEmpty()) return

            val currentHash = allTexts.hashCode()
            if (currentHash == lastScannedHash && _state.value.detectedAddressText.isNotBlank()) {
                // Conteúdo inalterado, não repete processamento
                return
            }
            lastScannedHash = currentHash

            if (scanMode == "NEXT_DELIVERY") {
                val nextDeliveryIndex = allTexts.indexOfFirst {
                    it.contains("Proxima Entrega", ignoreCase = true) ||
                    it.contains("Próxima Entrega", ignoreCase = true) ||
                    it.contains("Entrega Atual", ignoreCase = true) ||
                    it.contains("Destinatário", ignoreCase = true) ||
                    it.contains("Destinatario", ignoreCase = true)
                }

                if (nextDeliveryIndex != -1) {
                    for (i in (nextDeliveryIndex + 1)..minOf(nextDeliveryIndex + 5, allTexts.size - 1)) {
                        val text = allTexts[i]
                        if (!text.startsWith("Depois", ignoreCase = true) &&
                            !text.startsWith("Ordem", ignoreCase = true) &&
                            !text.startsWith("Ver objetos", ignoreCase = true) &&
                            looksLikeAddress(text)) {
                            detectedAddress = cleanAddressText(text)
                            break
                        }
                    }
                }
            }

            if (detectedAddress.isBlank()) {
                for (text in allTexts) {
                    if (looksLikeAddress(text)) {
                        val cleaned = cleanAddressText(text)
                        if (cleaned.isNotBlank()) {
                            detectedAddress = cleaned
                            break
                        }
                    }
                }
            }
        }

        val repo = personRepository
        val matched = if (detectedAddress.isNotBlank() && repo != null) {
            repo.findPersonsByAddress(detectedAddress)
        } else {
            emptyList()
        }

        val recebedores = extractAllRecebedores(matched)
        val logMessage = if (detectedAddress.isNotBlank()) {
            if (matched.isNotEmpty()) {
                "Endereço identificado (SALVO: ${matched.first().nome}): $detectedAddress"
            } else {
                "Endereço identificado (NÃO SALVO): $detectedAddress"
            }
        } else {
            "Reescaneamento concluído: Nenhum endereço encontrado."
        }

        val isLocked = detectedAddress.isNotBlank()
        _state.value = _state.value.copy(
            detectedAddressText = detectedAddress,
            isAddressLocked = isLocked,
            matchedPerson = matched.firstOrNull(),
            candidatePersons = matched,
            availableRecebedores = recebedores,
            selectedRecebedor = recebedores.firstOrNull(),
            logs = addLog(logMessage, matched.isNotEmpty())
        )
    }

    private fun collectTextsInRect(node: AccessibilityNodeInfo?, rectLeft: Float, rectTop: Float, rectRight: Float, rectBottom: Float, list: MutableList<String>, screenW: Float, screenH: Float) {
        if (node == null) return
        val nodeRect = android.graphics.Rect()
        node.getBoundsInScreen(nodeRect)

        val isFullScreen = nodeRect.width() > screenW * 0.9f && nodeRect.height() > screenH * 0.9f

        val intersects = nodeRect.left < rectRight && nodeRect.right > rectLeft &&
            nodeRect.top < rectBottom && nodeRect.bottom > rectTop

        if (intersects && !isFullScreen) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length > 2) {
                list.add(text)
            }
            val desc = node.contentDescription?.toString()?.trim()
            if (!desc.isNullOrBlank() && desc.length > 2 && desc != text) {
                list.add(desc)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextsInRect(child, rectLeft, rectTop, rectRight, rectBottom, list, screenW, screenH)
        }
    }

    private fun cleanAddressText(text: String): String {
        val extracted = AddressNormalizer.extractStreetAndNumber(text)
        if (extracted.isNotBlank()) {
            return extracted
        }
        var cleaned = text.trim()
        cleaned = cleaned.replace(Regex("^(Próxima Entrega|Proxima Entrega|Endereço|Endereco|Entrega|Destinatário|Destinatario|Para):?\\s*", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\s*\\d{2}/\\d{2}/\\d{4}.*"), "")
        return cleaned.trimEnd('-', ' ', ',', '.')
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && text.length > 3) {
            list.add(text)
        }
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrBlank() && desc.length > 3 && desc != text) {
            list.add(desc)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllTexts(child, list)
        }
    }

    private fun looksLikeAddress(text: String): Boolean {
        if (text.length < 5) return false
        val upper = text.uppercase(Locale.ROOT)
        
        // Evita botões ou comandos de navegação
        if (upper.startsWith("VER ") || upper.startsWith("VOLTAR") || upper.startsWith("CONFIRMAR") || upper.startsWith("CANCELAR")) {
            return false
        }

        val triggers = listOf(
            "RUA", "R.", "AVENIDA", "AV.", "AV ", "CORONEL", "CEL.", "ALAMEDA", "AL.",
            "PRAÇA", "PRACA", "PCA.", "TRAVESSA", "TV.", "RODOVIA", "ROD.", "ESTRADA", "EST.",
            "BECO", "VIELA", "LOTEAMENTO", "LOT.", "RESIDENCIAL", "RES.", "CONDOMINIO", "CONDOMÍNIO",
            "JD", "JARDIM", "VL", "VILA", "BAIRRO", "CENTRO", "Nº", "N°", "NUMERO", "NUM.", "KM"
        )
        val hasTrigger = triggers.any { upper.contains(it) }
        val hasDigits = text.any { it.isDigit() }
        
        // Tem logradouro/bairro ou formato de rua com número (ex: "São João, 120" ou "Brasil, 450")
        return (hasTrigger && hasDigits) || (hasTrigger && upper.length > 8) || (hasDigits && (text.contains(",") || text.contains("-")) && text.length > 8)
    }

    /**
     * Preenche os campos de Nome e Documento na árvore da tela atual
     */
    fun fillFields(name: String, document: String): FillResult {
        val service = activeService
        if (service == null) {
            val log = "Falha: Serviço de acessibilidade não está ativado."
            _state.value = _state.value.copy(
                lastAction = "Preenchimento de campos",
                lastActionResult = false,
                logs = addLog(log, false)
            )
            return FillResult(nameFilled = false, documentFilled = false, message = log)
        }

        val rootNode = service.rootInActiveWindow
        if (rootNode == null) {
            val log = "Falha: Não foi possível obter a tela ativa."
            _state.value = _state.value.copy(
                lastAction = "Preenchimento de campos",
                lastActionResult = false,
                logs = addLog(log, false)
            )
            return FillResult(nameFilled = false, documentFilled = false, message = log)
        }

        var nameFilled = false
        var docFilled = false
        var foundFieldDesc = ""

        try {
            val editableNodes = mutableListOf<AccessibilityNodeInfo>()
            findEditableNodes(rootNode, editableNodes)

            val profile = ExternalAppProfile.GENERIC_DEFAULT

            for (node in editableNodes) {
                val hint = node.hintText?.toString()?.lowercase(Locale.ROOT) ?: ""
                val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
                val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
                val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

                val combinedMetadata = "$hint $desc $viewId $text"

                // Tentar identificar Nome
                if (!nameFilled && name.isNotBlank() && matchesField(combinedMetadata, profile.nameFieldHints)) {
                    if (setTextToNode(node, name)) {
                        nameFilled = true
                        foundFieldDesc += "Nome "
                    }
                }
                // Tentar identificar Documento
                else if (!docFilled && document.isNotBlank() && matchesField(combinedMetadata, profile.documentFieldHints)) {
                    if (setTextToNode(node, document)) {
                        docFilled = true
                        foundFieldDesc += "Documento "
                    }
                }
            }

            // Fallback: se existirem exatamente 2 campos editáveis e nenhum identificou por tag, preencher 1º como Nome e 2º como Doc
            if (!nameFilled && !docFilled && editableNodes.size >= 2) {
                if (name.isNotBlank()) {
                    nameFilled = setTextToNode(editableNodes[0], name)
                }
                if (document.isNotBlank()) {
                    docFilled = setTextToNode(editableNodes[1], document)
                }
                foundFieldDesc = "Campos sequenciais (1º Nome, 2º Doc)"
            } else if (!nameFilled && editableNodes.size == 1 && name.isNotBlank()) {
                nameFilled = setTextToNode(editableNodes[0], name)
                foundFieldDesc = "Único campo detectado (Nome)"
            }

            val msg = when {
                nameFilled && docFilled -> "✓ Nome e Documento preenchidos com sucesso."
                nameFilled -> "✓ Nome preenchido. ⚠ Campo de documento não encontrado."
                docFilled -> "✓ Documento preenchido. ⚠ Campo de nome não encontrado."
                else -> "⚠ Nenhum campo compatível foi identificado na tela."
            }

            val isSuccess = nameFilled || docFilled
            _state.value = _state.value.copy(
                lastAction = "Preenchimento de formulário",
                lastActionResult = isSuccess,
                lastFoundField = foundFieldDesc.trim(),
                logs = addLog(msg, isSuccess)
            )

            return FillResult(nameFilled = nameFilled, documentFilled = docFilled, message = msg)
        } catch (e: Exception) {
            val errorMsg = "Erro ao preencher formulário: ${e.localizedMessage}"
            return FillResult(nameFilled = false, documentFilled = false, message = errorMsg)
        }
    }

    /**
     * Preenche Nome e Documento e em seguida reproduz os gestos da assinatura cadastrada
     */
    fun fillFieldsAndDrawSignature(
        name: String,
        document: String,
        signatureJson: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val fillResult = fillFields(name, document)
        if (signatureJson.isBlank()) {
            onResult(fillResult.nameFilled || fillResult.documentFilled, fillResult.message + " (Sem assinatura salva)")
            return
        }

        val signatureData = SignatureData.fromJson(signatureJson)
        if (signatureData == null || signatureData.strokes.isEmpty()) {
            onResult(fillResult.nameFilled || fillResult.documentFilled, fillResult.message + " (Assinatura vazia)")
            return
        }

        // Aguardar pequeno intervalo para estabilização do formulário
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            dispatchSignatureGestures(signatureData) { gestureSuccess, gestureMsg ->
                val finalMsg = if (fillResult.nameFilled || fillResult.documentFilled) {
                    if (gestureSuccess) "✓ Campos preenchidos e assinatura desenhada com sucesso!"
                    else "✓ Campos preenchidos! Assinatura: $gestureMsg"
                } else {
                    if (gestureSuccess) "✓ Assinatura desenhada na tela!"
                    else gestureMsg
                }
                onResult(fillResult.nameFilled || fillResult.documentFilled || gestureSuccess, finalMsg)
            }
        }, 400L)
    }

    private fun matchesField(metadata: String, hints: List<String>): Boolean {
        return hints.any { metadata.contains(it) }
    }

    private fun findEditableNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditableNodes(child, list)
        }
    }

    private fun setTextToNode(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isVisibleToUser) return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        var result = false
        try {
            result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } catch (e: Exception) {
            result = false
        }
        if (!result) {
            try {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                result = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            } catch (ignored: Exception) {}
        }
        return result
    }

    /**
     * Tenta reproduzir os traços da assinatura através do AccessibilityService em velocidade Ultra Lenta
     */
     fun dispatchSignatureGestures(
        signature: SignatureData,
        speedMode: String = "ULTRA_SLOW",
        onResult: (Boolean, String) -> Unit
    ) {
        val service = activeService
        if (service == null) {
            val msg = "Serviço de acessibilidade desativado. Ative nas Configurações do Android."
            _state.value = _state.value.copy(logs = addLog(msg, false))
            onResult(false, msg)
            return
        }

        if (signature.strokes.isEmpty()) {
            val msg = "Assinatura vazia. Nenhum traço para reproduzir."
            onResult(false, msg)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val msg = "Reprodução de gestos requer Android 7.0 (API 24) ou superior."
            onResult(false, msg)
            return
        }

        val displayMetrics = service.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val bounds = Rect()
        val rootNode = service.rootInActiveWindow
        if (rootNode != null) {
            val signatureNode = findSignatureAreaNode(rootNode)
            if (signatureNode != null) {
                signatureNode.getBoundsInScreen(bounds)
            }
        }

        // Se não encontrou nó explícito ou se as dimensões forem muito pequenas/incompatíveis, usar a área padrão do quadro de assinatura dos apps de entrega
        if (bounds.width() < (screenWidth * 0.3f) || bounds.height() < (screenHeight * 0.12f)) {
            bounds.set(
                (screenWidth * 0.08f).toInt(),
                (screenHeight * 0.42f).toInt(),
                (screenWidth * 0.92f).toInt(),
                (screenHeight * 0.82f).toInt()
            )
        }

        // 1. Encontrar a caixa delimitadora (Bounding Box) real da assinatura desenhada para remover todo o espaço em branco ao redor
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var hasPoints = false

        for (stroke in signature.strokes) {
            for (pt in stroke.points) {
                if (pt.x < minX) minX = pt.x
                if (pt.x > maxX) maxX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.y > maxY) maxY = pt.y
                hasPoints = true
            }
        }

        val origWidth: Float
        val origHeight: Float
        val pointOffsetX: Float
        val pointOffsetY: Float

        if (hasPoints && (maxX - minX) > 10f && (maxY - minY) > 10f) {
            // Adicionar uma pequena margem interna de segurança de 4% para a assinatura não encostar perigosamente nas bordas
            val paddingX = (maxX - minX) * 0.04f
            val paddingY = (maxY - minY) * 0.04f
            
            val cropMinX = (minX - paddingX).coerceAtLeast(0f)
            val cropMaxX = (maxX + paddingX).coerceAtMost(signature.canvasWidth)
            val cropMinY = (minY - paddingY).coerceAtLeast(0f)
            val cropMaxY = (maxY + paddingY).coerceAtMost(signature.canvasHeight)
            
            origWidth = cropMaxX - cropMinX
            origHeight = cropMaxY - cropMinY
            pointOffsetX = cropMinX
            pointOffsetY = cropMinY
        } else {
            // Caso não tenha pontos suficientes, usa o canvas inteiro como fallback
            origWidth = maxOf(10f, signature.canvasWidth)
            origHeight = maxOf(10f, signature.canvasHeight)
            pointOffsetX = 0f
            pointOffsetY = 0f
        }

        // Aumentamos o aproveitamento da tela de destino para 92% (margem menor para assinatura ficar bem grande e clara)
        val targetWidth = bounds.width().toFloat() * 0.92f
        val targetHeight = bounds.height().toFloat() * 0.92f

        val scaleX = targetWidth / origWidth
        val scaleY = targetHeight / origHeight
        val scale = minOf(scaleX, scaleY).coerceAtLeast(0.1f)

        val scaledW = origWidth * scale
        val scaledH = origHeight * scale
        val offsetX = bounds.left + (bounds.width() - scaledW) / 2f
        val offsetY = bounds.top + (bounds.height() - scaledH) / 2f

        val paths = mutableListOf<Path>()
        val strokeDurations = mutableListOf<Long>()

        // Fatores de tempo por ponto de acordo com a velocidade desejada (inclui modo Ultra Lento mais cadenciado)
        val (msPerPoint, minDuration, maxDuration, interStrokeDelay) = when (speedMode) {
            "ULTRA_SLOW" -> Quad(32L, 400L, 1600L, 100L) // Ultra devagar (desenho realista e cadenciado)
            "SLOW" -> Quad(18L, 250L, 950L, 60L)       // Lento e suave (padrão)
            "FAST" -> Quad(6L, 80L, 280L, 20L)          // Rápido
            else -> Quad(12L, 160L, 550L, 40L)          // Normal
        }

        for (stroke in signature.strokes) {
            val originalPts = stroke.points
            if (originalPts.isEmpty()) continue

            // Simplificação e filtragem inteligente de pontos de jitter muito próximos para evitar traço quadriculado/serrilhado
            val pts = mutableListOf<Point>()
            pts.add(originalPts.first())
            for (i in 1 until originalPts.size) {
                val lastAdded = pts.last()
                val current = originalPts[i]
                val dx = current.x - lastAdded.x
                val dy = current.y - lastAdded.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist >= 3.0f || i == originalPts.size - 1) {
                    pts.add(current)
                }
            }

            val path = Path()
            val first = pts.first()
            val startX = (offsetX + ((first.x - pointOffsetX) * scale)).coerceIn(bounds.left.toFloat() + 4f, bounds.right.toFloat() - 4f)
            val startY = (offsetY + ((first.y - pointOffsetY) * scale)).coerceIn(bounds.top.toFloat() + 4f, bounds.bottom.toFloat() - 4f)
            path.moveTo(startX, startY)

            if (pts.size == 1) {
                // Ponto ou pingo do i
                path.lineTo(startX + 1f, startY + 1f)
            } else if (pts.size == 2) {
                val p = pts[1]
                val px = (offsetX + ((p.x - pointOffsetX) * scale)).coerceIn(bounds.left.toFloat() + 4f, bounds.right.toFloat() - 4f)
                val py = (offsetY + ((p.y - pointOffsetY) * scale)).coerceIn(bounds.top.toFloat() + 4f, bounds.bottom.toFloat() - 4f)
                path.lineTo(px, py)
            } else {
                for (i in 1 until pts.size - 1) {
                    val p0 = pts[i]
                    val p1 = pts[i + 1]
                    val p0x = (offsetX + ((p0.x - pointOffsetX) * scale)).coerceIn(bounds.left.toFloat() + 4f, bounds.right.toFloat() - 4f)
                    val p0y = (offsetY + ((p0.y - pointOffsetY) * scale)).coerceIn(bounds.top.toFloat() + 4f, bounds.bottom.toFloat() - 4f)
                    val p1x = (offsetX + ((p1.x - pointOffsetX) * scale)).coerceIn(bounds.left.toFloat() + 4f, bounds.right.toFloat() - 4f)
                    val p1y = (offsetY + ((p1.y - pointOffsetY) * scale)).coerceIn(bounds.top.toFloat() + 4f, bounds.bottom.toFloat() - 4f)
                    val midX = (p0x + p1x) / 2f
                    val midY = (p0y + p1y) / 2f
                    path.quadTo(p0x, p0y, midX, midY)
                }
                val last = pts.last()
                val lastX = (offsetX + ((last.x - pointOffsetX) * scale)).coerceIn(bounds.left.toFloat() + 4f, bounds.right.toFloat() - 4f)
                val lastY = (offsetY + ((last.y - pointOffsetY) * scale)).coerceIn(bounds.top.toFloat() + 4f, bounds.bottom.toFloat() - 4f)
                path.lineTo(lastX, lastY)
            }
            paths.add(path)
            
            // Duração proporcional à velocidade escolhida
            val duration = (pts.size * msPerPoint).coerceIn(minDuration, maxDuration)
            strokeDurations.add(duration)
        }

        if (paths.isEmpty()) {
            val msg = "Nenhum traço válido encontrado na assinatura."
            onResult(false, msg)
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        _state.value = _state.value.copy(isDrawingSignature = true)

        // Executar traços sequencialmente para compatibilidade universal (Samsung, Xiaomi, Motorola, etc.)
        fun dispatchStroke(index: Int) {
            if (index >= paths.size) {
                val successMsg = "✓ Assinatura desenhada com sucesso na tela!"
                _state.value = _state.value.copy(
                    isDrawingSignature = false,
                    lastAction = "Desenho de assinatura",
                    lastActionResult = true,
                    logs = addLog(successMsg, true)
                )
                onResult(true, successMsg)
                return
            }

            val strokePath = paths[index]
            val duration = strokeDurations[index]

            try {
                val gestureBuilder = GestureDescription.Builder()
                gestureBuilder.addStroke(
                    GestureDescription.StrokeDescription(strokePath, 0L, duration)
                )
                val gesture = gestureBuilder.build()

                val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        mainHandler.postDelayed({
                            dispatchStroke(index + 1)
                        }, interStrokeDelay)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        // Se o traço for cancelado, continua para o próximo para não travar a assinatura inteira
                        mainHandler.postDelayed({
                            dispatchStroke(index + 1)
                        }, interStrokeDelay)
                    }
                }, null)

                if (!dispatched) {
                    // Tentar o próximo traço se este falhar
                    mainHandler.postDelayed({
                        dispatchStroke(index + 1)
                    }, interStrokeDelay)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isDrawingSignature = false)
                onResult(false, "Erro no traço ${index + 1}: ${e.localizedMessage}")
            }
        }

        dispatchStroke(0)
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun findSignatureAreaNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val profile = ExternalAppProfile.GENERIC_DEFAULT

        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
        val className = node.className?.toString()?.lowercase(Locale.ROOT) ?: ""

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Rótulos de texto simples (TextView sem filhos e com pouca altura) não são telas de desenho
        val isLabelOnly = className.contains("textview", ignoreCase = true) && node.childCount == 0 && bounds.height() < 120

        val metadata = "$desc $viewId $text $className"
        val isMatch = matchesField(metadata, profile.signatureFieldHints) ||
            viewId.contains("signature", ignoreCase = true) ||
            viewId.contains("canvas", ignoreCase = true) ||
            viewId.contains("pad", ignoreCase = true) ||
            viewId.contains("rubrica", ignoreCase = true) ||
            desc.contains("assinatura", ignoreCase = true) ||
            desc.contains("assine", ignoreCase = true) ||
            desc.contains("desenhe", ignoreCase = true) ||
            desc.contains("canvas", ignoreCase = true) ||
            text.contains("assine", ignoreCase = true) ||
            text.contains("assinatura", ignoreCase = true)

        if (isMatch && !isLabelOnly && bounds.width() >= 150 && bounds.height() >= 100) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSignatureAreaNode(child)
            if (found != null) return found
        }

        // Caso seja uma correspondência mas de tamanho marginal, retorna como plano B
        if (isMatch && !isLabelOnly) {
            return node
        }

        return null
    }

    fun extractAllRecebedores(persons: List<Person>): List<Recebedor> {
        val list = mutableListOf<Recebedor>()
        for (p in persons) {
            if (p.nome.isNotBlank()) {
                list.add(
                    Recebedor(
                        id = "p_${p.id}_main",
                        nome = p.nome,
                        documento = p.documento,
                        assinatura = p.assinatura
                    )
                )
            }
            val extras = Recebedor.listFromJson(p.coRecebedoresJson)
            for (r in extras) {
                if (r.nome.isNotBlank()) {
                    list.add(
                        Recebedor(
                            id = "p_${p.id}_co_${r.id}",
                            nome = r.nome,
                            documento = r.documento,
                            assinatura = r.assinatura
                        )
                    )
                }
            }
        }
        return list
    }

    fun selectRecebedor(recebedor: Recebedor) {
        _state.value = _state.value.copy(selectedRecebedor = recebedor)
    }

    fun setMatchedPersonDirect(person: Person) {
        val recebedores = extractAllRecebedores(listOf(person))
        val addressToSet = person.endereco.ifBlank { _state.value.detectedAddressText }
        _state.value = _state.value.copy(
            detectedAddressText = addressToSet,
            isAddressLocked = true,
            matchedPerson = person,
            candidatePersons = listOf(person),
            availableRecebedores = recebedores,
            selectedRecebedor = recebedores.firstOrNull(),
            logs = addLog("Destinatário selecionado manualmente: ${person.nome} (${if (person.endereco.isNotBlank()) person.endereco else "Sem endereço"})", true)
        )
    }

    fun setDetectedAddressDirect(address: String) {
        scope.launch {
            val repo = personRepository
            val matched = if (address.isNotBlank() && repo != null) {
                repo.findPersonsByAddress(address)
            } else {
                emptyList()
            }
            val recebedores = extractAllRecebedores(matched)
            _state.value = _state.value.copy(
                detectedAddressText = address,
                isAddressLocked = address.isNotBlank(),
                matchedPerson = matched.firstOrNull(),
                candidatePersons = matched,
                availableRecebedores = recebedores,
                selectedRecebedor = recebedores.firstOrNull(),
                logs = addLog(
                    if (matched.isNotEmpty()) "Endereço selecionado (SALVO: ${matched.first().nome}): $address"
                    else "Endereço selecionado (NÃO SALVO): $address",
                    matched.isNotEmpty()
                )
            )
        }
    }
}
