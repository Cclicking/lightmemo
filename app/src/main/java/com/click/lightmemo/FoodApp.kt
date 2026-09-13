package com.click.lightmemo

import android.app.Application
import com.click.lightmemo.data.FoodLogRepository
import com.click.lightmemo.data.SettingsRepository
import com.click.lightmemo.network.FoodRecognitionClient
import com.click.lightmemo.network.FoodDataCentralClient

class FoodApp : Application() {
    lateinit var foodLogRepository: FoodLogRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    val recognitionClient = FoodRecognitionClient()
    val nutritionDatabase by lazy { FoodDataCentralClient(this) }

    override fun onCreate() {
        super.onCreate()
        foodLogRepository = FoodLogRepository(this)
        settingsRepository = SettingsRepository(this)
    }
}
