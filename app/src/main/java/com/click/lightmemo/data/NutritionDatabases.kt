package com.click.lightmemo.data

import kotlinx.serialization.Serializable

enum class NutritionDatabase { SR_LEGACY, FOUNDATION, FNDDS, CHINA, ONLINE }

@Serializable
data class NutritionDatabases(
    val srLegacy: Boolean = true,
    val foundation: Boolean = true,
    val fndds: Boolean = true,
    val china: Boolean = true,
    val online: Boolean = true,
) {
    fun enabled(database: NutritionDatabase): Boolean = when (database) {
        NutritionDatabase.SR_LEGACY -> srLegacy
        NutritionDatabase.FOUNDATION -> foundation
        NutritionDatabase.FNDDS -> fndds
        NutritionDatabase.CHINA -> china
        NutritionDatabase.ONLINE -> online
    }

    fun withEnabled(database: NutritionDatabase, enabled: Boolean): NutritionDatabases = when (database) {
        NutritionDatabase.SR_LEGACY -> copy(srLegacy = enabled)
        NutritionDatabase.FOUNDATION -> copy(foundation = enabled)
        NutritionDatabase.FNDDS -> copy(fndds = enabled)
        NutritionDatabase.CHINA -> copy(china = enabled)
        NutritionDatabase.ONLINE -> copy(online = enabled)
    }

    fun allows(dataType: String): Boolean = when {
        dataType.startsWith("中国食物成分表") -> china
        dataType.contains("offline") && dataType.contains("Foundation") -> foundation
        dataType.contains("offline") && dataType.contains("SR Legacy") -> srLegacy
        dataType.contains("offline") && dataType.contains("FNDDS") -> fndds
        else -> online
    }
}
