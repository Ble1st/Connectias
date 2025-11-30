package com.ble1st.connectias.feature.usb.di

import android.content.Context
import com.ble1st.connectias.feature.usb.crypto.UsbCryptoProvider
import com.ble1st.connectias.feature.usb.detection.UsbDeviceDetector
import com.ble1st.connectias.feature.usb.media.AudioCdPlayer
import com.ble1st.connectias.feature.usb.media.AudioCdProvider
import com.ble1st.connectias.feature.usb.media.DvdNavigation
import com.ble1st.connectias.feature.usb.media.DvdPlayer
import com.ble1st.connectias.feature.usb.media.DvdVideoProvider
import com.ble1st.connectias.feature.usb.permission.UsbPermissionManager
import com.ble1st.connectias.feature.usb.provider.UsbProvider
import com.ble1st.connectias.feature.usb.settings.DvdSettings
import com.ble1st.connectias.feature.usb.storage.FileSystemReader
import com.ble1st.connectias.feature.usb.storage.MountManager
import com.ble1st.connectias.feature.usb.storage.OpticalDriveProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for USB feature dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object UsbModule {
    
    @Provides
    @Singleton
    fun provideUsbProvider(): UsbProvider = UsbProvider()
    
    @Provides
    @Singleton
    fun provideUsbPermissionManager(
        @ApplicationContext context: Context
    ): UsbPermissionManager = UsbPermissionManager(context)
    
    @Provides
    @Singleton
    fun provideUsbDeviceDetector(
        @ApplicationContext context: Context
    ): UsbDeviceDetector = UsbDeviceDetector(context)
    
    @Provides
    @Singleton
    fun provideUsbCryptoProvider(): UsbCryptoProvider = UsbCryptoProvider()
    
    @Provides
    @Singleton
    fun provideMountManager(
        @ApplicationContext context: Context
    ): MountManager = MountManager(context)
    
    @Provides
    @Singleton
    fun provideFileSystemReader(): FileSystemReader = FileSystemReader()
    
    @Provides
    @Singleton
    fun provideOpticalDriveProvider(
        deviceDetector: UsbDeviceDetector,
        mountManager: MountManager,
        fileSystemReader: FileSystemReader
    ): OpticalDriveProvider = OpticalDriveProvider(deviceDetector, mountManager, fileSystemReader)
    
    @Provides
    @Singleton
    fun provideAudioCdProvider(): AudioCdProvider = AudioCdProvider()
    
    @Provides
    @Singleton
    fun provideAudioCdPlayer(
        @ApplicationContext context: Context
    ): AudioCdPlayer = AudioCdPlayer(context)
    
    @Provides
    @Singleton
    fun provideDvdNavigation(): DvdNavigation = DvdNavigation()
    
    @Provides
    @Singleton
    fun provideDvdSettings(
        @ApplicationContext context: Context
    ): DvdSettings = DvdSettings(context)
    
    @Provides
    @Singleton
    fun provideDvdVideoProvider(
        opticalDriveProvider: OpticalDriveProvider,
        dvdSettings: DvdSettings
    ): DvdVideoProvider = DvdVideoProvider(opticalDriveProvider, dvdSettings)
    
    // Note: DvdNative is an object, no provider needed
    
    @Provides
    @Singleton
    fun provideDvdPlayer(
        @ApplicationContext context: Context
    ): DvdPlayer = DvdPlayer(context)
}
