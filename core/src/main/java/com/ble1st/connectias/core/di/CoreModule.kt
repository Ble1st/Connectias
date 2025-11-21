package com.ble1st.connectias.core.di

import com.ble1st.connectias.core.security.RaspManager
import com.ble1st.connectias.core.security.debug.DebuggerDetector
import com.ble1st.connectias.core.security.emulator.EmulatorDetector
import com.ble1st.connectias.core.security.root.RootDetector
import com.ble1st.connectias.core.security.tamper.TamperDetector
import com.ble1st.connectias.core.services.NetworkService
import com.ble1st.connectias.core.services.SecurityService
import com.ble1st.connectias.core.services.SystemService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    
    @Provides
    @Singleton
    fun provideRootDetector(): RootDetector = RootDetector()
    
    @Provides
    @Singleton
    fun provideDebuggerDetector(): DebuggerDetector = DebuggerDetector()
    
    @Provides
    @Singleton
    fun provideEmulatorDetector(): EmulatorDetector = EmulatorDetector()
    
    @Provides
    @Singleton
    fun provideTamperDetector(): TamperDetector = TamperDetector()
    
    @Provides
    @Singleton
    fun provideNetworkService(): NetworkService = NetworkService()
    
    @Provides
    @Singleton
    fun provideSystemService(): SystemService = SystemService()
}

