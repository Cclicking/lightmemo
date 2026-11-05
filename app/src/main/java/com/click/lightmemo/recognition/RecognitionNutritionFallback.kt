package com.click.lightmemo.recognition

import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.NutritionReference
import com.click.lightmemo.domain.RecognizedDish
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Keep database matches authoritative and fill unmatched ingredients with editable AI estimates. */
internal suspend fun MealRecognition.withAiNutritionFallback(
    estimate: suspend (FoodComponent) -> Nutrition,
): MealRecognition = coroutineScope {
    val pending = dishes.flatMap { it.allComponents }.distinctBy { it.id }
        .filter { it.nutritionReference == null }
    if (pending.isEmpty()) return@coroutineScope this@withAiNutritionFallback
    val permits = Semaphore(3)
    val references = pending.map { component ->
        async {
            val reference = permits.withPermit {
                try {
                    NutritionReference(
                        sourceId = "ai-estimate-${component.id}",
                        description = component.name,
                        dataType = "AI估算（仅供参考）",
                        per100g = estimate(component),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // An optional estimate must not discard successfully recognized food.
                    null
                }
            }
            component.id to reference
        }
    }.awaitAll().toMap()
    fun RecognizedDish.withEstimates(): RecognizedDish = copy(
        components = components.map { component ->
            references[component.id]?.let { component.copy(nutritionReference = it) } ?: component
        },
        children = children.map { it.withEstimates() },
    )
    copy(dishes = dishes.map { it.withEstimates() })
}
