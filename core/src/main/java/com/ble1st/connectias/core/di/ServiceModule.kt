package com.ble1st.connectias.core.di

import com.ble1st.connectias.core.eventbus.EventBus
import com.ble1st.connectias.core.module.ModuleRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    @Provides
    @Singleton
    fun provideEventBus(): EventBus = EventBus()
    
    @Provides
    @Singleton
    fun provideModuleRegistry(): ModuleRegistry = ModuleRegistry()
    
    // LoggingService is provided via @Inject constructor, no explicit provider needed
    // SecurityService is provided via @Inject constructor, no explicit provider needed
}

