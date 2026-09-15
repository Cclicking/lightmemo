package com.click.lightmemo.viewmodel

import com.click.lightmemo.data.PresetFood
import com.click.lightmemo.data.FoodImages
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.FoodApp
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.RecognizedDish
import com.click.lightmemo.domain.NutritionReference
import com.click.lightmemo.domain.RecognitionStage
import com.click.lightmemo.recognition.RecognitionRequest
import com.click.lightmemo.recognition.RecognitionRequestType
import com.click.lightmemo.recognition.RecognitionService
import com.click.lightmemo.recognition.RecognitionTaskStatus
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect


class AddFoodViewModel(app: Application) : AndroidViewModel(app) {
    private val foodApp = app as FoodApp
    private val repo = foodApp.foodLogRepository
    private val settingsRepo = foodApp.settingsRepository
    private val nutritionDatabase = foodApp.nutritionDatabase
    private val recognitionTaskStore = foodApp.recognitionTaskStore

    private val _uiState = MutableStateFlow(AddFoodUiState())
    val uiState: StateFlow<AddFoodUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val recognition = RecognitionCoordinator(
        app = foodApp,
        store = recognitionTaskStore,
        uiState = _uiState,
        notify = { message -> eventChannel.trySend(message) },
    )

    private val componentSearch = ComponentSearchInteractor(
        database = nutritionDatabase,
        uiState = _uiState,
        scope = viewModelScope,
        apiKey = { settings.value.foodDataCentralApiKey },
    )

    init {
        viewModelScope.launch {
            settingsRepo.foodPresets.collect { presets ->
                _uiState.value = _uiState.value.copy(presets = presets)
            }
        }
        viewModelScope.launch {
            recognitionTaskStore.state.collect(recognition::sync)
        }
    }

