package com.ble1st.connectias.feature.privacy.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for Privacy feature.
 * All providers and repository are automatically provided by Hilt via @Inject constructors.
 */
@Module
@InstallIn(SingletonComponent::class)
object PrivacyModule {
    // Privacy feature module - Hilt will automatically discover ViewModels and @Inject constructors
}

