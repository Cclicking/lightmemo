package com.click.lightmemo.recognition

import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.PersonalFoodMemory
import com.click.lightmemo.domain.RecognitionStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal data class PreparedImageRecognition(
    val result: MealRecognition,
    val strategy: RecognitionStrategy,
    val fast: Boolean,
)

/** Memory loading overlaps the first visual request; local prefetch overlaps visual review.
 * All children belong to this recognition, so cancellation/replacement cannot leave work behind. */
internal suspend fun recognizeWithPreparation(
    loadMemories: suspend () -> List<PersonalFoodMemory>,
    createStrategy: (List<PersonalFoodMemory>) -> RecognitionStrategy,
    prefetchOffline: suspend (MealRecognition) -> Unit,
    recognize: suspend (fastResult: suspend (MealRecognition) -> MealRecognition?, onInitialResult: (MealRecognition) -> Unit) -> MealRecognition,
): PreparedImageRecognition = coroutineScope {
    val strategy = async {
        val memories = try { loadMemories() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { emptyList() }
        createStrategy(memories)
    }
    var prefetch: Job? = null
    var fast = false
    val result = try {
        recognize(
            { initial ->
                try { strategy.await().fastResult(initial)?.also { fast = true } }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
            },
            { initial ->
                prefetch = launch {
                    try { prefetchOffline(initial) }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { /* Optional work; final matching remains authoritative. */ }
                }
            },
        )
    } finally {
        // Do not wait for speculative work on ingredients the reviewer may have removed.
        prefetch?.cancelAndJoin()
    }
    PreparedImageRecognition(result, strategy.await(), fast)
}
