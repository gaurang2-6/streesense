package com.stresssense.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Service-level dependency injection module.
 * Placeholder module in case any service-specific bindings or helpers are needed in the future.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    // Currently, all services use singleton-scoped repository injections directly.
    // If specific notification configurations or sensor listeners need custom scopes,
    // they can be defined here.
}
