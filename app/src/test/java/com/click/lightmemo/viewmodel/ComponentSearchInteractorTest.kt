package com.click.lightmemo.viewmodel

import com.click.lightmemo.domain.ComponentSource
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.network.FoodDataCentralClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ComponentSearchInteractorTest {
    private fun database() = FoodDataCentralClient(
        openAsset = { name -> when (name) {
            "fdc_sr_legacy_macros.tsv" -> "id\tdescription\tkcal\tprotein\tcarbs\tfat\n".byteInputStream()
            "china_food_composition.tsv" -> ("id\tname\tenglish\tkcal\tprotein\tcarbs\tfat\n" +
                "tofu\t北豆腐\t\t98\t12\t2\t5\n" +
                "rice\t米饭（蒸）\t\t116\t2.6\t26\t0.3\n").byteInputStream()
            else -> error("Unexpected asset: $name")
        } },
        listAssets = { emptyArray() },
    )

    private fun component(name: String = "白块") = FoodComponent(
        id = "component", name = name, databaseQuery = "unrecognized food", chinaDatabaseQuery = "北豆腐",
        source = ComponentSource.VISIBLE, estimatedWeightG = 100.0,
        weightMinG = 100.0, weightMaxG = 100.0, confidence = 1.0,
    )

    @Test fun unresolvedReviewUsesBothLanguagesAndEditedTextDropsRecognitionQueries() = runTest {
        val state = MutableStateFlow(AddFoodUiState())
        val search = ComponentSearchInteractor(database(), state, this) { "" }
        search.openReview(component())
        val initial = state.first { it.databaseSearch?.loading == false }.databaseSearch!!
        assertTrue(initial.results.any { it.sourceId == "tofu" })

        search.searchReview("component", "米饭")
        val edited = state.first { it.databaseSearch?.loading == false }.databaseSearch!!
        assertTrue(edited.results.any { it.sourceId == "rice" })
        assertFalse(edited.results.any { it.sourceId == "tofu" })
    }

    @Test fun replacementSearchUsesVisibleNameWithoutOldRecognitionTerms() = runTest {
        val state = MutableStateFlow(AddFoodUiState())
        val search = ComponentSearchInteractor(database(), state, this) { "" }
        search.openReview(component("米饭"), replaceComponentName = true)
        val result = state.first { it.databaseSearch?.loading == false }.databaseSearch!!
        assertTrue(result.results.any { it.sourceId == "rice" })
        assertFalse(result.results.any { it.sourceId == "tofu" })
    }
}
