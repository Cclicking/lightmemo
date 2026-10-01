package com.click.lightmemo.domain

enum class MemoryMatchKind { EXACT, ALIAS, SIMILAR }

data class PersonalFoodMatch(
    val memory: PersonalFoodMemory,
    val kind: MemoryMatchKind,
    val similarity: Double,
) {
    // Similar strings never authorize automatic database replacement.
    val canApply: Boolean get() = kind != MemoryMatchKind.SIMILAR &&
        (memory.correctionCount > 0 || memory.useCount >= 3)
}

object PersonalFoodMatcher {
    fun match(name: String, memories: List<PersonalFoodMemory>): PersonalFoodMatch? {
        val key = normalizeFoodName(name)
        if (key.isEmpty()) return null
        memories.firstOrNull { normalizeFoodName(it.canonicalName) == key }?.let {
            return PersonalFoodMatch(it, MemoryMatchKind.EXACT, 1.0)
        }
        val aliases = memories.filter { memory -> memory.aliases.any { normalizeFoodName(it) == key } }
        // Conflicting aliases are ambiguous; do not guess.
        if (aliases.size == 1) return PersonalFoodMatch(aliases.single(), MemoryMatchKind.ALIAS, 1.0)
        if (aliases.isNotEmpty() || key.length < 6) return null
        return memories.mapNotNull { memory ->
            val candidate = normalizeFoodName(memory.canonicalName)
            // Require all semantic food characters to be retained, so 炒蛋 cannot become 炒肉.
            if (candidate.length < 6 || !(key.contains(candidate) || candidate.contains(key))) return@mapNotNull null
            val similarity = minOf(key.length, candidate.length).toDouble() / maxOf(key.length, candidate.length)
            if (similarity < 0.75) null else PersonalFoodMatch(memory, MemoryMatchKind.SIMILAR, similarity)
        }.maxByOrNull { it.similarity }
    }
}

/** Compare edible content, ignoring ids, meal time, notes and presentation metadata. */
fun foodWasCorrected(saved: FoodLog, original: FoodLog): Boolean =
    normalizeFoodName(saved.name) != normalizeFoodName(original.name) ||
        kotlin.math.abs(saved.grams - original.grams) > 0.1 ||
        saved.nutrition != original.nutrition ||
        saved.components.map { it.copy(id = "") } != original.components.map { it.copy(id = "") }

data class PersonalFoodSuggestion(
    val dishId: String,
    val match: PersonalFoodMatch,
    val referenceApplied: Boolean = false,
    val accepted: Boolean = false,
)

/** Reuse composition only on explicit acceptance; normalize weights to the learned portion. */
fun RecognizedDish.withMemoryPortion(memory: PersonalFoodMemory): RecognizedDish {
    val components = memory.components.ifEmpty {
        if (!memory.nutritionGrams.isFinite() || memory.nutritionGrams <= 0) return this
        listOf(FoodComponent(
            id = "memory", name = name, databaseQuery = name,
            source = ComponentSource.USER_PROVIDED,
            estimatedWeightG = memory.nutritionGrams, weightMinG = memory.nutritionGrams, weightMaxG = memory.nutritionGrams,
            confidence = memory.confidence, needsConfirmation = true,
            nutritionReference = memory.preferredFoodReference ?: NutritionReference("personal-memory", name, "个人记录", memory.nutrition * (100 / memory.nutritionGrams)),
        ))
    }
    val total = components.sumOf { it.estimatedWeightG }
    if (components.isEmpty() || !total.isFinite() || total <= 0 || !memory.typicalGrams.isFinite() || memory.typicalGrams <= 0) return this
    val scale = memory.typicalGrams / total
    return copy(
        components = components.map { it.copy(
            id = java.util.UUID.randomUUID().toString(),
            estimatedWeightG = it.estimatedWeightG * scale,
            weightMinG = it.weightMinG * scale,
            weightMaxG = it.weightMaxG * scale,
        ) },
        children = emptyList(),
        needsConfirmation = true,
    )
}

fun RecognizedDish.withPreferredReferences(memory: PersonalFoodMemory): RecognizedDish = copy(
    components = components.map { component ->
        val reference = memory.preferredComponentReferences[normalizeFoodName(component.name)]
            ?: memory.preferredFoodReference?.takeIf { allComponents.size == 1 }
        if (reference == null) component else component.copy(nutritionReference = reference)
    },
    children = children.map { it.withPreferredReferences(memory) },
)

/** Undo only references automatically filled by memory, preserving subsequent user edits. */
fun RecognizedDish.withoutAutomaticMemoryReferences(rawDish: RecognizedDish, memory: PersonalFoodMemory): RecognizedDish = copy(
    components = components.map { component ->
        val raw = rawDish.allComponents.find { it.id == component.id }
        val automatic = memory.preferredComponentReferences[normalizeFoodName(component.name)]
            ?: memory.preferredFoodReference
        if (raw != null && automatic != null && component.nutritionReference == automatic) {
            component.copy(nutritionReference = raw.nutritionReference)
        } else component
    },
    children = children.map { it.withoutAutomaticMemoryReferences(rawDish, memory) },
)
