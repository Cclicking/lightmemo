package com.click.lightmemo.ui.platform

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MenuEnterMs = 200
private const val MenuExitMs = 160
private const val MenuHiddenScale = 0.72f
private const val MagnifierSuppressMs = 480L
/** Selection-bounds / anchor drift (px) treated as a selection change. */
private const val SelectionDriftPx = 4f
private const val SelectionPollMs = 16L

/**
 * HyperOS-style floating text selection menu drawn in Compose.
 *
 * Capsule popup with soft shadow; copy / paste / select-all only.
 * Non-focusable so system selection handles stay visible. Scales in/out
 * from the selection anchor. A new toolbar request while one is showing is
 * treated as a handle drag / magnifier and dismisses the menu.
 */
@Composable
fun NativeTextContextMenuHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val menuState = remember { mutableStateOf<StyledMenuState?>(null) }
    val menuVisible = remember { mutableStateOf(false) }
    val lastClosedAt = remember { mutableStateOf(0L) }

    fun dismissMenu() {
        val session = menuState.value?.session ?: return
        menuVisible.value = false
        lastClosedAt.value = SystemClock.uptimeMillis()
        session.close()
    }

    val provider = remember {
        StyledTextContextMenuProvider(
            rootCoordinates = { rootCoordinates },
            onShow = { state ->
                val sinceClose = SystemClock.uptimeMillis() - lastClosedAt.value
                if (menuVisible.value) {
                    dismissMenu()
                }
                if (sinceClose < MagnifierSuppressMs) {
                    state.session.close()
                    return@StyledTextContextMenuProvider
                }
                menuState.value = state
                menuVisible.value = true
            },
            onDismiss = { session ->
                if (menuState.value?.session === session) {
                    menuVisible.value = false
                    lastClosedAt.value = SystemClock.uptimeMillis()
                }
            },
        )
    }

    LaunchedEffect(menuVisible.value) {
        if (!menuVisible.value && menuState.value != null) {
            delay(MenuExitMs.toLong())
            if (!menuVisible.value) {
                menuState.value = null
            }
        }
    }

    // Hide the menu as soon as the selection moves or resizes (handle drag).
    LaunchedEffect(menuState.value) {
        val state = menuState.value ?: return@LaunchedEffect
        val initial = state.anchorBounds
        val initialAnchor = state.readAnchor()
        while (menuVisible.value && menuState.value === state) {
            delay(SelectionPollMs)
            val current = state.readLiveBounds() ?: continue
            val anchor = state.readAnchor()
            val boundsMoved =
                abs(current.left - initial.left) > SelectionDriftPx ||
                    abs(current.top - initial.top) > SelectionDriftPx ||
                    abs(current.right - initial.right) > SelectionDriftPx ||
                    abs(current.bottom - initial.bottom) > SelectionDriftPx
            val anchorMoved =
                initialAnchor != null &&
                    anchor != null &&
                    (anchor - initialAnchor).getDistance() > SelectionDriftPx
            if (boundsMoved || anchorMoved) {
                dismissMenu()
                break
            }
        }
    }

    val activeSession = menuState.value?.session
    BackHandler(enabled = activeSession != null) {
        dismissMenu()
    }

    CompositionLocalProvider(
        LocalTextContextMenuDropdownProvider provides provider,
        LocalTextContextMenuToolbarProvider provides provider,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { rootCoordinates = it }
                // Non-consuming press watch: outside taps dismiss; handle
                // windows never reach here (magnifier uses onShow suppress).
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.any { it.pressed && !it.previousPressed }
                            if (down && menuVisible.value) {
                                dismissMenu()
                            }
                        }
                    }
                },
        ) {
            content()
            menuState.value?.let { state ->
                StyledTextSelectionMenu(
                    state = state,
                    visible = menuVisible.value,
                )
            }
        }
    }
}

private class StyledTextContextMenuProvider(
    private val rootCoordinates: () -> LayoutCoordinates?,
    private val onShow: (StyledMenuState) -> Unit,
    private val onDismiss: (StyledTextSession) -> Unit,
) : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val root = rootCoordinates()
        if (root == null || !root.isAttached) return

        val items = dataProvider.data().components
            .filterIsInstance<TextContextMenuItem>()
            .filter { item ->
                item.key == TextContextMenuKeys.CopyKey ||
                    item.key == TextContextMenuKeys.PasteKey ||
                    item.key == TextContextMenuKeys.SelectAllKey
            }
        if (items.isEmpty()) return

        val session = StyledTextSession(onDismiss = onDismiss)
        val rootPosition = root.positionInRoot()

        fun liveBounds(): Rect {
            val bounds = dataProvider.contentBounds(root)
            return Rect(
                left = bounds.left + rootPosition.x,
                top = bounds.top + rootPosition.y,
                right = bounds.right + rootPosition.x,
                bottom = bounds.bottom + rootPosition.y,
            )
        }

        fun liveAnchor(): Offset? {
            val r = rootCoordinates() ?: return null
            if (!r.isAttached) return null
            return dataProvider.position(r)
        }

        onShow(
            StyledMenuState(
                session = session,
                items = items.map { item ->
                    StyledMenuItem(
                        label = item.label,
                        onClick = item.onClick,
                    )
                },
                anchorBounds = liveBounds(),
                readLiveBounds = ::liveBounds,
                readAnchor = ::liveAnchor,
            ),
        )
        session.awaitClosed()
    }
}

