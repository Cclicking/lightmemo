package com.click.lightmemo.network

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChinaMatchingParityTest {
    /** Frozen before feature precomputation; includes all recalled IDs, not just the first page. */
    @Test fun preparedMatchingKeepsRealDatabaseCandidatesRankingAndAutomaticAcceptance() = runBlocking {
        val assets = File(requireNotNull(System.getProperty("food.assets")))
        val database = FoodDataCentralClient(
            openAsset = { File(assets, it).inputStream() }, listAssets = { assets.list().orEmpty() },
            databases = { com.click.lightmemo.data.NutritionDatabases(online = false) },
        )
        val rows = requireNotNull(javaClass.getResourceAsStream("/china-matching-baseline.tsv"))
            .bufferedReader().use { it.readLines() }
        val started = System.nanoTime()
        for (row in rows) {
            val (query, accepted, candidates) = row.split('\t')
            assertEquals(query, candidates.split(',').filter { it.isNotEmpty() },
                database.browseOffline(query, FoodDataCentralClient.DatabaseSource.CHINA, 2000).map { it.sourceId })
            assertEquals(query, accepted, database.lookup(query, "")?.let { "${it.dataType}:${it.sourceId}" }.orEmpty())
        }
        println("China matching parity: ${(System.nanoTime() - started) / 1_000_000}ms for ${rows.size} queries (includes cold loading)")
    }
}
