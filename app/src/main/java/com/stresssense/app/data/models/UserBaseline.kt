package com.stresssense.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the user's baseline HRV/HR readings for personalized scoring.
 * Only one baseline exists at a time (PrimaryKey = 1).
 */
@Entity(tableName = "user_baseline")
data class UserBaseline(
    @PrimaryKey
    val id: Int = 1,
    val baselineRMSSD: Double,
    val baselineHR: Double,
    val calibrationDate: Long,
    val sampleCount: Int
)
