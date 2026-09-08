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
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.example.data.local.entity.Person
import com.example.data.model.ExternalAppProfile
import com.example.data.model.SignatureData
import com.example.data.model.Recebedor
import com.example.data.model.Point
import com.example.data.repository.PersonRepository
import com.example.data.repository.SettingsRepository
import com.example.util.AddressNormalizer
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val REGEX_CLEAN_HEADER = Regex("""^(?:Próxima\s+Entrega|Proxima\s+Entrega|Entrega\s+Atual|Endereço|Endereco|Entrega|Destinatário|Destinatario|Para|DETALHES|Detalhes|DETALHE|Detalhe|Depois|Objeto)[:\s-]+""", RegexOption.IGNORE_CASE)
private val REGEX_CLEAN_DATE = Regex("\\s*\\d{2}/\\d{2}/\\d{4}.*")

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

    /**
     * Chamado quando o usuário clica ou seleciona um item na tela (ex: marca um checkbox em 'Seleção' ou clica em uma entrega)
     */
    fun onNodeClickedOrSelected(packageName: String, className: String, node: AccessibilityNodeInfo) {
        if (_state.value.isPausedScanning) return
        val myPkg = activeService?.packageName ?: ""
        if (packageName == myPkg) return

        scope.launch {
            try {
                // 1. Tenta extrair endereço diretamente do nó clicado
                val selfText = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()
                if (selfText.isNotBlank()) {
                    val cleanedSelf = cleanAddressText(selfText)
                    if (looksLikeAddress(cleanedSelf) || AddressNormalizer.parseAddressComponents(cleanedSelf).number.isNotBlank()) {
                        applyDetectedAddress(cleanedSelf, isUserSelection = true)
                        return@launch
                    }
                }

                // 2. Se clicou em um checkbox, card, botão de linha ou texto próximo, sobe até o contêiner do item
                var current: AccessibilityNodeInfo? = node
                var depth = 0
                while (current != null && depth < 4) {
                    val texts = mutableListOf<String>()
                    collectAllTexts(current, texts)
                    val preferredIdx = texts.indexOfFirst {
                        it.contains("DETALHES", ignoreCase = true) ||
                        it.contains("Detalhes", ignoreCase = true) ||
                        it.contains("Depois", ignoreCase = true) ||
                        it.contains("Próxima", ignoreCase = true) ||
                        it.contains("Entrega", ignoreCase = true)
                    }
                    val addr = extractBestAddressFromTexts(texts, preferredIdx, allowDepois = true)
                    if (addr.isNotBlank()) {
                        applyDetectedAddress(addr, isUserSelection = true)
                        return@launch
                    }
                    current = current.parent
                    depth++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
            // Cancela varredura anterior se ainda pendente para processar o estado mais recente
            scanJob?.cancel()
            scanJob = scope.launch {
                kotlinx.coroutines.delay(180) // Debounce rapid accessibility events during scrolling/typing
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

    fun resetAndClearAfterSignature(delayBeforeResumeMs: Long = 5000L) {
        scanJob?.cancel()
        lastScannedHash = 0
        // Limpa o balão imediatamente e pausa o escaneamento durante o atraso
        _state.value = _state.value.copy(
            detectedAddressText = "",
            isAddressLocked = false,
            isPausedScanning = true,
            matchedPerson = null,
            candidatePersons = emptyList(),
            selectedRecebedor = null,
            availableRecebedores = emptyList(),
            logs = addLog("Assinatura concluída. Balão limpo! Aguardando ${delayBeforeResumeMs / 1000}s para pesquisar o próximo endereço...", true)
        )

        // Aguarda os 5 segundos e reativa a detecção/pesquisa
        scope.launch {
            kotlinx.coroutines.delay(delayBeforeResumeMs)
            lastScannedHash = 0
            _state.value = _state.value.copy(
                isPausedScanning = false,
                isAddressLocked = false,
                logs = addLog("Detecção ativada. Pronto para escanear a próxima entrega!", true)
            )
            rescanCurrentScreen(forceUnlock = true)
        }
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
                it.isNotBlank() && 
                !it.equals("ASSISTENTE", ignoreCase = true) &&
                !it.startsWith("Próxima", ignoreCase = true)
            }

            if (uniqueTexts.isNotEmpty()) {
                detectedAddress = cleanAddressText(uniqueTexts.joinToString(" "))
            }
        }

        val allTexts = mutableListOf<String>()
        if (detectedAddress.isBlank()) {
            // 1º Tentar extrair endereço de um item explicitamente marcado/selecionado (ex: checkbox na tela 'Seleção')
            val checkedAddress = findCheckedOrSelectedAddress(rootNode)
            if (!checkedAddress.isNullOrBlank()) {
                detectedAddress = checkedAddress
            }

            if (detectedAddress.isBlank()) {
                collectAllTexts(rootNode, allTexts)

                if (allTexts.isEmpty()) return

                val currentHash = allTexts.hashCode()
                if (currentHash == lastScannedHash && _state.value.detectedAddressText.isNotBlank()) {
                    // Conteúdo inalterado, não repete processamento
                    return
                }
                lastScannedHash = currentHash

                val nextDeliveryIndex = if (scanMode == "NEXT_DELIVERY") {
                    allTexts.indexOfFirst {
                        it.contains("Proxima Entrega", ignoreCase = true) ||
                        it.contains("Próxima Entrega", ignoreCase = true) ||
                        it.contains("Entrega Atual", ignoreCase = true) ||
                        it.contains("DETALHES", ignoreCase = true) ||
                        it.contains("Detalhes", ignoreCase = true) ||
                        it.contains("Destinatário", ignoreCase = true) ||
                        it.contains("Destinatario", ignoreCase = true)
                    }
                } else -1

                detectedAddress = extractBestAddressFromTexts(allTexts, nextDeliveryIndex)
            }
        }

        applyDetectedAddress(detectedAddress, isUserSelection = false)
    }

    private fun findCheckedOrSelectedAddress(rootNode: AccessibilityNodeInfo): String? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectCheckedOrSelectedNodes(rootNode, candidates)
        for (node in candidates) {
            val selfText = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()
            if (selfText.isNotBlank()) {
                val cleaned = cleanAddressText(selfText)
                if (looksLikeAddress(cleaned) || AddressNormalizer.parseAddressComponents(cleaned).number.isNotBlank()) {
                    return cleaned
                }
            }
            var current: AccessibilityNodeInfo? = node.parent
            var depth = 0
            while (current != null && depth < 4) {
                val containerTexts = mutableListOf<String>()
                collectAllTexts(current, containerTexts)
                val preferredIdx = containerTexts.indexOfFirst {
                    it.contains("DETALHES", ignoreCase = true) ||
                    it.contains("Detalhes", ignoreCase = true) ||
                    it.contains("Endereço", ignoreCase = true) ||
                    it.contains("Endereco", ignoreCase = true) ||
                    it.contains("Próxima", ignoreCase = true)
                }
                val addr = extractBestAddressFromTexts(containerTexts, preferredIdx, allowDepois = true)
                if (addr.isNotBlank()) {
                    return addr
                }
                current = current.parent
                depth++
            }
        }
        return null
    }

    private fun collectCheckedOrSelectedNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isChecked || node.isSelected) {
            list.add(node)
        }
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            collectCheckedOrSelectedNodes(child, list)
            try {
                if (!child.isChecked && !child.isSelected) {
                    child.recycle()
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun applyDetectedAddress(detectedAddress: String, isUserSelection: Boolean = false) {
        if (detectedAddress.isBlank()) return

        val repo = personRepository
        val matched = if (repo != null) {
            repo.findPersonsByAddress(detectedAddress)
        } else {
            emptyList()
        }

        // Se o endereço detectado na tela não tiver o número, mas o registro cadastrado tiver, complementa com o número
        val finalDetectedAddress = if (matched.isNotEmpty()) {
            val firstMatched = matched.first()
            val parsedDetected = AddressNormalizer.parseAddressComponents(detectedAddress)
            if (parsedDetected.number.isBlank() && firstMatched.numero.isNotBlank()) {
                val base = parsedDetected.street.ifBlank { detectedAddress }
                if (firstMatched.complemento.isNotBlank()) {
                    "$base, ${firstMatched.numero} - ${firstMatched.complemento}"
                } else {
                    "$base, ${firstMatched.numero}"
                }
            } else {
                detectedAddress
            }
        } else {
            detectedAddress
        }

        val recebedores = extractAllRecebedores(matched)
        val selectionTag = if (isUserSelection) "[SELECIONADO] " else ""
        val logMessage = if (finalDetectedAddress.isNotBlank()) {
            if (matched.isNotEmpty()) {
                "${selectionTag}Endereço identificado (SALVO: ${matched.first().nome}): $finalDetectedAddress"
            } else {
                "${selectionTag}Endereço identificado (NÃO SALVO): $finalDetectedAddress"
            }
        } else {
            "Reescaneamento concluído: Nenhum endereço encontrado."
        }

        val isLocked = finalDetectedAddress.isNotBlank()
        _state.value = _state.value.copy(
            detectedAddressText = finalDetectedAddress,
            isAddressLocked = isLocked,
            matchedPerson = matched.firstOrNull(),
            candidatePersons = matched,
            availableRecebedores = recebedores,
            selectedRecebedor = recebedores.firstOrNull(),
            logs = addLog(logMessage, matched.isNotEmpty())
        )

        if (isUserSelection) {
            val service = activeService
            if (service != null) {
                withContext(Dispatchers.Main) {
                    try {
                        val status = if (matched.isNotEmpty()) "Destinatário encontrado!" else "Endereço pronto para salvar"
                        Toast.makeText(service, "✓ $finalDetectedAddress\n$status", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                }
            }
        }
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

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            collectTextsInRect(child, rectLeft, rectTop, rectRight, rectBottom, list, screenW, screenH)
            try {
                child.recycle()
            } catch (_: Exception) {}
        }
    }

    private fun cleanAddressText(text: String): String {
        val extracted = AddressNormalizer.extractStreetAndNumber(text)
        if (extracted.isNotBlank()) {
            return extracted
        }
        var cleaned = text.trim()
        cleaned = REGEX_CLEAN_HEADER.replace(cleaned, "")
        cleaned = REGEX_CLEAN_DATE.replace(cleaned, "")
        return cleaned.trimEnd('-', ' ', ',', '.')
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            list.add(text)
        }
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrBlank() && desc != text) {
            list.add(desc)
        }
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            collectAllTexts(child, list)
            try {
                child.recycle()
            } catch (_: Exception) {}
        }
    }

    private fun isHouseNumber(candidate: String): Boolean {
        val t = candidate.trim()
        if (t.isBlank() || t.length > 30) return false
        val upper = t.uppercase(Locale.ROOT)
        
        // Ignora botões, ações, status ou comandos de entrega
        if (upper.startsWith("VER") || upper.startsWith("VOLTAR") || upper.startsWith("CONFIRMAR") || 
            upper.startsWith("CANCELAR") || upper.startsWith("DEPOIS") || upper.startsWith("ORDEM") ||
            upper.startsWith("PEDIDO") || upper.startsWith("OBJETO") || upper.startsWith("TENTATIVA")) {
            return false
        }
        
        // Ignora datas (ex: 04/09/2026 ou 04/09)
        if (Regex("""\b\d{1,2}/\d{1,2}(?:/\d{2,4})?\b""").containsMatchIn(t)) return false
        
        // Ignora CEP (ex: 12345-678)
        if (Regex("""\b\d{5}-?\d{3}\b""").matches(t)) return false
        
        // Ignora telefones
        if (Regex("""\b(?:\(?\d{2}\)?\s*)?9?\d{4}-?\d{4}\b""").containsMatchIn(t)) return false
        
        // Ignora CPF/CNPJ
        if (Regex("""\b\d{3}\.\d{3}\.\d{3}-\d{2}\b""").containsMatchIn(t)) return false
        
        val isExplicitNoNumber = upper == "S/N" || upper == "SN" || upper.contains("SEM NUMERO") || upper.contains("SEM NÚMERO")
        if (isExplicitNoNumber) return true
        
        val hasLotQd = Regex("""(?i)\b(?:lote|lt|quadra|qd)\s*\d+""").containsMatchIn(t)
        if (hasLotQd) return true
        
        val numMatch = Regex("""(?i)(?:^|[\s,-])(?:n[º°\.]|numero|num)?\s*(\d{1,6}[A-Za-z]?(?:-[A-Za-z0-9]+)?)""").find(t)
        return numMatch != null
    }

    private fun looksLikeComplement(text: String): Boolean {
        val t = text.trim()
        if (t.isBlank() || t.length > 30) return false
        val upper = t.uppercase(Locale.ROOT)
        val compTriggers = listOf(
            "APTO", "APT", "AP", "APARTAMENTO", "BLOCO", "BLO", "BL", "TORRE", "TOR",
            "CASA", "CS", "SALA", "SL", "CONJUNTO", "CONJ", "CJ", "QUADRA", "QD",
            "LOTE", "LT", "ANDAR", "PAVIMENTO", "PAV", "FUNDOS", "FDS", "FRENTE",
            "SOBRADO", "TÉRREO", "TERREO", "GALPÃO", "GALPAO", "SUBSOLO"
        )
        return compTriggers.any { upper.startsWith(it) || upper.contains(" $it") }
    }

    private fun extractBestAddressFromTexts(texts: List<String>, preferredIndex: Int = -1, allowDepois: Boolean = false): String {
        if (texts.isEmpty()) return ""

        val indicesToSearch = if (preferredIndex in texts.indices) {
            val range = (preferredIndex + 1)..minOf(preferredIndex + 6, texts.size - 1)
            range.toList() + texts.indices.filterNot { it in range || it == preferredIndex }
        } else {
            texts.indices.toList()
        }

        var fallbackAddressWithoutNumber = ""

        for (i in indicesToSearch) {
            val rawText = texts[i].trim()
            if (rawText.length < 4) continue
            if (rawText.startsWith("Ordem", ignoreCase = true) ||
                rawText.startsWith("Ver objetos", ignoreCase = true) ||
                rawText.startsWith("Ver mapa", ignoreCase = true) ||
                rawText.startsWith("Navegar", ignoreCase = true) ||
                rawText.startsWith("Unidade", ignoreCase = true) ||
                rawText.startsWith("Total de objetos", ignoreCase = true) ||
                rawText.equals("ENTREGUE", ignoreCase = true) ||
                rawText.equals("NÃO ENTREGUE", ignoreCase = true) ||
                rawText.equals("DETALHES:", ignoreCase = true) ||
                rawText.equals("DETALHES", ignoreCase = true) ||
                rawText.equals("Próxima Entrega:", ignoreCase = true) ||
                rawText.equals("Próxima Entrega", ignoreCase = true)) {
                continue
            }

            if (!allowDepois && (rawText.startsWith("Depois", ignoreCase = true) || rawText.startsWith("Depois:", ignoreCase = true))) {
                continue
            }

            val text = REGEX_CLEAN_HEADER.replace(rawText, "").trim()
            if (text.length < 5) continue

            if (looksLikeAddress(text)) {
                // 1. O próprio nó já contém logradouro e número?
                val parsed = AddressNormalizer.parseAddressComponents(text)
                if (parsed.number.isNotBlank()) {
                    val cleaned = cleanAddressText(text)
                    if (cleaned.isNotBlank()) {
                        return cleaned
                    }
                }

                // 2. O nó tem o logradouro, mas o número está nos nós seguintes
                for (j in (i + 1)..minOf(i + 3, texts.size - 1)) {
                    val nextText = texts[j].trim()
                    if (isHouseNumber(nextText)) {
                        var candidate = "$text, $nextText"
                        if (j + 1 < texts.size) {
                            val followingText = texts[j + 1].trim()
                            if (looksLikeComplement(followingText)) {
                                candidate = "$candidate - $followingText"
                            }
                        }
                        val combinedCleaned = cleanAddressText(candidate)
                        val combinedParsed = AddressNormalizer.parseAddressComponents(combinedCleaned)
                        if (combinedParsed.number.isNotBlank()) {
                            return combinedCleaned
                        }
                    }
                }

                if (fallbackAddressWithoutNumber.isBlank()) {
                    val cleaned = cleanAddressText(text)
                    if (cleaned.isNotBlank()) {
                        fallbackAddressWithoutNumber = cleaned
                    }
                }
            }
        }

        return fallbackAddressWithoutNumber
    }

    private fun looksLikeAddress(text: String): Boolean {
        if (text.length < 4) return false
        val upper = text.uppercase(Locale.ROOT)
        
        // Evita botões ou comandos de navegação
        if (upper.startsWith("VER ") || upper.startsWith("VOLTAR") || upper.startsWith("CONFIRMAR") || upper.startsWith("CANCELAR")) {
            return false
        }

        // Formato DDA / LOEC com ponto-e-vírgula (ex: "bela vista;José Augusto filho;200...")
        if (text.contains(";") && text.split(";").count { it.isNotBlank() } >= 2) {
            return true
        }

        // Padrão de Cidade/UF no final (ex: "- Monte Santo de Minas/MG" ou "Sem Bairro - Monte Santo de Minas/MG")
        if (Regex("""(?i)[,\-]\s*[A-Za-zÀ-ÿ\s\.\']{2,40}\s*/\s*(?:AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)\b""").containsMatchIn(text)) {
            return true
        }

        val triggers = listOf(
            "RUA", "R.", "AVENIDA", "AV.", "AV ", "CORONEL", "CEL.", "ALAMEDA", "AL.",
            "PRAÇA", "PRACA", "PCA.", "TRAVESSA", "TV.", "RODOVIA", "ROD.", "ESTRADA", "EST.",
            "BECO", "VIELA", "LOTEAMENTO", "LOT.", "RESIDENCIAL", "RES.", "CONDOMINIO", "CONDOMÍNIO",
            "JD", "JARDIM", "VL", "VILA", "BAIRRO", "CENTRO", "Nº", "N°", "NUMERO", "NUM.", "KM",
            "SEM BAIRRO", "SEM_BAIRRO"
        )
        val hasTrigger = triggers.any { upper.contains(it) }
        val hasDigits = text.any { it.isDigit() }
        
        // Tem logradouro/bairro ou formato de rua com número (ex: "São João, 120" ou "Brasil, 450")
        return (hasTrigger && hasDigits) || (hasTrigger && upper.length > 8) || (hasDigits && (text.contains(",") || text.contains("-") || text.contains(";")) && text.length > 6)
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
                val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.lowercase(Locale.ROOT) ?: ""
                } else ""
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
        val compatNode = AccessibilityNodeInfoCompat.wrap(node)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        var result = false
        try {
            result = compatNode.performAction(AccessibilityNodeInfoCompat.ACTION_SET_TEXT, arguments)
        } catch (e: Exception) {
            result = false
        }
        if (!result) {
            try {
                if (compatNode.isFocusable && !compatNode.isFocused) {
                    compatNode.performAction(AccessibilityNodeInfoCompat.ACTION_FOCUS)
                }
                result = compatNode.performAction(AccessibilityNodeInfoCompat.ACTION_SET_TEXT, arguments)
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

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSignatureAreaNode(child)
            if (found != null) return found
            try {
                child.recycle()
            } catch (_: Exception) {}
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
        val personId = recebedor.id.removePrefix("p_").substringBefore("_").toLongOrNull()
        val targetPerson = if (personId != null) {
            _state.value.candidatePersons.firstOrNull { it.id == personId } ?: _state.value.matchedPerson
        } else {
            _state.value.matchedPerson
        }
        _state.value = _state.value.copy(
            selectedRecebedor = recebedor,
            matchedPerson = targetPerson
        )
    }

    fun prioritizeRecebedor(recebedor: Recebedor) {
        val personId = recebedor.id.removePrefix("p_").substringBefore("_").toLongOrNull()
            ?: _state.value.matchedPerson?.id
            ?: return

        scope.launch {
            val repo = personRepository ?: return@launch
            val updated = repo.prioritizeRecebedor(personId, recebedor.id)
            if (updated != null) {
                val currentCandidates = _state.value.candidatePersons
                val newCandidates = if (currentCandidates.isNotEmpty()) {
                    val updatedList = currentCandidates.map { if (it.id == updated.id) updated else it }
                    if (updatedList.none { it.id == updated.id }) {
                        listOf(updated) + updatedList
                    } else {
                        updatedList.sortedByDescending { it.id == updated.id }
                    }
                } else {
                    listOf(updated)
                }
                val allRecs = extractAllRecebedores(newCandidates)
                val newSelected = allRecs.firstOrNull { it.id == "p_${updated.id}_main" }
                    ?: allRecs.firstOrNull { it.nome.equals(recebedor.nome, ignoreCase = true) }
                    ?: allRecs.firstOrNull()

                _state.value = _state.value.copy(
                    matchedPerson = updated,
                    candidatePersons = newCandidates,
                    availableRecebedores = allRecs,
                    selectedRecebedor = newSelected
                )
            }
        }
    }

    fun setMatchedPersonDirect(person: Person) {
        scope.launch {
            val repo = personRepository
            val fullAddr = buildString {
                append(person.endereco)
                if (person.numero.isNotBlank()) append(", ${person.numero}")
                if (person.complemento.isNotBlank()) append(" - ${person.complemento}")
            }.trim()
            val addressToSet = fullAddr.ifBlank { _state.value.detectedAddressText }
            val matched = if (addressToSet.isNotBlank() && repo != null) {
                val found = repo.findPersonsByAddress(addressToSet)
                val updatedFound = found.map { if (it.id == person.id) person else it }
                if (updatedFound.none { it.id == person.id }) {
                    listOf(person) + updatedFound
                } else {
                    updatedFound.sortedByDescending { it.id == person.id }
                }
            } else {
                listOf(person)
            }
            val recebedores = extractAllRecebedores(matched)
            val selected = recebedores.firstOrNull { it.id == "p_${person.id}_main" }
                ?: recebedores.firstOrNull { it.id.startsWith("p_${person.id}") }
                ?: recebedores.firstOrNull()
            _state.value = _state.value.copy(
                detectedAddressText = addressToSet,
                isAddressLocked = true,
                matchedPerson = person,
                candidatePersons = matched,
                availableRecebedores = recebedores,
                selectedRecebedor = selected,
                logs = addLog("Destinatário selecionado manualmente: ${person.nome} (${addressToSet.ifBlank { "Sem endereço" }})", true)
            )
        }
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
