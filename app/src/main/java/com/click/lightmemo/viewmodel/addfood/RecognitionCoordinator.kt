package com.click.lightmemo.viewmodel

import android.net.Uri
import com.click.lightmemo.FoodApp
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.recognition.RecognitionRequest
import com.click.lightmemo.recognition.RecognitionRequestType
import com.click.lightmemo.recognition.RecognitionService
import com.click.lightmemo.recognition.RecognitionTaskRecord
import com.click.lightmemo.recognition.RecognitionTaskStatus
import com.click.lightmemo.recognition.RecognitionTaskStore
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Owns recognition task start / retry / task-store sync.
 * UI step transitions stay expressed against [AddFoodUiState].
 */
internal class RecognitionCoordinator(
    private val app: FoodApp,
    private val store: RecognitionTaskStore,
    private val uiState: MutableStateFlow<AddFoodUiState>,
    private val notify: (String) -> Unit,
) {
    private var activeTaskId: String? = null

    val currentRecord: RecognitionTaskRecord?
        get() = store.state.value

    fun clear(taskId: String? = null) {
        store.clear(taskId)
    }

    fun start(request: RecognitionRequest, startMessage: String) {
        val previous = store.state.value
        if (previous?.status == RecognitionTaskStatus.RUNNING) {
            RecognitionService.cancel(app, previous.request.id)
        }
        store.begin(request)
        uiState.value = uiState.value.copy(
            canRetryRecognition = false,
            error = null,
            aiEstimatingComponentIds = emptySet(),
        )
        notify(startMessage)
        try {
            RecognitionService.start(app, request)
        } catch (error: Exception) {
            val message = error.message ?: "无法启动后台识别"
            store.fail(request.id, message)
            notify(message)
        }
    }

    /** 失败后用原请求重新发起识别（新 id，避免与旧任务冲突）。 */
    fun retry() {
        val record = store.state.value
        if (record?.status != RecognitionTaskStatus.FAILED) return
        val request = record.request
        val state = uiState.value
        when (request.type) {
            RecognitionRequestType.MANUAL_GRAMS,
            RecognitionRequestType.MANUAL_PORTIONS,
            -> {
                val name = request.foodName.orEmpty()
                val grams = record.estimatedPortionGrams ?: request.grams ?: 0.0
                uiState.value = state.copy(
                    step = AddStep.Review(
                        imageUri = null,
                        result = buildManualMealRecognition(name, grams, Nutrition()),
                    ),
                    recognizing = true,
                    recognitionStage = com.click.lightmemo.domain.RecognitionStage.PREPARING,
                    quantityMode = if (request.type == RecognitionRequestType.MANUAL_PORTIONS) {
                        QuantityMode.PORTIONS
                    } else {
                        state.quantityMode
                    },
                    portionCount = request.portions ?: state.portionCount,
                )
            }

            RecognitionRequestType.REPLACE_DISH -> {
                val base = request.baseResult ?: return
                uiState.value = state.copy(
                    step = AddStep.Review(request.imageUri, base),
                    recognizing = true,
                    recognitionStage = com.click.lightmemo.domain.RecognitionStage.PREPARING,
                    replacingDishId = request.replaceDishId,
                )
            }

            RecognitionRequestType.TEXT,
            RecognitionRequestType.IMAGE,
            -> {
                uiState.value = state.copy(
                    step = AddStep.PickSource,
                    quickInput = request.text ?: request.foodName ?: state.quickInput,
                    recognizing = true,
                    recognitionStage = com.click.lightmemo.domain.RecognitionStage.PREPARING,
                )
            }
        }
        start(
            request.copy(id = java.util.UUID.randomUUID().toString()),
            "正在重试识别…",
        )
    }

    fun sync(record: RecognitionTaskRecord?) {
        if (record == null) {
            uiState.value = uiState.value.copy(
                recognizing = false,
                recognitionStage = null,
                replacingDishId = null,
            )
            return
        }

        val request = record.request
        val baseState = uiState.value.copy(
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
                    -> {
                        val name = request.foodName.orEmpty()
                        val grams = request.grams ?: 0.0
                        val existing = uiState.value.step as? AddStep.Review
                        existing ?: AddStep.Review(
                            imageUri = null,
                            result = buildManualMealRecognition(name, grams, Nutrition()),
                        )
                    }

                    RecognitionRequestType.REPLACE_DISH -> AddStep.Review(
                        imageUri = request.imageUri,
                        result = request.baseResult ?: return,
                    )

                    else -> AddStep.PickSource
                }
                uiState.value = baseState.copy(
                    step = step,
                    quickInput = request.text ?: request.foodName ?: baseState.quickInput,
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
                    -> {
                        val name = request.foodName.orEmpty()
                        val grams = record.estimatedPortionGrams
                            ?: request.grams
                            ?: 0.0
                        val nutrition = record.manualNutrition ?: Nutrition()
                        uiState.value = baseState.copy(
                            step = AddStep.Review(
                                imageUri = null,
                                result = buildManualMealRecognition(name, grams, nutrition),
                            ),
                            quantityMode = if (request.type == RecognitionRequestType.MANUAL_PORTIONS) {
                                QuantityMode.PORTIONS
                            } else {
                                QuantityMode.GRAMS
                            },
                            portionCount = request.portions ?: baseState.portionCount,
                            estimatedPortionGrams = record.estimatedPortionGrams,
                            manualNutrition = nutrition,
                            recognizing = false,
                            recognitionStage = null,
                            replacingDishId = null,
                            error = null,
                        )
                    }

                    RecognitionRequestType.TEXT,
                    RecognitionRequestType.IMAGE,
                    -> record.result?.let { result ->
                        uiState.value = baseState.copy(
                            step = AddStep.Review(record.imageUri, result),
                            quickInput = request.text ?: request.foodName ?: baseState.quickInput,
                            recognizing = false,
                            recognitionStage = null,
                            replacingDishId = null,
                            error = null,
                        )
                    }

                    RecognitionRequestType.REPLACE_DISH -> record.result?.let { replacement ->
                        uiState.value = baseState.copy(
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
                uiState.value = baseState.copy(
                    recognizing = false,
                    recognitionStage = null,
                    replacingDishId = null,
                    error = record.error ?: "识别失败",
                    canRetryRecognition = true,
                )
                if (shouldNotify) notify(record.error ?: "识别失败")
            }
        }
    }

    private fun mergeReplacement(
        request: RecognitionRequest,
        replacement: MealRecognition,
    ): MealRecognition {
        val original = request.baseResult ?: return replacement
        val replacementDishes = replacement.dishes.map { it.withFreshIds() }
        return original.copy(
            dishes = original.dishes.flatMap { dish ->
                if (dish.id == request.replaceDishId) replacementDishes else listOf(dish)
            },
        )
    }
}
