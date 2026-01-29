@file:Suppress("unused")

package com.ble1st.connectias.core.data.di

import com.ble1st.connectias.core.data.repository.LogRepository
import com.ble1st.connectias.core.data.repository.SecurityRepository
import com.ble1st.connectias.core.data.repository.impl.LogRepositoryImpl
import com.ble1st.connectias.core.data.repository.impl.SecurityRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides repository implementations.
 * Phase 2: Core Data Layer
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    
    @Binds
    @Singleton
    abstract fun bindLogRepository(
        impl: LogRepositoryImpl
    ): LogRepository
    
    @Binds
    @Singleton
    abstract fun bindSecurityRepository(
        impl: SecurityRepositoryImpl
    ): SecurityRepository
}
