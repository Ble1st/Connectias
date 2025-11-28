package com.ble1st.connectias.feature.utilities.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for Utilities feature.
 * All providers and repository are automatically provided by Hilt via @Inject constructors.
 * 
 * Reserved for future Hilt providers/bindings if needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object UtilitiesModule {
    // Utilities feature module - Hilt will automatically discover ViewModels and @Inject constructors
}

