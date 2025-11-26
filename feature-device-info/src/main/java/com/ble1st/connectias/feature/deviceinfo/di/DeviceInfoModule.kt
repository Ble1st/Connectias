package com.ble1st.connectias.feature.deviceinfo.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DeviceInfoModule {
    // Device Info feature module - Hilt will automatically discover ViewModels and Providers
}

