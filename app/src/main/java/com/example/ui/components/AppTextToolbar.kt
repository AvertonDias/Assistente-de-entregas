package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.platform.LocalContext

class CustomAppTextToolbar : TextToolbar {
    var statusState by mutableStateOf(TextToolbarStatus.Hidden)
        private set

    var menuRect by mutableStateOf(Rect.Zero)
        private set

    var onCopy: (() -> Unit)? by mutableStateOf(null)
        private set
    var onPaste: (() -> Unit)? by mutableStateOf(null)
        private set
    var onCut: (() -> Unit)? by mutableStateOf(null)
        private set
    var onSelectAll: (() -> Unit)? by mutableStateOf(null)
        private set

    private var protectedPasteUntilMs: Long = 0L

    override val status: TextToolbarStatus
        get() = statusState

    fun forceHide() {
        protectedPasteUntilMs = 0L
        statusState = TextToolbarStatus.Hidden
        onCopy = null
        onPaste = null
        onCut = null
        onSelectAll = null
    }

    fun showProtectedPasteMenu(
        rect: Rect,
        onPasteRequested: () -> Unit
    ) {
        protectedPasteUntilMs = SystemClock.elapsedRealtime() + 700L
        menuRect = rect
        onCopy = null
        onPaste = onPasteRequested
        onCut = null
        onSelectAll = null
        statusState = TextToolbarStatus.Shown
    }

    override fun hide() {
        if (SystemClock.elapsedRealtime() < protectedPasteUntilMs && statusState == TextToolbarStatus.Shown) {
            return
        }
        forceHide()
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        val hasAnyAction = onCopyRequested != null || onPasteRequested != null || onCutRequested != null || onSelectAllRequested != null
        if (!hasAnyAction && SystemClock.elapsedRealtime() < protectedPasteUntilMs && statusState == TextToolbarStatus.Shown) {
            return
        }
        protectedPasteUntilMs = 0L
        menuRect = rect
        onCopy = onCopyRequested
        onPaste = onPasteRequested
        onCut = onCutRequested
        onSelectAll = onSelectAllRequested
        statusState = if (hasAnyAction) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
    }
}

