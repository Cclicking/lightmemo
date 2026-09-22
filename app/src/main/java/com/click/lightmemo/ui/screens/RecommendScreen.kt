package com.click.lightmemo.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.click.lightmemo.data.DefaultPresetFoods
import com.click.lightmemo.data.DefaultRecommendationFoods
import com.click.lightmemo.data.PresetFood
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
private const val LowCalorieThresholdKcal = 350.0
private const val RecordedFoodShare = 0.20

private enum class RecommendationMode(val label: String) {
    CASUAL("随便吃"),
    HEALTHY("健康吃"),
    CHANGE("新口味"),
    HABITUAL("照旧吃"),
}

internal data class DishRecommendation(
    val preset: PresetFood,
    val nutrition: Nutrition,
    val score: Int,
    val reasons: List<String>,
)

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
    val recommendations = remember(
        state.foodPresets,
        state.recordedFoods,
        state.todayNutrition,
        state.target,
        state.proteinTarget,
        state.carbsTarget,
        state.fatTarget,
    ) {
        buildRecommendations(state)
    }
    var selectedMode by remember { mutableStateOf(RecommendationMode.CASUAL) }
    val modeRecommendations = remember(
        recommendations,
        selectedMode,
        state.foodFrequency,
        state.recentFoodNames,
        state.todayNutrition,
        state.target,
        state.proteinTarget,
        state.carbsTarget,
        state.fatTarget,
    ) {
        recommendationsForMode(recommendations, state, selectedMode)
    }
    val savedResultDishes = remember(recommendations, recommendationSession.dishName) {
        centerRecommendation(recommendations, recommendationSession.dishName)
    }
    val initialDisplayDishes = if (recommendationSession.hasResult) {
        savedResultDishes
    } else {
        idleRollingDishes(modeRecommendations)
    }
    var displayDishes by remember(recommendations) {
        mutableStateOf(initialDisplayDishes)
    }
    // Start at the focused slot so the selected border is never painted on the first card
    // while the initial LaunchedEffect is moving the carousel into position.
    val dishListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialDisplayDishes.focusedDishIndex(),
    )
    var showRecommendation by remember(recommendations) {
        mutableStateOf(recommendationSession.hasResult)
    }
    var initialRolling by remember(recommendations) {
        mutableStateOf(!recommendationSession.hasResult)
    }
    var carouselReady by remember(recommendations) {
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
    val pairingFoods = remember(selectedDish) {
        selectedDish?.let(::buildPairingFoods).orEmpty()
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
        if (isPicking || displayDishes.size < 2) return
        initialRolling = false
        scope.launch {
            val resultDishes = nextRecommendationDishes(
                recommendations = modeRecommendations,
                previousDishName = recommendationSession.dishName ?: selectedDish?.preset?.name,
            )
            val resultIndex = resultDishes.focusedDishIndex()
            val resultDish = resultDishes.getOrNull(resultIndex) ?: return@launch
            val targetIndex = FocusedDishIndex + RollSteps
            animateDishContent = false
            selectedBorderVisible = false
            isPicking = true
            showRecommendation = false
            // Let the previous result finish fading out before the next roll starts.
            delay(180)
            displayDishes = rollingDishes(
                recommendations = modeRecommendations,
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
            viewModel.saveRecommendationResult(resultDish.preset.name)
            isPicking = false
        }
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
                        if (!isPicking) selectedMode = RecommendationMode.entries[index]
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
                enabled = !isPicking && displayDishes.size > 1,
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
                    Text("抽选菜品")
                }
            }
        }
        item(key = "recommendation") {
            AnimatedVisibility(
                visible = showRecommendation && selectedDish != null,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(260),
                ) + fadeIn(animationSpec = tween(260)),
                exit = fadeOut(animationSpec = tween(180)),
            ) {
                selectedDish?.let { dish ->
                    RecommendationCard(
                        recommendation = dish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }
        item(key = "pairing") {
            val lowCalorieDish = selectedDish?.takeIf {
                it.nutrition.caloriesKcal <= LowCalorieThresholdKcal && pairingFoods.isNotEmpty()
            }
            AnimatedVisibility(
                visible = showRecommendation && lowCalorieDish != null,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 260, delayMillis = 80),
                ) + fadeIn(animationSpec = tween(durationMillis = 260, delayMillis = 80)),
                exit = fadeOut(animationSpec = tween(180)),
            ) {
                PairingCard(
                    foods = pairingFoods,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
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
    }
}

@Composable
private fun PairingCard(
    foods: List<PresetFood>,
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
            text = "这道菜热量较低，搭配下面的食物更容易吃饱，营养也更完整",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            foods.forEach { food ->
                Column(
                    modifier = Modifier
                        .weight(1f)
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
                        text = "${food.portionLabel} · ${food.nutrition?.caloriesKcal?.toInt() ?: 0} kcal",
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

private fun buildPairingFoods(recommendation: DishRecommendation): List<PresetFood> {
    val available = DefaultPresetFoods.filter { food ->
        food.id != recommendation.preset.id && food.name != recommendation.preset.name
    }
    val proteinOptions = available.filter { it.id in setOf("chicken_breast", "egg", "milk", "yogurt") }
    val stapleOptions = available.filter { it.id in setOf("rice", "mantou", "bread", "oats") }
    val lightOptions = available.filter { it.id in setOf("yogurt", "apple", "banana") }
    val result = mutableListOf<PresetFood>()

    fun addFirst(options: List<PresetFood>) {
        options.firstOrNull { candidate -> result.none { it.id == candidate.id } }?.let(result::add)
    }

    if (recommendation.nutrition.proteinG < 20.0) {
        addFirst(proteinOptions)
    }
    if (recommendation.nutrition.carbsG < 30.0) {
        addFirst(stapleOptions)
    }
    if (result.size < 2) {
        addFirst(lightOptions)
    }
    if (result.size < 2) {
        addFirst(proteinOptions)
    }
    if (result.size < 2) {
        addFirst(stapleOptions)
    }
    return result.take(2)
}

internal fun buildRecommendations(state: StatsViewModel.StatsUiState): List<DishRecommendation> {
    val baseCandidates = deduplicateFoods(
        DefaultRecommendationFoods +
            DefaultPresetFoods +
            state.foodPresets,
        )
    val baseNames = baseCandidates.mapTo(mutableSetOf()) { it.name.trim() }
    val recordedCandidates = deduplicateFoods(state.recordedFoods)
        .filterNot { it.name.trim() in baseNames }
    val recordedLimit = if (baseCandidates.isEmpty()) {
        recordedCandidates.size
    } else {
        ((baseCandidates.size * RecordedFoodShare) / (1.0 - RecordedFoodShare))
            .toInt()
            .coerceAtLeast(1)
    }
    val candidates = baseCandidates + recordedCandidates
        .sortedWith(
            compareByDescending<PresetFood> { state.foodFrequency[it.name.trim()].orZero() }
                .thenBy { it.name },
        )
        .take(recordedLimit)
    return candidates.map { preset ->
        val nutrition = presetNutrition(preset)
        DishRecommendation(
            preset = preset,
            nutrition = nutrition,
            score = recommendationScore(nutrition, state),
            reasons = recommendationReasons(preset, nutrition, state),
        )
    }.sortedByDescending { it.score }
}

private fun deduplicateFoods(foods: List<PresetFood>): List<PresetFood> = foods
    .asReversed()
    .distinctBy { it.name.trim() }
    .asReversed()

private fun recommendationsForMode(
    recommendations: List<DishRecommendation>,
    state: StatsViewModel.StatsUiState,
    mode: RecommendationMode,
): List<DishRecommendation> = when (mode) {
    RecommendationMode.CASUAL -> recommendations.shuffled()
    RecommendationMode.HABITUAL -> {
        val frequent = recommendations
            .filter { state.foodFrequency[it.preset.name.trim()].orZero() > 0 }
            .sortedWith(
                compareByDescending<DishRecommendation> {
                    state.foodFrequency[it.preset.name.trim()].orZero()
                }.thenByDescending { it.score },
            )
        if (frequent.size >= 2) frequent else {
            recommendations.sortedWith(
                compareByDescending<DishRecommendation> {
                    state.foodFrequency[it.preset.name.trim()].orZero()
                }.thenByDescending { it.score },
            )
        }
    }
    RecommendationMode.HEALTHY -> recommendations.sortedWith(
        compareByDescending<DishRecommendation> { healthyModePriority(it, state) }
            .thenByDescending { it.score },
    )
    RecommendationMode.CHANGE -> {
        val fresh = recommendations
            .filterNot { state.recentFoodNames.contains(it.preset.name.trim()) }
            .shuffled()
        val recentlyEaten = recommendations
            .filter { state.recentFoodNames.contains(it.preset.name.trim()) }
            .sortedByDescending { it.score }
        (fresh + recentlyEaten).distinctBy { it.preset.name.trim() }
    }
}

private fun healthyModePriority(
    recommendation: DishRecommendation,
    state: StatsViewModel.StatsUiState,
): Double {
    val today = state.todayNutrition
    val remainingCalories = (state.target - today.caloriesKcal).coerceAtLeast(0.0)
    val mealCalories = (remainingCalories / 2.0).coerceIn(300.0, 750.0)
    val proteinNeed = (state.proteinTarget - today.proteinG).coerceAtLeast(20.0)
    val carbsNeed = (state.carbsTarget - today.carbsG).coerceAtLeast(40.0)
    val fatRoom = (state.fatTarget - today.fatG).coerceAtLeast(1.0)
    val proteinCoverage = (recommendation.nutrition.proteinG / proteinNeed).coerceIn(0.0, 1.0)
    val carbsCoverage = (recommendation.nutrition.carbsG / carbsNeed).coerceIn(0.0, 1.0)
    val calorieFit = (
        1.0 - kotlin.math.abs(recommendation.nutrition.caloriesKcal - mealCalories) / mealCalories
    ).coerceIn(0.0, 1.0)
    val fatFit = (
        1.0 - (recommendation.nutrition.fatG - fatRoom).coerceAtLeast(0.0) / fatRoom
    ).coerceIn(0.0, 1.0)
    return proteinCoverage * 0.40 + carbsCoverage * 0.20 + calorieFit * 0.25 + fatFit * 0.15
}

private fun Int?.orZero(): Int = this ?: 0

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

/** Move through the score-sorted candidates so each new draw changes the dish. */
private fun nextRecommendationDishes(
    recommendations: List<DishRecommendation>,
    previousDishName: String?,
): List<DishRecommendation> {
    if (recommendations.isEmpty()) return emptyList()
    val previousIndex = recommendations.indexOfFirst { it.preset.name == previousDishName }
    val nextIndex = if (previousIndex < 0) {
        0
    } else {
        (previousIndex + 1) % recommendations.size
    }
    return centerRecommendation(recommendations, recommendations[nextIndex].preset.name)
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

private fun presetNutrition(preset: PresetFood): Nutrition {
    return preset.nutrition ?: preset.components
        .takeIf { components ->
            components.isNotEmpty() && components.all { it.nutritionReference != null }
        }
        ?.fold(Nutrition()) { total, component -> total + component.nutrition }
        ?: Nutrition()
}

private fun recommendationScore(nutrition: Nutrition, state: StatsViewModel.StatsUiState): Int {
    val today = state.todayNutrition
    val remainingCalories = (state.target - today.caloriesKcal).coerceAtLeast(0.0)
    val mealCalories = (remainingCalories / 2.0).coerceIn(300.0, 750.0)
    val calorieFit = (1.0 - kotlin.math.abs(nutrition.caloriesKcal - mealCalories) / mealCalories).coerceIn(0.0, 1.0)
    val proteinGap = (state.proteinTarget - today.proteinG).coerceAtLeast(0.0)
    val proteinCoverage = (nutrition.proteinG / proteinGap.coerceAtLeast(20.0)).coerceIn(0.0, 1.0)
    val fatAllowance = (state.fatTarget - today.fatG).coerceAtLeast(1.0)
    val fatFit = (1.0 - (nutrition.fatG - fatAllowance).coerceAtLeast(0.0) / fatAllowance).coerceIn(0.0, 1.0)
    return ((calorieFit * 0.35 + proteinCoverage * 0.45 + fatFit * 0.20) * 100.0)
        .roundToInt()
        .coerceIn(1, 99)
}

private fun recommendationReasons(
    preset: PresetFood,
    nutrition: Nutrition,
    state: StatsViewModel.StatsUiState,
): List<String> {
    val remainingCalories = (state.target - state.todayNutrition.caloriesKcal).coerceAtLeast(0.0)
    val mealCalories = (remainingCalories / 2.0).coerceIn(300.0, 750.0)
    val proteinGap = (state.proteinTarget - state.todayNutrition.proteinG).coerceAtLeast(0.0)
    val carbsGap = (state.carbsTarget - state.todayNutrition.carbsG).coerceAtLeast(0.0)
    val fatAllowance = (state.fatTarget - state.todayNutrition.fatG).coerceAtLeast(0.0)
    val wordingVariant = (preset.name.hashCode() and Int.MAX_VALUE) % 3
    return buildList {
        if (proteinGap >= 8.0 && nutrition.proteinG >= 10.0) {
            add(
                when (wordingVariant) {
                    0 -> "这份大约有 ${nutrition.proteinG.toInt()}g 蛋白质，吃完更顶饱"
                    1 -> "含约 ${nutrition.proteinG.toInt()}g 蛋白质，适合当今天的一顿正餐"
                    else -> "蛋白质约 ${nutrition.proteinG.toInt()}g，和主食、蔬菜搭配就很完整"
                },
            )
        } else if (nutrition.proteinG >= 10.0) {
            add(
                when (wordingVariant) {
                    0 -> "蛋白质约 ${nutrition.proteinG.toInt()}g，比只吃主食更顶饱"
                    1 -> "含约 ${nutrition.proteinG.toInt()}g 蛋白质，作为一餐比较扎实"
                    else -> "这道菜有 ${nutrition.proteinG.toInt()}g 蛋白质，适合配饭一起吃"
                },
            )
        }
        if (nutrition.caloriesKcal <= mealCalories * 1.15) {
            add(
                when (wordingVariant) {
                    0 -> "热量约 ${nutrition.caloriesKcal.toInt()} kcal，作为今天这一餐分量不重"
                    1 -> "一份约 ${nutrition.caloriesKcal.toInt()} kcal，今天吃它比较轻松"
                    else -> "约 ${nutrition.caloriesKcal.toInt()} kcal，放在正餐里刚好"
                },
            )
        }
        if (nutrition.fatG <= state.fatTarget / 4.0) {
            add(
                when (wordingVariant) {
                    0 -> "脂肪约 ${nutrition.fatG.toInt()}g，想吃清淡一点可以选它"
                    1 -> "脂肪只有约 ${nutrition.fatG.toInt()}g，比油炸类更轻"
                    else -> "脂肪约 ${nutrition.fatG.toInt()}g，今天少油一点时很合适"
                },
            )
        } else if (nutrition.fatG <= fatAllowance) {
            add(
                when (wordingVariant) {
                    0 -> "脂肪约 ${nutrition.fatG.toInt()}g，今天控制油量时还能安排"
                    1 -> "脂肪约 ${nutrition.fatG.toInt()}g，配一份蔬菜会更合适"
                    else -> "这份脂肪约 ${nutrition.fatG.toInt()}g，别再搭配太多油炸小菜就好"
                },
            )
        }
        if (carbsGap >= 20.0 && nutrition.carbsG >= 20.0) {
            add(
                when (wordingVariant) {
                    0 -> "碳水约 ${nutrition.carbsG.toInt()}g，能补上米饭或面条的能量"
                    1 -> "有约 ${nutrition.carbsG.toInt()}g 碳水，适合今天想吃主食的一顿"
                    else -> "这份约含 ${nutrition.carbsG.toInt()}g 碳水，吃它时主食不用点太多"
                },
            )
        } else if (nutrition.carbsG <= state.carbsTarget / 5.0) {
            add(
                when (wordingVariant) {
                    0 -> "碳水约 ${nutrition.carbsG.toInt()}g，不想吃太多米饭时可以选它"
                    1 -> "主食量不多，适合今天少吃一点面或饭"
                    else -> "这份碳水约 ${nutrition.carbsG.toInt()}g，和其他菜一起点也好控制"
                },
            )
        }
        if (preset.portionLabel.isNotBlank()) {
            add(
                when (wordingVariant) {
                    0 -> "建议吃${preset.portionLabel}（约 ${preset.defaultGrams.toInt()}g），分量一眼就能看清"
                    1 -> "点${preset.portionLabel}就够一餐，约 ${preset.defaultGrams.toInt()}g"
                    else -> "按${preset.portionLabel}来吃，约 ${preset.defaultGrams.toInt()}g，不用特意估份量"
                },
            )
        }
        if (isEmpty()) {
            add(
                when (wordingVariant) {
                    0 -> "没有明显短板，按推荐分量吃就可以"
                    1 -> "营养信息齐全，照着一份的量吃即可"
                    else -> "分量清楚，今天直接点一份比较合适"
                },
            )
        }
    }.take(3)
}

private fun recommendationBadge(nutrition: Nutrition): String = when {
    nutrition.proteinG >= 20.0 -> "高蛋白"
    nutrition.fatG <= 5.0 -> "低脂"
    nutrition.caloriesKcal <= 180.0 -> "轻食"
    else -> "营养均衡"
}
