package com.stresssense.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single stress scan result.
 * Stores both the composite score and individual component scores.
 */
@Entity(tableName = "stress_scans")
data class StressScan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val finalScore: Int,
    val hrvScore: Int? = null,
    val voiceScore: Int? = null,
    val facialScore: Int? = null,
    val heartRate: Int? = null,
    val rmssd: Double? = null,
    val sdnn: Double? = null,
    val pnn50: Double? = null,
    val scanType: String = "ACTIVE"  // "ACTIVE" or "PASSIVE"
)
