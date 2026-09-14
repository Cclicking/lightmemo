package com.click.lightmemo.ui.platform

import android.graphics.Rect as AndroidRect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Keeps Compose/Miuix text fields intact while using Android's native floating ActionMode menu.
 *
 * This host must be above the text fields. It replaces both Compose context-menu providers so
 * that long-press and secondary-click paths use the same platform menu.
 */
@Composable
fun NativeTextContextMenuHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val provider = remember(view) {
        NativeTextContextMenuProvider(view) { rootCoordinates }
    }

    DisposableEffect(provider) {
        onDispose(provider::dispose)
    }

    CompositionLocalProvider(
        LocalTextContextMenuDropdownProvider provides provider,
        LocalTextContextMenuToolbarProvider provides provider,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { rootCoordinates = it },
        ) {
            content()
        }
    }
}

private class NativeTextContextMenuProvider(
    private val view: View,
    private val rootCoordinates: () -> LayoutCoordinates?,
) : TextContextMenuProvider {
    private var actionMode: ActionMode? = null

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val session = NativeTextContextMenuSession()
        val callback = NativeActionModeCallback(session, dataProvider, rootCoordinates)

        val startedActionMode = withContext(Dispatchers.Main.immediate) {
            actionMode?.finish()
            view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        }

        if (startedActionMode == null) {
            session.close()
            return
        }

        actionMode = startedActionMode
        try {
            session.awaitClosed()
        } finally {
            withContext(Dispatchers.Main.immediate) {
                if (actionMode === startedActionMode) {
                    startedActionMode.finish()
                    actionMode = null
                }
            }
        }
    }

    fun dispose() {
        actionMode?.finish()
        actionMode = null
    }
}

private class NativeActionModeCallback(
    private val session: NativeTextContextMenuSession,
    private val dataProvider: TextContextMenuDataProvider,
    private val rootCoordinates: () -> LayoutCoordinates?,
) : ActionMode.Callback2() {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        rebuildMenu(menu)
        return menu.size() > 0
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        rebuildMenu(menu)
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode) {
        session.close()
    }

    override fun onGetContentRect(mode: ActionMode, view: View?, outRect: AndroidRect) {
        val coordinates = rootCoordinates()
        if (coordinates == null || !coordinates.isAttached) {
            outRect.setEmpty()
            return
        }

        val bounds = dataProvider.contentBounds(coordinates)
        val rootPosition = coordinates.positionInRoot()
        outRect.set(
            (bounds.left + rootPosition.x).roundToInt(),
            (bounds.top + rootPosition.y).roundToInt(),
            (bounds.right + rootPosition.x).roundToInt(),
            (bounds.bottom + rootPosition.y).roundToInt(),
        )
    }

    private fun rebuildMenu(menu: Menu) {
        menu.clear()
        var groupId = 1
        var order = 1

        dataProvider.data().components.forEach { component ->
            when (component) {
                is TextContextMenuItem -> {
                    val itemId = when (component.key) {
                        TextContextMenuKeys.CutKey -> android.R.id.cut
                        TextContextMenuKeys.CopyKey -> android.R.id.copy
                        TextContextMenuKeys.PasteKey -> android.R.id.paste
                        TextContextMenuKeys.SelectAllKey -> android.R.id.selectAll
                        TextContextMenuKeys.AutofillKey -> android.R.id.autofill
                        else -> 0x1000 + order
                    }
                    menu.add(groupId, itemId, order++, component.label).apply {
                        // Let the ROM decide whether this action is shown as an icon or label.
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                        setOnMenuItemClickListener {
                            component.onClick(session)
                            session.close()
                            true
                        }
                    }
                }

                TextContextMenuSeparator -> groupId++
                else -> Unit
            }
        }
    }
}

private class NativeTextContextMenuSession : TextContextMenuSession {
    private val closed = CompletableDeferred<Unit>()

    override fun close() {
        closed.complete(Unit)
    }

    suspend fun awaitClosed() {
        closed.await()
    }
}
