@file:Suppress("unused")

package com.ble1st.connectias.core.di

import android.content.Context
import com.ble1st.connectias.core.eventbus.EventBus
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.core.services.pdf.PdfGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
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
    
    @Provides
    @Singleton
    fun providePdfGenerator(
        @ApplicationContext context: Context
    ): PdfGenerator = PdfGenerator(context)

    // SecurityService is provided via @Inject constructor, no explicit provider needed
}

