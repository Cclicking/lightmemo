/** Custom blur bottom sheet with full-area liquid glass blur. */
package com.foodcalorie.app.ui.overlay

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.foodcalorie.app.ui.basic.ProgressiveBlurTopBar
import com.foodcalorie.app.ui.basic.rememberCollapsibleTopAppBarState
import com.foodcalorie.app.ui.basic.rememberSharedScrollBehavior
import com.foodcalorie.app.ui.effects.edgelight.edgeLight
import com.foodcalorie.app.ui.effects.edgelight.rememberDefaultEdgeLight
import com.foodcalorie.app.ui.utils.LocalForcedDarkTheme
import com.foodcalorie.app.ui.utils.LocalOverScrollState
import com.foodcalorie.app.ui.utils.OverScrollState
import com.foodcalorie.app.ui.utils.rememberAppSettingDark
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout
import kotlin.math.abs

@Stable
class SheetTopBarMaterial(val backdropAlpha: Float, val shadowAlpha: Float)

val LocalSheetTopBarMaterial = compositionLocalOf { SheetTopBarMaterial(1f, 1f) }

@Composable
fun BlurBottomSheet(
    show: Boolean,
    title: String,
    liquidGlassBackdrop: Backdrop? = null,
    blurRadius: Float = 24f,
    dimBackground: Boolean = false,
    sheetBackgroundColor: Color? = null,
    sheetBackgroundAlpha: Float? = null,
    sheetOffsetDp: Dp = Dp.Unspecified,
    sheetMaxWidth: Dp = Dp.Unspecified,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onSheetContentBackdropCreated: ((Backdrop?) -> Unit)? = null,
    skipEnterAnimation: Boolean = false,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { mutableStateOf(show) }
    val sheetContentBackdropHolder = remember { mutableStateOf<Backdrop?>(null) }

    LaunchedEffect(show) {
        if (show) {
            visibleState.value = true
        }
    }

    LaunchedEffect(sheetContentBackdropHolder.value) {
        onSheetContentBackdropCreated?.invoke(sheetContentBackdropHolder.value)
    }

    BackHandler(enabled = show) {
        onDismissRequest()
    }

    DialogLayout(
        visible = visibleState,
        enableWindowDim = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        enableAutoLargeScreen = false,
        renderInRootScaffold = true,
    ) {
        BlurBottomSheetContent(
            show = show,
            visibleState = visibleState,
            title = title,
            liquidGlassBackdrop = liquidGlassBackdrop,
            blurRadius = blurRadius,
            dimBackground = dimBackground,
            sheetBackgroundColor = sheetBackgroundColor,
            sheetBackgroundAlpha = sheetBackgroundAlpha,
            onDismissRequest = onDismissRequest,
            startAction = startAction,
            endAction = endAction,
            sheetContentBackdropHolder = sheetContentBackdropHolder,
            sheetOffsetDp = sheetOffsetDp,
            sheetMaxWidth = sheetMaxWidth,
            skipEnterAnimation = skipEnterAnimation,
            content = content,
        )
    }
}

