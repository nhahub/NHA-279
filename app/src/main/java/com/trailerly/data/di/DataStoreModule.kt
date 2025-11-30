package com.trailerly.data.di

import android.content.Context
import com.trailerly.data.PreferencesRepository
import com.trailerly.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Singleton
    @Provides
    fun providePreferencesRepository(
        @ApplicationContext context: Context,
        authRepository: AuthRepository
    ): PreferencesRepository {
        return PreferencesRepository(context, authRepository)
    }
}
