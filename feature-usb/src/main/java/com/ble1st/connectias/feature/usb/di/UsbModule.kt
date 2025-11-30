package com.ble1st.connectias.feature.usb.di

import com.ble1st.connectias.feature.usb.detection.UsbDeviceDetector
import com.ble1st.connectias.feature.usb.media.DvdVideoProvider
import com.ble1st.connectias.feature.usb.settings.DvdSettings
import com.ble1st.connectias.feature.usb.storage.FileSystemReader
import com.ble1st.connectias.feature.usb.storage.MountManager
import com.ble1st.connectias.feature.usb.storage.OpticalDriveProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for USB feature dependencies.
 * 
 * Note: Most classes use @Inject constructors and are automatically provided by Hilt.
 * This module only contains providers for classes that require custom instantiation logic
 * or complex dependency wiring.
 */
@Module
@InstallIn(SingletonComponent::class)
object UsbModule {
    
    @Provides
    @Singleton
    fun provideOpticalDriveProvider(
        deviceDetector: UsbDeviceDetector,
        mountManager: MountManager,
        fileSystemReader: FileSystemReader
    ): OpticalDriveProvider = OpticalDriveProvider(deviceDetector, mountManager, fileSystemReader)
    
    @Provides
    @Singleton
    fun provideDvdVideoProvider(
        opticalDriveProvider: OpticalDriveProvider,
        dvdSettings: DvdSettings
    ): DvdVideoProvider = DvdVideoProvider(opticalDriveProvider, dvdSettings)
    
    // Note: DvdNative is an object, no provider needed
    // 
    // The following classes are automatically provided by Hilt via @Inject constructors:
    // - UsbProvider (@Inject constructor())
    // - UsbPermissionManager (@Inject constructor(@ApplicationContext context: Context))
    // - UsbDeviceDetector (@Inject constructor(@ApplicationContext context: Context))
    // - UsbCryptoProvider (@Inject constructor())
    // - MountManager (@Inject constructor(@ApplicationContext context: Context))
    // - FileSystemReader (@Inject constructor())
    // - AudioCdProvider (@Inject constructor())
    // - AudioCdPlayer (@Inject constructor(@ApplicationContext context: Context))
    // - DvdNavigation (@Inject constructor())
    // - DvdSettings (@Inject constructor(@ApplicationContext context: Context))
    // - DvdPlayer (@Inject constructor(@ApplicationContext context: Context))
}
