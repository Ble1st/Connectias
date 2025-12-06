package com.ble1st.connectias.feature.deviceinfo.connectivity.di

import android.content.Context
import com.ble1st.connectias.feature.deviceinfo.connectivity.bluetooth.BluetoothAnalyzerProvider
import com.ble1st.connectias.feature.deviceinfo.connectivity.nfc.NfcToolsProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for hardware feature dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object HardwareModule {

    @Provides
    @Singleton
    fun provideNfcToolsProvider(
        @ApplicationContext context: Context
    ): NfcToolsProvider {
        return NfcToolsProvider(context)
    }

    @Provides
    @Singleton
    fun provideBluetoothAnalyzerProvider(
        @ApplicationContext context: Context
    ): BluetoothAnalyzerProvider {
        return BluetoothAnalyzerProvider(context)
    }
}
