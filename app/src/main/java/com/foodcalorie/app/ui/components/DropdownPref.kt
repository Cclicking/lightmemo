package com.foodcalorie.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
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

/**
 * Dropdown preference styled after NexioSchedule's OverlayDropdownMenu.
 * It is intentionally application-owned so settings rows use one consistent selector.
 */
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
        modifier = modifier,
        interactionSource = interactionSource,
        insideMargin = insideMargin,
        title = title,
        summary = summary,
        endActions = {
            selectedText?.let {
                Text(
                    text = it,
                    fontSize = 14.2.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(end = 8.dp),
                    maxLines = 1,
                )
            }
            DropdownArrowEndAction(
                actionColor = actionColor,
            )
            if (validItems.isNotEmpty()) {
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
                } else {
                    holdDown = true
                }
            }
        },
        role = Role.DropdownList,
        holdDownState = holdDown,
        enabled = actualEnabled,
    )
}
