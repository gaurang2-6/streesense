package com.stresssense.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores passive monitoring metrics captured in the background.
 * No personal content is stored — only aggregate metrics.
 */
@Entity(tableName = "passive_metrics")
data class PassiveMetric(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val typingScore: Int? = null,
    val usageScore: Int? = null,
    val motionScore: Int? = null,
    /** Composite behavioral stress score from the on-device TFLite model. */
    val mlStressScore: Int? = null
)
