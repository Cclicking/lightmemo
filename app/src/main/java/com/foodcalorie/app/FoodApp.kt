package com.foodcalorie.app

import android.app.Application
import com.foodcalorie.app.data.FoodLogRepository
import com.foodcalorie.app.data.SettingsRepository
import com.foodcalorie.app.network.FoodRecognitionClient

class FoodApp : Application() {
    lateinit var foodLogRepository: FoodLogRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    val recognitionClient = FoodRecognitionClient()

    override fun onCreate() {
        super.onCreate()
        foodLogRepository = FoodLogRepository(this)
        settingsRepository = SettingsRepository(this)
    }
}
