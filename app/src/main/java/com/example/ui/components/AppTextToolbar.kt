package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

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

    override val status: TextToolbarStatus
        get() = statusState

    override fun hide() {
        statusState = TextToolbarStatus.Hidden
        onCopy = null
        onPaste = null
        onCut = null
        onSelectAll = null
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        menuRect = rect
        onCopy = onCopyRequested
        onPaste = onPasteRequested
        onCut = onCutRequested
        onSelectAll = onSelectAllRequested
        statusState = TextToolbarStatus.Shown
    }
}

@Composable
fun ProvideAppTextToolbar(content: @Composable () -> Unit) {
    val textToolbar = remember { CustomAppTextToolbar() }

    CompositionLocalProvider(LocalTextToolbar provides textToolbar) {
        Box {
            content()

            if (textToolbar.status == TextToolbarStatus.Shown) {
                val density = LocalDensity.current
                val rect = textToolbar.menuRect

                val popupX = with(density) { (rect.left - 20f).coerceAtLeast(10f).toDp().value.toInt() }
                val popupY = with(density) { (rect.top - 50f).coerceAtLeast(10f).toDp().value.toInt() }

                Popup(
                    offset = IntOffset(popupX, popupY),
                    onDismissRequest = { textToolbar.hide() },
                    properties = PopupProperties(
                        focusable = false,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            textToolbar.onCopy?.let { copyAction ->
                                ToolbarButton("Copiar") {
                                    copyAction()
                                    textToolbar.hide()
                                }
                            }
                            textToolbar.onPaste?.let { pasteAction ->
                                ToolbarButton("Colar") {
                                    pasteAction()
                                    textToolbar.hide()
                                }
                            }
                            textToolbar.onCut?.let { cutAction ->
                                ToolbarButton("Recortar") {
                                    cutAction()
                                    textToolbar.hide()
                                }
                            }
                            textToolbar.onSelectAll?.let { selectAllAction ->
                                ToolbarButton("Selecionar Tudo") {
                                    selectAllAction()
                                    textToolbar.hide()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xFF1E293B), shape = RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
