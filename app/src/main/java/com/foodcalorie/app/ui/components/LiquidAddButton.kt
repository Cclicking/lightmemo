package com.foodcalorie.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.foodcalorie.app.ui.effects.edgelight.edgeLight
import com.foodcalorie.app.ui.effects.edgelight.rememberDefaultEdgeLight
import com.foodcalorie.app.ui.effects.liquidglass.InteractiveHighlight
import com.foodcalorie.app.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LiquidAddButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isAppDarkTheme()
    val containerColor = if (isLightTheme) MiuixTheme.colorScheme.primary.copy(0.72f) else
        MiuixTheme.colorScheme.primary.copy(0.62f)

    val click = {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        onClick()
    }

    if (backdrop == null) {
        Box(
            modifier = modifier
                .size(56.dp)
                .background(MiuixTheme.colorScheme.primary, CircleShape)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = click,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Demibold.Add,
                contentDescription = "记录食物",
                modifier = Modifier.size(24.dp),
                tint = Color.White.copy(0.92f),
            )
        }
        return
    }

    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
        )
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .zIndex(1f)
            // Tap handling first so drawBackdrop / highlight never steal the click.
            .pointerInput(click) {
                detectTapGestures(onTap = { click() })
            }
            .semantics {
                role = Role.Button
                contentDescription = "记录食物"
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(10f.dp.toPx(), 32f.dp.toPx())
                },
                highlight = null,
                layerBlock = {
                    val progress = interactiveHighlight.pressProgress
                    val scale = 1f + 4f.dp.toPx() / size.height * progress
                    scaleX = scale
                    scaleY = scale
                    val offset = interactiveHighlight.offset
                    translationX = size.minDimension * 0.05f * offset.x / size.maxDimension
                    translationY = size.minDimension * 0.05f * offset.y / size.maxDimension
                },
                onDrawSurface = {
                    val overlayColor = if (isLightTheme) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f)
                    drawRect(overlayColor)
                    drawRect(containerColor)
                    drawRect(Color.Black.copy(alpha = 0.03f * interactiveHighlight.pressProgress))
                },
            )
            .edgeLight(shape = CircleShape, edgeLight = rememberDefaultEdgeLight())
            .then(interactiveHighlight.modifier)
            // Drag-aware highlight so layerBlock offset/scale can follow the finger.
            .then(interactiveHighlight.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Demibold.Add,
            contentDescription = "记录食物",
            modifier = Modifier.size(24.dp),
            tint = Color.White.copy(0.92f),
        )
    }
}
