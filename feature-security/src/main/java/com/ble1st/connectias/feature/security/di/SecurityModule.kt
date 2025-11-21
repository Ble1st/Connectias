package com.ble1st.connectias.feature.security.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    // Security feature module - Hilt will automatically discover ViewModels
}

