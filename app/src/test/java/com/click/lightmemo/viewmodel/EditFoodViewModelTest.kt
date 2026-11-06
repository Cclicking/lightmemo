package com.click.lightmemo.viewmodel

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.click.lightmemo.FoodApp
import com.click.lightmemo.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FoodApp::class)
class EditFoodViewModelTest {
    private val editors = mutableListOf<EditFoodViewModel>()
    private val app get() = ApplicationProvider.getApplicationContext<FoodApp>()
    private val component = FoodComponent("rice", "米饭", "rice", source = ComponentSource.USER_PROVIDED,
        estimatedWeightG = 100.0, weightMinG = 100.0, weightMaxG = 100.0, confidence = 1.0,
        nutritionReference = NutritionReference("rice", "米饭", "测试", Nutrition(116.0, 3.0, 26.0, 1.0)))

    @Before fun setup() { Dispatchers.setMain(Dispatchers.Unconfined) }
    @After fun cleanup() { editors.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }

    private suspend fun editor(handle: SavedStateHandle = SavedStateHandle(), id: Long? = null): EditFoodViewModel {
        val entryId = id ?: app.foodLogRepository.insert(FoodLog(
            name = "米饭", mealType = MealType.LUNCH, grams = 100.0,
            nutrition = component.nutrition, components = listOf(component), mealMinuteOfDay = 720))
        return EditFoodViewModel(app, handle).also {
            editors += it
            it.load(entryId)
            await { !it.uiState.value.loading }
            assertNotNull(it.uiState.value.draft)
        }
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(10)
    }

    @Test fun unchangedDraftExitsButEditedDraftRequiresAnExplicitChoice() = runBlocking {
        val vm = editor()
        var exits = 0
        vm.requestBack { exits++ }
        assertEquals(1, exits)
        vm.updateFoodDraft(vm.uiState.value.draft!!.food.copy(name = "晚餐米饭"))
        vm.requestBack { exits++ }
        assertTrue(vm.uiState.value.showLeaveConfirm)
        assertEquals(1, exits)
        vm.dismissLeaveConfirm()
        assertEquals("晚餐米饭", vm.uiState.value.draft!!.food.name)
        vm.requestBack { exits++ }
        vm.discardDraft { exits++ }
        assertEquals(2, exits)
        assertEquals("米饭", app.foodLogRepository.getById(vm.uiState.value.entry!!.id)!!.name)
    }

    @Test fun restoredDraftIncludesRawInvalidInputAndDoesNotOverwriteAnotherRecord() = runBlocking {
        val handle = SavedStateHandle()
        val vm = editor(handle)
        vm.updateDraft { it.copy(food = it.food.copy(name = "修改后", nutrition = Nutrition(180.0)),
            grams = "", date = "2026-11-04", time = "19:30") }
        val restoredHandle = SavedStateHandle(mapOf("food_editor_draft" to Bundle(handle.get<Bundle>("food_editor_draft")!!)))
        val restored = editor(restoredHandle, vm.uiState.value.entry!!.id)
        assertEquals(vm.uiState.value.draft, restored.uiState.value.draft)
        assertNull(restored.uiState.value.draft!!.validatedFood())
        var exits = 0
        restored.requestBack { exits++ }
        assertTrue(restored.uiState.value.showLeaveConfirm)
        assertEquals(0, exits)
        val another = editor(restoredHandle)
        assertEquals("米饭", another.uiState.value.draft!!.food.name)
    }

    @Test fun pinUsesCurrentNamePortionComponentsAndManualNutritionWithoutSavingTheRecord() = runBlocking {
        val vm = editor()
        val food = vm.uiState.value.draft!!.food
        vm.updateDraft { it.copy(food = food.copy(name = "我的米饭", nutrition = Nutrition(180.0)), grams = "150") }
        vm.toggleDraftPreset()
        await { vm.uiState.value.pinMessage != null }
        val preset = app.settingsRepository.foodPresets.first().single { it.name == "我的米饭" }
        assertEquals(150.0, preset.defaultGrams, 0.0)
        assertEquals(Nutrition(180.0), preset.nutrition)
        assertEquals(food.components, preset.components)
        assertEquals(food, app.foodLogRepository.getById(food.id))
    }

    @Test fun invalidDraftCannotPinOrSave() = runBlocking {
        val vm = editor()
        vm.updateDraft { it.copy(food = it.food.copy(name = "无效预设"), grams = "0") }
        vm.toggleDraftPreset()
        var saved = false
        vm.saveDraft { saved = true }
        assertFalse(saved)
        assertNotNull(vm.uiState.value.error)
        assertFalse(app.settingsRepository.foodPresets.first().any { it.name == "无效预设" })
    }

    @Test fun saveKeepsCompositionAndManualTotalsAndIgnoresDuplicateSubmission() = runBlocking {
        val vm = editor()
        vm.updateFoodDraft(vm.uiState.value.draft!!.food.copy(nutrition = Nutrition(180.0)))
        var exits = 0
        vm.requestBack { exits++ }
        var saves = 0
        vm.saveDraft { saves++ }
        vm.saveDraft { saves++ }
        vm.requestBack { exits++ }
        await { !vm.uiState.value.saving }
        assertEquals(1, saves)
        assertEquals(0, exits)
        val saved = app.foodLogRepository.getById(vm.uiState.value.entry!!.id)!!
        assertEquals(Nutrition(180.0), saved.nutrition)
        assertEquals(listOf(component), saved.components)
        assertFalse(vm.uiState.value.showLeaveConfirm)
    }

    @Test fun failedSaveKeepsTheDraftAndAllowsContinuedEditing() = runBlocking {
        val vm = editor()
        vm.updateFoodDraft(vm.uiState.value.draft!!.food.copy(name = "未保存修改"))
        app.foodLogRepository.deleteById(vm.uiState.value.entry!!.id)
        vm.requestBack { fail("dirty draft must not exit") }
        vm.saveDraft { fail("deleted record must not save") }
        await { !vm.uiState.value.saving }
        assertNotNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.showLeaveConfirm)
        assertEquals("未保存修改", vm.uiState.value.draft!!.food.name)
        vm.dismissLeaveConfirm()
        vm.updateFoodDraft(vm.uiState.value.draft!!.food.copy(name = "继续修改"))
        assertEquals("继续修改", vm.uiState.value.draft!!.food.name)
    }
}
