package com.memorylink.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for calendar-related dependencies.
 *
 * Note: Most calendar classes use @Singleton and constructor injection,
 * so they don't need explicit @Provides methods.
 *
 * This module exists for documentation and any future provider methods.
 *
 * Auto-injected classes:
 * - TokenStorage: EncryptedSharedPreferences for OAuth tokens
 * - GoogleAuthManager: OAuth2 authentication flow
 * - GoogleCalendarService: Calendar API client
 * - CalendarRepository: API + Room cache bridge
 * - CalendarSyncWorker: Background sync (via HiltWorker)
 */
@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {
    // All calendar classes use constructor injection with @Singleton
    // No explicit @Provides methods needed currently
}
