package com.click.lightmemo.viewmodel

import androidx.test.core.app.ApplicationProvider
import com.click.lightmemo.FoodApp
import com.click.lightmemo.domain.*
import com.click.lightmemo.recognition.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FoodApp::class)
class PersonalFoodRecognitionTest {
    private val reference = NutritionReference("user", "鸡蛋", "中国库", Nutrition(140.0))

    @Test fun cacheHitWaitsForUserChoiceAndSurvivesTaskStoreRecreation() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<FoodApp>()
        val store = RecognitionTaskStore(app)
        val pending = request().copy(imageUri = "content://test/image")
        store.begin(pending)
        store.awaitCacheConfirmation(pending.id)
        val restored = RecognitionTaskStore(app).state.value!!
        assertEquals(RecognitionTaskStatus.AWAITING_CACHE_CONFIRMATION, restored.status)
        val state = MutableStateFlow(AddFoodUiState(recognizing = true))
        coordinator(state) { error("must not read memory while waiting") }.sync(restored)
        assertFalse(state.value.recognizing)
        assertEquals(pending, state.value.pendingImageReuse)
        assertTrue(state.value.step is AddStep.PickSource)
        coordinator(state) { emptyList() }.sync(null)
        assertNull(state.value.pendingImageReuse)
        store.clear()
    }

    @Test fun imagePathMetricsArePersistedWithCompletedTask() {
        val app = ApplicationProvider.getApplicationContext<FoodApp>()
        val store = RecognitionTaskStore(app)
        val request = request()
        store.begin(request)
        store.complete(request.id, result = meal(), path = RecognitionPath.FAST,
            modelCallCount = 1, durationMillis = 500, cacheReused = false)
        val restored = RecognitionTaskStore(app).state.value!!
        assertEquals(RecognitionPath.FAST, restored.path)
        assertEquals(1, restored.modelCallCount)
        assertEquals(500L, restored.durationMillis)
        assertFalse(restored.cacheReused)
        store.clear()
    }
    private fun dish(name: String = "番茄炒蛋", id: String = "dish") = RecognizedDish(
        id, name, DishType.MIXED_DISH, 0.9,
        listOf(FoodComponent("component", "鸡蛋", "egg", source = ComponentSource.VISIBLE,
            estimatedWeightG = 320.0, weightMinG = 300.0, weightMaxG = 340.0, confidence = 0.9)),
    )
    private fun meal(dish: RecognizedDish = dish()) = MealRecognition(true, "午餐", listOf(dish), 0.9, emptyList())
    private fun memory() = PersonalFoodMemory("番茄炒蛋", typicalGrams = 230.0, minGrams = 230.0, maxGrams = 230.0,
        nutrition = Nutrition(322.0), components = dish().components.map { it.copy(nutritionReference = reference) },
        preferredComponentReferences = mapOf("鸡蛋" to reference), correctionCount = 1, useCount = 1)
    private fun request(type: RecognitionRequestType = RecognitionRequestType.IMAGE) = RecognitionRequest(
        type = type, mealType = MealType.LUNCH, targetDateEpochDay = 20000, mealMinuteOfDay = 720)
    private fun coordinator(state: MutableStateFlow<AddFoodUiState>, load: suspend () -> List<PersonalFoodMemory>): RecognitionCoordinator {
        val app = ApplicationProvider.getApplicationContext<FoodApp>()
        return RecognitionCoordinator(app, RecognitionTaskStore(app), state, {}, load)
    }

    @Test fun completedRecognitionReadsMemoryAndPrefersReferenceWithoutChangingVisualWeight() = runBlocking {
        val state = MutableStateFlow(AddFoodUiState())
        val initial = meal()
        coordinator(state) { listOf(memory()) }.sync(RecognitionTaskRecord(request(), RecognitionTaskStatus.COMPLETED, result = initial))
        val review = state.value.step as AddStep.Review
        assertEquals(320.0, review.result.dishes.single().grams, 0.001)
        assertEquals(reference, review.result.dishes.single().components.single().nutritionReference)
        assertTrue(review.memorySuggestions.single().referenceApplied)
        assertEquals(initial, review.unassistedResult)
        assertEquals(review.result, review.originalResult)
    }

    @Test fun repeatedCompletionDoesNotOverwriteUserEditsOrReloadMemory() = runBlocking {
        val state = MutableStateFlow(AddFoodUiState())
        var reads = 0
        val coordinator = coordinator(state) { reads++; listOf(memory()) }
        val record = RecognitionTaskRecord(request(), RecognitionTaskStatus.COMPLETED, result = meal())
        coordinator.sync(record)
        val review = state.value.step as AddStep.Review
        val edited = review.copy(result = meal(dish().copy(name = "用户修改")))
        state.value = state.value.copy(step = edited)
        coordinator.sync(record)
        assertEquals(edited, state.value.step)
        assertEquals(1, reads)
    }

    @Test fun unavailableMemoryFallsBackToUnchangedRecognition() = runBlocking {
        val state = MutableStateFlow(AddFoodUiState())
        val initial = meal()
        coordinator(state) { error("memory unavailable") }.sync(RecognitionTaskRecord(request(), RecognitionTaskStatus.COMPLETED, result = initial))
        val review = state.value.step as AddStep.Review
        assertEquals(initial, review.result)
        assertTrue(review.memorySuggestions.isEmpty())
        assertFalse(state.value.recognizing)
    }

    @Test fun replacementKeepsOriginalDishIdentityAndCorrectionBaseline() = runBlocking {
        val initial = meal(dish("错误菜名"))
        val state = MutableStateFlow(AddFoodUiState(step = AddStep.Review(null, initial)))
        val request = request(RecognitionRequestType.REPLACE_DISH).copy(baseResult = initial, baseOriginalResult = initial, replaceDishId = "dish")
        val coordinator = coordinator(state) { emptyList() }
        coordinator.sync(RecognitionTaskRecord(request))
        coordinator.sync(RecognitionTaskRecord(request, RecognitionTaskStatus.COMPLETED, result = meal(dish("正确菜名", "fresh"))))
        val review = state.value.step as AddStep.Review
        assertEquals("dish", review.result.dishes.single().id)
        assertEquals("正确菜名", review.result.dishes.single().name)
        assertEquals("错误菜名", review.originalResult.dishes.single().name)
    }

    @Test fun manualExplicitGramsRemainUnchangedWhenMemoryMatches() = runBlocking {
        val state = MutableStateFlow(AddFoodUiState())
        val request = request(RecognitionRequestType.MANUAL_GRAMS).copy(foodName = "番茄炒蛋", grams = 150.0)
        coordinator(state) { listOf(memory()) }.sync(RecognitionTaskRecord(request, RecognitionTaskStatus.COMPLETED, manualNutrition = Nutrition(200.0)))
        val review = state.value.step as AddStep.Review
        assertEquals(150.0, review.result.dishes.single().grams, 0.001)
        assertEquals(1, review.memorySuggestions.size)
    }
}
