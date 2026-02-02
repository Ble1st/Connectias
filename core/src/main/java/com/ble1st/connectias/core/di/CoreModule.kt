@file:Suppress("unused")

package com.ble1st.connectias.core.di

import android.content.Context
import com.ble1st.connectias.core.security.debug.DebuggerDetector
import com.ble1st.connectias.core.security.emulator.EmulatorDetector
import com.ble1st.connectias.core.data.security.SecurityCheckProvider
import com.ble1st.connectias.core.security.SecurityCheckProviderImpl
import com.ble1st.connectias.core.security.root.RootDetector
import com.ble1st.connectias.core.security.tamper.TamperDetector
import com.ble1st.connectias.core.services.NetworkService
import com.ble1st.connectias.core.services.SystemService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    
    @Provides
    @Singleton
    fun provideRootDetector(
        @ApplicationContext context: Context
    ): RootDetector = RootDetector(context)
    
    @Provides
    @Singleton
    fun provideDebuggerDetector(): DebuggerDetector = DebuggerDetector()
    
    @Provides
    @Singleton
    fun provideEmulatorDetector(
        @ApplicationContext context: Context
    ): EmulatorDetector = EmulatorDetector(context)
    
    @Provides
    @Singleton
    fun provideTamperDetector(
        @ApplicationContext context: Context
    ): TamperDetector = TamperDetector(context)
    
    @Provides
    @Singleton
    fun provideNetworkService(
        @ApplicationContext context: Context
    ): NetworkService = NetworkService(context)
    
    @Provides
    @Singleton
    fun provideSystemService(
        @ApplicationContext context: Context
    ): SystemService = SystemService(context)

    @Provides
    @Singleton
    fun provideSecurityCheckProvider(
        impl: SecurityCheckProviderImpl
    ): SecurityCheckProvider = impl
}