private class StyledTextSession(
    private val onDismiss: (StyledTextSession) -> Unit,
) : StyledMenuSession {
    private val closed = CompletableDeferred<Unit>()

    override fun close() {
        if (closed.isCompleted) return
        closed.complete(Unit)
        onDismiss(this)
    }

    suspend fun awaitClosed() {
        closed.await()
    }
}

private interface StyledMenuSession : TextContextMenuSession

private data class StyledMenuItem(
    val label: String,
    val onClick: (TextContextMenuSession) -> Unit,
)

private data class StyledMenuState(
    val session: StyledMenuSession,
    val items: List<StyledMenuItem>,
    val anchorBounds: Rect,
    val readLiveBounds: () -> Rect,
    val readAnchor: () -> Offset?,
)

@Composable
private fun StyledTextSelectionMenu(
    state: StyledMenuState,
    visible: Boolean,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    var menuSize by remember(state.session) { mutableStateOf(IntSize.Zero) }

    // Animatable so enter starts from the hidden scale (animateFloatAsState
    // would snap straight to the target on first composition).
    val scale = remember(state.session) { Animatable(MenuHiddenScale) }
    val alpha = remember(state.session) { Animatable(0f) }

    LaunchedEffect(state.session, visible) {
        if (visible) {
            scale.snapTo(MenuHiddenScale)
            alpha.snapTo(0f)
            launch {
                scale.animateTo(1f, tween(MenuEnterMs, easing = FastOutSlowInEasing))
            }
            launch {
                alpha.animateTo(1f, tween(MenuEnterMs, easing = FastOutSlowInEasing))
            }
        } else {
            scale.animateTo(MenuHiddenScale, tween(MenuExitMs, easing = FastOutSlowInEasing))
            alpha.animateTo(0f, tween(MenuExitMs, easing = FastOutSlowInEasing))
        }
    }

    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val startSegmentShape = remember {
        RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    }
    val endSegmentShape = remember {
        RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    }
    val middleSegmentShape = remember { RoundedCornerShape(0.dp) }
    val isDark = MiuixTheme.colorScheme.background.luminanceCompat() < 0.5f
    val containerColor = if (isDark) Color(0xFF2C2C2E) else Color.White
    val itemColor = if (isDark) Color(0xFFF5F5F7) else Color(0xFF111114)
    val pressedOverlay = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color(0xFFD1D1D6)
    }

    val gap = with(density) { 10.dp.toPx() }
    val margin = with(density) { 12.dp.toPx() }
    val windowWidth = view.width.toFloat()
    val windowHeight = view.height.toFloat()

    val menuWidthPx = menuSize.width.toFloat().coerceAtLeast(1f)
    val menuHeightPx = menuSize.height.toFloat().coerceAtLeast(1f)

    val desiredX = state.anchorBounds.center.x - menuWidthPx / 2f
    val desiredYAbove = state.anchorBounds.top - menuHeightPx - gap
    val desiredYBelow = state.anchorBounds.bottom + gap
    val x = desiredX.coerceIn(margin, (windowWidth - menuWidthPx - margin).coerceAtLeast(margin))
    val placedAbove = desiredYAbove >= margin
    val y = if (placedAbove) {
        desiredYAbove
    } else {
        desiredYBelow.coerceAtMost((windowHeight - menuHeightPx - margin).coerceAtLeast(margin))
    }

    val originX = ((state.anchorBounds.center.x - x) / menuWidthPx).coerceIn(0.15f, 0.85f)
    val originY = if (placedAbove) 1f else 0f

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = { state.session.close() },
        // focusable=false keeps the text field focused so Compose continues
        // drawing system selection handles (and the magnifier) while the
        // capsule is on screen.
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            excludeFromSystemGesture = true,
        ),
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                    transformOrigin = TransformOrigin(originX, originY)
                }
                .onSizeChanged { menuSize = it }
                .shadow(
                    elevation = 10.dp,
                    shape = pillShape,
                    ambientColor = Color.Black.copy(alpha = 0.14f),
                    spotColor = Color.Black.copy(alpha = 0.20f),
                )
                .background(color = containerColor, shape = pillShape)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val lastIndex = state.items.lastIndex
            state.items.forEachIndexed { index, item ->
                val interactionSource = remember(item.label) { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                // Capsule is split into flush segments; press fill rounds only
                // the outer ends so it matches the pill silhouette.
                val segmentShape = when {
                    lastIndex == 0 -> pillShape
                    index == 0 -> startSegmentShape
                    index == lastIndex -> endSegmentShape
                    else -> middleSegmentShape
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(
                            color = if (pressed) pressedOverlay else Color.Transparent,
                            shape = segmentShape,
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) {
                            item.onClick(state.session)
                            state.session.close()
                        }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.label,
                        color = itemColor,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

private fun Color.luminanceCompat(): Float =
    (0.2126f * red + 0.7152f * green + 0.0722f * blue)
