package com.click.lightmemo.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class RecognitionPath { FULL, ASSISTED, FAST }

/** Stable composition means the same foods, proportions and database references, not just a name. */
fun compositionKey(components: List<FoodComponent>): String {
    val total = components.sumOf { it.estimatedWeightG }
    if (!total.isFinite() || total <= 0) return ""
    return components.map {
        "${normalizeFoodName(it.name)}:${it.nutritionReference?.sourceId.orEmpty()}:${it.nutritionReference?.per100g}:${kotlin.math.round(it.estimatedWeightG / total * 20).toInt()}"
    }.sorted().joinToString("|")
}

object RecognitionPathSelector {
    fun isStable(memory: PersonalFoodMemory): Boolean =
        memory.confidence >= 0.85 && memory.useCount >= 5 && memory.recentStableCount >= 3 &&
            (memory.correctionCount.toDouble() / memory.useCount <= 0.2 || memory.recentStableCount >= 5) &&
            memory.typicalGrams.isFinite() && memory.typicalGrams > 0 &&
            memory.components.isNotEmpty() && memory.components.all { component ->
                val reference = memory.preferredComponentReferences[normalizeFoodName(component.name)]
                    ?: memory.preferredFoodReference?.takeIf { memory.components.size == 1 }
                reference != null && reference.sourceId.isNotBlank() &&
                    !reference.sourceId.startsWith("ai-estimate", ignoreCase = true) && reference.sourceId != "personal-memory" &&
                    listOf(reference.per100g.caloriesKcal, reference.per100g.proteinG, reference.per100g.carbsG, reference.per100g.fatG).all { it.isFinite() && it >= 0 } &&
                    reference.per100g.caloriesKcal > 0 && component.estimatedWeightG.isFinite() && component.estimatedWeightG > 0
            }

    /** Never skip the visual call. Ambiguous photos, changed portions or composition need full review. */
    fun fastResult(visual: MealRecognition, memories: List<PersonalFoodMemory>): MealRecognition? {
        if (!visual.isFoodImage || visual.dishes.size != 1 || !visual.overallConfidence.isFinite() || visual.overallConfidence < 0.9 ||
            visual.imageQualityIssues.isNotEmpty() || visual.confirmationQuestions.isNotEmpty()) return null
        val dish = visual.dishes.single()
        if (dish.children.isNotEmpty() || !dish.confidence.isFinite() || dish.confidence < 0.9 || dish.needsConfirmation ||
            !dish.uncertaintyReason.isNullOrBlank() || dish.allComponents.any {
                !it.confidence.isFinite() || it.confidence < 0.85 || it.needsConfirmation ||
                    !it.estimatedWeightG.isFinite() || it.estimatedWeightG <= 0
            }) return null
        val match = PersonalFoodMatcher.match(dish.name, memories) ?: return null
        if (match.kind == MemoryMatchKind.SIMILAR || !isStable(match.memory)) return null
        val memory = match.memory
        if (!dish.grams.isFinite() || abs(dish.grams - memory.typicalGrams) > memory.typicalGrams * 0.2 ||
            dish.allComponents.map { normalizeFoodName(it.name) }.sorted() != memory.components.map { normalizeFoodName(it.name) }.sorted()) return null
        val memoryTotal = memory.components.sumOf { it.estimatedWeightG }
        val visualRatios = dish.allComponents.groupBy { normalizeFoodName(it.name) }
            .mapValues { (_, components) -> components.sumOf { it.estimatedWeightG } / dish.grams }
        val memoryRatios = memory.components.groupBy { normalizeFoodName(it.name) }
            .mapValues { (_, components) -> components.sumOf { it.estimatedWeightG } / memoryTotal }
        if (visualRatios.any { (name, ratio) -> abs(ratio - (memoryRatios[name] ?: 0.0)) > 0.1 }) return null
        val reused = dish.withMemoryPortion(memory).withPreferredReferences(memory)
        if (reused.allComponents.any { it.nutritionReference == null }) return null
        return visual.copy(dishes = listOf(reused), confirmationQuestions = listOf("参考了你之前确认的记录，请核对食物组成和份量。"))
    }

    fun assist(result: MealRecognition, memories: List<PersonalFoodMemory>): MealRecognition = result.copy(
        dishes = result.dishes.map { dish ->
            val match = PersonalFoodMatcher.match(dish.name, memories)
            if (match?.canApply == true) dish.withPreferredReferences(match.memory) else dish
        },
    )
}

/** One policy for entry-point restrictions, fast eligibility and assisted database reuse. */
class RecognitionStrategy(
    private val memories: List<PersonalFoodMemory>,
    customPrompts: Boolean = false,
    explicitDescription: Boolean = false,
    multipleServings: Boolean = false,
) {
    private val allowFast = !customPrompts && !explicitDescription && !multipleServings
    fun fastResult(visual: MealRecognition): MealRecognition? =
        if (allowFast) RecognitionPathSelector.fastResult(visual, memories) else null
    fun assist(visual: MealRecognition): MealRecognition = RecognitionPathSelector.assist(visual, memories)
}
