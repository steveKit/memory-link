package com.memorylink.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Qualifier for IO dispatcher (file/network operations). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

/** Qualifier for Default dispatcher (CPU-intensive work). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

/** Qualifier for application-scoped coroutine scope. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

/** Main Hilt module providing application-scoped dependencies. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val PREFS_FILE_NAME = "memorylink_secure_prefs"

    /**
     * Provides EncryptedSharedPreferences for secure storage. Used for PIN, OAuth tokens, and
     * sensitive settings.
     */
    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        val masterKey =
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

        return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Provides IO dispatcher for file and network operations. */
    @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /** Provides Default dispatcher for CPU-intensive work. */
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * Provides application-scoped CoroutineScope. Uses SupervisorJob so child coroutine failures
     * don't cancel siblings. Lives for the entire application lifecycle.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): kotlinx.coroutines.CoroutineScope =
            kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() + Dispatchers.Default
            )
}
