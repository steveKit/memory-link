package com.memorylink.di

import com.memorylink.domain.SystemTimeProvider
import com.memorylink.domain.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for domain layer bindings.
 *
 * Provides interface-to-implementation bindings for:
 * - TimeProvider (testable time abstraction)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    /**
     * Binds [SystemTimeProvider] as the production implementation of [TimeProvider].
     *
     * This allows tests to mock TimeProvider for deterministic time-based testing.
     */
    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
