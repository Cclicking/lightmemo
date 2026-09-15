package com.click.lightmemo.viewmodel

import com.click.lightmemo.data.DefaultPresetFoods
import com.click.lightmemo.data.PresetFood
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.NutritionReference
import com.click.lightmemo.domain.RecognitionStage
import java.time.LocalDate
import java.time.LocalTime

sealed interface AddStep {
    data object PickSource : AddStep
    data class Review(
        val imageUri: String?,
        val result: MealRecognition,
    ) : AddStep

    /** 文字录入：名称 / 计量 / 自动识别 / 手动录入入口 */
    data class Manual(
        val initialName: String = "",
        val initialGrams: Double? = null,
        val initialNutrition: Nutrition? = null,
    ) : AddStep

    /** 手动录入：完整复用编辑记录页元素 */
    data class ManualEdit(
        val initialName: String = "",
        val initialGrams: Double? = null,
        val initialNutrition: Nutrition? = null,
        val initialComponents: List<FoodComponent> = emptyList(),
    ) : AddStep

    /** 预设列表（与手动录入同级的 bottomsheet 步骤页） */
    data object PresetList : AddStep

    /** 预设编辑：复用手动录入页，去掉餐次与用餐信息 */
    data class PresetEdit(
        val preset: PresetFood,
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
    /** 上次识别失败后是否允许一键重试 */
    val canRetryRecognition: Boolean = false,
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
    val presets: List<PresetFood> = DefaultPresetFoods,
    val databaseSearch: DatabaseSearchState? = null,
    /** 手动录入（编辑记录式）页的成分数据库搜索 */
    val draftDatabaseSearch: DatabaseSearchState? = null,
    /** 二级页是否已有未保存内容（用于返回确认） */
    val secondaryHasContent: Boolean = false,
    val showLeaveConfirm: Boolean = false,
    val showDeletePresetConfirm: Boolean = false,
    /** 手动录入页当前草稿（供顶栏 pin/unpin 预设） */
    val manualDraftSnapshot: ManualDraftSnapshot? = null,
)

/** 手动录入页草稿快照，用于顶栏加入/取消预设。 */
data class ManualDraftSnapshot(
    val name: String,
    val grams: Double,
    val nutrition: Nutrition,
    val components: List<FoodComponent>,
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
    val hour = LocalTime.now().hour
    return when {
        hour < 10 -> MealType.BREAKFAST
        hour < 15 -> MealType.LUNCH
        hour < 21 -> MealType.DINNER
        else -> MealType.SNACK
    }
}

val DefaultMealTags = listOf("无糖", "少油", "少盐", "清淡", "多菜", "无主食", "高蛋白", "外食")
