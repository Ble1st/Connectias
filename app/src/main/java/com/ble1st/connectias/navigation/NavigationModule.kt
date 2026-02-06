// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.navigation

import com.ble1st.connectias.plugin.navigation.PluginNavigator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides navigation implementations for the plugin system.
 *
 * This module binds the PluginNavigator interface (defined in :plugin module)
 * to the app-specific implementation PluginNavigatorImpl.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {

    @Binds
    @Singleton
    abstract fun bindPluginNavigator(impl: PluginNavigatorImpl): PluginNavigator
}