@Composable
fun ProvideAppTextToolbar(
    useInlineOverlay: Boolean = true,
    content: @Composable () -> Unit
) {
    val textToolbar = remember { CustomAppTextToolbar() }
    var boxPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    CompositionLocalProvider(LocalTextToolbar provides textToolbar) {
        Box(
            modifier = Modifier.onGloballyPositioned { coords ->
                boxPositionInRoot = coords.positionInRoot()
                containerSize = coords.size
            }
        ) {
            content()

            if (textToolbar.status == TextToolbarStatus.Shown) {
                var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
                val density = LocalDensity.current
                val rect = textToolbar.menuRect
                val marginPx = with(density) { 8.dp.roundToPx() }
                val gapPx = with(density) { 10.dp.roundToPx() }
                val estWidthPx = with(density) { 180.dp.roundToPx() }
                val estHeightPx = with(density) { 42.dp.roundToPx() }

                val tbWidth = if (toolbarSize.width > 0) toolbarSize.width else estWidthPx
                val tbHeight = if (toolbarSize.height > 0) toolbarSize.height else estHeightPx

                val localTop = rect.top - boxPositionInRoot.y
                val localBottom = rect.bottom - boxPositionInRoot.y
                val localCenterX = ((rect.left + rect.right) / 2f) - boxPositionInRoot.x

                if (useInlineOverlay) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .zIndex(9999f)
                            .onSizeChanged { containerSize = it }
                    ) {
                        val maxW = containerSize.width
                        val maxH = containerSize.height

                        val rawX = (localCenterX - tbWidth / 2f).roundToInt()
                        val popupX = if (maxW > 0) {
                            rawX.coerceIn(marginPx, (maxW - tbWidth - marginPx).coerceAtLeast(marginPx))
                        } else {
                            rawX.coerceAtLeast(marginPx)
                        }

                        val fitsAbove = localTop >= tbHeight + gapPx + marginPx
                        val rawY = if (fitsAbove) {
                            (localTop - tbHeight - gapPx).roundToInt()
                        } else {
                            (localBottom + gapPx).roundToInt()
                        }
                        val popupY = if (maxH > 0) {
                            rawY.coerceIn(marginPx, (maxH - tbHeight - marginPx).coerceAtLeast(marginPx))
                        } else {
                            rawY.coerceAtLeast(marginPx)
                        }

                        FloatingToolbarPill(
                            textToolbar = textToolbar,
                            modifier = Modifier
                                .offset { IntOffset(popupX, popupY) }
                                .onSizeChanged { toolbarSize = it }
                        )
                    }
                } else {
                    val fitsAbove = rect.top >= tbHeight + gapPx + marginPx
                    val rawX = (localCenterX - tbWidth / 2f).roundToInt()
                    val popupX = if (containerSize.width > 0) {
                        rawX.coerceIn(-marginPx, (containerSize.width - tbWidth + marginPx).coerceAtLeast(-marginPx))
                    } else {
                        rawX
                    }
                    val popupY = if (fitsAbove) {
                        (localTop - tbHeight - gapPx).roundToInt()
                    } else {
                        (localBottom + gapPx).roundToInt()
                    }

                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(popupX, popupY),
                        onDismissRequest = { textToolbar.hide() },
                        properties = PopupProperties(
                            focusable = false,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = false,
                            clippingEnabled = false
                        )
                    ) {
                        FloatingToolbarPill(
                            textToolbar = textToolbar,
                            modifier = Modifier.onSizeChanged { toolbarSize = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PasteSupportBox(
    text: String,
    onPasteText: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val textToolbar = LocalTextToolbar.current
    val haptic = LocalHapticFeedback.current
    var boundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var openedEmptyPasteMenu by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }

    val hideToolbarSafely: () -> Unit = {
        if (textToolbar is CustomAppTextToolbar) {
            textToolbar.forceHide()
        } else {
            textToolbar.hide()
        }
    }

    LaunchedEffect(text) {
        if (text.isNotEmpty() && openedEmptyPasteMenu) {
            openedEmptyPasteMenu = false
            if (textToolbar.status == TextToolbarStatus.Shown) {
                hideToolbarSafely()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (openedEmptyPasteMenu && textToolbar.status == TextToolbarStatus.Shown) {
                hideToolbarSafely()
            }
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                boundsInRoot = coords.boundsInRoot()
            }
            .onFocusChanged { focusState ->
                if (hadFocus && !focusState.hasFocus && openedEmptyPasteMenu) {
                    openedEmptyPasteMenu = false
                    if (textToolbar.status == TextToolbarStatus.Shown) {
                        hideToolbarSafely()
                    }
                }
                hadFocus = focusState.hasFocus
            }
            .pointerInput(text.isEmpty(), enabled, readOnly, textToolbar) {
                if (!enabled || readOnly || text.isNotEmpty()) return@pointerInput
                val touchSlop = viewConfiguration.touchSlop
                val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val downPos = down.position
                    var cancelled = false
                    var releasedCleanly = false
                    withTimeoutOrNull(longPressTimeout) {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                cancelled = true
                                releasedCleanly = (change != null && !change.pressed)
                                return@withTimeoutOrNull null
                            }
                            if ((change.position - downPos).getDistance() > touchSlop) {
                                cancelled = true
                                return@withTimeoutOrNull null
                            }
                        }
                    }
                    if (releasedCleanly && openedEmptyPasteMenu) {
                        openedEmptyPasteMenu = false
                        if (textToolbar.status == TextToolbarStatus.Shown) {
                            hideToolbarSafely()
                        }
                    }
                    if (!cancelled) {
                        val composeClip = clipboardManager.getText()?.text
                        val clipText = if (!composeClip.isNullOrEmpty()) {
                            composeClip
                        } else {
                            try {
                                val sysClip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? AndroidClipboardManager
                                val item = sysClip?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                                item?.coerceToText(context)?.toString()
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        if (!clipText.isNullOrEmpty() && boundsInRoot != Rect.Zero) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val rectHeight = boundsInRoot.height
                            val menuRect = Rect(
                                left = boundsInRoot.left,
                                top = boundsInRoot.top + (rectHeight * 0.15f),
                                right = boundsInRoot.right,
                                bottom = boundsInRoot.bottom
                            )
                            openedEmptyPasteMenu = true
                            val pasteCallback = {
                                openedEmptyPasteMenu = false
                                onPasteText(clipText)
                            }
                            if (textToolbar is CustomAppTextToolbar) {
                                textToolbar.showProtectedPasteMenu(
                                    rect = menuRect,
                                    onPasteRequested = pasteCallback
                                )
                            } else {
                                textToolbar.showMenu(
                                    rect = menuRect,
                                    onCopyRequested = null,
                                    onPasteRequested = pasteCallback,
                                    onCutRequested = null,
                                    onSelectAllRequested = null
                                )
                            }
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) break
                                change.consume()
                                if (!change.pressed) break
                            }
                        }
                    }
                }
            }
    ) {
        content()
    }
}

@Composable
private fun FloatingToolbarPill(
    textToolbar: CustomAppTextToolbar,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { /* Consome toques no fundo da barra para não fechar seleção */ }
        },
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0F172A),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            textToolbar.onCut?.let { cutAction ->
                ToolbarButton("Recortar", Icons.Default.ContentCut) {
                    cutAction()
                    textToolbar.forceHide()
                }
            }
            textToolbar.onCopy?.let { copyAction ->
                ToolbarButton("Copiar", Icons.Default.ContentCopy) {
                    copyAction()
                    textToolbar.forceHide()
                }
            }
            textToolbar.onPaste?.let { pasteAction ->
                ToolbarButton("Colar", Icons.Default.ContentPaste) {
                    pasteAction()
                    textToolbar.forceHide()
                }
            }
            textToolbar.onSelectAll?.let { selectAllAction ->
                ToolbarButton("Selecionar Tudo", Icons.Default.SelectAll) {
                    selectAllAction()
                }
            }
        }
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .background(
                color = if (isPressed) Color(0xFF334155) else Color(0xFF1E293B),
                shape = RoundedCornerShape(6.dp)
            )
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF93C5FD),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
