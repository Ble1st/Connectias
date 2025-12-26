package com.ble1st.connectias.feature.dvd.di

import android.content.Context
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Hilt module for DVD feature dependencies.
 * 
 * Most classes use @Inject constructors:
 * - DvdPlayer
 * - DvdVideoProvider
 * - DvdNavigation
 * - OpticalDriveProvider
 * - AudioCdPlayer
 */
@Module
@InstallIn(SingletonComponent::class)
object DvdModule {
    // Providers can be added here if needed.
}
