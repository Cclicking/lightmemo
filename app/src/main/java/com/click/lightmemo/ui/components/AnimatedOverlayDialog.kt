package com.click.lightmemo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** Keeps the Miuix dialog in the composition until its exit animation has finished. */
@Composable
fun AnimatedOverlayDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var mounted by remember { mutableStateOf(show) }

    LaunchedEffect(show) {
        if (show) mounted = true
    }

    if (mounted || show) {
        OverlayDialog(
            show = show,
            modifier = modifier,
            title = title,
            summary = summary,
            onDismissRequest = onDismissRequest,
            onDismissFinished = {
                if (!show) {
                    mounted = false
                    onDismissFinished()
                }
            },
            content = content,
        )
    }
}
