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
import com.click.lightmemo.domain.splitDishes
import com.click.lightmemo.network.RecognitionException
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val client = foodApp.recognitionClient
    private val nutritionDatabase = foodApp.nutritionDatabase

    private var recognitionJob: Job? = null
    private var databaseSearchJob: Job? = null
    private val _uiState = MutableStateFlow(AddFoodUiState())
    val uiState: StateFlow<AddFoodUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.foodPresets.collect { presets ->
                _uiState.value = _uiState.value.copy(presets = presets)
            }
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

    fun openComponentSearch(component: FoodComponent) {
        _uiState.value = _uiState.value.copy(
            databaseSearch = DatabaseSearchState(
                componentId = component.id,
                query = component.name,
                loading = true,
            ),
        )
        searchComponentDatabase(component.id, component.name)
    }

    fun searchComponentDatabase(componentId: String, query: String) {
        databaseSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.updateDatabaseSearch(componentId) { it.copy(query = query, loading = false, results = emptyList(), error = "请输入食物名称") }
            return
        }
        _uiState.updateDatabaseSearch(componentId) { it.copy(query = query, loading = true, error = null) }
        databaseSearchJob = viewModelScope.launch {
            try {
                val results = nutritionDatabase.searchCandidates(
                    query = trimmed,
                    apiKey = settings.value.foodDataCentralApiKey,
                    limit = 20,
                )
                _uiState.updateDatabaseSearch(componentId) {
                    it.copy(loading = false, results = results, error = if (results.isEmpty()) "没有找到相近的食物" else null)
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

    fun selectComponentReference(componentId: String, reference: NutritionReference) {
        val step = _uiState.value.step as? AddStep.Review ?: return
        _uiState.value = _uiState.value.copy(
            step = step.copy(result = step.result.copy(
                dishes = step.result.dishes.map { it.withReference(componentId, reference) },
            )),
            databaseSearch = null,
        )
        notify("已匹配：${reference.description}")
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
        recognitionJob?.cancel()
        databaseSearchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            step = AddStep.PickSource,
            error = null,
            recognizing = false,
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
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            val current = settings.value
            if (!current.isRecognitionConfigured) {
                notify("请先在设置中配置 API Key 与 Base URL")
                _uiState.value = _uiState.value.copy(error = "请先在设置中配置 API Key 与 Base URL")
                return@launch
            }
            _uiState.value = _uiState.value.copy(recognizing = true, error = null)
            notify("已开始识别营养，完成后自动回填")
            try {
                val query = client.normalizeFoodQuery(
                    baseUrl = current.baseUrl,
                    apiKey = current.apiKey,
                    model = current.model,
                    foodName = trimmedName,
                )
                val reference = nutritionDatabase.lookup(query, current.foodDataCentralApiKey)
                    ?: throw RecognitionException("USDA 数据库中未找到该食物，请尝试更具体的名称")
                _uiState.value = _uiState.value.copy(
                    recognizing = false,
                    manualNutrition = reference.per100g * (grams / 100.0),
                )
                notify("营养识别完成，已自动回填")
            } catch (e: RecognitionException) {
                notify(e.message ?: "识别失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(e.message ?: "识别失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message ?: "识别失败")
            }
        }
    }

    /** 份数模式：先用大模型估重，再识别营养。 */
    fun recognizeManualWithPortions(name: String, portions: Double) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            notify("请输入食物名称")
            _uiState.value = _uiState.value.copy(error = "请输入食物名称")
            return
        }
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            val current = settings.value
            if (!current.isRecognitionConfigured) {
                notify("请先在设置中配置 API Key 与 Base URL")
                _uiState.value = _uiState.value.copy(error = "请先在设置中配置 API Key 与 Base URL")
                return@launch
            }
            _uiState.value = _uiState.value.copy(recognizing = true, error = null)
            notify("正在用大模型估计重量…")
            try {
                val grams = client.estimatePortionGrams(
                    baseUrl = current.baseUrl,
                    apiKey = current.apiKey,
                    model = current.model,
                    foodName = trimmedName,
                    portions = portions,
                    portionHint = _uiState.value.photoDescription,
                )
                _uiState.value = _uiState.value.copy(estimatedPortionGrams = grams)
                notify("估计重量 ${grams.toInt()}g，继续识别营养…")
                recognizeManual(trimmedName, grams)
            } catch (e: RecognitionException) {
                notify(e.message ?: "估重失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(e.message ?: "估重失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message ?: "估重失败")
            }
        }
    }

    /** 顶部输入框回车：立刻文字识别。 */
    fun recognizeFromText(raw: String? = null) {
        val text = (raw ?: _uiState.value.quickInput).trim()
        if (text.isEmpty()) {
            notify("请先输入食物描述")
            _uiState.value = _uiState.value.copy(error = "请先输入食物描述")
            return
        }
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            val current = settings.value
            if (!current.isRecognitionConfigured) {
                notify("请先在设置中配置 API Key 与 Base URL")
                _uiState.value = _uiState.value.copy(error = "请先在设置中配置 API Key 与 Base URL")
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                recognizing = true,
                error = null,
                quickInput = text,
                step = AddStep.PickSource,
            )
            notify("已开始文字识别…")
            try {
                val tagsDesc = _uiState.value.selectedTags.joinToString("、")
                val visualResult = client.recognizeFromText(
                    baseUrl = current.baseUrl,
                    apiKey = current.apiKey,
                    model = current.model,
                    text = text,
                    userDescription = buildList {
                        current.systemBackground.takeIf { it.isNotBlank() }?.let { add("用户背景：$it") }
                        tagsDesc.takeIf { it.isNotBlank() }?.let { add("本餐说明：$it") }
                        _uiState.value.note.takeIf { it.isNotBlank() }?.let { add("备注：$it") }
                    }.joinToString("\n"),
                    mealType = _uiState.value.mealType.label,
                    plateSize = _uiState.value.plateSize,
                )
                if (visualResult.dishes.isEmpty()) {
                    throw RecognitionException("未能识别到可记录的食物，请换个说法")
                }
                val result = nutritionDatabase.enrich(visualResult, current.foodDataCentralApiKey).splitDishes()
                _uiState.value = _uiState.value.copy(
                    recognizing = false,
                    step = AddStep.Review(imageUri = null, result = result),
                )
                notify("识别完成，请确认结果")
            } catch (e: RecognitionException) {
                notify(e.message ?: "识别失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(e.message ?: "识别失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message ?: "识别失败")
            }
        }
    }

    fun recognizeFromUri(uri: Uri) {
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            val current = settings.value
            if (!current.isRecognitionConfigured) {
                notify("请先在设置中配置 API Key 与 Base URL")
                _uiState.value = _uiState.value.copy(error = "请先在设置中配置 API Key 与 Base URL")
                return@launch
            }
            _uiState.value = _uiState.value.copy(recognizing = true, error = null, step = AddStep.PickSource)
            notify("已开始识别食物，结果将在后台返回")
            try {
                val base64 = withContext(Dispatchers.IO) { encodeImage(uri) }
                val tagsDesc = _uiState.value.selectedTags.joinToString("、")
                val visualResult = client.recognize(
                    baseUrl = current.baseUrl,
                    apiKey = current.apiKey,
                    model = current.model,
                    imageBase64 = base64,
                    mimeType = "image/jpeg",
                    userDescription = buildList {
                        current.systemBackground.takeIf { it.isNotBlank() }?.let { add("用户背景：$it") }
                        tagsDesc.takeIf { it.isNotBlank() }?.let { add("本餐说明：$it") }
                        _uiState.value.note.takeIf { it.isNotBlank() }?.let { add("备注：$it") }
                    }.joinToString("\n"),
                    mealType = _uiState.value.mealType.label,
                    plateSize = _uiState.value.plateSize,
                )
                if (!visualResult.isFoodImage || visualResult.dishes.isEmpty()) {
                    throw RecognitionException("图片中没有识别到可记录的食物")
                }
                val result = nutritionDatabase.enrich(visualResult, current.foodDataCentralApiKey).splitDishes()
                val savedImageUri = FoodImages.persistEncoded(foodApp, base64)
                _uiState.value = _uiState.value.copy(
                    recognizing = false,
                    step = AddStep.Review(imageUri = savedImageUri.toString(), result = result),
                )
                notify("食物识别完成，照片已保存，请确认结果")
            } catch (e: RecognitionException) {
                notify(e.message ?: "识别失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(e.message ?: "识别失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message ?: "识别失败")
            }
        }
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
        val current = settings.value
        if (!current.isRecognitionConfigured) {
            notify("请先在设置中配置 API Key 与 Base URL")
            return
        }
        _uiState.value = state.copy(recognizing = true, replacingDishId = dishId, error = null)
        recognitionJob = viewModelScope.launch {
            try {
                val visual = client.recognizeFromText(
                    baseUrl = current.baseUrl,
                    apiKey = current.apiKey,
                    model = current.model,
                    text = "${original.grams}克${name.trim()}",
                    userDescription = "更正一道菜品，保持总重量，重新识别组成。",
                    mealType = state.mealType.label,
                    plateSize = state.plateSize,
                )
                val replacement = nutritionDatabase.enrich(visual, current.foodDataCentralApiKey).splitDishes()
                require(replacement.dishes.isNotEmpty() && replacement.dishes.all { it.allComponents.isNotEmpty() }) {
                    "未识别到新菜品，请重试"
                }
                val latest = _uiState.value.step as? AddStep.Review ?: return@launch
                // Fresh IDs isolate replacement rows from old edit state and other recognition results.
                val dishes = replacement.dishes.map { dish ->
                    dish.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        components = dish.components.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
                    )
                }
                _uiState.value = _uiState.value.copy(step = latest.copy(result = latest.result.copy(
                    dishes = latest.result.dishes.flatMap { if (it.id == dishId) dishes else listOf(it) },
                )))
                notify("菜品已更换")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: "更换失败，请重试"
                _uiState.value = _uiState.value.copy(error = message)
                notify(message)
            } finally {
                _uiState.value = _uiState.value.copy(recognizing = false, replacingDishId = null)
            }
        }
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

    private suspend fun encodeImage(uri: Uri): String = FoodImages.encode(foodApp, uri)

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
): RecognizedDish = copy(
    components = components.map { component ->
        if (component.id == componentId) component.copy(nutritionReference = reference) else component
    },
    children = children.map { it.withReference(componentId, reference) },
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
