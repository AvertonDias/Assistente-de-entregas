package com.example.util

import android.graphics.Rect as AndroidRect
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

class PortugueseTextToolbar(private val view: View) : TextToolbar {
    private var actionMode: ActionMode? = null
    private var currentCallback: PortugueseFloatingTextActionModeCallback? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    private fun getTargetView(): View {
        return view.findFocus() ?: view.rootView ?: view
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        val targetView = getTargetView()
        val callback = currentCallback
        if (actionMode == null || callback == null) {
            val newCallback = PortugueseFloatingTextActionModeCallback(
                rect = rect,
                onCopyRequested = onCopyRequested,
                onPasteRequested = onPasteRequested,
                onCutRequested = onCutRequested,
                onSelectAllRequested = onSelectAllRequested,
                onDismiss = {
                    status = TextToolbarStatus.Hidden
                    actionMode = null
                    currentCallback = null
                }
            )
            currentCallback = newCallback
            actionMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                targetView.startActionMode(newCallback, ActionMode.TYPE_FLOATING)
            } else {
                targetView.startActionMode(newCallback)
            }
        } else {
            callback.rect = rect
            callback.onCopyRequested = onCopyRequested
            callback.onPasteRequested = onPasteRequested
            callback.onCutRequested = onCutRequested
            callback.onSelectAllRequested = onSelectAllRequested
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                actionMode?.invalidateContentRect()
            }
            actionMode?.invalidate()
        }
        status = if (actionMode != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
        currentCallback = null
    }
}

class PortugueseFloatingTextActionModeCallback(
    var rect: Rect,
    var onCopyRequested: (() -> Unit)?,
    var onPasteRequested: (() -> Unit)?,
    var onCutRequested: (() -> Unit)?,
    var onSelectAllRequested: (() -> Unit)?,
    private val onDismiss: () -> Unit
) : ActionMode.Callback2() {

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return onPrepareActionMode(mode, menu)
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        if (menu == null) return false
        menu.clear()

        // Ordem padrão no Android: Recortar, Copiar, Colar, Selecionar tudo
        onCutRequested?.let {
            menu.add(Menu.NONE, android.R.id.cut, 1, "Recortar")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        onCopyRequested?.let {
            menu.add(Menu.NONE, android.R.id.copy, 2, "Copiar")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        onPasteRequested?.let {
            menu.add(Menu.NONE, android.R.id.paste, 3, "Colar")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        onSelectAllRequested?.let {
            menu.add(Menu.NONE, android.R.id.selectAll, 4, "Selecionar tudo")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.cut -> onCutRequested?.invoke()
            android.R.id.copy -> onCopyRequested?.invoke()
            android.R.id.paste -> onPasteRequested?.invoke()
            android.R.id.selectAll -> onSelectAllRequested?.invoke()
            else -> return false
        }
        mode?.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        onDismiss()
    }

    override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: AndroidRect?) {
        outRect?.set(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt()
        )
    }
}
