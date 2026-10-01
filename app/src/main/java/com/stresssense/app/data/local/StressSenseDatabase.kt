package com.stresssense.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.stresssense.app.data.models.PassiveMetric
import com.stresssense.app.data.models.StressScan
import com.stresssense.app.data.models.UserBaseline

/**
 * Room database for StressSense.
 * All user data is stored locally on-device — nothing is ever sent to a server.
 */
@Database(
    entities = [
        StressScan::class,
        PassiveMetric::class,
        UserBaseline::class
    ],
    version = 2,
    exportSchema = false
)
abstract class StressSenseDatabase : RoomDatabase() {
    abstract fun stressScanDao(): StressScanDao
    abstract fun passiveMetricDao(): PassiveMetricDao
    abstract fun userBaselineDao(): UserBaselineDao
}
