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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.click.lightmemo.data.FoodImages
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.components.ComponentDatabaseOverlay
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.viewmodel.AddFoodViewModel
import com.click.lightmemo.viewmodel.AddStep
import java.io.File
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme


private fun stepOrder(step: AddStep): Int = when (step) {
    AddStep.PickSource -> 0
    is AddStep.Manual, AddStep.PresetList -> 1
    is AddStep.ManualEdit, is AddStep.PresetEdit -> 2
    is AddStep.Review -> 3
}

@Composable
fun AddFoodRoute(
    viewModel: AddFoodViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    onDone: () -> Unit,
    palette: FoodPaletteColors = FoodPaletteColors.Default,
    pendingAddAction: com.click.lightmemo.ShortcutAddAction? = null,
    onPendingAddActionConsumed: () -> Unit = {},
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

    fun onGalleryClick() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    // Launcher shortcuts: once the sheet is composed, fire the matching entry path.
    LaunchedEffect(pendingAddAction) {
        when (pendingAddAction) {
            com.click.lightmemo.ShortcutAddAction.CAMERA -> withNotificationPermission(::onCameraClick)
            com.click.lightmemo.ShortcutAddAction.GALLERY -> withNotificationPermission(::onGalleryClick)
            com.click.lightmemo.ShortcutAddAction.MANUAL -> viewModel.openManual()
            null -> return@LaunchedEffect
        }
        onPendingAddActionConsumed()
    }

    ComponentDatabaseOverlay(
        searchState = state.databaseSearch,
        adding = false,
        onDismiss = viewModel::closeComponentSearch,
        onSearch = viewModel::searchComponentDatabase,
        onSelect = { componentId, query, _, reference ->
            viewModel.selectComponentReference(componentId, query, reference)
        },
        onEstimateNutrition = { componentId, query, weightG, _ ->
            viewModel.estimateComponentNutrition(componentId, query, weightG)
        },
        estimatingComponentId = state.aiEstimatingComponentIds.firstOrNull(),
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
                        withNotificationPermission(::onGalleryClick)
                    },
                    onCamera = { withNotificationPermission(::onCameraClick) },
                    onManual = { viewModel.openManual() },
                    onPreset = viewModel::openPresetList,
                    canRetryRecognition = state.canRetryRecognition,
                    onRetryRecognition = viewModel::retryRecognition,
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
                    canRetryRecognition = state.canRetryRecognition,
                    onRetryRecognition = viewModel::retryRecognition,
                )

                is AddStep.ManualEdit -> {
                    val calorieTarget = settings.dailyCalorieTarget
                    val proteinTarget = settings.effectiveProteinG.takeIf { it > 0f } ?: AddFoodProteinTarget
                    val carbsTarget = settings.effectiveCarbsG.takeIf { it > 0f } ?: AddFoodCarbsTarget
                    val fatTarget = settings.effectiveFatG.takeIf { it > 0f } ?: AddFoodFatTarget

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
                        onDraftSync = viewModel::syncManualDraft,
                        onEstimateNutrition = { componentId, query, weightG, onResolved ->
                            viewModel.estimateComponentNutrition(componentId, query, weightG, onResolved)
                        },
                        estimatingComponentId = state.aiEstimatingComponentIds.firstOrNull(),
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
                    val proteinTarget = settings.effectiveProteinG.takeIf { it > 0f } ?: AddFoodProteinTarget
                    val carbsTarget = settings.effectiveCarbsG.takeIf { it > 0f } ?: AddFoodCarbsTarget
                    val fatTarget = settings.effectiveFatG.takeIf { it > 0f } ?: AddFoodFatTarget
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
                        onDraftSync = viewModel::syncManualDraft,
                        onEstimateNutrition = { componentId, query, weightG, onResolved ->
                            viewModel.estimateComponentNutrition(componentId, query, weightG, onResolved)
                        },
                        estimatingComponentId = state.aiEstimatingComponentIds.firstOrNull(),
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
                        canRetryRecognition = state.canRetryRecognition,
                        onRetryRecognition = viewModel::retryRecognition,
                    )
                }
            }
        }
    }
}
