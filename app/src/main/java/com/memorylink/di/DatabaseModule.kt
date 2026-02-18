package com.memorylink.di

import android.content.Context
import androidx.room.Room
import com.memorylink.data.local.AppDatabase
import com.memorylink.data.local.EventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Room database and DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "memorylink_db"

    /**
     * Provides the Room database instance with proper migrations.
     *
     * Migrations:
     * - v1 → v2: Add is_holiday column for holiday calendar support
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    /**
     * Provides EventDao for cached calendar events.
     */
    @Provides
    @Singleton
    fun provideEventDao(database: AppDatabase): EventDao {
        return database.eventDao()
    }
}
