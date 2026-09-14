package com.click.lightmemo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.offset
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
import com.click.lightmemo.viewmodel.NewComponentId
import com.click.lightmemo.viewmodel.newFoodComponent
import com.click.lightmemo.domain.ComponentSource
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.RecognizedDish
import com.click.lightmemo.domain.RecognitionStage
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.components.ComponentDatabaseOverlay
import com.click.lightmemo.ui.components.DropdownPref
import com.click.lightmemo.ui.overlay.BlurBottomSheet
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.AddFoodViewModel
import com.click.lightmemo.viewmodel.AddStep
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
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.SmallTitle
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
    is AddStep.Manual, AddStep.PresetList -> 1
    is AddStep.ManualEdit, is AddStep.PresetEdit -> 2
    is AddStep.Review -> 3
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

    // 二级页：系统返回键回上级；有内容时先确认
    val isSecondaryStep = state.step !is AddStep.PickSource
    BackHandler(enabled = isSecondaryStep && !state.saving) {
        viewModel.requestSecondaryBack()
    }
    LeaveConfirmDialog(
        show = state.showLeaveConfirm,
        onDismiss = viewModel::dismissLeaveConfirm,
        onConfirm = viewModel::confirmLeaveSecondary,
    )

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

    ComponentDatabaseOverlay(
        searchState = state.databaseSearch,
        adding = false,
        onDismiss = viewModel::closeComponentSearch,
        onSearch = viewModel::searchComponentDatabase,
        onSelect = { componentId, query, _, reference ->
            viewModel.selectComponentReference(componentId, query, reference)
        },
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
                    onPreset = viewModel::openPresetList,
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
                    onOpenManualEdit = { name, grams ->
                        viewModel.openManualEdit(
                            initialName = name,
                            initialGrams = grams,
                            initialNutrition = null,
                        )
                    },
                    onHasContentChange = viewModel::setSecondaryHasContent,
                )

                is AddStep.ManualEdit -> {
                    val calorieTarget = settings.dailyCalorieTarget
                    val proteinTarget = settings.effectiveProteinG.takeIf { it > 0f } ?: ProteinTarget
                    val carbsTarget = settings.effectiveCarbsG.takeIf { it > 0f } ?: CarbsTarget
                    val fatTarget = settings.effectiveFatG.takeIf { it > 0f } ?: FatTarget

                    ManualEditContent(
                        padding = contentPadding,
                        initialName = step.initialName,
                        initialGrams = step.initialGrams,
                        initialNutrition = step.initialNutrition,
                        initialComponents = step.initialComponents,
                        defaultMealType = state.mealType,
                        defaultDateEpochDay = state.targetDateEpochDay,
                        defaultMinuteOfDay = state.mealMinuteOfDay,
                        defaultNote = state.note,
                        defaultTags = state.selectedTags,
                        calorieTarget = calorieTarget,
                        proteinTarget = proteinTarget,
                        carbsTarget = carbsTarget,
                        fatTarget = fatTarget,
                        palette = palette,
                        draftDatabaseSearch = state.draftDatabaseSearch,
                        saving = state.saving,
                        error = state.error,
                        showMealType = true,
                        showMealInfo = true,
                        onOpenComponentSearch = viewModel::openDraftComponentSearch,
                        onOpenNewComponentSearch = viewModel::openDraftNewComponentSearch,
                        onSearchComponent = viewModel::searchDraftComponentDatabase,
                        onCloseComponentSearch = viewModel::closeDraftComponentSearch,
                        onSave = { draft -> viewModel.saveManualDraft(draft, onDone) },
                        onHasContentChange = viewModel::setSecondaryHasContent,
                    )
                }

                AddStep.PresetList -> PresetListContent(
                    padding = contentPadding,
                    presets = state.presets,
                    onSelect = viewModel::applyPreset,
                    onEdit = viewModel::openPresetEdit,
                    onCreate = viewModel::openCreatePreset,
                )

                is AddStep.PresetEdit -> {
                    val calorieTarget = settings.dailyCalorieTarget
                    val proteinTarget = settings.effectiveProteinG.takeIf { it > 0f } ?: ProteinTarget
                    val carbsTarget = settings.effectiveCarbsG.takeIf { it > 0f } ?: CarbsTarget
                    val fatTarget = settings.effectiveFatG.takeIf { it > 0f } ?: FatTarget
                    val preset = step.preset
                    val presetExists = state.presets.any { it.id == preset.id }

                    ManualEditContent(
                        padding = contentPadding,
                        initialName = preset.name,
                        initialGrams = preset.defaultGrams,
                        initialNutrition = preset.nutrition,
                        initialComponents = preset.components,
                        defaultMealType = state.mealType,
                        defaultDateEpochDay = state.targetDateEpochDay,
                        defaultMinuteOfDay = state.mealMinuteOfDay,
                        defaultNote = "",
                        defaultTags = emptySet(),
                        calorieTarget = calorieTarget,
                        proteinTarget = proteinTarget,
                        carbsTarget = carbsTarget,
                        fatTarget = fatTarget,
                        palette = palette,
                        draftDatabaseSearch = state.draftDatabaseSearch,
                        saving = state.saving,
                        error = state.error,
                        showMealType = false,
                        showMealInfo = false,
                        saveButtonLabel = "保存预设",
                        showDeletePreset = presetExists,
                        onOpenDeletePresetConfirm = viewModel::openDeletePresetConfirm,
                        onOpenComponentSearch = viewModel::openDraftComponentSearch,
                        onOpenNewComponentSearch = viewModel::openDraftNewComponentSearch,
                        onSearchComponent = viewModel::searchDraftComponentDatabase,
                        onCloseComponentSearch = viewModel::closeDraftComponentSearch,
                        onSave = { draft ->
                            val nutritionFromComponents = completeComponentNutrition(draft.components)
                            val nutrition = nutritionFromComponents ?: draft.nutrition
                            viewModel.updatePreset(
                                preset.copy(
                                    name = draft.name.trim().ifBlank { preset.name },
                                    defaultGrams = draft.grams.takeIf { it.isFinite() && it > 0 }
                                        ?: preset.defaultGrams,
                                    nutrition = nutrition,
                                    components = draft.components,
                                ),
                            )
                        },
                        onHasContentChange = viewModel::setSecondaryHasContent,
                    )

                    AnimatedOverlayDialog(
                        show = state.showDeletePresetConfirm,
                        title = "删除预设？",
                        summary = "删除「${preset.name.ifBlank { "该预设" }}」后不可恢复",
                        onDismissRequest = viewModel::dismissDeletePresetConfirm,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = { viewModel.confirmDeletePreset(preset.id, preset.name) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(
                                    color = MiuixTheme.colorScheme.error,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text("删除")
                            }
                            Button(
                                onClick = viewModel::dismissDeletePresetConfirm,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(),
                            ) {
                                Text("取消")
                            }
                        }
                    }
                }

                                is AddStep.Review -> {
                    LaunchedEffect(step.result.dishes.size) {
                        viewModel.setSecondaryHasContent(step.result.dishes.isNotEmpty())
                    }
                    ReviewContent(
                        padding = contentPadding,
                        result = step.result,
                        recognizing = state.recognizing || state.saving,
                        recognitionStage = state.recognitionStage,
                        replacingDishId = state.replacingDishId,
                        selectedComponentId = state.databaseSearch?.componentId,
                        mealType = state.mealType,
                        onMealType = viewModel::setMealType,
                        onWeightChange = viewModel::updateComponentWeight,
                        onRemoveComponent = viewModel::removeComponent,
                        onRemoveDish = viewModel::removeDish,
                        onDatabaseSearch = { component -> viewModel.openComponentSearch(component) },
                        onReplaceComponent = { component ->
                            viewModel.openComponentSearch(component, replaceComponentName = true)
                        },
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
                        text = "请到「我的 → 识别 API」填写 Base URL 与 API Key。文字录入不依赖网络。",
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
                label = "文字录入",
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
    error: String?,
    quantityMode: QuantityMode,
    onQuantityModeChange: (QuantityMode) -> Unit,
    portionCount: Double,
    onPortionCountChange: (Double) -> Unit,
    estimatedGrams: Double?,
    onRecognizeGrams: (String, Double) -> Unit,
    onRecognizePortions: (String, Double) -> Unit,
    onOpenManualEdit: (name: String, grams: Double?) -> Unit,
    onHasContentChange: (Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var grams by rememberSaveable { mutableStateOf(initialGrams?.formatInput() ?: "100") }
    var showPortionDialog by remember { mutableStateOf(false) }
    var showGramsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(estimatedGrams) {
        estimatedGrams?.let { grams = it.formatInput() }
    }
    LaunchedEffect(name) {
        onHasContentChange(name.isNotBlank())
    }

    val effectiveGrams = when (quantityMode) {
        QuantityMode.GRAMS -> grams.toDoubleOrNull() ?: Double.NaN
        QuantityMode.PORTIONS -> if (estimatedGrams != null) grams.toDoubleOrNull() ?: Double.NaN else Double.NaN
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

        MethodButton(
            icon = { Icon(MiuixIcons.Os4.Edit, contentDescription = null) },
            label = "手动录入",
            enabled = !recognizing,
            onClick = {
                val n = name.trim()
                val g = grams.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                    ?: estimatedGrams
                onOpenManualEdit(n, g)
            },
        )

        if (!configured) {
            Text(
                text = "自动识别需要先在「我的 → 识别 API」配置服务；也可以手动录入填写营养。",
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
    }

    NumberInputDialog(
        show = showGramsDialog,
        title = "克数",
        summary = "输入可食用重量",
        initial = grams,
        onDismiss = { showGramsDialog = false },
        onConfirm = { grams = it; showGramsDialog = false },
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

/** 手动录入：完整复刻编辑记录页元素，高度与记录食物 bottomsheet 一致。 */
@Composable
private fun ManualEditContent(
    padding: PaddingValues,
    initialName: String,
    initialGrams: Double?,
    initialNutrition: Nutrition?,
    initialComponents: List<FoodComponent> = emptyList(),
    defaultMealType: MealType,
    defaultDateEpochDay: Long,
    defaultMinuteOfDay: Int,
    defaultNote: String,
    defaultTags: Set<String>,
    calorieTarget: Float,
    proteinTarget: Float,
    carbsTarget: Float,
    fatTarget: Float,
    palette: FoodPaletteColors,
    draftDatabaseSearch: com.click.lightmemo.viewmodel.DatabaseSearchState?,
    saving: Boolean,
    error: String?,
    showMealType: Boolean = true,
    showMealInfo: Boolean = true,
    saveButtonLabel: String = "保存",
    showDeletePreset: Boolean = false,
    deletePresetLabel: String = "删除预设",
    onOpenDeletePresetConfirm: (() -> Unit)? = null,
    onOpenComponentSearch: (FoodComponent) -> Unit,
    onOpenNewComponentSearch: () -> Unit,
    onSearchComponent: (String, String) -> Unit,
    onCloseComponentSearch: () -> Unit,
    onSave: (FoodLog) -> Unit,
    onHasContentChange: (Boolean) -> Unit,
) {
    val initialEpochDay = defaultDateEpochDay
    val componentGrams = initialComponents.sumOf { it.estimatedWeightG }
    val initialDraft = remember(
        initialName,
        initialGrams,
        initialNutrition,
        initialComponents,
        defaultMealType,
        initialEpochDay,
    ) {
        FoodLog(
            name = initialName,
            mealType = defaultMealType,
            grams = when {
                componentGrams > 0.0 -> componentGrams
                initialGrams != null && initialGrams.isFinite() && initialGrams > 0 -> initialGrams
                else -> 100.0
            },
            nutrition = initialNutrition
                ?: completeComponentNutrition(initialComponents)
                ?: Nutrition(),
            components = initialComponents,
            dateEpochDay = initialEpochDay,
            mealMinuteOfDay = defaultMinuteOfDay,
            note = defaultNote.takeIf { it.isNotBlank() },
            mealTags = defaultTags.toList(),
        )
    }
    var draft by remember { mutableStateOf(initialDraft) }
    var gramsInput by remember { mutableStateOf(initialDraft.grams.formatEditNumber()) }

    LaunchedEffect(draft.name, draft.components, draft.nutrition, draft.note) {
        val emptyNutrition = Nutrition()
        val has = draft.name.isNotBlank() ||
            draft.components.isNotEmpty() ||
            draft.nutrition != emptyNutrition ||
            !draft.note.isNullOrBlank()
        onHasContentChange(has)
    }
    var dateInput by remember { mutableStateOf(LocalDate.ofEpochDay(initialEpochDay).toString()) }
    var timeInput by remember {
        mutableStateOf("%02d:%02d".format(java.util.Locale.ROOT, defaultMinuteOfDay / 60, defaultMinuteOfDay % 60))
    }
    var nutritionField by remember { mutableStateOf<NutritionField?>(null) }
    var textEditField by remember { mutableStateOf<TextEditField?>(null) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showAddComponent by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val busy = saving
    val editingAddComponent = draftDatabaseSearch?.componentId == NewComponentId
    val parsedWeight = gramsInput.toDoubleOrNull()
    val parsedDate = runCatching { LocalDate.parse(dateInput) }.getOrNull()
    val parsedTime = runCatching { LocalTime.parse(timeInput) }.getOrNull()
    val valid = draft.name.isNotBlank() &&
        parsedWeight?.let { it.isFinite() && it > 0 } == true &&
        parsedDate != null &&
        draft.nutrition.isValidEditNutrition()
    val componentNutrition = completeComponentNutrition(draft.components)
    val hasManualNutritionOverride = componentNutrition != null && draft.nutrition != componentNutrition

    fun updateWeight(value: String) {
        gramsInput = value
        val nextWeight = value.toDoubleOrNull()
        if (nextWeight != null && nextWeight.isFinite() && nextWeight > 0 && draft.grams > 0) {
            val ratio = nextWeight / draft.grams
            val componentsAreSource = completeComponentNutrition(draft.components)?.let {
                draft.nutrition == it
            } == true
            val nextComponents = if (draft.components.isNotEmpty()) {
                draft.components.map { it.withEditWeight(it.estimatedWeightG * ratio) }
            } else {
                draft.components
            }
            draft = draft.copy(
                grams = nextWeight,
                components = nextComponents,
                nutrition = if (nextComponents.isNotEmpty()) {
                    if (componentsAreSource) {
                        completeComponentNutrition(nextComponents) ?: draft.nutrition * ratio
                    } else {
                        draft.nutrition * ratio
                    }
                } else {
                    draft.nutrition * ratio
                },
            )
        }
    }

    MealDatePickerOverlay(
        show = showDatePicker,
        date = parsedDate ?: LocalDate.ofEpochDay(initialEpochDay),
        onDismiss = { showDatePicker = false },
        onConfirm = {
            dateInput = it.toString()
            showDatePicker = false
        },
    )
    MealTimePickerOverlay(
        show = showTimePicker,
        minuteOfDay = parsedTime?.let { it.hour * 60 + it.minute }
            ?: defaultMinuteOfDay.coerceIn(0, 1439),
        onDismiss = { showTimePicker = false },
        onConfirm = { minute ->
            timeInput = "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)
            showTimePicker = false
        },
    )

    EditNutritionDialog(
        field = nutritionField,
        nutrition = draft.nutrition,
        onDismiss = { nutritionField = null },
        onConfirm = { field, value ->
            draft = draft.copy(nutrition = draft.nutrition.withField(field, value))
            nutritionField = null
        },
    )

    NumberInputDialog(
        show = showWeightDialog,
        title = "重量 g",
        summary = "修改后按比例更新营养与组成",
        initial = gramsInput,
        onDismiss = { showWeightDialog = false },
        onConfirm = {
            updateWeight(it)
            showWeightDialog = false
        },
    )

    TextInputDialog(
        field = textEditField,
        initial = when (textEditField) {
            TextEditField.FOOD_NAME -> draft.name
            TextEditField.NOTE -> draft.note.orEmpty()
            null -> ""
        },
        onDismiss = { textEditField = null },
        onConfirm = { field, value ->
            when (field) {
                TextEditField.FOOD_NAME -> draft = draft.copy(name = value)
                TextEditField.NOTE -> draft = draft.copy(note = value.trim().ifBlank { null })
            }
            textEditField = null
        },
    )

    ComponentDatabaseOverlay(
        searchState = draftDatabaseSearch,
        adding = editingAddComponent,
        showAdd = showAddComponent,
        onDismiss = {
            showAddComponent = false
            onCloseComponentSearch()
        },
        onSearch = onSearchComponent,
        onSelect = { componentId, query, grams, reference ->
            if (componentId == NewComponentId) {
                val weight = grams.toDoubleOrNull()
                if (weight != null && weight.isFinite() && weight > 0 && query.isNotBlank()) {
                    val next = draft.components + newFoodComponent(query, weight, reference)
                    draft = draft.copy(
                        components = next,
                        grams = next.sumOf { it.estimatedWeightG },
                        nutrition = completeComponentNutrition(next) ?: draft.nutrition,
                    )
                    gramsInput = draft.grams.formatEditNumber()
                    showAddComponent = false
                    onCloseComponentSearch()
                }
            } else {
                val next = draft.components.map { component ->
                    if (component.id == componentId) {
                        val selectedQuery = query.trim()
                        component.copy(
                            nutritionReference = reference,
                            databaseQuery = if (reference.dataType.contains("中国")) {
                                selectedQuery
                            } else {
                                reference.description
                            },
                            chinaDatabaseQuery = selectedQuery,
                        )
                    } else {
                        component
                    }
                }
                draft = draft.copy(
                    components = next,
                    nutrition = completeComponentNutrition(next) ?: draft.nutrition,
                )
                onCloseComponentSearch()
            }
        },
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(AddFoodSheetHeight)
            .overScrollVertical()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showMealType) {
            item {
                MealTypeSelector(
                    selected = draft.mealType,
                    onSelect = { if (!busy) draft = draft.copy(mealType = it) },
                )
            }
        }

        item {
            Column {
                SmallTitle(
                    text = "食物信息",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PickerField(
                        label = "食物名称",
                        value = draft.name.ifBlank { "未设置" },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = { textEditField = TextEditField.FOOD_NAME },
                    )
                    PickerField(
                        label = "重量 g",
                        value = gramsInput,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = { showWeightDialog = true },
                    )
                }
            }
        }

        item {
            EditNutritionSummary(
                nutrition = draft.nutrition,
                calorieTarget = calorieTarget,
                proteinTarget = proteinTarget,
                carbsTarget = carbsTarget,
                fatTarget = fatTarget,
                palette = palette,
                enabled = !busy,
                onNutritionField = { nutritionField = it },
            )
        }

        if (showMealInfo) {
            item {
                Column {
                    SmallTitle(
                        text = "用餐信息",
                        modifier = Modifier.offset(x = (-16).dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PickerField(
                            label = "日期",
                            value = dateInput,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            onClick = { showDatePicker = true },
                        )
                        PickerField(
                            label = "时间",
                            value = timeInput.ifBlank { "未设置" },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            onClick = { showTimePicker = true },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Card(
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        ArrowPreference(
                            title = "备注",
                            summary = draft.note?.takeIf { it.isNotBlank() } ?: "点击添加备注",
                            enabled = !busy,
                            onClick = { textEditField = TextEditField.NOTE },
                        )
                    }
                }
            }
        }

        item {
            Column {
                SmallTitle(
                    text = "组成部分",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    if (draft.components.isEmpty()) {
                        Text("暂无食物成分", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    } else {
                        Column {
                            draft.components.forEach { component ->
                                ComponentResultRow(
                                    component = component,
                                    onWeightChange = { componentId, weight ->
                                        if (!busy) {
                                            val next = draft.components.map {
                                                if (it.id == componentId) it.withEditWeight(weight) else it
                                            }
                                            draft = draft.copy(
                                                components = next,
                                                grams = next.sumOf { it.estimatedWeightG },
                                                nutrition = completeComponentNutrition(next) ?: draft.nutrition,
                                            )
                                            gramsInput = draft.grams.formatEditNumber()
                                        }
                                    },
                                    onRemove = { componentId ->
                                        if (!busy) {
                                            val next = draft.components.filterNot { it.id == componentId }
                                            draft = if (next.isEmpty()) {
                                                draft.copy(components = emptyList())
                                            } else {
                                                draft.copy(
                                                    components = next,
                                                    grams = next.sumOf { it.estimatedWeightG },
                                                    nutrition = completeComponentNutrition(next) ?: draft.nutrition,
                                                )
                                            }
                                            if (next.isNotEmpty()) gramsInput = draft.grams.formatEditNumber()
                                        }
                                    },
                                    onMatch = { if (!busy) onOpenComponentSearch(it) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (!busy) {
                            showAddComponent = true
                            onOpenNewComponentSearch()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = sheetSecondaryButtonColors(enabled = !busy),
                ) { Text("添加食物成分") }
            }
        }

        if (hasManualNutritionOverride) {
            item {
                Text(
                    "手动修改了总营养后，保存时将移除组成部分；如需保留组成，请修改克重或数据库匹配。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        error?.let { message ->
            item { Text(message, color = MiuixTheme.colorScheme.error) }
        }
        item {
            Button(
                onClick = {
                    if (parsedWeight != null && parsedDate != null) {
                        val saved = draft.copy(
                            name = draft.name.trim(),
                            grams = parsedWeight,
                            dateEpochDay = parsedDate.toEpochDay(),
                            mealMinuteOfDay = parsedTime?.let { it.hour * 60 + it.minute },
                            note = draft.note?.trim()?.ifBlank { null },
                        ).let { candidate ->
                            val nutritionFromComponents = completeComponentNutrition(candidate.components)
                            if (nutritionFromComponents != null && candidate.nutrition != nutritionFromComponents) {
                                candidate.copy(components = emptyList())
                            } else {
                                candidate
                            }
                        }
                        onSave(saved)
                    }
                },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (busy) "保存中…" else saveButtonLabel) }
        }

        if (showDeletePreset && onOpenDeletePresetConfirm != null) {
            item {
                Button(
                    onClick = { if (!busy) onOpenDeletePresetConfirm() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(deletePresetLabel)
                }
            }
        }
    }
}

@Composable
fun NumberInputDialog(
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
fun MealDatePickerOverlay(
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
fun MealTimePickerOverlay(
    show: Boolean,
    minuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: (() -> Unit)? = null,
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
                onClear?.let {
                    Button(onClick = it, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                        Text("清除")
                    }
                }
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
private fun PresetListContent(
    padding: PaddingValues,
    presets: List<PresetFood>,
    onSelect: (PresetFood) -> Unit,
    onEdit: (PresetFood) -> Unit,
    onCreate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(AddFoodSheetHeight)
            .overScrollVertical()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            // 与上级页边距一致，标题下再多留一点
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(presets, key = { it.id }) { preset ->
            Card(
                cornerRadius = 14.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
                        } ?: completeComponentNutrition(preset.components)?.let {
                            "${it.caloriesKcal.toInt()} kcal · 蛋 ${it.proteinG.formatInput()}g · 碳 ${it.carbsG.formatInput()}g · 脂 ${it.fatG.formatInput()}g"
                        } ?: "未设置营养"
                        val portion = buildString {
                            if (preset.portionLabel.isNotBlank()) append(preset.portionLabel).append(" · ")
                            append("约 ${preset.defaultGrams.toInt()}g")
                            if (preset.components.isNotEmpty()) {
                                append(" · ").append("${preset.components.size} 种成分")
                            }
                        }
                        Text(
                            portion,
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
                        onClick = { onEdit(preset) },
                        minWidth = 48.dp,
                        minHeight = 28.dp,
                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        // 浅灰底，与列表内小操作按钮一致
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text("编辑", style = MiuixTheme.textStyles.footnote1)
                    }
                }
            }
        }

        // 列表末尾：添加预设（与外层录入方式按钮一致）
        item {
            MethodButton(
                icon = { Icon(MiuixIcons.Os4.Edit, contentDescription = null) },
                label = "添加预设食物",
                enabled = true,
                onClick = onCreate,
            )
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
    selectedComponentId: String?,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    onWeightChange: (String, Double) -> Unit,
    onRemoveComponent: (String) -> Unit,
    onRemoveDish: (String) -> Unit,
    onDatabaseSearch: (FoodComponent) -> Unit,
    onReplaceComponent: (FoodComponent) -> Unit,
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
                selectedComponentId = selectedComponentId,
                selected = replacingDish?.id == dish.id,
                onWeightChange = onWeightChange,
                onRemoveComponent = onRemoveComponent,
                onRemoveDish = onRemoveDish,
                onDatabaseSearch = onDatabaseSearch,
                onReplaceComponent = onReplaceComponent,
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
    selectedComponentId: String?,
    selected: Boolean,
    onWeightChange: (String, Double) -> Unit,
    onRemoveComponent: (String) -> Unit,
    onRemoveDish: (String) -> Unit,
    onDatabaseSearch: (FoodComponent) -> Unit,
    onReplaceComponent: (FoodComponent) -> Unit,
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
                        color = if (selected) MiuixTheme.colorScheme.onSurfaceVariantSummary
                        else MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = enabled,
                            onClickLabel = "更换菜品",
                            onClick = { onReplaceDish(dish) },
                        ),
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
                            onNameClick = onReplaceComponent,
                            selected = selectedComponentId == component.id,
                            enabled = enabled,
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
                                onNameClick = onReplaceComponent,
                                selected = selectedComponentId == component.id,
                                enabled = enabled,
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
fun ComponentResultRow(
    component: FoodComponent,
    onWeightChange: (String, Double) -> Unit,
    onRemove: (String) -> Unit,
    onMatch: (FoodComponent) -> Unit,
    onNameClick: ((FoodComponent) -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
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
            Text(
                component.name,
                style = MiuixTheme.textStyles.body1,
                color = if (selected) MiuixTheme.colorScheme.onSurfaceVariantSummary
                else MiuixTheme.colorScheme.onSurface,
                modifier = onNameClick?.let {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        enabled = enabled,
                        onClickLabel = "更换食物",
                        onClick = { it(component) },
                    )
                } ?: Modifier,
            )
            val ref = component.nutritionReference
            Text(
                text = ref?.let {
                    val source = if (it.dataType.contains("中国")) "中国食物成分表" else "USDA"
                    "来自 $source #${it.sourceId}"
                } ?: "未匹配到营养数据",
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
                    modifier = Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        enabled = enabled,
                        onClick = { onMatch(component) },
                    ),
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

/** bottomsheet 内次要按钮：浅色白底，深色 surfaceContainer（与 MethodButton 一致） */
@Composable
private fun sheetSecondaryButtonColors(enabled: Boolean = true): ButtonColors {
    val isLight = isLightTheme()
    val container = when {
        !enabled && isLight -> MiuixTheme.colorScheme.secondaryVariant
        !enabled -> MiuixTheme.colorScheme.disabledSecondaryVariant
        isLight -> Color.White
        else -> MiuixTheme.colorScheme.surfaceContainer
    }
    val content = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.disabledOnSurface
    }
    return ButtonDefaults.buttonColors(
        color = container,
        disabledColor = if (isLight) {
            MiuixTheme.colorScheme.secondaryVariant
        } else {
            MiuixTheme.colorScheme.disabledSecondaryVariant
        },
        contentColor = content,
        disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
    )
}

@Composable
private fun LeaveConfirmDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AnimatedOverlayDialog(
        show = show,
        title = "放弃当前内容？",
        summary = "返回后未保存的填写将丢失",
        onDismissRequest = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("放弃并返回")
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("继续填写")
            }
        }
    }
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
