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
import com.foodcalorie.app.network.RecognitionException
import java.io.ByteArrayOutputStream
import java.time.LocalDate
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

    data object Manual : AddStep
}

data class AddFoodUiState(
    val step: AddStep = AddStep.PickSource,
    val recognizing: Boolean = false,
    val error: String? = null,
    val mealType: MealType = defaultMealType(),
    val manualNutrition: Nutrition? = null,
    val plateSize: String = "",
    val photoDescription: String = "",
    val targetDateEpochDay: Long = LocalDate.now().toEpochDay(),
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

    fun openManual() {
        _uiState.value = _uiState.value.copy(
            step = AddStep.Manual,
            error = null,
            manualNutrition = null,
        )
    }

    fun backToPick() {
        _uiState.value = _uiState.value.copy(
            step = AddStep.PickSource,
            error = null,
            recognizing = false,
            manualNutrition = null,
        )
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
                val visualResult = client.recognize(
                    baseUrl = current.baseUrl,
                    apiKey = current.apiKey,
                    model = current.model,
                    imageBase64 = base64,
                    mimeType = "image/jpeg",
                    userDescription = buildList {
                        current.systemBackground.takeIf { it.isNotBlank() }?.let { add("用户背景：$it") }
                        _uiState.value.photoDescription.takeIf { it.isNotBlank() }?.let { add("本餐说明：$it") }
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

    fun saveRecognized(result: MealRecognition, imageUri: String?) {
        viewModelScope.launch {
            val meal = _uiState.value.mealType
            val day = _uiState.value.targetDateEpochDay
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
                    ),
                )
            }
            _uiState.value = _uiState.value.copy(step = AddStep.PickSource, error = null)
            notify("食物已保存")
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
