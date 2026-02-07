// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.di

import android.content.Context
import com.ble1st.connectias.service.logging.LoggingServiceProxy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Service module dependencies.
 * Provides LoggingServiceProxy for injection in app and plugin modules.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    @Provides
    @Singleton
    fun provideLoggingServiceProxy(
        @ApplicationContext context: Context
    ): LoggingServiceProxy {
        return LoggingServiceProxy(context)
    }
}
