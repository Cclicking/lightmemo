package com.click.lightmemo.recognition

import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.NutritionReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ManualNutritionResolverTest {
    private val rice = NutritionReference("rice", "米饭（蒸）", "中国食物成分表", Nutrition(116.0))

    @Test fun localHitSkipsNormalizationAndOnlineLookup() = runTest {
        val resolver = ManualNutritionResolver(
            lookupOffline = { assertEquals("米饭", it); rice },
            normalize = { error("Local hit must skip model") },
            lookupNormalized = { _, _ -> error("Local hit must skip network") },
            estimateGrams = { _, _, _ -> 300.0 },
        )
        assertEquals(rice, resolver.resolve("米饭"))
        assertEquals(ManualNutritionResolver.PortionResult(rice, 300.0), resolver.resolvePortions("米饭", 2.0, ""))
    }

    @Test fun portionEstimateAndNormalizationRunConcurrentlyAndPreserveOriginalName() = runTest {
        val normalizeStarted = CompletableDeferred<Unit>()
        val estimateStarted = CompletableDeferred<Unit>()
        val resolver = ManualNutritionResolver(
            lookupOffline = { null },
            normalize = { normalizeStarted.complete(Unit); estimateStarted.await(); "rice cooked" },
            lookupNormalized = { query, name ->
                assertEquals("rice cooked", query)
                assertEquals("熟米饭", name)
                rice
            },
            estimateGrams = { name, portions, hint ->
                assertEquals("熟米饭", name)
                assertEquals(2.0, portions, 0.0)
                assertEquals("小碗", hint)
                estimateStarted.complete(Unit)
                normalizeStarted.await()
                300.0
            },
        )
        assertEquals(ManualNutritionResolver.PortionResult(rice, 300.0), resolver.resolvePortions("熟米饭", 2.0, "小碗"))
    }

    @Test fun cancellationStopsBothRequests() = runTest {
        val normalizeCancelled = CompletableDeferred<Unit>()
        val estimateCancelled = CompletableDeferred<Unit>()
        val resolver = ManualNutritionResolver(
            lookupOffline = { null },
            normalize = { try { awaitCancellation() } finally { normalizeCancelled.complete(Unit) } },
            lookupNormalized = { _, _ -> error("Not reached") },
            estimateGrams = { _, _, _ -> try { awaitCancellation() } finally { estimateCancelled.complete(Unit) } },
        )
        val task = async { resolver.resolvePortions("未知食物", 1.0, "") }
        runCurrent()
        task.cancelAndJoin()
        assertTrue(normalizeCancelled.isCompleted)
        assertTrue(estimateCancelled.isCompleted)
    }
}
