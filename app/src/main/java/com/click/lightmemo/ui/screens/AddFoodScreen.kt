package com.click.lightmemo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.click.lightmemo.data.FoodImages
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.RecognizedDish
import com.click.lightmemo.domain.RecognitionStage
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.components.DropdownPref
import com.click.lightmemo.ui.overlay.BlurBottomSheet
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.AddFoodViewModel
import com.click.lightmemo.viewmodel.AddStep
import com.click.lightmemo.viewmodel.DatabaseSearchState
import com.click.lightmemo.viewmodel.DefaultMealTags
import com.click.lightmemo.data.PresetFood
import com.click.lightmemo.viewmodel.QuantityMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.os4.Close
import top.yukonga.miuix.kmp.icon.os4.Edit
import top.yukonga.miuix.kmp.icon.os4.FavoritesFill
import top.yukonga.miuix.kmp.icon.os4.Image
import top.yukonga.miuix.kmp.icon.os4.Photos
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private val AddFoodSheetHeight = 780.dp
private val timeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun stepOrder(step: AddStep): Int = when (step) {
    AddStep.PickSource -> 0
    is AddStep.Manual -> 1
    is AddStep.Review -> 2
}

private val ProteinTarget = 120f
private val CarbsTarget = 250f
private val FatTarget = 60f

