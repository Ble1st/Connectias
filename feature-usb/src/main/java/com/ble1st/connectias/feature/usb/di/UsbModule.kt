package com.ble1st.connectias.feature.usb.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for USB feature dependencies.
 * 
 * Note: Most classes use @Inject constructors and are automatically provided by Hilt.
 * This module only contains providers for classes that require custom instantiation logic.
 */
@Module
@InstallIn(SingletonComponent::class)
object UsbModule {
    
    // No custom providers needed currently.
    // 
    // The following classes are automatically provided by Hilt via @Inject constructors:
    // - UsbProvider (@Inject constructor())
    // - UsbPermissionManager (@Inject constructor(@ApplicationContext context: Context))
    // - UsbDeviceDetector (@Inject constructor(@ApplicationContext context: Context))
    // - UsbCryptoProvider (@Inject constructor())
    // - MountManager (@Inject constructor(@ApplicationContext context: Context))
    // 
    // DVD/Optical drive components have been moved to DvdModule in feature-dvd.
}