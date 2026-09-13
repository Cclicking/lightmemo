package com.foodcalorie.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun presetsSurviveReopeningAndCanBeReset() = runBlocking {
        val file = folder.newFolder().resolve("settings.preferences_pb").toOkioPath()
        suspend fun useStore(block: suspend (SettingsRepository) -> Unit) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store = PreferenceDataStoreFactory.create(scope = scope,
                storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file })
            try { block(SettingsRepository(store)) }
            finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
        val edited = DefaultPresetFoods.first().copy(name = "我的米饭", defaultGrams = 180.0)
        useStore { repo -> repo.updateFoodPreset(edited) }
        useStore { repo ->
            assertEquals(edited, repo.foodPresets.first().first())
            repo.resetFoodPresets()
            assertEquals(DefaultPresetFoods, repo.foodPresets.first())
        }
    }

    @Test fun corruptFoodPresetsFallBackToDefaultsAndCanBeSavedAgain() = runBlocking {
        val file = folder.newFolder().resolve("settings.preferences_pb").toOkioPath()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope,
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file })
        try {
            val repo = SettingsRepository(store)
            store.edit { it[stringPreferencesKey("food_presets")] = "{not-json" }
            assertEquals(DefaultPresetFoods, repo.foodPresets.first())
            val edited = DefaultPresetFoods.first().copy(name = "修复后的米饭")
            repo.updateFoodPreset(edited)
            assertEquals(edited, repo.foodPresets.first().first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun defaultApiPresetIdentityRemainsStableAcrossSettingsChanges() = runBlocking {
        val file = folder.newFolder().resolve("settings.preferences_pb").toOkioPath()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope,
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file })
        try {
            val repo = SettingsRepository(store)
            val initial = repo.settings.first().activePreset
            repo.updateDailyTarget(2000f)
            assertEquals(initial.id, repo.settings.first().activePreset.id)
            repo.renamePreset(initial.id, "我的接口")
            assertEquals("我的接口", repo.settings.first().activePreset.name)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
