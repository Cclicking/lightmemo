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
import com.foodcalorie.app.domain.RecognizedFood
import com.foodcalorie.app.network.RecognitionException
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AddStep {
    data object PickSource : AddStep
    data class Review(
        val imageUri: String?,
        val items: List<RecognizedFood>,
    ) : AddStep

    data object Manual : AddStep
}

data class AddFoodUiState(
    val step: AddStep = AddStep.PickSource,
    val recognizing: Boolean = false,
    val error: String? = null,
    val mealType: MealType = defaultMealType(),
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

    private val _uiState = MutableStateFlow(AddFoodUiState())
    val uiState: StateFlow<AddFoodUiState> = _uiState.asStateFlow()

    val settings = settingsRepo.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.foodcalorie.app.data.AppSettings(),
    )

    fun setMealType(type: MealType) {
        _uiState.value = _uiState.value.copy(mealType = type)
    }

    fun openManual() {
        _uiState.value = _uiState.value.copy(step = AddStep.Manual, error = null)
    }

    fun backToPick() {
        _uiState.value = _uiState.value.copy(step = AddStep.PickSource, error = null, recognizing = false)
    }

    fun recognizeFromUri(uri: Uri) {
        viewModelScope.launch {
            val current = settings.value
            if (!current.isRecognitionConfigured) {
                _uiState.value = _uiState.value.copy(error = "请先在设置中配置 API Key 与 Base URL")
                return@launch
            }
            _uiState.value = _uiState.value.copy(recognizing = true, error = null, step = AddStep.PickSource)
            try {
                val base64 = withContext(Dispatchers.IO) { encodeImage(uri) }
                val items = client.recognize(
                    baseUrl = current.baseUrl,
                    apiKey = current.apiKey,
                    model = current.model,
                    imageBase64 = base64,
                    mimeType = "image/jpeg",
                )
                _uiState.value = _uiState.value.copy(
                    recognizing = false,
                    step = AddStep.Review(imageUri = uri.toString(), items = items),
                )
            } catch (e: RecognitionException) {
                _uiState.value = _uiState.value.copy(recognizing = false, error = e.message)
            } catch (e: Exception) {
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
                    dateEpochDay = LocalDate.now().toEpochDay(),
                ),
            )
            _uiState.value = _uiState.value.copy(step = AddStep.PickSource, error = null)
        }
    }

    fun saveRecognized(items: List<RecognizedFood>, imageUri: String?) {
        viewModelScope.launch {
            val meal = _uiState.value.mealType
            val day = LocalDate.now().toEpochDay()
            items.forEach { item ->
                repo.insert(
                    FoodLog(
                        name = item.name,
                        mealType = meal,
                        grams = item.grams,
                        nutrition = item.nutrition,
                        imageUri = imageUri,
                        dateEpochDay = day,
                    ),
                )
            }
            _uiState.value = _uiState.value.copy(step = AddStep.PickSource, error = null)
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
