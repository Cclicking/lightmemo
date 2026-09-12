package com.foodcalorie.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foodcalorie.app.FoodApp
import com.foodcalorie.app.domain.FoodLog
import com.foodcalorie.app.domain.MealType
import com.foodcalorie.app.domain.Nutrition
import com.foodcalorie.app.domain.FoodComponent
import com.foodcalorie.app.domain.MealRecognition
import com.foodcalorie.app.domain.RecognizedDish
import com.foodcalorie.app.domain.NutritionReference
import com.foodcalorie.app.network.RecognitionException
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
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

data class PresetFood(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val defaultGrams: Double,
    val portionLabel: String,
    val nutrition: Nutrition? = null,
)

data class AddFoodUiState(
    val step: AddStep = AddStep.PickSource,
    val recognizing: Boolean = false,
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

val DefaultPresetFoods = listOf(
    PresetFood(id = "rice", name = "米饭", defaultGrams = 150.0, portionLabel = "1 小碗", nutrition = Nutrition(174.0, 3.9, 38.9, 0.5)),
    PresetFood(id = "mantou", name = "馒头", defaultGrams = 100.0, portionLabel = "1 个", nutrition = Nutrition(223.0, 7.0, 47.0, 1.1)),
    PresetFood(id = "egg", name = "鸡蛋", defaultGrams = 50.0, portionLabel = "1 个", nutrition = Nutrition(72.0, 6.3, 0.4, 4.8)),
    PresetFood(id = "milk", name = "牛奶", defaultGrams = 250.0, portionLabel = "1 盒", nutrition = Nutrition(162.0, 8.0, 12.0, 8.8)),
    PresetFood(id = "chicken_breast", name = "鸡胸肉", defaultGrams = 120.0, portionLabel = "1 块", nutrition = Nutrition(198.0, 37.1, 0.0, 4.3)),
    PresetFood(id = "apple", name = "苹果", defaultGrams = 200.0, portionLabel = "1 个", nutrition = Nutrition(104.0, 0.6, 27.6, 0.3)),
    PresetFood(id = "banana", name = "香蕉", defaultGrams = 120.0, portionLabel = "1 根", nutrition = Nutrition(107.0, 1.3, 27.4, 0.4)),
    PresetFood(id = "bread", name = "全麦面包", defaultGrams = 60.0, portionLabel = "2 片", nutrition = Nutrition(154.0, 7.2, 25.8, 2.4)),
    PresetFood(id = "yogurt", name = "酸奶", defaultGrams = 150.0, portionLabel = "1 杯", nutrition = Nutrition(93.0, 5.3, 11.9, 3.3)),
    PresetFood(id = "oats", name = "燕麦片", defaultGrams = 40.0, portionLabel = "1 份", nutrition = Nutrition(150.0, 5.3, 26.4, 2.7)),
    PresetFood(id = "beef", name = "牛肉", defaultGrams = 100.0, portionLabel = "1 份", nutrition = Nutrition(250.0, 26.0, 0.0, 15.0)),
    PresetFood(id = "salmon", name = "三文鱼", defaultGrams = 120.0, portionLabel = "1 份", nutrition = Nutrition(250.0, 27.0, 0.0, 15.0)),
)

class AddFoodViewModel(app: Application) : AndroidViewModel(app) {
    private val foodApp = app as FoodApp
    private val repo = foodApp.foodLogRepository
    private val settingsRepo = foodApp.settingsRepository
    private val client = foodApp.recognitionClient
    private val nutritionDatabase = foodApp.nutritionDatabase

    private val _uiState = MutableStateFlow(AddFoodUiState())
    val uiState: StateFlow<AddFoodUiState> = _uiState.asStateFlow()

    val settings = settingsRepo.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.foodcalorie.app.data.AppSettings(),
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
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.updateDatabaseSearch(componentId) { it.copy(query = query, loading = false, results = emptyList(), error = "请输入食物名称") }
            return
        }
        _uiState.updateDatabaseSearch(componentId) { it.copy(query = query, loading = true, error = null) }
        viewModelScope.launch {
            try {
                val results = nutritionDatabase.searchCandidates(
                    query = trimmed,
                    apiKey = settings.value.foodDataCentralApiKey,
                    limit = 20,
                )
                _uiState.updateDatabaseSearch(componentId) {
                    it.copy(loading = false, results = results, error = if (results.isEmpty()) "没有找到相近的食物" else null)
                }
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

    fun updatePreset(updated: PresetFood) {
        _uiState.value = _uiState.value.copy(
            presets = _uiState.value.presets.map { if (it.id == updated.id) updated else it },
        )
        notify("预设「${updated.name}」已更新")
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
        _uiState.value = _uiState.value.copy(portionCount = value.coerceAtLeast(0.1))
    }

    fun recognizeManual(name: String, grams: Double) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            notify("请输入食物名称")
            _uiState.value = _uiState.value.copy(error = "请输入食物名称")
            return
        }
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
                val result = nutritionDatabase.enrich(visualResult, current.foodDataCentralApiKey)
                _uiState.value = _uiState.value.copy(
                    recognizing = false,
                    step = AddStep.Review(imageUri = null, result = result),
                )
                notify("识别完成，请确认结果")
            } catch (e: RecognitionException) {
                notify(e.message ?: "识别失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message)
            } catch (e: Exception) {
                notify(e.message ?: "识别失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message ?: "识别失败")
            }
        }
    }

    fun recognizeFromUri(uri: Uri) {
        viewModelScope.launch {
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
                val result = nutritionDatabase.enrich(visualResult, current.foodDataCentralApiKey)
                _uiState.value = _uiState.value.copy(
                    recognizing = false,
                    step = AddStep.Review(imageUri = uri.toString(), result = result),
                )
                notify("食物识别完成，请确认结果")
            } catch (e: RecognitionException) {
                notify(e.message ?: "识别失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message)
            } catch (e: Exception) {
                notify(e.message ?: "识别失败")
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message ?: "识别失败")
            }
        }
    }

    fun saveManual(name: String, grams: Double, nutrition: Nutrition) {
        viewModelScope.launch {
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

    fun saveRecognized(result: MealRecognition, imageUri: String?) {
        viewModelScope.launch {
            val meal = _uiState.value.mealType
            val day = _uiState.value.targetDateEpochDay
            val note = _uiState.value.note.takeIf { it.isNotBlank() }
            val tags = _uiState.value.selectedTags.toList()
            result.dishes.forEach { dish ->
                repo.insert(
                    FoodLog(
                        name = dish.name,
                        mealType = meal,
                        grams = dish.grams,
                        nutrition = dish.nutrition,
                        components = dish.allComponents,
                        imageUri = imageUri,
                        dateEpochDay = day,
                        mealMinuteOfDay = _uiState.value.mealMinuteOfDay,
                        note = note,
                        mealTags = tags,
                    ),
                )
            }
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

    private suspend fun encodeImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = getApplication<FoodApp>().contentResolver
        val input = resolver.openInputStream(uri) ?: throw RecognitionException("无法读取图片")
        val raw = input.use { it.readBytes() }
        var bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: throw RecognitionException("图片格式无法解析")
        val maxSide = 1024
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale < 1f) {
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true,
            )
        }
        val os = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, os)
        Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
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