    val settings = settingsRepo.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.click.lightmemo.data.AppSettings(),
    )

    private fun notify(message: String) {
        eventChannel.trySend(message)
    }

    fun setMealType(type: MealType) {
        _uiState.value = _uiState.value.copy(mealType = type)
    }

    fun setPlateSize(value: String) {
        _uiState.value = _uiState.value.copy(plateSize = value)
    }

    fun setPhotoDescription(value: String) {
        _uiState.value = _uiState.value.copy(photoDescription = value)
    }

    fun setTargetDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(targetDateEpochDay = date.toEpochDay())
    }

    fun setQuickInput(value: String) {
        _uiState.value = _uiState.value.copy(quickInput = value)
    }

    fun toggleTag(tag: String) {
        val current = _uiState.value.selectedTags
        val next = if (tag in current) current - tag else current + tag
        _uiState.value = _uiState.value.copy(
            selectedTags = next,
            photoDescription = next.joinToString("、"),
        )
    }

    fun setMealMinuteOfDay(minute: Int) {
        _uiState.value = _uiState.value.copy(mealMinuteOfDay = minute.coerceIn(0, 1439))
    }

    fun setNote(value: String) {
        _uiState.value = _uiState.value.copy(note = value)
    }

    /** 进入文字录入页（原手动录入入口）。 */
    fun openManual(
        initialName: String = "",
        initialGrams: Double? = null,
        initialNutrition: Nutrition? = null,
    ) {
        _uiState.value = _uiState.value.copy(
            step = AddStep.Manual(
                initialName = initialName,
                initialGrams = initialGrams,
                initialNutrition = initialNutrition,
            ),
            error = null,
            manualNutrition = initialNutrition,
            quantityMode = QuantityMode.GRAMS,
            estimatedPortionGrams = null,
            portionCount = 1.0,
            secondaryHasContent = initialName.isNotBlank() || initialNutrition != null,
            showLeaveConfirm = false,
        )
    }

    /** 进入手动录入页（复用编辑记录页）。 */
    fun openManualEdit(
        initialName: String = "",
        initialGrams: Double? = null,
        initialNutrition: Nutrition? = null,
        initialComponents: List<FoodComponent> = emptyList(),
    ) {
        _uiState.value = _uiState.value.copy(
            step = AddStep.ManualEdit(
                initialName = initialName,
                initialGrams = initialGrams,
                initialNutrition = initialNutrition,
                initialComponents = initialComponents,
            ),
            error = null,
            draftDatabaseSearch = null,
            secondaryHasContent = initialName.isNotBlank() ||
                (initialGrams != null && initialGrams > 0) ||
                initialNutrition != null ||
                initialComponents.isNotEmpty(),
            showLeaveConfirm = false,
        )
    }

    fun openPresetList() {
        _uiState.value = _uiState.value.copy(
            step = AddStep.PresetList,
            error = null,
            draftDatabaseSearch = null,
            secondaryHasContent = false,
            showLeaveConfirm = false,
            showDeletePresetConfirm = false,
        )
    }

    fun openPresetEdit(preset: PresetFood) {
        _uiState.value = _uiState.value.copy(
            step = AddStep.PresetEdit(preset),
            error = null,
            draftDatabaseSearch = null,
            secondaryHasContent = true,
            showLeaveConfirm = false,
        )
    }

    /** 新建预设：空白草稿进入编辑页。 */
    fun openCreatePreset() {
        val blank = PresetFood(
            id = java.util.UUID.randomUUID().toString(),
            name = "",
            defaultGrams = 100.0,
            portionLabel = "",
            nutrition = null,
            components = emptyList(),
        )
        _uiState.value = _uiState.value.copy(
            step = AddStep.PresetEdit(blank),
            error = null,
            draftDatabaseSearch = null,
            secondaryHasContent = false,
            showLeaveConfirm = false,
        )
    }

    fun openDraftComponentSearch(component: FoodComponent) {
        componentSearch.openDraft(component)
    }

    fun openDraftNewComponentSearch() {
        componentSearch.openDraftNew()
    }

    fun searchDraftComponentDatabase(componentId: String, query: String) {
        componentSearch.searchDraft(componentId, query)
    }

    fun closeDraftComponentSearch() {
        componentSearch.closeDraft()
    }

    /** 手动录入页保存：按完整草稿写入一条食物记录。 */
    fun saveManualDraft(draft: FoodLog, onSaved: () -> Unit = {}) {
        save(onSaved) {
            repo.insert(draft)
            recognitionTaskStore.clear()
            _uiState.value = _uiState.value.copy(
                step = AddStep.PickSource,
                error = null,
                draftDatabaseSearch = null,
                secondaryHasContent = false,
                showLeaveConfirm = false,
            )
            notify("食物已保存")
        }
    }

    /** 克重变化时按比例缩放已填营养。 */
    fun scaleManualNutrition(fromGrams: Double, toGrams: Double) {
        val current = _uiState.value.manualNutrition ?: return
        if (!fromGrams.isFinite() || fromGrams <= 0.0 || !toGrams.isFinite() || toGrams <= 0.0) return
        val ratio = toGrams / fromGrams
        _uiState.value = _uiState.value.copy(
            manualNutrition = Nutrition(
                caloriesKcal = current.caloriesKcal * ratio,
                proteinG = current.proteinG * ratio,
                carbsG = current.carbsG * ratio,
                fatG = current.fatG * ratio,
            ),
        )
    }


    fun openComponentSearch(component: FoodComponent, replaceComponentName: Boolean = false) {
        componentSearch.openReview(component, replaceComponentName)
    }

    fun searchComponentDatabase(componentId: String, query: String) {
        componentSearch.searchReview(componentId, query)
    }

    fun selectComponentReference(componentId: String, query: String, reference: NutritionReference) {
        val step = _uiState.value.step as? AddStep.Review ?: return
        val search = _uiState.value.databaseSearch
        val replacementName = query.trim().takeIf {
            search?.replaceComponentName == true && it.isNotBlank()
        }
        _uiState.value = _uiState.value.copy(
            step = step.copy(result = step.result.copy(
                dishes = step.result.dishes.map {
                    it.withReference(componentId, reference, replacementName)
                },
            )),
            databaseSearch = null,
        )
        notify(if (replacementName == null) "已匹配：${reference.description}" else "已更换食物：$replacementName")
    }

    fun closeComponentSearch() {
        componentSearch.closeReview()
    }

    fun updatePreset(updated: PresetFood, onSaved: () -> Unit = {}) {
        save(onSaved) {
            settingsRepo.updateFoodPreset(updated)
            _uiState.value = _uiState.value.copy(
                step = AddStep.PresetList,
                error = null,
                draftDatabaseSearch = null,
                secondaryHasContent = false,
                showLeaveConfirm = false,
                showDeletePresetConfirm = false,
            )
            notify("预设「${updated.name}」已保存")
        }
    }

    fun openDeletePresetConfirm() {
        _uiState.value = _uiState.value.copy(showDeletePresetConfirm = true)
    }

    fun dismissDeletePresetConfirm() {
        _uiState.value = _uiState.value.copy(showDeletePresetConfirm = false)
    }

    fun confirmDeletePreset(presetId: String, presetName: String) {
        save({
            _uiState.value = _uiState.value.copy(showDeletePresetConfirm = false)
        }) {
            settingsRepo.deleteFoodPreset(presetId)
            _uiState.value = _uiState.value.copy(
                step = AddStep.PresetList,
                error = null,
                draftDatabaseSearch = null,
                secondaryHasContent = false,
                showLeaveConfirm = false,
                showDeletePresetConfirm = false,
            )
            notify("预设「$presetName」已删除")
        }
    }

    /** 识别结果卡片：是否已按名称加入预设 */
    fun isDishPinned(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        return _uiState.value.presets.any { it.name == trimmed }
    }

    /** 手动录入页草稿同步到 ViewModel，供顶栏 pin 使用 */
    fun syncManualDraft(snapshot: ManualDraftSnapshot?) {
        if (_uiState.value.manualDraftSnapshot == snapshot) return
        _uiState.value = _uiState.value.copy(manualDraftSnapshot = snapshot)
    }

    /** 编辑页顶栏：把当前草稿加入预设 */
    fun pinManualDraftAsPreset() {
        val draft = _uiState.value.manualDraftSnapshot ?: return
        val name = draft.name.trim()
        if (name.isEmpty()) {
            notify("请先填写食物名称")
            return
        }
        if (isDishPinned(name)) return
        val grams = draft.grams.takeIf { it.isFinite() && it > 0.0 } ?: 100.0
        val nutrition = draft.nutrition
        val preset = PresetFood(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            defaultGrams = grams,
            portionLabel = "",
            nutrition = nutrition.takeIf {
                it.caloriesKcal > 0.0 || it.proteinG > 0.0 || it.carbsG > 0.0 || it.fatG > 0.0
            },
            components = draft.components,
        )
        save({
            notify("已加入预设「$name」")
        }) {
            settingsRepo.updateFoodPreset(preset)
        }
    }

    /** 编辑页顶栏：按名称取消预设 */
    fun unpinManualDraftPreset() {
        unpinDishPreset(_uiState.value.manualDraftSnapshot?.name.orEmpty())
    }

    /** 将已识别菜品加入预设（含成分与营养） */
    fun pinDishAsPreset(dish: RecognizedDish) {
        val name = dish.name.trim()
        if (name.isEmpty()) {
            notify("菜品名称为空，无法加入预设")
            return
        }
        if (isDishPinned(name)) return
        val grams = dish.grams.takeIf { it.isFinite() && it > 0.0 } ?: 100.0
        val nutrition = dish.nutrition
        val preset = PresetFood(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            defaultGrams = grams,
            portionLabel = "",
            nutrition = nutrition.takeIf { it.caloriesKcal > 0.0 || it.proteinG > 0.0 || it.carbsG > 0.0 || it.fatG > 0.0 },
            components = dish.allComponents,
        )
        save({
            notify("已加入预设「$name」")
        }) {
            settingsRepo.updateFoodPreset(preset)
        }
    }

    /** 从预设中移除同名菜品 */
    fun unpinDishPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val target = _uiState.value.presets.firstOrNull { it.name == trimmed } ?: return
        save({
            notify("已从预设移除「${target.name}」")
        }) {
            settingsRepo.deleteFoodPreset(target.id)
        }
    }

    fun applyPreset(preset: PresetFood) {
        // 已有营养/成分时直接进手动录入编辑页，避免再走识别
        val nutrition = preset.nutrition
        val hasNutrition = nutrition != null && nutrition.caloriesKcal > 0.0
        if (hasNutrition || preset.components.isNotEmpty()) {
            openManualEdit(
                initialName = preset.name,
                initialGrams = preset.defaultGrams,
                initialNutrition = nutrition,
                initialComponents = preset.components,
            )
            return
        }
        val text = if (preset.portionLabel.isNotBlank()) {
            "${preset.portionLabel}${preset.name}"
        } else {
            "${preset.defaultGrams.toInt()}克${preset.name}"
        }
        _uiState.value = _uiState.value.copy(
            quickInput = text,
            step = AddStep.PickSource,
            error = null,
        )
        recognizeFromText(text)
    }

    fun applyPresetManual(preset: PresetFood) {
        openManualEdit(
            initialName = preset.name,
            initialGrams = preset.defaultGrams,
            initialNutrition = preset.nutrition,
            initialComponents = preset.components,
        )
    }

    fun backToPick() {
        if (_uiState.value.saving) return
        val task = recognitionTaskStore.state.value
        if (task?.status == RecognitionTaskStatus.RUNNING) {
            RecognitionService.cancel(foodApp, task.request.id)
        } else {
            recognitionTaskStore.clear(task?.request?.id)
        }
        componentSearch.cancel()
        _uiState.value = _uiState.value.copy(
            step = AddStep.PickSource,
            error = null,
            recognizing = false,
            recognitionStage = null,
            manualNutrition = null,
            databaseSearch = null,
            draftDatabaseSearch = null,
            secondaryHasContent = false,
            showLeaveConfirm = false,
            showDeletePresetConfirm = false,
            manualDraftSnapshot = null,
        )
    }

    /** 手动录入页返回文字录入，保留名称与克重。 */
    fun backFromManualEdit() {
        if (_uiState.value.saving) return
        val step = _uiState.value.step
        if (step is AddStep.ManualEdit) {
            componentSearch.cancel()
            _uiState.value = _uiState.value.copy(
                step = AddStep.Manual(
                    initialName = step.initialName,
                    initialGrams = step.initialGrams,
                    initialNutrition = step.initialNutrition,
                ),
                error = null,
                draftDatabaseSearch = null,
                showLeaveConfirm = false,
            )
        } else {
            backToPick()
        }
    }

    fun setSecondaryHasContent(value: Boolean) {
        if (_uiState.value.secondaryHasContent == value) return
        _uiState.value = _uiState.value.copy(secondaryHasContent = value)
    }

    /** 二级页返回：先关搜索层，有内容则弹确认，否则直接回上级。 */
    fun requestSecondaryBack() {
        val current = _uiState.value
        if (current.step is AddStep.PickSource) return
        if (current.saving) return
        if (current.draftDatabaseSearch != null) {
            closeDraftComponentSearch()
            return
        }
        if (current.databaseSearch != null) {
            closeComponentSearch()
            return
        }
        if (current.step is AddStep.PresetEdit) {
            if (current.showDeletePresetConfirm) {
                dismissDeletePresetConfirm()
                return
            }
            if (current.secondaryHasContent) {
                _uiState.value = current.copy(showLeaveConfirm = true)
            } else {
                openPresetList()
            }
            return
        }
        if (current.secondaryHasContent) {
            _uiState.value = current.copy(showLeaveConfirm = true)
        } else {
            performSecondaryBack()
        }
    }

    fun dismissLeaveConfirm() {
        _uiState.value = _uiState.value.copy(showLeaveConfirm = false)
    }

    fun confirmLeaveSecondary() {
        _uiState.value = _uiState.value.copy(showLeaveConfirm = false)
        performSecondaryBack()
    }

    private fun performSecondaryBack() {
        when (_uiState.value.step) {
            is AddStep.ManualEdit -> backFromManualEdit()
            is AddStep.PresetEdit -> openPresetList()
            is AddStep.Manual, is AddStep.Review, AddStep.PresetList -> backToPick()
            AddStep.PickSource -> Unit
        }
    }

    fun setQuantityMode(mode: QuantityMode) {
        _uiState.value = _uiState.value.copy(
            quantityMode = mode,
            estimatedPortionGrams = if (mode == QuantityMode.GRAMS) null else _uiState.value.estimatedPortionGrams,
        )
    }

    fun setPortionCount(value: Double) {
        if (!value.isFinite() || value <= 0) return
        _uiState.value = _uiState.value.copy(portionCount = value, estimatedPortionGrams = null, manualNutrition = null)
    }

    fun recognizeManual(name: String, grams: Double) {
        if (!grams.isFinite() || grams <= 0) {
            _uiState.value = _uiState.value.copy(error = "请输入大于 0 的有效重量")
            return
        }
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            notify("请输入食物名称")
            _uiState.value = _uiState.value.copy(error = "请输入食物名称")
            return
        }
        val state = _uiState.value
        _uiState.value = state.copy(
            step = AddStep.Review(
                imageUri = null,
                result = buildManualMealRecognition(trimmedName, grams, Nutrition()),
            ),
            quantityMode = QuantityMode.GRAMS,
            manualNutrition = null,
            error = null,
            recognizing = true,
            recognitionStage = RecognitionStage.PREPARING,
        )
        recognition.start(
            RecognitionRequest(
                type = RecognitionRequestType.MANUAL_GRAMS,
                foodName = trimmedName,
                grams = grams,
                mealType = state.mealType,
                targetDateEpochDay = state.targetDateEpochDay,
                mealMinuteOfDay = state.mealMinuteOfDay,
                note = state.note,
                selectedTags = state.selectedTags.toList(),
                plateSize = state.plateSize,
            ),
            startMessage = "已开始识别营养，完成后自动回填",
        )
    }

    /** 份数模式：先用大模型估重，再识别营养。 */
    fun recognizeManualWithPortions(name: String, portions: Double) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            notify("请输入食物名称")
            _uiState.value = _uiState.value.copy(error = "请输入食物名称")
            return
        }
        if (!portions.isFinite() || portions <= 0) {
            _uiState.value = _uiState.value.copy(error = "请输入大于 0 的有效份数")
            return
        }
        val state = _uiState.value
        _uiState.value = state.copy(
            step = AddStep.Review(
                imageUri = null,
                result = buildManualMealRecognition(trimmedName, 0.0, Nutrition()),
            ),
            quantityMode = QuantityMode.PORTIONS,
            portionCount = portions,
            estimatedPortionGrams = null,
            manualNutrition = null,
            error = null,
            recognizing = true,
            recognitionStage = RecognitionStage.PREPARING,
        )
        recognition.start(
            RecognitionRequest(
                type = RecognitionRequestType.MANUAL_PORTIONS,
                foodName = trimmedName,
                portions = portions,
                portionHint = state.photoDescription,
                mealType = state.mealType,
                targetDateEpochDay = state.targetDateEpochDay,
                mealMinuteOfDay = state.mealMinuteOfDay,
                note = state.note,
                selectedTags = state.selectedTags.toList(),
                plateSize = state.plateSize,
            ),
            startMessage = "正在用大模型估计重量…",
        )
    }

    /** 顶部输入框回车：立刻文字识别。 */
    fun recognizeFromText(raw: String? = null) {
        val text = (raw ?: _uiState.value.quickInput).trim()
        if (text.isEmpty()) {
            notify("请先输入食物描述")
            _uiState.value = _uiState.value.copy(error = "请先输入食物描述")
            return
        }
        val state = _uiState.value
        _uiState.value = state.copy(
            quickInput = text,
            step = AddStep.PickSource,
            error = null,
        )
        recognition.start(
            RecognitionRequest(
                type = RecognitionRequestType.TEXT,
                text = text,
                mealType = state.mealType,
                targetDateEpochDay = state.targetDateEpochDay,
                mealMinuteOfDay = state.mealMinuteOfDay,
                note = state.note,
                selectedTags = state.selectedTags.toList(),
                plateSize = state.plateSize,
            ),
            startMessage = "已开始文字识别…",
        )
    }

    fun recognizeFromUri(uri: Uri) {
        val state = _uiState.value
        _uiState.value = state.copy(step = AddStep.PickSource, error = null)
        recognition.start(
            RecognitionRequest(
                type = RecognitionRequestType.IMAGE,
                imageUri = uri.toString(),
                mealType = state.mealType,
                targetDateEpochDay = state.targetDateEpochDay,
                mealMinuteOfDay = state.mealMinuteOfDay,
                note = state.note,
                selectedTags = state.selectedTags.toList(),
                plateSize = state.plateSize,
            ),
            startMessage = "已开始识别食物，结果将在后台返回",
        )
    }

    fun retryRecognition() {
        recognition.retry()
    }

    fun saveManual(name: String, grams: Double, nutrition: Nutrition, onSaved: () -> Unit = {}) {
        save(onSaved) {
            repo.insert(
                FoodLog(
                    name = name,
                    mealType = _uiState.value.mealType,
                    grams = grams,
                    nutrition = nutrition,
                    dateEpochDay = _uiState.value.targetDateEpochDay,
                    mealMinuteOfDay = _uiState.value.mealMinuteOfDay,
                    note = _uiState.value.note.takeIf { it.isNotBlank() },
                    mealTags = _uiState.value.selectedTags.toList(),
                ),
            )
            recognitionTaskStore.clear()
            _uiState.value = _uiState.value.copy(step = AddStep.PickSource, error = null)
            notify("食物已保存")
        }
    }

    fun updateComponentWeight(componentId: String, grams: Double) {
        if (!grams.isFinite() || grams < 0) {
            notify("请输入非负有效重量")
            return
        }
        val step = _uiState.value.step as? AddStep.Review ?: return
        val safeGrams = grams.coerceAtLeast(0.0)
        _uiState.value = _uiState.value.copy(
            step = step.copy(
                result = step.result.copy(
                    dishes = step.result.dishes.map { it.updateWeight(componentId, safeGrams) },
                ),
            ),
        )
    }

    fun removeComponent(componentId: String) {
        val step = _uiState.value.step as? AddStep.Review ?: return
        _uiState.value = _uiState.value.copy(
            step = step.copy(
                result = step.result.copy(
                    dishes = step.result.dishes.map { it.removeComponent(componentId) }
                        .filter { it.allComponents.isNotEmpty() },
                ),
            ),
        )
    }

    fun removeDish(dishId: String) {
        val step = _uiState.value.step as? AddStep.Review ?: return
        val remaining = step.result.dishes.filterNot { it.id == dishId }
        if (remaining.isEmpty()) {
            backToPick()
            return
        }
        _uiState.value = _uiState.value.copy(
            step = step.copy(result = step.result.copy(dishes = remaining)),
        )
    }

    fun replaceDish(dishId: String, name: String) {
        val state = _uiState.value
        val review = state.step as? AddStep.Review ?: return
        val original = review.result.dishes.find { it.id == dishId } ?: return
        if (state.recognizing || state.saving || name.isBlank()) return
        _uiState.value = state.copy(recognizing = true, replacingDishId = dishId, error = null)
        recognition.start(
            RecognitionRequest(
                type = RecognitionRequestType.REPLACE_DISH,
                imageUri = review.imageUri,
                foodName = name.trim(),
                mealType = state.mealType,
                targetDateEpochDay = state.targetDateEpochDay,
                mealMinuteOfDay = state.mealMinuteOfDay,
                note = state.note,
                selectedTags = state.selectedTags.toList(),
                plateSize = state.plateSize,
                baseResult = review.result,
                replaceDishId = dishId,
            ),
            startMessage = "正在重新识别菜品…",
        )
    }

    fun saveRecognized(result: MealRecognition, imageUri: String?, onSaved: () -> Unit = {}) {
        save(onSaved) {
            require(result.dishes.isNotEmpty()) { "请先添加食物" }
            require(result.dishes.all { dish -> dish.allComponents.all { it.nutritionReference != null } }) {
                "请先匹配所有食物组成的营养数据"
            }
            val meal = _uiState.value.mealType
            val day = _uiState.value.targetDateEpochDay
            val note = _uiState.value.note.takeIf { it.isNotBlank() }
            val tags = _uiState.value.selectedTags.toList()
            val minuteOfDay = _uiState.value.mealMinuteOfDay
            val savedImageUri = imageUri?.let { source ->
                FoodImages.persist(foodApp, Uri.parse(source)).toString()
            }
            repo.insertAll(result.dishes.map { dish ->
                    FoodLog(
                        name = dish.name,
                        mealType = meal,
                        grams = dish.grams,
                        nutrition = dish.nutrition,
                        components = dish.allComponents,
                        imageUri = savedImageUri,
                        dateEpochDay = day,
                        mealMinuteOfDay = minuteOfDay,
                        note = note,
                        mealTags = tags,
                    )
            })
            recognitionTaskStore.clear()
            _uiState.value = _uiState.value.copy(
                step = AddStep.PickSource,
                error = null,
                quickInput = "",
                note = "",
                selectedTags = emptySet(),
            )
            notify("已保存 ${result.dishes.size} 道菜")
        }
    }

    private fun save(onSaved: () -> Unit, block: suspend () -> Unit) {
        if (_uiState.value.saving || _uiState.value.recognizing) return
        _uiState.value = _uiState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                block()
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: "保存失败，请重试"
                _uiState.value = _uiState.value.copy(error = message)
                notify(message)
            } finally {
                _uiState.value = _uiState.value.copy(saving = false)
            }
        }
    }

}
