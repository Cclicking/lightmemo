package com.click.lightmemo.data

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
    @Test fun emptyApiPresetUsesDeepSeekDefaults() = runBlocking {
        val file = folder.newFolder().resolve("empty-settings.preferences_pb").toOkioPath()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope,
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file })
        try {
            val preset = SettingsRepository(store).settings.first().activePreset
            assertEquals(DEFAULT_API_BASE_URL, preset.baseUrl)
            assertEquals(DEFAULT_API_MODEL, preset.model)
            assertEquals("", preset.apiKey)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun existingApiPresetIsNotReplacedByNewDefaults() = runBlocking {
        val file = folder.newFolder().resolve("existing-settings.preferences_pb").toOkioPath()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope,
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file })
        try {
            val repo = SettingsRepository(store)
            val id = repo.settings.first().activePreset.id
            repo.updatePreset(id, "我的服务", "https://example.com/v1", "user-key", "user-model")

            val saved = repo.settings.first().activePreset
            assertEquals("https://example.com/v1", saved.baseUrl)
            assertEquals("user-key", saved.apiKey)
            assertEquals("user-model", saved.model)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun promptOverridesPersistAndRestoreIndependently() = runBlocking {
        val file = folder.newFolder().resolve("prompts.preferences_pb").toOkioPath()
        suspend fun useStore(block: suspend (SettingsRepository) -> Unit) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store = PreferenceDataStoreFactory.create(scope = scope,
                storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file })
            try { block(SettingsRepository(store)) }
            finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
        useStore { repo ->
            assertTrue(repo.settings.first().promptOverrides.isEmpty())
            repo.updatePromptOverride("TEXT", "文字提示词\nJSON")
            repo.updatePromptOverride("IMAGE", "图片提示词")
            repo.updateSystemBackground("清淡饮食")
        }
        useStore { repo ->
            assertEquals("文字提示词\nJSON", repo.settings.first().promptOverrides["TEXT"])
            repo.updatePromptOverride("TEXT", null)
            assertEquals(mapOf("IMAGE" to "图片提示词"), repo.settings.first().promptOverrides)
            assertEquals("清淡饮食", repo.settings.first().systemBackground)
        }
    }
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

    @Test fun modelPresetDraftIsSavedAtomically() = runBlocking {
        val file = folder.newFolder().resolve("preset-draft.preferences_pb").toOkioPath()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope,
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file })
        try {
            val repo = SettingsRepository(store)
            val id = repo.settings.first().activePreset.id
            repo.updatePreset(id, "我的服务", "https://example.com/v1/", "  secret-key  ", "  vision-model  ")

            val saved = repo.settings.first().activePreset
            assertEquals("我的服务", saved.name)
            assertEquals("https://example.com/v1", saved.baseUrl)
            assertEquals("secret-key", saved.apiKey)
            assertEquals("vision-model", saved.model)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun colorThemeAndGeneratedPalettePersistAcrossReopening() = runBlocking {
        val file = folder.newFolder().resolve("colors.preferences_pb").toOkioPath()
        suspend fun useStore(block: suspend (SettingsRepository) -> Unit) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store = PreferenceDataStoreFactory.create(scope = scope,
                storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file })
            try { block(SettingsRepository(store)) }
            finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }

        val seed = 0xFFF28B3CL
        useStore { repo ->
            repo.updateColorTheme(ColorThemePreset.BLUE)
            assertEquals(ColorThemePreset.BLUE, repo.settings.first().colorTheme)
            assertEquals(FoodColorPalette.forPreset(ColorThemePreset.BLUE), repo.settings.first().colorPalette)
            repo.updateCustomColorTheme(seed)
        }
        useStore { repo ->
            val settings = repo.settings.first()
            assertEquals(ColorThemePreset.CUSTOM, settings.colorTheme)
            assertEquals(seed, settings.colorSeed)
            assertEquals(FoodColorPalette.forPreset(ColorThemePreset.CUSTOM, seed), settings.colorPalette)
        }
    }

    @Test fun completeBackupContainsAndRestoresAllUserOwnedSettings() = runBlocking {
        val file = folder.newFolder().resolve("complete-backup.preferences_pb").toOkioPath()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope,
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file })
        try {
            val repo = SettingsRepository(store)
            val preset = DefaultPresetFoods.first().copy(name = "我的米饭", defaultGrams = 180.0)
            repo.updateFoodPreset(preset)
            repo.updateActiveBaseUrl("https://example.com/v1")
            repo.updateActiveApiKey("model-secret")
            repo.updateActiveModel("vision-model")
            repo.updateFoodDataCentralApiKey("database-secret")
            repo.updatePromptOverride("TEXT", "自定义提示")
            repo.updateHeight(172f)
            repo.updateWeight(68f)
            repo.updateAge(31)
            repo.updateDailyTarget(2100f)
            repo.updateProteinTarget(130f)
            repo.updateColorTheme(ColorThemePreset.BLUE)
            repo.updateGlassEffectsEnabled(true)
            repo.updateTopGradientBlurEnabled(true)
            repo.updateTopGradientBlurRangeDp(96)

            val backup = repo.exportBackupJson()
            assertEquals("model-secret", backup.getJSONObject("settings")
                .getJSONArray("apiPresets").getJSONObject(0).getString("apiKey"))
            assertEquals("database-secret", backup.getJSONObject("settings")
                .getString("foodDataCentralApiKey"))
            assertEquals("我的米饭", backup.getJSONArray("foodPresets").getJSONObject(0).getString("name"))
            assertEquals(172.0, backup.getJSONObject("settings").getDouble("heightCm"), 0.001)
            assertEquals("BLUE", backup.getJSONObject("settings").getString("colorTheme"))
            assertTrue(backup.getJSONObject("settings").getBoolean("glassEffectsEnabled"))

            repo.updateDailyTarget(1500f)
            repo.importBackupJson(backup.getJSONObject("settings"), backup.getJSONArray("foodPresets"))
            val restored = repo.settings.first()
            assertEquals(2100f, restored.dailyCalorieTarget)
            assertEquals("vision-model", restored.model)
            assertEquals("model-secret", restored.apiKey)
            assertEquals("database-secret", restored.foodDataCentralApiKey)
            assertEquals("自定义提示", restored.promptOverrides["TEXT"])
            assertEquals(ColorThemePreset.BLUE, restored.colorTheme)
            assertEquals(96, restored.topGradientBlurRangeDp)
            assertEquals(preset, repo.foodPresets.first().first())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
