@file:Suppress("unused")

package com.ble1st.connectias.feature.settings.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.FragmentComponent

/**
 * Hilt module for Settings feature.
 * SettingsRepository is provided by core module, so this module is mainly for
 * feature-specific bindings if needed in the future.
 */
@Module
@InstallIn(FragmentComponent::class)
object SettingsFeatureModule {
    // SettingsRepository is provided via @Inject constructor in core module
    // ViewModel is provided via @HiltViewModel annotation
    // No additional bindings needed at this time
}

