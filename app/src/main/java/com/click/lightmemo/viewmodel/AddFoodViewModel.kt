package com.click.lightmemo.viewmodel

import com.click.lightmemo.data.PresetFood
import com.click.lightmemo.data.DefaultPresetFoods
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
import com.click.lightmemo.recognition.RecognitionTaskRecord
import com.click.lightmemo.recognition.RecognitionTaskStatus
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AddStep {
    data object PickSource : AddStep
    data class Review(
        val imageUri: String?,
        val result: MealRecognition,
    ) : AddStep

    data class Manual(
        val initialName: String = "",
        val initialGrams: Double? = null,
        val initialNutrition: Nutrition? = null,
    ) : AddStep
}

enum class QuantityMode(val label: String) {
    GRAMS("克重"),
    PORTIONS("份数"),
}


data class AddFoodUiState(
    val step: AddStep = AddStep.PickSource,
    val recognizing: Boolean = false,
    val recognitionStage: RecognitionStage? = null,
    val replacingDishId: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val mealType: MealType = defaultMealType(),
    val manualNutrition: Nutrition? = null,
    val plateSize: String = "",
    val photoDescription: String = "",
    val targetDateEpochDay: Long = LocalDate.now().toEpochDay(),
    val quickInput: String = "",
    val selectedTags: Set<String> = emptySet(),
    val mealMinuteOfDay: Int = LocalTime.now().hour * 60 + LocalTime.now().minute,
    val note: String = "",
    val quantityMode: QuantityMode = QuantityMode.GRAMS,
    val portionCount: Double = 1.0,
    val estimatedPortionGrams: Double? = null,
    val showPresetSheet: Boolean = false,
    val presets: List<PresetFood> = DefaultPresetFoods,
    val databaseSearch: DatabaseSearchState? = null,
)

data class DatabaseSearchState(
    val componentId: String,
    val query: String,
    val loading: Boolean = false,
    val results: List<NutritionReference> = emptyList(),
    val error: String? = null,
    val replaceComponentName: Boolean = false,
    val initialLookupQuery: String? = null,
)

fun defaultMealType(): MealType {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 10 -> MealType.BREAKFAST
        hour < 15 -> MealType.LUNCH
        hour < 21 -> MealType.DINNER
        else -> MealType.SNACK
    }
}

val DefaultMealTags = listOf("无糖", "少油", "少盐", "清淡", "多菜", "无主食", "高蛋白", "外食")


class AddFoodViewModel(app: Application) : AndroidViewModel(app) {
    private val foodApp = app as FoodApp
    private val repo = foodApp.foodLogRepository
    private val settingsRepo = foodApp.settingsRepository
    private val nutritionDatabase = foodApp.nutritionDatabase
    private val recognitionTaskStore = foodApp.recognitionTaskStore

