package com.click.lightmemo.recognition

import com.click.lightmemo.domain.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ImageRecognitionPipelineTest {
    private val component = FoodComponent("c", "米饭", "rice cooked", source = ComponentSource.VISIBLE,
        estimatedWeightG = 200.0, weightMinG = 180.0, weightMaxG = 220.0, confidence = 0.95)
    private val initial = MealRecognition(true, "餐食", listOf(
        RecognizedDish("d", "米饭", DishType.SINGLE_FOOD, 0.95, listOf(component))), 0.95, emptyList())

    @Test fun memoryAndOfflinePreparationOverlapTheTwoVisualRequests() = runTest {
        var prefetched = false
        val reviewed = initial.copy(mealName = "复核后的餐食")
        val result = recognizeWithPreparation(
            loadMemories = { delay(300); emptyList() },
            createStrategy = { RecognitionStrategy(it) },
            prefetchOffline = { assertEquals(initial, it); delay(400); prefetched = true },
        ) { fast, onInitial ->
            delay(500)
            assertNull(fast(initial))
            onInitial(initial)
            delay(500)
            assertTrue(prefetched)
            reviewed
        }
        assertEquals(1000L, currentTime)
        assertEquals(reviewed, result.result)
        assertFalse(result.fast)
    }

    @Test fun unfinishedSpeculativeWorkIsCancelledWhenReviewReturns() = runTest {
        var cancelled = false
        val result = recognizeWithPreparation(
            loadMemories = { emptyList() }, createStrategy = { RecognitionStrategy(it) },
            prefetchOffline = { try { awaitCancellation() } finally { cancelled = true } },
        ) { fast, onInitial ->
            assertNull(fast(initial))
            onInitial(initial)
            delay(100)
            initial
        }
        assertEquals(initial, result.result)
        assertEquals(100L, currentTime)
        assertTrue(cancelled)
    }

    @Test fun optionalMemoryAndPrefetchFailuresDoNotReplaceReviewedResult() = runTest {
        val reviewed = initial.copy(mealName = "已复核")
        val result = recognizeWithPreparation(
            loadMemories = { throw IOException("memory unavailable") },
            createStrategy = { assertTrue(it.isEmpty()); RecognitionStrategy(it) },
            prefetchOffline = { throw IOException("local assets unavailable") },
        ) { fast, onInitial ->
            assertNull(fast(initial))
            onInitial(initial)
            delay(1)
            reviewed
        }
        assertEquals(reviewed, result.result)
    }

    @Test fun cancellationStopsReviewAndItsPrefetchChild() = runTest {
        val started = CompletableDeferred<Unit>()
        var prefetchCancelled = false
        var reviewCancelled = false
        val task = async {
            recognizeWithPreparation(
                loadMemories = { emptyList() }, createStrategy = { RecognitionStrategy(it) },
                prefetchOffline = { try { started.complete(Unit); awaitCancellation() } finally { prefetchCancelled = true } },
            ) { fast, onInitial ->
                assertNull(fast(initial))
                onInitial(initial)
                try { started.await(); awaitCancellation() } finally { reviewCancelled = true }
            }
        }
        runCurrent()
        task.cancelAndJoin()
        assertTrue(prefetchCancelled)
        assertTrue(reviewCancelled)
    }

    @Test fun failedFirstRequestCancelsPendingMemoryRead() = runTest {
        val started = CompletableDeferred<Unit>()
        var memoryCancelled = false
        val result = runCatching { recognizeWithPreparation(
            loadMemories = { try { started.complete(Unit); awaitCancellation() } finally { memoryCancelled = true } },
            createStrategy = { RecognitionStrategy(it) }, prefetchOffline = { error("No initial result") },
        ) { _, _ -> started.await(); throw IOException("visual request failed") } }
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(memoryCancelled)
    }

    @Test fun stableMemoryFastPathRemainsEligibleAndDoesNotPrefetch() = runTest {
        val reference = NutritionReference("rice", "米饭", "中国食物成分表", Nutrition(116.0))
        val memory = PersonalFoodMemory("米饭", typicalGrams = 200.0, minGrams = 180.0, maxGrams = 220.0,
            nutrition = Nutrition(232.0), components = listOf(component.copy(nutritionReference = reference)),
            preferredFoodReference = reference, useCount = 10, confidence = 0.95, recentStableCount = 5)
        val result = recognizeWithPreparation(
            loadMemories = { delay(100); listOf(memory) }, createStrategy = { RecognitionStrategy(it) },
            prefetchOffline = { error("Fast path needs no prefetch") },
        ) { fast, _ -> delay(50); requireNotNull(fast(initial)) }
        assertTrue(result.fast)
        assertEquals(100L, currentTime)
        assertEquals(232.0, result.result.nutrition.caloriesKcal, 0.01)
    }
}
