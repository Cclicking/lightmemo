package com.click.lightmemo.recognition

import com.click.lightmemo.domain.NutritionReference
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Local names keep all identity constraints; only misses need model normalization. */
internal class ManualNutritionResolver(
    private val lookupOffline: suspend (String) -> NutritionReference?,
    private val normalize: suspend (String) -> String,
    private val lookupNormalized: suspend (query: String, originalName: String) -> NutritionReference?,
    private val estimateGrams: suspend (name: String, portions: Double, hint: String) -> Double,
) {
    suspend fun resolve(name: String): NutritionReference? = lookupOffline(name)
        ?: lookupNormalized(normalize(name), name)

    suspend fun resolvePortions(name: String, portions: Double, hint: String): PortionResult = coroutineScope {
        val reference = async { resolve(name) }
        val grams = async { estimateGrams(name, portions, hint) }
        PortionResult(reference.await(), grams.await())
    }

    data class PortionResult(val reference: NutritionReference?, val grams: Double)
}
