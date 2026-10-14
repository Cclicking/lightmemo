package com.click.lightmemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.click.lightmemo.data.StatsCard
import com.click.lightmemo.data.StatsCardLayout
import com.click.lightmemo.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.Menu
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class StatsDrag(
    val id: String,
    val bounds: Rect,
    val delta: Offset = Offset.Zero,
) {
    val centerY get() = bounds.center.y + delta.y
}

@Composable
fun ManageStatsScreen(viewModel: SettingsViewModel, contentPadding: PaddingValues) {
    val settings by viewModel.settings.collectAsState()
    val ready by viewModel.settingsReady.collectAsState()
    val error by viewModel.error.collectAsState()
    var layout by remember { mutableStateOf(settings.statsCardLayout.normalized()) }
    LaunchedEffect(settings.statsCardLayout) { layout = settings.statsCardLayout.normalized() }
    var drag by remember { mutableStateOf<StatsDrag?>(null) }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    var hiddenBounds by remember { mutableStateOf(Rect.Zero) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val edgePx = with(density) { 56.dp.toPx() }
    val speedPx = with(density) { 8.dp.toPx() }
    val currentDrag by rememberUpdatedState(drag)

    fun save(next: StatsCardLayout) {
        layout = next
        viewModel.setStatsCardLayout(next)
    }

    fun destination(active: StatsDrag): Pair<Boolean, Int> {
        val toHidden = active.centerY >= hiddenBounds.top
        val group = if (toHidden) layout.hidden else layout.visible
        val index = group.filterNot { it == active.id }.count { id ->
            cardBounds[id]?.center?.y?.let { active.centerY > it } == true
        }
        return toHidden to index
    }

    LaunchedEffect(drag?.id) {
        while (currentDrag != null) {
            val centerY = currentDrag?.centerY ?: break
            val amount = when {
                centerY < viewport.top + edgePx -> -speedPx
                centerY > viewport.bottom - edgePx -> speedPx
                else -> 0f
            }
            if (amount != 0f) scroll.scrollTo((scroll.value + amount.roundToInt()).coerceIn(0, scroll.maxValue))
            delay(16)
        }
    }

    Box(Modifier.fillMaxSize().padding(contentPadding).onGloballyPositioned { viewport = it.boundsInRoot() }) {
        if (!ready) return@Box
        val drop = drag?.let { destination(it) }
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            @Composable
            fun Group(title: String, hidden: Boolean, ids: List<String>) {
                Column(
                    Modifier.fillMaxWidth().then(if (hidden) Modifier.onGloballyPositioned {
                        hiddenBounds = it.boundsInRoot()
                    } else Modifier),
                ) {
                    SmallTitle(title, modifier = Modifier.offset(x = (-16).dp))
                    val others = ids.filterNot { it == drag?.id }
                    var insertion = 0
                    ids.forEach { id ->
                        val card = StatsCard.entries.first { it.id == id }
                        if (drop?.first == hidden && drop.second == insertion && id != drag?.id) {
                            StatsDropLine()
                        }
                        key(id) {
                            StatsManagementCard(
                                card = card,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                                    .onGloballyPositioned { cardBounds[id] = it.boundsInRoot() }
                                    .alpha(if (drag?.id == id) 0.25f else 1f)
                                    .semantics {
                                        customActions = listOf(
                                            CustomAccessibilityAction(if (hidden) "移到展示" else "移到隐藏") {
                                                save(layout.move(id, !hidden, if (hidden) layout.visible.size else layout.hidden.size)); true
                                            },
                                            CustomAccessibilityAction("上移") {
                                                save(layout.move(id, hidden, (ids.indexOf(id) - 1).coerceAtLeast(0))); true
                                            },
                                            CustomAccessibilityAction("下移") {
                                                save(layout.move(id, hidden, (ids.indexOf(id) + 1).coerceAtMost(ids.lastIndex))); true
                                            },
                                        )
                                    },
                                handleModifier = Modifier.pointerInput(id) {
                                    detectDragGestures(
                                        onDragStart = { cardBounds[id]?.let { drag = StatsDrag(id, it) } },
                                        onDragCancel = { drag = null },
                                        onDragEnd = {
                                            drag?.let { active ->
                                                val (toHidden, index) = destination(active)
                                                save(layout.move(id, toHidden, index))
                                            }
                                            drag = null
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            drag = drag?.let { it.copy(delta = it.delta + amount) }
                                        },
                                    )
                                },
                            )
                        }
                        if (id != drag?.id) insertion++
                    }
                    if (drop?.first == hidden && drop.second == others.size) StatsDropLine()
                    if (ids.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                            Text(if (hidden) "拖到这里隐藏卡片" else "拖到这里展示卡片",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            }
            Group("展示", false, layout.visible)
            Group("隐藏", true, layout.hidden)
            error?.let { Text(it, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.error) }
        }
        drag?.let { active ->
            val card = StatsCard.entries.first { it.id == active.id }
            StatsManagementCard(
                card,
                modifier = Modifier.zIndex(1f).offset {
                    IntOffset((active.bounds.left - viewport.left).roundToInt(),
                        (active.bounds.top + active.delta.y - viewport.top).roundToInt())
                }.width(with(density) { active.bounds.width.toDp() }),
            )
        }
    }
}

@Composable
private fun StatsDropLine() {
    Box(Modifier.fillMaxWidth().height(2.dp).background(MiuixTheme.colorScheme.primary))
}

@Composable
private fun StatsManagementCard(card: StatsCard, modifier: Modifier = Modifier, handleModifier: Modifier = Modifier) {
    Card(modifier = modifier, cornerRadius = 20.dp, insideMargin = PaddingValues(start = 24.dp, end = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(card.title, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.headline1)
            Box(handleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(MiuixIcons.Os4.Menu, contentDescription = "拖动${card.title}", modifier = Modifier.size(24.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
            }
        }
    }
}
