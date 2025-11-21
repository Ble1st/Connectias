package com.ble1st.connectias.feature.settings.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    // Settings feature module - Hilt will automatically discover ViewModels
}