@Composable
fun AddFoodRoute(
    viewModel: AddFoodViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    onDone: () -> Unit,
    palette: FoodPaletteColors = FoodPaletteColors.Default,
) {
    val state by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val gallerySaveScope = rememberCoroutineScope()

    var cameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraUri = cameraUriString?.let(Uri::parse)
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    var pendingRecognitionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val action = pendingRecognitionAction
        pendingRecognitionAction = null
        action?.invoke()
    }

    fun withNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingRecognitionAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            withNotificationPermission { viewModel.recognizeFromUri(uri) }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) {
            gallerySaveScope.launch {
                if (settings.savePhotosToGallery) {
                    runCatching {
                        FoodImages.saveToGallery(context, uri, settings.gallerySaveLocation)
                    }.onFailure {
                        android.widget.Toast.makeText(context, "照片未能保存到相册", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                withNotificationPermission { viewModel.recognizeFromUri(uri) }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            cameraUri?.let { cameraLauncher.launch(it) }
        } else {
            cameraPermissionDenied = true
        }
    }

    fun prepareCameraUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun onCameraClick() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        val uri = prepareCameraUri()
        cameraUriString = uri.toString()
        if (granted) {
            cameraPermissionDenied = false
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 预设弹层
    PresetOverlay(
        show = state.showPresetSheet,
        presets = state.presets,
        saving = state.saving,
        error = state.error,
        onDismiss = viewModel::closePresetSheet,
        onSelect = viewModel::applyPreset,
        onUpdate = viewModel::updatePreset,
    )

    DatabaseMatchOverlay(
        searchState = state.databaseSearch,
        onDismiss = viewModel::closeComponentSearch,
        onSearch = viewModel::searchComponentDatabase,
        onSelect = viewModel::selectComponentReference,
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val from = stepOrder(initialState)
                val to = stepOrder(targetState)
                val forward = to >= from
                // Bottom-sheet style: next step rises from the bottom, previous drops away.
                if (forward) {
                    slideInVertically(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetY = { it },
                    ) togetherWith slideOutVertically(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        targetOffsetY = { -it / 8 },
                    ) + fadeOut(tween(200))
                } else {
                    slideInVertically(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        initialOffsetY = { -it / 8 },
                    ) + fadeIn(tween(220)) togetherWith slideOutVertically(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        targetOffsetY = { it },
                    )
                }
            },
            label = "addFoodStep",
        ) { step ->
            when (step) {
                is AddStep.PickSource -> PickSourceContent(
                    padding = contentPadding,
                    configured = settings.isRecognitionConfigured,
                    recognizing = state.recognizing || state.saving,
                    recognitionStage = state.recognitionStage,
                    mealType = state.mealType,
                    onMealType = viewModel::setMealType,
                    quickInput = state.quickInput,
                    onQuickInputChange = viewModel::setQuickInput,
                    onQuickRecognize = { withNotificationPermission { viewModel.recognizeFromText() } },
                    selectedTags = state.selectedTags,
                    onToggleTag = viewModel::toggleTag,
                    minuteOfDay = state.mealMinuteOfDay,
                    onMinuteChange = viewModel::setMealMinuteOfDay,
                    targetDateEpochDay = state.targetDateEpochDay,
                    onDateChange = viewModel::setTargetDate,
                    note = state.note,
                    onNoteChange = viewModel::setNote,
                    error = state.error,
                    permissionHint = if (cameraPermissionDenied) {
                        "相机权限被拒绝，请在系统设置中开启，或改用相册"
                    } else {
                        null
                    },
                    onGallery = {
                        withNotificationPermission {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                    },
                    onCamera = { withNotificationPermission(::onCameraClick) },
                    onManual = { viewModel.openManual() },
                    onPreset = viewModel::openPresetSheet,
                )

                is AddStep.Manual -> ManualEntryContent(
                    padding = contentPadding,
                    mealType = state.mealType,
                    onMealType = viewModel::setMealType,
                    initialName = step.initialName,
                    initialGrams = step.initialGrams,
                    configured = settings.isRecognitionConfigured,
                    recognizing = state.recognizing || state.saving,
                    recognitionStage = state.recognitionStage,
                    nutritionSuggestion = state.manualNutrition,
                    error = state.error,
                    quantityMode = state.quantityMode,
                    onQuantityModeChange = viewModel::setQuantityMode,
                    portionCount = state.portionCount,
                    onPortionCountChange = viewModel::setPortionCount,
                    estimatedGrams = state.estimatedPortionGrams,
                    onRecognizeGrams = { name, grams ->
                        withNotificationPermission { viewModel.recognizeManual(name, grams) }
                    },
                    onRecognizePortions = { name, portions ->
                        withNotificationPermission { viewModel.recognizeManualWithPortions(name, portions) }
                    },
                    onSave = { name, grams, nutrition ->
                        viewModel.saveManual(name, grams, nutrition, onDone)
                    },
                )

                is AddStep.Review -> ReviewContent(
                    padding = contentPadding,
                    result = step.result,
                    recognizing = state.recognizing || state.saving,
                    recognitionStage = state.recognitionStage,
                    replacingDishId = state.replacingDishId,
                    mealType = state.mealType,
                    onMealType = viewModel::setMealType,
                    onWeightChange = viewModel::updateComponentWeight,
                    onRemoveComponent = viewModel::removeComponent,
                    onRemoveDish = viewModel::removeDish,
                    onDatabaseSearch = viewModel::openComponentSearch,
                    onReplaceDish = viewModel::replaceDish,
                    error = state.error,
                    onSave = {
                        viewModel.saveRecognized(step.result, step.imageUri, onDone)
                    },
                    listState = listState,
                    palette = palette,
                )
            }
        }
    }
}

@Composable
private fun PickSourceContent(
    padding: PaddingValues,
    configured: Boolean,
    recognizing: Boolean,
    recognitionStage: RecognitionStage?,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    quickInput: String,
    onQuickInputChange: (String) -> Unit,
    onQuickRecognize: () -> Unit,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    minuteOfDay: Int,
    onMinuteChange: (Int) -> Unit,
    targetDateEpochDay: Long,
    onDateChange: (LocalDate) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    error: String?,
    permissionHint: String?,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onManual: () -> Unit,
    onPreset: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    MealDatePickerOverlay(
        show = showDatePicker,
        date = LocalDate.ofEpochDay(targetDateEpochDay),
        onDismiss = { showDatePicker = false },
        onConfirm = {
            onDateChange(it)
            showDatePicker = false
        },
    )
    MealTimePickerOverlay(
        show = showTimePicker,
        minuteOfDay = minuteOfDay,
        onDismiss = { showTimePicker = false },
        onConfirm = {
            onMinuteChange(it)
            showTimePicker = false
        },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(AddFoodSheetHeight)
            .verticalScroll(rememberScrollState())
            .overScrollVertical()
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 40.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MealTypeSelector(mealType, onMealType)

        // 快速识别输入
        TextField(
            value = quickInput,
            onValueChange = onQuickInputChange,
            label = "点击输入，快速识别",
            singleLine = true,
            enabled = !recognizing,
            colors = sheetFieldColors(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { onQuickRecognize() }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (recognizing) RecognitionStatusCard(recognitionStage)

        if (!configured) {
            Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                Column {
                    Text("尚未配置识别 API", style = MiuixTheme.textStyles.title4)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "请到「我的 → 识别 API」填写 Base URL 与 API Key。手动录入不依赖网络。",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        // 本餐说明 + 时间
        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("本餐说明", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                    MealTagRow(selectedTags = selectedTags, onToggle = onToggleTag)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("时间", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimePill(
                            text = LocalDate.ofEpochDay(targetDateEpochDay).format(timeFormatter),
                            onClick = { showDatePicker = true },
                        )
                        TimePill(
                            text = minuteOfDay.toLocalTime().format(clockFormatter),
                            onClick = { showTimePicker = true },
                        )
                    }
                }
            }
        }

        TextField(
            value = note,
            onValueChange = onNoteChange,
            label = "请输入备注",
            singleLine = true,
            colors = sheetFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (permissionHint != null) {
            Text(
                text = permissionHint,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.subtitle,
            )
        }

        // 分隔后的录入方式：与备注更贴近
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MethodButton(
                icon = { Icon(MiuixIcons.Os4.Image, contentDescription = null) },
                label = if (configured) "拍照识别" else "拍照识别（需先配置 API）",
                enabled = configured && !recognizing,
                onClick = onCamera,
            )
            MethodButton(
                icon = { Icon(MiuixIcons.Os4.Photos, contentDescription = null) },
                label = "从相册选择",
                enabled = configured && !recognizing,
                onClick = onGallery,
            )
            MethodButton(
                icon = { Icon(MiuixIcons.Os4.Edit, contentDescription = null) },
                label = "手动录入",
                enabled = !recognizing,
                onClick = onManual,
            )
            MethodButton(
                icon = { Icon(MiuixIcons.Os4.FavoritesFill, contentDescription = null) },
                label = "预设",
                enabled = !recognizing,
                onClick = onPreset,
            )
        }

        if (error != null) {
            Text(
                text = error,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.subtitle,
            )
        }
    }
}

@Composable
private fun MealTagRow(selectedTags: Set<String>, onToggle: (String) -> Unit) {
    // 简单换行：两行标签
    val chunked = DefaultMealTags.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunked.forEach { rowTags ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowTags.forEach { tag ->
                    MealTagChip(
                        label = tag,
                        selected = tag in selectedTags,
                        onClick = { onToggle(tag) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 补齐不足一行的空白
                if (rowTags.size < 4) {
                    repeat(4 - rowTags.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MealTagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = isLightTheme()
    val bg = if (selected) {
        if (isLight) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
        else MiuixTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        if (isLight) MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.06f)
        else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f)
    }
    val fg = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = if (isLight) 0.72f else 0.92f)
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = if (isLight) 0.45f else 0.72f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun TimePill(text: String, onClick: () -> Unit) {
    // 与未选中说明标签同一套背景
    val bg = if (isLightTheme()) {
        MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.06f)
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface.copy(
                alpha = if (isLightTheme()) 0.45f else 0.72f,
            ),
        )
    }
}

@Composable
private fun MethodButton(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    highlightIcon: Boolean = false,
) {
    val isLight = isLightTheme()
    val contentColor = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.disabledOnSurface
    }
    // 浅色：白底；深色：与本餐说明 Card 一致（surfaceContainer）
    val container = when {
        !enabled && isLight -> MiuixTheme.colorScheme.secondaryVariant
        !enabled -> MiuixTheme.colorScheme.disabledSecondaryVariant
        isLight -> Color.White
        else -> MiuixTheme.colorScheme.surfaceContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            // 透明底 Favorites，深浅色都用 contentColor 着色
            icon()
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    }
}

@Composable
private fun ManualEntryContent(
    padding: PaddingValues,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    initialName: String,
    initialGrams: Double? = null,
    configured: Boolean,
    recognizing: Boolean,
    recognitionStage: RecognitionStage?,
    nutritionSuggestion: Nutrition?,
    error: String?,
    quantityMode: QuantityMode,
    onQuantityModeChange: (QuantityMode) -> Unit,
    portionCount: Double,
    onPortionCountChange: (Double) -> Unit,
    estimatedGrams: Double?,
    onRecognizeGrams: (String, Double) -> Unit,
    onRecognizePortions: (String, Double) -> Unit,
    onSave: (String, Double, Nutrition) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var grams by rememberSaveable { mutableStateOf(initialGrams?.formatInput() ?: "100") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var showPortionDialog by remember { mutableStateOf(false) }
    var showGramsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(nutritionSuggestion) {
        nutritionSuggestion?.let { nutrition ->
            kcal = nutrition.caloriesKcal.formatInput()
            protein = nutrition.proteinG.formatInput()
            carbs = nutrition.carbsG.formatInput()
            fat = nutrition.fatG.formatInput()
        }
    }

    LaunchedEffect(estimatedGrams) {
        estimatedGrams?.let { grams = it.formatInput() }
    }

    val effectiveGrams = when (quantityMode) {
        QuantityMode.GRAMS -> grams.toDoubleOrNull() ?: Double.NaN
        QuantityMode.PORTIONS -> if (estimatedGrams != null) grams.toDoubleOrNull() ?: Double.NaN else Double.NaN
    }
    val validInput = effectiveGrams.isFinite() && effectiveGrams > 0.0 &&
        listOf(kcal, protein, carbs, fat).all { value ->
            value.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(AddFoodSheetHeight)
            .verticalScroll(rememberScrollState())
            .overScrollVertical()
            .navigationBarsPadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 40.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MealTypeSelector(mealType, onMealType)

        if (recognizing) RecognitionStatusCard(recognitionStage)

        TextField(
            value = name,
            onValueChange = { name = it },
            label = "食物名称",
            singleLine = true,
            colors = sheetFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        // 计量方式 + 数量
        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column {
                DropdownPref(
                    title = "计量方式",
                    summary = if (quantityMode == QuantityMode.GRAMS) "按克重录入" else "用大模型估计重量",
                    items = QuantityMode.entries.map { it.label },
                    selectedIndex = QuantityMode.entries.indexOf(quantityMode).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        QuantityMode.entries.getOrNull(index)?.let(onQuantityModeChange)
                    },
                )

                if (quantityMode == QuantityMode.GRAMS) {
                    ArrowPreference(
                        title = "克数 g",
                        endActions = {
                            Text(
                                text = grams,
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = { showGramsDialog = true },
                    )
                } else {
                    ArrowPreference(
                        title = "份数",
                        summary = estimatedGrams?.let { "估计 ${it.formatInput()} g" } ?: "识别时用大模型估重",
                        endActions = {
                            Text(
                                text = "${portionCount.formatInput()} 份",
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = { showPortionDialog = true },
                    )
                    if (estimatedGrams != null) {
                        ArrowPreference(
                            title = "克数 g",
                            summary = "由份数估计，可手动调整",
                            endActions = {
                                Text(
                                    text = grams,
                                    fontSize = 14.5.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = { showGramsDialog = true },
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val n = name.trim()
                if (n.isEmpty()) return@Button
                when (quantityMode) {
                    QuantityMode.GRAMS -> onRecognizeGrams(n, effectiveGrams)
                    QuantityMode.PORTIONS -> onRecognizePortions(n, portionCount)
                }
            },
            enabled = configured && !recognizing && name.isNotBlank() &&
                (quantityMode == QuantityMode.PORTIONS || (effectiveGrams.isFinite() && effectiveGrams > 0)),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(if (recognizing) "正在识别营养..." else "自动识别热量与营养")
        }

        if (!configured) {
            Text(
                text = "自动识别需要先在「我的 → 识别 API」配置服务；也可以继续手动填写。",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (error != null) {
            Text(
                text = error,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.subtitle,
            )
        }

        Card(
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            Column {
                NutritionArrowRow("热量 kcal", kcal) { kcal = it }
                NutritionArrowRow("蛋白质 g", protein) { protein = it }
                NutritionArrowRow("碳水 g", carbs) { carbs = it }
                NutritionArrowRow("脂肪 g", fat) { fat = it }
            }
        }

        Button(
            onClick = {
                val n = name.trim()
                if (n.isEmpty()) return@Button
                onSave(
                    n,
                    effectiveGrams,
                    Nutrition(
                        caloriesKcal = kcal.toDoubleOrNull() ?: 0.0,
                        proteinG = protein.toDoubleOrNull() ?: 0.0,
                        carbsG = carbs.toDoubleOrNull() ?: 0.0,
                        fatG = fat.toDoubleOrNull() ?: 0.0,
                    ),
                )
            },
            enabled = name.isNotBlank() && validInput && !recognizing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("保存")
        }
    }

    NumberInputDialog(
        show = showGramsDialog,
        title = "克数",
        summary = "输入可食用重量",
        initial = grams,
        onDismiss = { showGramsDialog = false },
        onConfirm = { value ->
            val before = grams.toDoubleOrNull()
            val after = value.toDoubleOrNull()
            if (before != null && before > 0 && after != null && after.isFinite() && after > 0) {
                val ratio = after / before
                kcal = kcal.toDoubleOrNull()?.times(ratio)?.formatInput() ?: kcal
                protein = protein.toDoubleOrNull()?.times(ratio)?.formatInput() ?: protein
                carbs = carbs.toDoubleOrNull()?.times(ratio)?.formatInput() ?: carbs
                fat = fat.toDoubleOrNull()?.times(ratio)?.formatInput() ?: fat
            }
            grams = value
            showGramsDialog = false
        },
    )
    NumberInputDialog(
        show = showPortionDialog,
        title = "份数",
        summary = "例如 1 碗、2 份",
        initial = portionCount.formatInput(),
        allowDecimal = true,
        onDismiss = { showPortionDialog = false },
        onConfirm = { value ->
            value.toDoubleOrNull()?.let(onPortionCountChange)
            showPortionDialog = false
        },
    )
}

@Composable
private fun NutritionArrowRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    ArrowPreference(
        title = title,
        endActions = {
            Text(
                text = value.ifBlank { "0" },
                fontSize = 14.5.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = { showDialog = true },
    )
    NumberInputDialog(
        show = showDialog,
        title = title,
        initial = value,
        onDismiss = { showDialog = false },
        onConfirm = { draft ->
            onValueChange(draft)
            showDialog = false
        },
    )
}

@Composable
private fun NumberInputDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    initial: String,
    allowDecimal: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember(show, initial) { mutableStateOf(initial) }
    AnimatedOverlayDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = draft,
                onValueChange = { value ->
                    draft = value
                },
                singleLine = true,
                colors = dialogFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Text("取消")
                }
                Button(
                    onClick = { onConfirm(draft) },
                    enabled = draft.toDoubleOrNull()?.let { it.isFinite() && it >= 0 && (allowDecimal || it % 1.0 == 0.0) } == true,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
private fun MealDatePickerOverlay(
    show: Boolean,
    date: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var year by remember(date) { mutableStateOf(date.year) }
    var month by remember(date) { mutableStateOf(date.monthValue) }
    var day by remember(date) { mutableStateOf(date.dayOfMonth) }
    val maxDay = java.time.YearMonth.of(year, month).lengthOfMonth()
    LaunchedEffect(maxDay) { if (day > maxDay) day = maxDay }

    AnimatedOverlayDialog(show = show, title = "选择日期", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                NumberPicker(
                    value = year,
                    onValueChange = { year = it },
                    range = 2020..LocalDate.now().year + 1,
                    label = { "${it}年" },
                    visibleItemCount = 3,
                    modifier = Modifier.weight(1.25f),
                )
                NumberPicker(
                    value = month,
                    onValueChange = { month = it },
                    range = 1..12,
                    label = { "${it}月" },
                    visibleItemCount = 3,
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
                NumberPicker(
                    value = day,
                    onValueChange = { day = it },
                    range = 1..maxDay,
                    label = { "${it}日" },
                    visibleItemCount = 3,
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Text("取消")
                }
                Button(
                    onClick = { onConfirm(LocalDate.of(year, month, day)) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
private fun MealTimePickerOverlay(
    show: Boolean,
    minuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hour by remember(minuteOfDay) { mutableStateOf(minuteOfDay / 60) }
    var minute by remember(minuteOfDay) { mutableStateOf(minuteOfDay % 60) }

    AnimatedOverlayDialog(show = show, title = "选择时间", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                NumberPicker(
                    value = hour,
                    onValueChange = { hour = it },
                    range = 0..23,
                    label = { "%02d".format(it) },
                    visibleItemCount = 3,
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
                NumberPicker(
                    value = minute,
                    onValueChange = { minute = it },
                    range = 0..59,
                    label = { "%02d".format(it) },
                    visibleItemCount = 3,
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Text("取消")
                }
                Button(
                    onClick = { onConfirm(hour * 60 + minute) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
private fun PresetOverlay(
    show: Boolean,
    presets: List<PresetFood>,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSelect: (PresetFood) -> Unit,
    onUpdate: (PresetFood, () -> Unit) -> Unit,
) {
    var editing by remember { mutableStateOf<PresetFood?>(null) }
    BlurBottomSheet(
        show = show,
        title = "预设食物",
        dimBackground = true,
        sheetOffsetDp = 0.dp,
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .heightIn(min = 220.dp, max = 500.dp)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 61.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(presets, key = { it.id }) { preset ->
                Card(
                    cornerRadius = 14.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(12.dp),
                    pressFeedbackType = PressFeedbackType.Sink,
                    onClick = { onSelect(preset) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                            val nutritionText = preset.nutrition?.let {
                                "${it.caloriesKcal.toInt()} kcal · 蛋 ${it.proteinG.formatInput()}g · 碳 ${it.carbsG.formatInput()}g · 脂 ${it.fatG.formatInput()}g"
                            } ?: "未设置营养"
                            Text(
                                "${preset.portionLabel} · 约 ${preset.defaultGrams.toInt()}g",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                nutritionText,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Button(
                            onClick = { editing = preset },
                            minWidth = 48.dp,
                            minHeight = 28.dp,
                            insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("编辑", style = MiuixTheme.textStyles.footnote1)
                        }
                    }
                }
            }
        }
    }

    PresetEditDialog(
        show = editing != null,
        preset = editing,
        saving = saving,
        error = error,
        onDismiss = { if (!saving) editing = null },
        onSave = { updated ->
            onUpdate(updated) { editing = null }
        },
    )
}

@Composable
private fun PresetEditDialog(
    show: Boolean,
    preset: PresetFood?,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (PresetFood) -> Unit,
) {
    var displayedPreset by remember { mutableStateOf<PresetFood?>(null) }
    LaunchedEffect(preset) {
        if (preset != null) displayedPreset = preset
    }
    val currentPreset = displayedPreset
    if (currentPreset == null) return
    var name by remember(currentPreset.id) { mutableStateOf(currentPreset.name) }
    var grams by remember(currentPreset.id) { mutableStateOf(currentPreset.defaultGrams.formatInput()) }
    var kcal by remember(currentPreset.id) { mutableStateOf(currentPreset.nutrition?.caloriesKcal?.formatInput() ?: "") }
    var protein by remember(currentPreset.id) { mutableStateOf(currentPreset.nutrition?.proteinG?.formatInput() ?: "") }
    var carbs by remember(currentPreset.id) { mutableStateOf(currentPreset.nutrition?.carbsG?.formatInput() ?: "") }
    var fat by remember(currentPreset.id) { mutableStateOf(currentPreset.nutrition?.fatG?.formatInput() ?: "") }

    AnimatedOverlayDialog(
        title = "编辑预设",
        summary = "修改名称、默认克重与营养数据",
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = { if (!show) displayedPreset = null },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 浅色保持默认浅灰底，不强制白底
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "食物名称",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = grams,
                onValueChange = { grams = it },
                label = "默认克数 g",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = kcal,
                onValueChange = { kcal = it },
                label = "热量 kcal",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = protein,
                onValueChange = { protein = it },
                label = "蛋白质 g",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = carbs,
                onValueChange = { carbs = it },
                label = "碳水 g",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = fat,
                onValueChange = { fat = it },
                label = "脂肪 g",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        onSave(
                            currentPreset.copy(
                                name = name.trim().ifBlank { currentPreset.name },
                                defaultGrams = grams.toDoubleOrNull()?.takeIf { it > 0.0 } ?: currentPreset.defaultGrams,
                                nutrition = Nutrition(
                                    caloriesKcal = kcal.toDoubleOrNull() ?: 0.0,
                                    proteinG = protein.toDoubleOrNull() ?: 0.0,
                                    carbsG = carbs.toDoubleOrNull() ?: 0.0,
                                    fatG = fat.toDoubleOrNull() ?: 0.0,
                                ),
                            ),
                        )
                    },
                    enabled = !saving && name.isNotBlank() && grams.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true &&
                        listOf(kcal, protein, carbs, fat).all { it.toDoubleOrNull()?.let { n -> n.isFinite() && n >= 0 } == true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun ReviewContent(
    padding: PaddingValues,
    result: MealRecognition,
    recognizing: Boolean,
    recognitionStage: RecognitionStage?,
    replacingDishId: String?,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    onWeightChange: (String, Double) -> Unit,
    onRemoveComponent: (String) -> Unit,
    onRemoveDish: (String) -> Unit,
    onDatabaseSearch: (FoodComponent) -> Unit,
    onReplaceDish: (String, String) -> Unit,
    error: String?,
    onSave: () -> Unit,
    listState: LazyListState,
    palette: FoodPaletteColors,
) {
    val total = result.nutrition
    val calorieColor = if (total.caloriesKcal > 1800.0) palette.overTarget else palette.calorie
    var replacingDish by remember { mutableStateOf<RecognizedDish?>(null) }
    var replacementName by remember { mutableStateOf("") }
    AnimatedOverlayDialog(
        show = replacingDish != null,
        title = "更换菜品",
        summary = "输入正确的菜品名称，按当前重量重新识别组成和营养",
        onDismissRequest = { replacingDish = null },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = replacementName,
                onValueChange = { replacementName = it },
                label = "菜品名称",
                singleLine = true,
                colors = dialogFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    replacingDish?.let { onReplaceDish(it.id, replacementName) }
                    replacingDish = null
                },
                enabled = replacementName.isNotBlank() && !recognizing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("确认更换") }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(AddFoodSheetHeight)
            .navigationBarsPadding()
            .overScrollVertical(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { MealTypeSelector(mealType, onMealType) }

        if (recognizing) {
            item { RecognitionStatusCard(recognitionStage) }
        }

        // 总览卡片：热量 + 宏量环（进度条与圆环间距 27dp，圆环高度对齐文字）
        item {
            Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(27.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("热量", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${total.caloriesKcal.toInt()} kcal",
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        LinearProgressIndicator(
                            progress = (total.caloriesKcal / 1800.0).toFloat().coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                            height = 8.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = calorieColor),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MacroRing("蛋白质", total.proteinG, ProteinTarget, palette.protein, Modifier.weight(1f))
                        MacroRing("碳水", total.carbsG, CarbsTarget, palette.carbs, Modifier.weight(1f))
                        MacroRing("脂肪", total.fatG, FatTarget, palette.fat, Modifier.weight(1f))
                    }
                }
            }
        }

        if (result.imageQualityIssues.isNotEmpty()) {
            item {
                Text(
                    text = "图片质量：${result.imageQualityIssues.joinToString("；")}",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        // 每道菜一张可折叠卡片
        items(result.dishes, key = { it.id }) { dish ->
            DishResultCard(
                dish = dish,
                replacing = dish.id == replacingDishId,
                onWeightChange = onWeightChange,
                onRemoveComponent = onRemoveComponent,
                onRemoveDish = onRemoveDish,
                onDatabaseSearch = onDatabaseSearch,
                onReplaceDish = {
                    replacingDish = it
                    replacementName = it.name
                },
                enabled = !recognizing,
            )
        }

        if (result.confirmationQuestions.isNotEmpty()) {
            item {
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("建议确认", style = MiuixTheme.textStyles.title4)
                        result.confirmationQuestions.forEach { question ->
                            Text(
                                text = "• $question",
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }

        item {
            error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
            Button(
                onClick = onSave,
                enabled = !recognizing && result.dishes.isNotEmpty() && result.dishes.all { dish ->
                    dish.allComponents.isNotEmpty()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("保存 ${result.dishes.size} 道菜")
            }
        }
    }
}

@Composable
private fun RecognitionStatusCard(stage: RecognitionStage?) {
    val currentStage = stage ?: RecognitionStage.PREPARING
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Text(currentStage.title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = currentStage.progress / 100f,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            currentStage.detail,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "已启用后台通知，离开应用后会继续处理",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MacroRing(label: String, value: Double, target: Float, color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var ringSize by remember { mutableStateOf(32.dp) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            progress = (value / target).toFloat().coerceIn(0f, 1f),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
            size = ringSize,
            strokeWidth = (ringSize.value * 4f / 36f).dp.coerceAtLeast(3.dp),
        )
        Spacer(Modifier.width(6.dp))
        Column(
            Modifier.onSizeChanged { coords ->
                val textHeight = with(density) { coords.height.toDp() }
                if (textHeight > 0.dp && abs(textHeight.value - ringSize.value) > 0.5f) {
                    ringSize = textHeight
                }
            },
        ) {
            Text(label, style = MiuixTheme.textStyles.footnote2, maxLines = 1)
            Text("${value.formatInput()}g", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DishResultCard(
    dish: RecognizedDish,
    replacing: Boolean,
    onWeightChange: (String, Double) -> Unit,
    onRemoveComponent: (String) -> Unit,
    onRemoveDish: (String) -> Unit,
    onDatabaseSearch: (FoodComponent) -> Unit,
    onReplaceDish: (RecognizedDish) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember(dish.id) { mutableStateOf(true) }
    // ArrowRight 默认向右；展开时逆时针 90°（向上），收起再转 180°（向下）
    val expandIconRotation by animateFloatAsState(
        targetValue = if (expanded) -90f else 90f,
        animationSpec = tween(durationMillis = 200),
        label = "expandIconRotation",
    )

    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dish.name,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = enabled, onClickLabel = "更换菜品") { onReplaceDish(dish) },
                    )
                    Text(
                        text = "约 ${dish.nutrition.caloriesKcal.toInt()} kcal · ${dish.grams.toInt()}g",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        MiuixIcons.Os4.Close,
                        contentDescription = "移除菜品",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(enabled = enabled) { onRemoveDish(dish.id) }
                            .padding(4.dp),
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { expanded = !expanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            MiuixIcons.Basic.ArrowRight,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .size(width = 10.dp, height = 16.dp)
                                .rotate(expandIconRotation),
                        )
                    }
                }
            }

            if (replacing) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(progress = null, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "正在更换菜品…",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    dish.components.forEach { component ->
                        ComponentResultRow(
                            component = component,
                            onWeightChange = onWeightChange,
                            onRemove = onRemoveComponent,
                            onMatch = onDatabaseSearch,
                        )
                    }
                    dish.children.forEach { child ->
                        if (dish.components.isNotEmpty() || child != dish.children.first()) {
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(
                            child.name,
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        child.components.forEach { component ->
                            ComponentResultRow(
                                component = component,
                                onWeightChange = onWeightChange,
                                onRemove = onRemoveComponent,
                                onMatch = onDatabaseSearch,
                            )
                        }
                    }
                    if (dish.uncertaintyReason != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "主要误差：${dish.uncertaintyReason}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentResultRow(
    component: FoodComponent,
    onWeightChange: (String, Double) -> Unit,
    onRemove: (String) -> Unit,
    onMatch: (FoodComponent) -> Unit,
) {
    var input by remember(component.id) { mutableStateOf(component.estimatedWeightG.formatInput()) }
    var showEdit by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(component.name, style = MiuixTheme.textStyles.body1)
            val ref = component.nutritionReference
            Text(
                text = ref?.let { "来自 USDA #${it.sourceId}" } ?: "未匹配到营养数据",
                style = MiuixTheme.textStyles.footnote2,
                color = if (ref == null) MiuixTheme.colorScheme.error
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ref == null) {
                Text(
                    text = "手动匹配数据库",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onMatch(component) },
                )
            }
        }

        // 克重
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.06f))
                .clickable { showEdit = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("${input}g", style = MiuixTheme.textStyles.footnote1)
        }

        Text(
            text = "${component.nutrition.caloriesKcal.toInt()} kcal",
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )

        Icon(
            MiuixIcons.Os4.Close,
            contentDescription = "移除",
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable { onRemove(component.id) }
                .padding(2.dp),
        )
    }

    NumberInputDialog(
        show = showEdit,
        title = component.name,
        summary = "修改克重",
        initial = input,
        onDismiss = { showEdit = false },
        onConfirm = { draft ->
            input = draft
            draft.toDoubleOrNull()?.let { onWeightChange(component.id, it) }
            showEdit = false
        },
    )
}

@Composable
private fun DatabaseMatchOverlay(
    searchState: DatabaseSearchState?,
    onDismiss: () -> Unit,
    onSearch: (String, String) -> Unit,
    onSelect: (String, com.click.lightmemo.domain.NutritionReference) -> Unit,
) {
    var displayedState by remember { mutableStateOf<DatabaseSearchState?>(null) }
    LaunchedEffect(searchState) {
        if (searchState != null) displayedState = searchState
    }
    var query by remember(displayedState?.componentId) {
        mutableStateOf(displayedState?.query.orEmpty())
    }

    val current = displayedState
    if (current != null) {
        AnimatedOverlayDialog(
            show = searchState != null,
            title = "手动匹配数据库",
            summary = "选择正确的食物后会立即回填营养数据",
            onDismissRequest = onDismiss,
            onDismissFinished = { displayedState = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索食物名称",
                    singleLine = true,
                    colors = dialogFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                Button(
                    onClick = { onSearch(current.componentId, query) },
                    enabled = !current.loading && query.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (current.loading) "查询中…" else "查询数据库")
                }
                if (current.loading) {
                    LinearProgressIndicator(progress = null, modifier = Modifier.fillMaxWidth())
                }
                current.error?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(current.results, key = { "${it.dataType}:${it.sourceId}" }) { reference ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 14.dp,
                            insideMargin = PaddingValues(12.dp),
                            onClick = { onSelect(current.componentId, reference) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(reference.description, style = MiuixTheme.textStyles.body1)
                                    Text(
                                        text = "每 100g · ${reference.dataType}",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${reference.per100g.caloriesKcal.toInt()} kcal",
                                    style = MiuixTheme.textStyles.title4,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MealTypeSelector(
    selected: MealType,
    onSelect: (MealType) -> Unit,
) {
    val types = MealType.entries
    TabRowWithContour(
        tabs = types.map { it.label },
        selectedTabIndex = types.indexOf(selected).coerceAtLeast(0),
        onTabSelected = { index -> onSelect(types[index]) },
    )
}

private fun Double.formatInput(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(java.util.Locale.ROOT, this)

private fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)

@Composable
private fun isLightTheme(): Boolean {
    val bg = MiuixTheme.colorScheme.background
    return (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) > 0.5f
}

@Composable
private fun sheetFieldColors(): TextFieldColors {
    return if (isLightTheme()) {
        TextFieldDefaults.textFieldColors(backgroundColor = Color.White)
    } else {
        TextFieldDefaults.textFieldColors()
    }
}

@Composable
private fun dialogFieldColors(): TextFieldColors {
    return if (isLightTheme()) {
        TextFieldDefaults.textFieldColors(backgroundColor = MiuixTheme.colorScheme.secondaryContainer)
    } else {
        TextFieldDefaults.textFieldColors()
    }
}
