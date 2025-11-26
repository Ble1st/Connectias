package com.ble1st.connectias.feature.network.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for Network feature.
 * All providers and repository are automatically provided by Hilt via @Inject constructors.
 * 
 * Reserved for future Hilt providers/bindings if needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // Network feature module - Hilt will automatically discover ViewModels and @Inject constructors
}

