package com.ble1st.connectias.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    // Settings module - Hilt will automatically discover SettingsRepository
    // SettingsRepository is provided via @Inject constructor
}