    private var databaseSearchJob: Job? = null
    private var activeTaskId: String? = null
    private val _uiState = MutableStateFlow(AddFoodUiState())
    val uiState: StateFlow<AddFoodUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.foodPresets.collect { presets ->
                _uiState.value = _uiState.value.copy(presets = presets)
            }
        }
        viewModelScope.launch {
            recognitionTaskStore.state.collect(::syncRecognitionTask)
        }
    }

    val settings = settingsRepo.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.click.lightmemo.data.AppSettings(),
    )

    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

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
            showPresetSheet = false,
            quantityMode = QuantityMode.GRAMS,
            estimatedPortionGrams = null,
            portionCount = 1.0,
        )
    }

    fun openPresetSheet() {
        _uiState.value = _uiState.value.copy(showPresetSheet = true, error = null)
    }

    fun closePresetSheet() {
        _uiState.value = _uiState.value.copy(showPresetSheet = false)
    }

    fun openComponentSearch(component: FoodComponent, replaceComponentName: Boolean = false) {
        // The component name is user-facing (usually Chinese), while the
        // bundled USDA descriptions and the USDA API are searched in English.
        val initialLookupQuery = component.databaseQuery.trim().ifBlank { component.name }
        _uiState.value = _uiState.value.copy(
            databaseSearch = DatabaseSearchState(
                componentId = component.id,
                query = component.name,
                loading = true,
                replaceComponentName = replaceComponentName,
                initialLookupQuery = initialLookupQuery,
            ),
        )
        searchComponentDatabaseInternal(
            componentId = component.id,
            displayQuery = component.name,
            lookupQuery = initialLookupQuery,
            keepInitialLookupQuery = true,
        )
    }

    fun searchComponentDatabase(componentId: String, query: String) {
        val current = _uiState.value.databaseSearch
        val useInitialLookupQuery = current?.componentId == componentId &&
            current.query.trim() == query.trim()
        val lookupQuery = if (useInitialLookupQuery) {
            current.initialLookupQuery ?: query
        } else {
            query
        }
        searchComponentDatabaseInternal(
            componentId = componentId,
            displayQuery = query,
            lookupQuery = lookupQuery,
            keepInitialLookupQuery = false,
        )
    }

    private fun searchComponentDatabaseInternal(
        componentId: String,
        displayQuery: String,
        lookupQuery: String,
        keepInitialLookupQuery: Boolean,
    ) {
        databaseSearchJob?.cancel()
        val trimmed = lookupQuery.trim()
        if (trimmed.isBlank()) {
            _uiState.updateDatabaseSearch(componentId) {
                it.copy(
                    query = displayQuery,
                    loading = false,
                    results = emptyList(),
                    error = "请输入食物名称",
                    initialLookupQuery = if (keepInitialLookupQuery) it.initialLookupQuery else null,
                )
            }
            return
        }
        _uiState.updateDatabaseSearch(componentId) {
            it.copy(
                query = displayQuery,
                loading = true,
                error = null,
                initialLookupQuery = if (keepInitialLookupQuery) it.initialLookupQuery else null,
            )
        }
        databaseSearchJob = viewModelScope.launch {
            try {
                val results = nutritionDatabase.searchCandidates(
                    query = trimmed,
                    apiKey = settings.value.foodDataCentralApiKey,
                    limit = 20,
                )
                _uiState.updateDatabaseSearch(componentId) {
                    it.copy(
                        loading = false,
                        results = results,
                        error = if (results.isEmpty()) "没有找到相近的食物" else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.updateDatabaseSearch(componentId) {
                    it.copy(loading = false, results = emptyList(), error = e.message ?: "数据库查询失败")
                }
            }
        }
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
        _uiState.value = _uiState.value.copy(databaseSearch = null)
    }

    fun updatePreset(updated: PresetFood, onSaved: () -> Unit) {
        save(onSaved) {
            settingsRepo.updateFoodPreset(updated)
            notify("预设「${updated.name}」已保存")
        }
    }

    fun applyPreset(preset: PresetFood) {
        // 已有营养数据时直接进手动录入并回填，避免再走识别
        val nutrition = preset.nutrition
        if (nutrition != null && nutrition.caloriesKcal > 0.0) {
            openManual(
                initialName = preset.name,
                initialGrams = preset.defaultGrams,
                initialNutrition = nutrition,
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
            showPresetSheet = false,
            error = null,
        )
        recognizeFromText(text)
    }

    fun applyPresetManual(preset: PresetFood) {
        openManual(
            initialName = preset.name,
            initialGrams = preset.defaultGrams,
            initialNutrition = preset.nutrition,
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
        databaseSearchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            step = AddStep.PickSource,
            error = null,
            recognizing = false,
            recognitionStage = null,
            manualNutrition = null,
            showPresetSheet = false,
            databaseSearch = null,
        )
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
            step = AddStep.Manual(initialName = trimmedName, initialGrams = grams),
            quantityMode = QuantityMode.GRAMS,
            manualNutrition = null,
            error = null,
        )
        startRecognition(
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
            step = AddStep.Manual(initialName = trimmedName),
            quantityMode = QuantityMode.PORTIONS,
            portionCount = portions,
            estimatedPortionGrams = null,
            manualNutrition = null,
            error = null,
        )
        startRecognition(
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
        startRecognition(
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
        startRecognition(
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

    private fun startRecognition(request: RecognitionRequest, startMessage: String) {
        val previous = recognitionTaskStore.state.value
        if (previous?.status == RecognitionTaskStatus.RUNNING) {
            RecognitionService.cancel(foodApp, previous.request.id)
        }
        recognitionTaskStore.begin(request)
        notify(startMessage)
        try {
            RecognitionService.start(foodApp, request)
        } catch (error: Exception) {
            val message = error.message ?: "无法启动后台识别"
            recognitionTaskStore.fail(request.id, message)
            notify(message)
        }
    }

    private fun syncRecognitionTask(record: RecognitionTaskRecord?) {
        if (record == null) {
            _uiState.value = _uiState.value.copy(
                recognizing = false,
                recognitionStage = null,
                replacingDishId = null,
            )
            return
        }

        val request = record.request
        val baseState = _uiState.value.copy(
            mealType = request.mealType,
            targetDateEpochDay = request.targetDateEpochDay,
            mealMinuteOfDay = request.mealMinuteOfDay,
            note = request.note,
            selectedTags = request.selectedTags.toSet(),
            plateSize = request.plateSize,
        )
        when (record.status) {
            RecognitionTaskStatus.RUNNING -> {
                activeTaskId = request.id
                val step = when (request.type) {
                    RecognitionRequestType.MANUAL_GRAMS,
                    RecognitionRequestType.MANUAL_PORTIONS,
                    -> AddStep.Manual(
                        initialName = request.foodName.orEmpty(),
                        initialGrams = request.grams,
                    )

                    RecognitionRequestType.REPLACE_DISH -> AddStep.Review(
                        imageUri = request.imageUri,
                        result = request.baseResult ?: return,
                    )

                    else -> AddStep.PickSource
                }
                _uiState.value = baseState.copy(
                    step = step,
                    quickInput = request.text ?: baseState.quickInput,
                    quantityMode = if (request.type == RecognitionRequestType.MANUAL_PORTIONS) {
                        QuantityMode.PORTIONS
                    } else {
                        baseState.quantityMode
                    },
                    portionCount = request.portions ?: baseState.portionCount,
                    recognizing = true,
                    recognitionStage = record.stage,
                    replacingDishId = request.replaceDishId,
                    error = null,
                )
            }

            RecognitionTaskStatus.COMPLETED -> {
                val shouldNotify = activeTaskId == request.id
                activeTaskId = null
                when (request.type) {
                    RecognitionRequestType.MANUAL_GRAMS,
                    RecognitionRequestType.MANUAL_PORTIONS,
                    -> _uiState.value = baseState.copy(
                        step = AddStep.Manual(
                            initialName = request.foodName.orEmpty(),
                            initialGrams = request.grams,
                        ),
                        quantityMode = if (request.type == RecognitionRequestType.MANUAL_PORTIONS) {
                            QuantityMode.PORTIONS
                        } else {
                            QuantityMode.GRAMS
                        },
                        portionCount = request.portions ?: baseState.portionCount,
                        estimatedPortionGrams = record.estimatedPortionGrams,
                        manualNutrition = record.manualNutrition,
                        recognizing = false,
                        recognitionStage = null,
                        replacingDishId = null,
                        error = null,
                    )

                    RecognitionRequestType.TEXT,
                    RecognitionRequestType.IMAGE,
                    -> record.result?.let { result ->
                        _uiState.value = baseState.copy(
                            step = AddStep.Review(record.imageUri, result),
                            quickInput = request.text ?: baseState.quickInput,
                            recognizing = false,
                            recognitionStage = null,
                            replacingDishId = null,
                            error = null,
                        )
                    }

                    RecognitionRequestType.REPLACE_DISH -> record.result?.let { replacement ->
                        _uiState.value = baseState.copy(
                            step = AddStep.Review(
                                request.imageUri,
                                mergeReplacement(request, replacement),
                            ),
                            recognizing = false,
                            recognitionStage = null,
                            replacingDishId = null,
                            error = null,
                        )
                    }
                }
                if (shouldNotify) {
                    notify(
                        when (request.type) {
                            RecognitionRequestType.MANUAL_GRAMS,
                            RecognitionRequestType.MANUAL_PORTIONS,
                            -> "营养识别完成，已自动回填"
                            RecognitionRequestType.REPLACE_DISH -> "菜品已更换"
                            else -> "识别完成，请确认结果"
                        },
                    )
                }
            }

            RecognitionTaskStatus.FAILED -> {
                val shouldNotify = activeTaskId == request.id
                activeTaskId = null
                _uiState.value = baseState.copy(
                    recognizing = false,
                    recognitionStage = null,
                    replacingDishId = null,
                    error = record.error ?: "识别失败",
                )
                if (shouldNotify) notify(record.error ?: "识别失败")
            }
        }
    }

    private fun mergeReplacement(request: RecognitionRequest, replacement: MealRecognition): MealRecognition {
        val original = request.baseResult ?: return replacement
        val replacementDishes = replacement.dishes.map { it.withFreshIds() }
        return original.copy(
            dishes = original.dishes.flatMap { dish ->
                if (dish.id == request.replaceDishId) replacementDishes else listOf(dish)
            },
        )
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
        startRecognition(
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

private fun RecognizedDish.updateWeight(componentId: String, grams: Double): RecognizedDish = copy(
    components = components.map { component ->
        if (component.id == componentId) component.withWeight(grams) else component
    },
    children = children.map { it.updateWeight(componentId, grams) },
)

private fun RecognizedDish.removeComponent(componentId: String): RecognizedDish = copy(
    components = components.filterNot { it.id == componentId },
    children = children.map { it.removeComponent(componentId) },
)

private fun FoodComponent.withWeight(grams: Double): FoodComponent {
    val oldEstimate = estimatedWeightG.takeIf { it > 0.0 } ?: 1.0
    val minRatio = weightMinG / oldEstimate
    val maxRatio = weightMaxG / oldEstimate
    return copy(
        estimatedWeightG = grams,
        weightMinG = (grams * minRatio).coerceAtMost(grams),
        weightMaxG = (grams * maxRatio).coerceAtLeast(grams),
    )
}

private fun RecognizedDish.withReference(
    componentId: String,
    reference: NutritionReference,
    replacementName: String? = null,
): RecognizedDish = copy(
    components = components.map { component ->
        if (component.id == componentId) {
            val nextName = replacementName ?: component.name
            val isChinaReference = reference.dataType.contains("中国")
            component.copy(
                name = nextName,
                databaseQuery = when {
                    replacementName == null -> component.databaseQuery
                    isChinaReference -> nextName
                    else -> reference.description
                },
                chinaDatabaseQuery = replacementName ?: component.chinaDatabaseQuery,
                nutritionReference = reference,
            )
        } else {
            component
        }
    },
    children = children.map { it.withReference(componentId, reference, replacementName) },
)

private fun RecognizedDish.withFreshIds(): RecognizedDish = copy(
    id = java.util.UUID.randomUUID().toString(),
    components = components.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
    children = children.map { it.withFreshIds() },
)

private inline fun MutableStateFlow<AddFoodUiState>.updateDatabaseSearch(
    componentId: String,
    transform: (DatabaseSearchState) -> DatabaseSearchState,
) {
    val current = value.databaseSearch ?: return
    if (current.componentId == componentId) {
        value = value.copy(databaseSearch = transform(current))
    }
}
