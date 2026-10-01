package com.click.lightmemo.data

import androidx.room.withTransaction
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.NutritionReference
import com.click.lightmemo.domain.PersonalFoodMemory
import com.click.lightmemo.domain.normalizeFoodName
import com.click.lightmemo.domain.PersonalFoodMatcher
import com.click.lightmemo.domain.PersonalFoodMatch
import com.click.lightmemo.domain.foodWasCorrected
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Call only after a successful user-confirmed save, never during recognition or import. */
class PersonalFoodMemoryRepository(private val database: FoodDatabase) {
    private val dao = database.personalFoodMemoryDao()
    private val json = Json { ignoreUnknownKeys = true }
    val memories = dao.observeAll().map { rows -> rows.map { it.id to decode(it) } }

    suspend fun findExact(name: String): PersonalFoodMemory? =
        dao.find(normalizeFoodName(name))?.let(::decode)

    suspend fun readAll(): List<PersonalFoodMemory> = dao.getAll().mapNotNull { row ->
        runCatching { decode(row) }.getOrNull()
    }

    suspend fun match(name: String): PersonalFoodMatch? = PersonalFoodMatcher.match(name, readAll())

    /** A committed log must remain successful even when optional learning is unavailable. */
    suspend fun learnAfterSave(saved: FoodLog, original: FoodLog? = null): Boolean = withContext(NonCancellable) {
        try {
            val changedReferences = saved.components.filter { component ->
                component.nutritionReference != null && (original == null || original.components.none {
                    normalizeFoodName(it.name) == normalizeFoodName(component.name) && it.nutritionReference == component.nutritionReference
                })
            }.associate { normalizeFoodName(it.name) to it.nutritionReference!! }
            recordConfirmed(
                saved,
                corrected = original == null || foodWasCorrected(saved, original),
                originalName = original?.name,
                preferredReferences = changedReferences,
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun recordConfirmed(
        log: FoodLog,
        corrected: Boolean,
        originalName: String? = null,
        preferredReference: NutritionReference? = null,
        preferredReferences: Map<String, NutritionReference> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        log.validate()
        require(preferredReference?.per100g?.isValid() != false)
        require(preferredReferences.values.all { it.per100g.isValid() })
        val normalized = normalizeFoodName(log.name)
        require(normalized.isNotEmpty())
        database.withTransaction {
            // An alias of an existing corrected dish should update that same memory.
            val exact = dao.find(normalized)
            val renamed = if (corrected && !originalName.isNullOrBlank() && normalizeFoodName(originalName) != normalized) {
                dao.find(normalizeFoodName(originalName))
            } else null
            val aliasRows = if (exact == null) dao.getAll().filter { row ->
                runCatching { decode(row).aliases.any { normalizeFoodName(it) == normalized } }.getOrDefault(false)
            } else emptyList()
            val row = exact ?: renamed ?: aliasRows.singleOrNull()
            val targetKey = if (renamed != null) normalized else row?.normalizedName ?: normalized
            val previous = row?.let(::decode)
            // Corrections carry four times the smoothing weight of direct confirmations.
            val alpha = if (corrected) 0.4 else 0.1
            val typical = previous?.let { it.typicalGrams * (1 - alpha) + log.grams * alpha } ?: log.grams
            val count = (previous?.useCount ?: 0) + 1
            val composition = com.click.lightmemo.domain.compositionKey(log.components)
            val stable = previous != null && composition.isNotEmpty() && composition == previous.lastCompositionKey &&
                kotlin.math.abs(log.grams - previous.lastConfirmedGrams) <= previous.lastConfirmedGrams * 0.15
            val memory = PersonalFoodMemory(
                canonicalName = if (renamed == null && row != null && row.normalizedName != normalized) previous!!.canonicalName else log.name.trim(),
                aliases = (previous?.aliases.orEmpty() + listOfNotNull(originalName?.trim(), log.name.trim(), previous?.canonicalName))
                    .filter { it.isNotBlank() && normalizeFoodName(it) != targetKey }.toSet(),
                typicalGrams = typical,
                minGrams = minOf(previous?.minGrams ?: log.grams, log.grams),
                maxGrams = maxOf(previous?.maxGrams ?: log.grams, log.grams),
                // Preserve explicit corrections against later unedited AI observations.
                nutrition = if (!corrected && (previous?.correctionCount ?: 0) > 0) previous!!.nutrition else log.nutrition,
                nutritionGrams = if (!corrected && (previous?.correctionCount ?: 0) > 0) previous!!.nutritionGrams else log.grams,
                components = if (!corrected && (previous?.correctionCount ?: 0) > 0) previous!!.components else log.components,
                preferredFoodReference = preferredReference ?: previous?.preferredFoodReference,
                preferredComponentReferences = previous?.preferredComponentReferences.orEmpty() + preferredReferences,
                correctionCount = (previous?.correctionCount ?: 0) + if (corrected) 1 else 0,
                useCount = count,
                lastUsedAtMillis = nowMillis,
                lastCorrectedAtMillis = if (corrected) nowMillis else previous?.lastCorrectedAtMillis ?: 0,
                // Confidence grows slowly; path selection must also inspect stability later.
                confidence = ((previous?.confidence ?: 0.0) + if (corrected) 0.15 else 0.05).coerceAtMost(0.98),
                recentStableCount = if (stable) previous!!.recentStableCount + 1 else 1,
                lastConfirmedGrams = log.grams,
                lastCompositionKey = composition,
            )
            if (renamed != null && renamed.id != row?.id) dao.delete(renamed.id)
            dao.upsert(PersonalFoodMemoryEntity(row?.id ?: 0, targetKey, json.encodeToString(memory)))
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.clear()
    private fun decode(row: PersonalFoodMemoryEntity) = json.decodeFromString<PersonalFoodMemory>(row.memoryJson)
}
