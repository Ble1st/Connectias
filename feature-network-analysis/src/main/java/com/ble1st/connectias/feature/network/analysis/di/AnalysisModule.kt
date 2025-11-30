package com.ble1st.connectias.feature.network.analysis.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for Network Analysis feature.
 * All providers are automatically provided by Hilt via @Inject constructors.
 * 
 * Reserved for future Hilt providers/bindings if needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalysisModule
