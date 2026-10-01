package com.click.lightmemo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "personal_food_memories", indices = [Index(value = ["normalizedName"], unique = true)])
data class PersonalFoodMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedName: String,
    /** Versioned Kotlin serialization payload, containing local food data only. */
    val memoryJson: String,
)
