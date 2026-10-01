package com.click.lightmemo.recognition

import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.RecognitionPath
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CachedRecognition(
    val fingerprint: String,
    val contextKey: String,
    val result: MealRecognition,
    val createdAtMillis: Long,
    val model: String,
    val appVersion: String,
    val path: RecognitionPath = RecognitionPath.FULL,
)

/** Metadata and result only: no image bytes or image URI. Exact hashes avoid false photo matches. */
class RecognitionImageCache(private val file: File, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun find(fingerprint: String, contextKey: String): CachedRecognition? = read().firstOrNull {
        it.fingerprint == fingerprint && it.contextKey == contextKey
    }

    @Synchronized
    fun put(entry: CachedRecognition) {
        val entries = (listOf(entry) + read().filterNot {
            it.fingerprint == entry.fingerprint && it.contextKey == entry.contextKey
        }).take(MAX_ENTRIES)
        write(entries)
    }

    private fun write(entries: List<CachedRecognition>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(json.encodeToString(entries))
        java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun read(): List<CachedRecognition> {
        val entries = runCatching {
            if (file.exists()) json.decodeFromString<List<CachedRecognition>>(file.readText()) else emptyList()
        }.getOrDefault(emptyList())
        val valid = entries.filter { now() - it.createdAtMillis in 0 until TTL_MILLIS }.take(MAX_ENTRIES)
        if (valid != entries) runCatching { write(valid) }
        return valid
    }

    companion object {
        const val TTL_MILLIS = 48 * 60 * 60 * 1000L
        const val MAX_ENTRIES = 24
        fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** A repeated record must get fresh ids so component editing cannot affect another result. */
fun MealRecognition.withFreshIds(): MealRecognition {
    fun fresh(dish: com.click.lightmemo.domain.RecognizedDish): com.click.lightmemo.domain.RecognizedDish = dish.copy(
        id = UUID.randomUUID().toString(),
        components = dish.components.map { it.copy(id = UUID.randomUUID().toString()) },
        children = dish.children.map { fresh(it) },
    )
    return copy(dishes = dishes.map { fresh(it) })
}
