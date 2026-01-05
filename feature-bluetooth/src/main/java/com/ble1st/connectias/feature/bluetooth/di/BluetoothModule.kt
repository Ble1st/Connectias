package com.ble1st.connectias.feature.bluetooth.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.ble1st.connectias.feature.bluetooth.data.BluetoothScanner
import com.ble1st.connectias.feature.bluetooth.data.BluetoothScannerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    @Provides
    @Singleton
    fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager? {
        return context.getSystemService(BluetoothManager::class.java)
    }

    @Provides
    @Singleton
    fun provideBluetoothAdapter(manager: BluetoothManager?): BluetoothAdapter? = manager?.adapter

    @Provides
    @Singleton
    fun provideBluetoothScanner(
        @ApplicationContext context: Context,
        adapter: BluetoothAdapter?
    ): BluetoothScanner = BluetoothScannerImpl(context, adapter)
}
