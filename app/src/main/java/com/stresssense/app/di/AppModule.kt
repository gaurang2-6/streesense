package com.stresssense.app.di

import android.content.Context
import androidx.room.Room
import com.stresssense.app.data.local.PassiveMetricDao
import com.stresssense.app.data.local.StressScanDao
import com.stresssense.app.data.local.StressSenseDatabase
import com.stresssense.app.data.local.UserBaselineDao
import com.stresssense.app.data.repository.StressRepository
import com.stresssense.app.data.repository.StressRepositoryImpl
import com.stresssense.app.domain.behavioral.BehavioralFeatureCollector
import com.stresssense.app.domain.behavioral.CommunicationHelper
import com.stresssense.app.domain.behavioral.StressInferenceEngine
import com.stresssense.app.domain.behavioral.UsageStatsHelper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Main Hilt dependency injection module.
 * Provides database and DAO instances, and binds repository implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindStressRepository(
        impl: StressRepositoryImpl
    ): StressRepository

    companion object {

        @Provides
        @Singleton
        fun provideDatabase(
            @ApplicationContext context: Context
        ): StressSenseDatabase {
            return Room.databaseBuilder(
                context,
                StressSenseDatabase::class.java,
                "stress_sense_database"
            )
            .fallbackToDestructiveMigration()
            .build()
        }

        @Provides
        fun provideStressScanDao(db: StressSenseDatabase): StressScanDao {
            return db.stressScanDao()
        }

        @Provides
        fun providePassiveMetricDao(db: StressSenseDatabase): PassiveMetricDao {
            return db.passiveMetricDao()
        }

        @Provides
        fun provideUserBaselineDao(db: StressSenseDatabase): UserBaselineDao {
            return db.userBaselineDao()
        }

        // ─── Behavioral detection (ported from StressDetect) ───────────

        /** Singleton so the service accumulates sensor/typing state across ticks. */
        @Provides
        @Singleton
        fun provideBehavioralFeatureCollector(): BehavioralFeatureCollector =
            BehavioralFeatureCollector()

        @Provides
        fun provideUsageStatsHelper(
            @ApplicationContext context: Context
        ): UsageStatsHelper = UsageStatsHelper(context)

        @Provides
        fun provideCommunicationHelper(
            @ApplicationContext context: Context
        ): CommunicationHelper = CommunicationHelper(context)

        @Provides
        @Singleton
        fun provideStressInferenceEngine(
            @ApplicationContext context: Context
        ): StressInferenceEngine = StressInferenceEngine(context)
    }
}
