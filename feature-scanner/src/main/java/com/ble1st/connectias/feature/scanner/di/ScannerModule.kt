package com.ble1st.connectias.feature.scanner.di

import com.ble1st.connectias.feature.scanner.data.ScannerRepository
import com.ble1st.connectias.feature.scanner.data.ScannerRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {

    @Binds
    abstract fun bindScannerRepository(
        impl: ScannerRepositoryImpl
    ): ScannerRepository
}