package com.ble1st.connectias.analytics.di

import com.ble1st.connectias.analytics.store.PluginAnalyticsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AnalyticsStoreEntryPoint {
    fun analyticsStore(): PluginAnalyticsStore
}

