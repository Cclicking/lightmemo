package com.click.lightmemo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.click.lightmemo.domain.DishRecommendation
import com.click.lightmemo.domain.RecommendationMode
import com.click.lightmemo.domain.PairingRecommendationEngine
import com.click.lightmemo.domain.PairingResult
import com.click.lightmemo.domain.RecommendationEngine
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.StatsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DishCardWidth = 104.dp
private const val FocusedDishIndex = 2
private const val RollSteps = 12
private const val InitialRollDurationMillis = 3000
private const val InitialRollCycles = 4
@Composable
fun RecommendScreen(
    viewModel: StatsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val state by viewModel.uiState.collectAsState()
    val readError by viewModel.readError.collectAsState()
    val recommendationSession by viewModel.recommendationSession.collectAsState()
    LaunchedEffect(Unit) { viewModel.recommendationOpened() }
    val feedback by viewModel.recommendationFeedback.collectAsState()
    val operation by viewModel.recommendationOperation.collectAsState()
    val androidContext = LocalContext.current
    LaunchedEffect(operation.message) {
        operation.message?.let { Toast.makeText(androidContext, it, Toast.LENGTH_SHORT).show() }
    }
    val context = remember(state, feedback) { viewModel.recommendationContext(state) }
    val recommendations = remember(context) { RecommendationEngine.rank(context) }
    var selectedMode by remember { mutableStateOf(RecommendationMode.CASUAL) }
    val modeRecommendations = remember(recommendations, selectedMode) {
        RecommendationEngine.forMode(recommendations, context, selectedMode)
    }
    var confirmationDish by remember { mutableStateOf<DishRecommendation?>(null) }
    val savedResultDishes = remember(recommendations, recommendationSession.dishName) {
        centerRecommendation(recommendations, recommendationSession.dishName)
    }
    val initialDisplayDishes = if (recommendationSession.hasResult) {
        savedResultDishes
    } else {
        idleRollingDishes(modeRecommendations)
    }
    var displayDishes by remember {
        mutableStateOf(initialDisplayDishes)
    }
    // Start at the focused slot so the selected border is never painted on the first card
    // while the initial LaunchedEffect is moving the carousel into position.
    val dishListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialDisplayDishes.focusedDishIndex(),
    )
    var showRecommendation by remember {
        mutableStateOf(recommendationSession.hasResult)
    }
    var initialRolling by remember {
        mutableStateOf(!recommendationSession.hasResult)
    }
    var carouselReady by remember {
        mutableStateOf(false)
    }
    var animateDishContent by remember { mutableStateOf(false) }
    var selectedBorderVisible by remember { mutableStateOf(!initialRolling) }
    var suppressResultSync by remember { mutableStateOf(false) }
    var isPicking by remember { mutableStateOf(false) }
    var modeInitialized by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val carouselStepPx = with(LocalDensity.current) { (DishCardWidth + 6.dp).toPx() }
    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = dishListState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { item -> abs(item.offset + item.size / 2 - viewportCenter) }
                ?.index
                ?.coerceIn(0, (displayDishes.size - 1).coerceAtLeast(0))
                ?: 0
        }
    }
    val selectedDish = displayDishes.getOrNull(centeredIndex)?.takeIf {
        showRecommendation && carouselReady
    }
    val pairingResult = remember(selectedDish, state, recommendationSession.savedDishName) {
        selectedDish?.let { dish ->
            PairingRecommendationEngine.recommend(dish, viewModel.pairingContext(state,
                mainAlreadyRecorded = recommendationSession.savedDishName == dish.preset.name))
        }
    }

    LaunchedEffect(initialRolling, selectedMode, recommendationSession.hasResult, recommendations) {
        if (!initialRolling || recommendationSession.hasResult || displayDishes.size < 2) {
            return@LaunchedEffect
        }
        val resetIndex = initialRollResetIndex(modeRecommendations.size)
        delay(240)
        while (true) {
            if (dishListState.firstVisibleItemIndex >= resetIndex || !dishListState.canScrollForward) {
                dishListState.scrollToItem(FocusedDishIndex)
            }
            dishListState.animateScrollBy(
                value = carouselStepPx,
                animationSpec = tween(durationMillis = InitialRollDurationMillis, easing = LinearEasing),
            )
        }
    }

    LaunchedEffect(recommendations, recommendationSession.hasResult, recommendationSession.dishName) {
        // Room/settings can emit while rolling; moving the list here cancels its animation.
        if (isPicking) return@LaunchedEffect
        if (recommendationSession.hasResult) {
            if (suppressResultSync) {
                suppressResultSync = false
                return@LaunchedEffect
            }
            carouselReady = false
            val alreadyShowingResult = showRecommendation &&
                displayDishes.getOrNull(centeredIndex)?.preset?.name == recommendationSession.dishName
            if (!alreadyShowingResult) {
                displayDishes = savedResultDishes
                showRecommendation = true
                if (displayDishes.size > 1) {
                    dishListState.scrollToItem(displayDishes.focusedDishIndex())
                }
            }
        } else {
            carouselReady = false
            displayDishes = if (initialRolling) {
                idleRollingDishes(modeRecommendations)
            } else {
                rollingDishes(modeRecommendations)
            }
            showRecommendation = false
            if (displayDishes.size > 1) {
                dishListState.scrollToItem(displayDishes.focusedDishIndex())
            }
        }
        withFrameNanos { }
        carouselReady = true
    }

    LaunchedEffect(selectedMode) {
        if (modeInitialized) {
            carouselReady = false
            displayDishes = if (initialRolling) {
                idleRollingDishes(modeRecommendations)
            } else {
                rollingDishes(modeRecommendations)
            }
            showRecommendation = false
            animateDishContent = false
            selectedBorderVisible = true
            if (displayDishes.size > 1) {
                dishListState.scrollToItem(displayDishes.focusedDishIndex())
            }
            withFrameNanos { }
            carouselReady = true
        }
        modeInitialized = true
    }

    fun pickDish() {
        if (isPicking || operation.busy || modeRecommendations.isEmpty()) return
        initialRolling = false
        scope.launch {
            val drawPool = modeRecommendations
            val previousDish = selectedDish?.preset?.name?.takeUnless { it == recommendationSession.savedDishName }
            val resultDish = RecommendationEngine.pick(drawPool, recommendationSession.recentResults) ?: return@launch
            if (drawPool.size == 1) {
                displayDishes = listOf(resultDish)
                showRecommendation = true
                carouselReady = true
                viewModel.completeRecommendationPick(resultDish.preset.name, previousDish)
                return@launch
            }
            val targetIndex = FocusedDishIndex + RollSteps
            animateDishContent = false
            selectedBorderVisible = false
            isPicking = true
            try {
                showRecommendation = false
                // Let the previous result finish fading out before the next roll starts.
                delay(180)
                displayDishes = rollingDishes(
                    recommendations = drawPool,
                    targetIndex = targetIndex,
                    targetDishName = resultDish.preset.name,
                )
                dishListState.scrollToItem(displayDishes.focusedDishIndex())
                dishListState.animateScrollBy(
                    value = carouselStepPx * RollSteps,
                    animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing),
                )
                // The result was already placed at this index, so this only corrects tiny pixel rounding.
                dishListState.scrollToItem(targetIndex)
                animateDishContent = true
                delay(50)
                showRecommendation = true
                selectedBorderVisible = true
                suppressResultSync = true
                viewModel.completeRecommendationPick(resultDish.preset.name, previousDish)
            } finally {
                isPicking = false
            }
        }
    }

    RecommendationRecordDialog(
        dish = confirmationDish,
        operation = operation,
        onDismiss = { if (!operation.busy) confirmationDish = null },
        onSave = { dish, grams, meal -> viewModel.acceptRecommendation(dish, grams, meal) },
    )
    LaunchedEffect(recommendationSession.savedDishName) {
        if (recommendationSession.savedDishName != null) confirmationDish = null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        readError?.let { message ->
            item {
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item {
            // Keep the same SmallTitle inset and title-to-content rhythm as the other pages.
            SmallTitle(
                text = "今天吃什么",
                modifier = Modifier.padding(horizontal = 16.dp),
                insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
            )
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                TabRow(
                    tabs = RecommendationMode.entries.map { it.label },
                    selectedTabIndex = RecommendationMode.entries.indexOf(selectedMode),
                    onTabSelected = { index ->
                        if (!isPicking && !operation.busy) selectedMode = RecommendationMode.entries[index]
                    },
                )
            }
        }
        item {
            DishCarousel(
                dishes = displayDishes,
                selectedIndex = if (initialRolling || !carouselReady) -1 else centeredIndex,
                listState = dishListState,
                selectedBorderVisible = selectedBorderVisible,
                animateDishContent = animateDishContent,
                recommendationVisible = showRecommendation,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = ::pickDish,
                enabled = !isPicking && !operation.busy && modeRecommendations.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColorsPrimary(),
                insideMargin = PaddingValues(vertical = 8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("✦", fontSize = 20.sp, color = Color.White)
                    Text(if (selectedDish != null) "换一个" else "抽选菜品")
                }
            }
        }
        item(key = "recommendation") {
            RecommendationResultEntrance(visible = showRecommendation && selectedDish != null) {
                selectedDish?.let { dish ->
                    RecommendationCard(
                        recommendation = dish,
                        liked = feedback.any { it.foodName == com.click.lightmemo.domain.normalizeFoodName(dish.preset.name) && it.likedCount > 0 },
                        saved = recommendationSession.savedDishName == dish.preset.name,
                        enabled = !isPicking && !operation.busy,
                        onLike = { viewModel.likeRecommendation(dish.preset.name) },
                        onAccept = { viewModel.clearRecommendationMessage(); confirmationDish = dish },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }
        if (pairingResult?.foods?.isNotEmpty() == true) {
            item(key = "pairing") {
                RecommendationResultEntrance(visible = showRecommendation, delayMillis = 80) {
                    PairingCard(
                        result = pairingResult,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/** Starts hidden even when a lazy item is first composed after the result becomes visible. */
@Composable
private fun RecommendationResultEntrance(visible: Boolean, delayMillis: Int = 0, content: @Composable () -> Unit) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    AnimatedVisibility(
        visibleState = visibility,
        enter = slideInHorizontally(initialOffsetX = { it },
            animationSpec = tween(durationMillis = 260, delayMillis = delayMillis)) +
            fadeIn(animationSpec = tween(durationMillis = 260, delayMillis = delayMillis)),
        exit = fadeOut(animationSpec = tween(180)),
    ) { content() }
}

@Composable
private fun DishCarousel(
    dishes: List<DishRecommendation>,
    selectedIndex: Int,
    listState: LazyListState,
    selectedBorderVisible: Boolean,
    animateDishContent: Boolean,
    recommendationVisible: Boolean,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val sidePadding = ((maxWidth - DishCardWidth) / 2).coerceAtLeast(0.dp)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            userScrollEnabled = false,
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(dishes, key = { index, item -> "${item.preset.id}-$index" }) { index, dish ->
                val selected = index == selectedIndex
                val emphasized = selected && selectedBorderVisible
                val emphasis by animateFloatAsState(
                    targetValue = if (emphasized) 1f else 0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "selectedDishEmphasis",
                )
                val contentAlpha by animateFloatAsState(
                    targetValue = if (!selected || !animateDishContent || recommendationVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "selectedDishContent",
                )
                Card(
                    modifier = Modifier
                        .width(DishCardWidth)
                        .height(110.dp)
                        .then(
                            if (emphasis > 0f) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = MiuixTheme.colorScheme.primary.copy(alpha = emphasis),
                                    shape = RoundedCornerShape(18.dp),
                                )
                            } else {
                                Modifier
                            },
                        ),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    val regularStyle = MiuixTheme.textStyles.body2
                    val emphasizedStyle = MiuixTheme.textStyles.body1
                    val dishNameStyle = regularStyle.copy(
                        fontSize = lerp(
                            regularStyle.fontSize,
                            emphasizedStyle.fontSize,
                            emphasis,
                        ),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(contentAlpha),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = dish.preset.name,
                            style = dishNameStyle,
                            fontWeight = FontWeight(
                                (FontWeight.Normal.weight +
                                    (FontWeight.SemiBold.weight - FontWeight.Normal.weight) * emphasis)
                                    .roundToInt(),
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = recommendationBadge(dish.nutrition),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: DishRecommendation,
    liked: Boolean,
    saved: Boolean,
    enabled: Boolean,
    onLike: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "为你推荐",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
                Text(
                    text = recommendation.preset.name,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "推荐度 ${recommendation.score}%",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "推荐理由",
            style = MiuixTheme.textStyles.subtitle,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            recommendation.reasons.forEach { reason ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Os4.Ok,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = reason,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onLike, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text(if (liked) "取消喜欢" else "喜欢")
            }
            Button(onClick = onAccept, enabled = enabled && !saved, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) {
                Text(if (saved) "已记录" else "就吃这个")
            }
        }
    }
}

@Composable
private fun PairingCard(
    result: PairingResult,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "推荐搭配",
            style = MiuixTheme.textStyles.subtitle,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = result.summary,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (result.foods.isNotEmpty()) Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            result.foods.forEach { pairing ->
                val food = pairing.food
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = food.name,
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${food.portionLabel} · ${food.defaultGrams.roundToInt()}g · ${pairing.nutrition.caloriesKcal.roundToInt()} kcal",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(pairing.reason, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
}

private fun initialRollResetIndex(recommendationCount: Int): Int =
    FocusedDishIndex + recommendationCount * InitialRollCycles

/** Keep several complete cycles so the idle carousel can jump to an identical sequence. */
private fun idleRollingDishes(
    recommendations: List<DishRecommendation>,
): List<DishRecommendation> {
    if (recommendations.size < 2) return recommendations
    return rollingDishes(
        recommendations = recommendations,
        targetIndex = initialRollResetIndex(recommendations.size) + 4,
    )
}

/** Keep enough repeated cards for every fast roll to retain a neighbor on the right. */
private fun rollingDishes(
    recommendations: List<DishRecommendation>,
    targetIndex: Int? = null,
    targetDishName: String? = null,
): List<DishRecommendation> {
    if (recommendations.size < 2) return recommendations
    val targetDish = targetDishName?.let { name ->
        recommendations.firstOrNull { it.preset.name == name }
    }
    val rollingPool = recommendations
        .filter { it.preset.id != targetDish?.preset?.id }
        .shuffled()
        .ifEmpty { recommendations.shuffled() }
    val minimumSize = maxOf(18, (targetIndex ?: 0) + 3)
    val cards = List(minimumSize) { rollingPool[it % rollingPool.size] }.toMutableList()
    if (targetDish != null && targetIndex != null && targetIndex in cards.indices) {
        cards[targetIndex] = targetDish
    }
    return cards
}

private fun List<DishRecommendation>.focusedDishIndex(): Int = when {
    size > FocusedDishIndex -> FocusedDishIndex
    size > 1 -> 1
    else -> 0
}

/** Put the selected result in the third slot so both edges can show a partial neighbor. */
private fun centerRecommendation(
    recommendations: List<DishRecommendation>,
    dishName: String?,
): List<DishRecommendation> {
    if (recommendations.size < 2) return recommendations
    val selected = recommendations.firstOrNull { it.preset.name == dishName } ?: recommendations.first()
    val neighbors = recommendations.filter { it.preset.id != selected.preset.id }
    val left = neighbors.firstOrNull() ?: return recommendations
    val secondLeft = neighbors.getOrNull(1) ?: left
    val remaining = recommendations.filter {
        it.preset.id != selected.preset.id &&
            it.preset.id != left.preset.id &&
            it.preset.id != secondLeft.preset.id
    }
    // Keep two cards before the focus and at least one card after it for a symmetric carousel.
    return if (remaining.isEmpty()) {
        listOf(left, secondLeft, selected, left)
    } else {
        listOf(left, secondLeft, selected) + remaining
    }
}

private fun recommendationBadge(nutrition: Nutrition): String = when {
    nutrition.proteinG >= 20.0 -> "高蛋白"
    nutrition.fatG <= 5.0 -> "低脂"
    nutrition.caloriesKcal <= 180.0 -> "轻食"
    else -> "营养均衡"
}