@Composable
private fun BlurBottomSheetContent(
    show: Boolean,
    visibleState: MutableState<Boolean>,
    title: String,
    liquidGlassBackdrop: Backdrop?,
    blurRadius: Float,
    dimBackground: Boolean = false,
    sheetBackgroundColor: Color? = null,
    sheetBackgroundAlpha: Float? = null,
    sheetOffsetDp: Dp = Dp.Unspecified,
    sheetMaxWidth: Dp = Dp.Unspecified,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    sheetContentBackdropHolder: MutableState<Backdrop?>? = null,
    skipEnterAnimation: Boolean = false,
    content: @Composable () -> Unit,
) {
    val sheetAppDark = rememberAppSettingDark()
    val sheetAppController = remember(sheetAppDark) {
        ThemeController(if (sheetAppDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
    }
    CompositionLocalProvider(LocalForcedDarkTheme provides null) {
        MiuixTheme(controller = sheetAppController) {
            val animationProgress = remember { Animatable(if (show && skipEnterAnimation) 1f else 0f) }
            val dragOffsetY = remember { Animatable(0f) }
            val density = LocalDensity.current
            val windowInfo = LocalWindowInfo.current
            val coroutineScope = rememberCoroutineScope()
            val sheetHeightPx = remember { mutableIntStateOf(0) }
            val imeInsets = WindowInsets.ime

            val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
            val sheetBgColor = sheetBackgroundColor ?: if (isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F2)
            val dismissThresholdPx = with(density) { 150.dp.toPx() }
            val velocityThresholdPx = with(density) { 800.dp.toPx() }

            LaunchedEffect(show) {
                if (show) {
                    dragOffsetY.snapTo(0f)
                    if (skipEnterAnimation) {
                        animationProgress.snapTo(1f)
                    } else {
                        animationProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 480,
                                easing = CubicBezierEasing(0.34f, 1.12f, 0.3f, 1f)
                            )
                        )
                    }
                } else {
                    animationProgress.animateTo(0f, animationSpec = tween(320, easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f)))
                    visibleState.value = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (dimBackground) {
                            Modifier.background(Color.Black.copy(alpha = 0.2f * animationProgress.value))
                        } else Modifier
                    )
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            ) {
                val sheetModifier = Modifier
                    .graphicsLayer {
                        val progress = animationProgress.value
                        val currentHeight = sheetHeightPx.intValue.toFloat()
                        val windowHeightPx = with(density) { windowInfo.containerDpSize.height.toPx() }
                        val baseOffset = if (currentHeight > 0) currentHeight else windowHeightPx
                        translationY = baseOffset * (1f - progress) + dragOffsetY.value
                    }

                val sheetOffsetDpValue = if (sheetOffsetDp != Dp.Unspecified) sheetOffsetDp else 200.dp

                Box(
                    modifier = sheetModifier
                        .offset(y = sheetOffsetDpValue)
                        .align(Alignment.BottomCenter)
                        .then(
                            if (sheetMaxWidth != Dp.Unspecified) Modifier.width(sheetMaxWidth).fillMaxWidth()
                            else Modifier.fillMaxWidth()
                        )
                        .heightIn(max = windowInfo.containerDpSize.height)
                        .onGloballyPositioned { coordinates ->
                            if (imeInsets.getBottom(density) == 0) {
                                val newHeight = coordinates.size.height
                                if (sheetHeightPx.intValue != newHeight) {
                                    sheetHeightPx.intValue = newHeight
                                }
                            }
                        }
                        .imePadding()
                        .clip(ContinuousRoundedRectangle(36.dp))
                        .then(
                            if (liquidGlassBackdrop != null && Build.VERSION.SDK_INT >= 33) {
                                val blurPx = with(density) { blurRadius.dp.toPx() }
                                val backdropEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(liquidGlassBackdrop, blurPx) {
                                    {
                                        vibrancy()
                                        blur(blurPx)
                                    }
                                }
                                Modifier.drawBackdrop(
                                    backdrop = liquidGlassBackdrop,
                                    shape = { ContinuousRoundedRectangle(36.dp) },
                                    effects = backdropEffects,
                                    highlight = null
                                )
                            } else {
                                Modifier
                            }
                        )
                        .edgeLight(shape = ContinuousRoundedRectangle(36.dp), edgeLight = rememberDefaultEdgeLight())
                        .background(sheetBgColor.copy(alpha = sheetBackgroundAlpha ?: if (liquidGlassBackdrop != null)
                            if (Build.VERSION.SDK_INT >= 33) 0.9f else 1f
                            else 1f))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {})
                        }
                        .semantics {
                            onClick(label = "Dismiss") {
                                onDismissRequest()
                                true
                            }
                        }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { dragAmount ->
                                coroutineScope.launch {
                                    val newOffset = dragOffsetY.value + dragAmount
                                    val dampedOffset = if (newOffset < 0f) {
                                        val resistance = 1f / (1f + abs(newOffset) / 30f)
                                        dragOffsetY.value + dragAmount * resistance
                                    } else {
                                        newOffset
                                    }
                                    dragOffsetY.snapTo(dampedOffset)
                                }
                            },
                            onDragStopped = { velocity ->
                                coroutineScope.launch {
                                    val shouldDismiss = velocity > velocityThresholdPx || dragOffsetY.value > dismissThresholdPx
                                    if (shouldDismiss) {
                                        onDismissRequest()
                                    } else {
                                        dragOffsetY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = 0.72f,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            initialVelocity = velocity * 0.12f
                                        )
                                    }
                                }
                            },
                        ),
                    content = {
                        val topBarState = rememberCollapsibleTopAppBarState()
                        val scrollBehavior = rememberSharedScrollBehavior(topBarState)
                        val overScrollState = remember { OverScrollState() }
                        LaunchedEffect(Unit) { topBarState.heightOffsetLimit = -1f }

                        val showButtonShadow by remember(scrollBehavior) {
                            derivedStateOf {
                                val contentOffset = scrollBehavior.state.contentOffset
                                val os = overScrollState.offset
                                contentOffset < 0f || os < 0f
                            }
                        }
                        val proxyConnection = remember(scrollBehavior, overScrollState) {
                            val delegate = scrollBehavior.nestedScrollConnection
                            object : NestedScrollConnection {
                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                                    delegate.onPreScroll(available, source)

                                override fun onPostScroll(
                                    consumed: Offset,
                                    available: Offset,
                                    source: NestedScrollSource,
                                ): Offset {
                                    if (overScrollState.offset != 0f) {
                                        return delegate.onPostScroll(Offset.Zero, available, source)
                                    }
                                    return delegate.onPostScroll(consumed, available, source)
                                }

                                override suspend fun onPreFling(available: Velocity): Velocity =
                                    delegate.onPreFling(available)

                                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                                    delegate.onPostFling(consumed, available)
                            }
                        }
                        val shadowAlpha = remember { Animatable(0f) }
                        val backdropAlpha = remember { Animatable(0f) }
                        LaunchedEffect(showButtonShadow) {
                            val target = if (showButtonShadow) 1f else 0f
                            val spec = if (showButtonShadow) {
                                folmeSpring(damping = 1.0f, response = 0.6f)
                            } else {
                                folmeSpring<Float>(damping = 1.0f, response = 0.4f)
                            }
                            launch { shadowAlpha.animateTo(target, spec) }
                            launch { backdropAlpha.animateTo(target, spec) }
                        }

                        CompositionLocalProvider(
                            LocalOverScrollState provides overScrollState,
                            LocalSheetTopBarMaterial provides SheetTopBarMaterial(backdropAlpha.value, shadowAlpha.value),
                        ) {
                            DragHandleArea()

                            val sheetBackdropColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF4F4F4)
                            val sheetContentBackdrop = rememberLayerBackdrop {
                                drawRect(sheetBackdropColor)
                                drawContent()
                            }

                            LaunchedEffect(sheetContentBackdrop) {
                                sheetContentBackdropHolder?.value = sheetContentBackdrop
                            }

                            Box(
                                modifier = Modifier
                                    .nestedScroll(proxyConnection)
                                    .wrapContentHeight()
                                    .layerBackdrop(sheetContentBackdrop)
                            ) {
                                content()
                            }

                            ProgressiveBlurTopBar(
                                backdrop = sheetContentBackdrop,
                                height = 84.dp,
                                tintColor = sheetBgColor,
                                tintIntensity = 0f,
                                blurAlpha = backdropAlpha.value,
                                modifier = Modifier.zIndex(1f)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().height(60.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(76.dp)
                                    .zIndex(2f),
                            ) {
                                Text(
                                    text = title,
                                    modifier = Modifier.align(Alignment.Center),
                                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                if (startAction != null) {
                                    Box(modifier = Modifier.align(Alignment.CenterStart)) {
                                        startAction()
                                    }
                                }
                                if (endAction != null) {
                                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                                        endAction()
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

@Composable
private fun DragHandleArea() {
    val pressScale = remember { Animatable(1f) }
    val pressWidth = remember { Animatable(45f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .zIndex(2f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        coroutineScope.launch {
                            launch { pressScale.animateTo(1.15f, tween(100)) }
                            launch { pressWidth.animateTo(55f, tween(100)) }
                        }
                        waitForUpOrCancellation()
                        coroutineScope.launch {
                            launch { pressScale.animateTo(1f, tween(150)) }
                            launch { pressWidth.animateTo(45f, tween(150)) }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(pressWidth.value.dp)
                .height(4.dp)
                .graphicsLayer {
                    scaleY = pressScale.value
                }
                .clip(RoundedCornerShape(2.dp))
                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f)),
        )
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForUpOrCancellation() {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.none { it.pressed }) break
    }
}
