package com.click.lightmemo.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import top.yukonga.miuix.kmp.glass.GlassDropdownPopup
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.glass.GlassMotion
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.glassPopupAnchorValue
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.PopupLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared settings selector, with a Glass dropdown on shader-capable devices. */
@Composable
fun DropdownPref(
    title: String,
    summary: String? = null,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val useGlass = glassControlsEnabled()
    val backdrop = LocalGlassControlsBackdrop.current.takeIf { glassControlsMaterialEnabled() }
    val anchor = rememberGlassPopupAnchor()
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }
    var expanded by remember { mutableStateOf(false) }
    var holdDown by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val currentHaptic by rememberUpdatedState(haptic)
    val validItems = items.mapIndexed { index, text ->
        DropdownItem(
            text = text,
            selected = index == selectedIndex,
            onClick = { onSelectedIndexChange(index) },
        )
    }
    val selectedText = items.getOrNull(selectedIndex)
    val actualEnabled = enabled && validItems.isNotEmpty()
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    BasicComponent(
        modifier = modifier.onGloballyPositioned { anchorBounds = it.boundsInRoot() },
        interactionSource = interactionSource,
        insideMargin = insideMargin,
        title = title,
        summary = summary,
        endActions = {
            Row(modifier = if (useGlass) Modifier.glassPopupAnchorValue(anchor) else Modifier) {
                selectedText?.let {
                    Text(
                        text = it,
                        fontSize = 14.2.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier
                            .widthIn(max = 152.dp)
                            .padding(end = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownArrowEndAction(
                    actionColor = actionColor,
                )
            }
            if (!useGlass && validItems.isNotEmpty()) {
                OverlayDropdownPopup(
                    entry = DropdownEntry(items = validItems),
                    show = expanded,
                    onDismiss = {
                        expanded = false
                        holdDown = false
                    },
                    onDismissFinished = { holdDown = false },
                    maxHeight = maxHeight,
                    dropdownColors = dropdownColors,
                    renderInRootScaffold = true,
                    collapseOnSelection = true,
                )
            }
        },
        onClick = {
            if (actualEnabled) {
                expanded = !expanded
                if (expanded) {
                    currentHaptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                } else if (!useGlass) {
                    holdDown = true
                }
            }
        },
        role = Role.DropdownList,
        holdDownState = holdDown,
        enabled = actualEnabled,
    )

    if (useGlass && validItems.isNotEmpty()) {
        val popupVisible = remember { mutableStateOf(false) }
        // Match the dropdown's spring so its host survives until the exit has settled.
        val progress by animateFloatAsState(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = GlassMotion.arcBounds(expanded),
            label = "settingsDropdownHost",
        )
        LaunchedEffect(expanded, progress > 0.0001f) {
            popupVisible.value = expanded || progress > 0.0001f
        }
        PopupLayout(
            visible = popupVisible,
            enterTransition = EnterTransition.None,
            exitTransition = ExitTransition.None,
            enableWindowDim = false,
            enableBackHandler = false,
            renderInRootScaffold = true,
        ) {
            Box(Modifier.fillMaxSize()) {
                GlassDropdownPopup(
                    show = expanded,
                    onDismissRequest = { expanded = false },
                    anchorBounds = anchorBounds,
                    anchor = anchor,
                    backdrop = backdrop,
                    sizing = GlassPopupSizing().let { defaults ->
                        maxHeight?.let { defaults.copy(maxHeight = it) } ?: defaults
                    },
                ) {
                    items.forEachIndexed { index, label ->
                        GlassPopupItem(
                            text = label,
                            selected = index == selectedIndex,
                            onClick = {
                                expanded = false
                                onSelectedIndexChange(index)
                            },
                        )
                    }
                }
            }
        }
    }
}
