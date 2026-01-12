package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.core.module.ModuleRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PluginModule {
    
    @Provides
    @Singleton
    fun providePluginDirectory(
        @ApplicationContext context: Context
    ): File {
        val pluginDir = File(context.filesDir, "plugins")
        pluginDir.mkdirs()
        return pluginDir
    }
    
    @Provides
    @Singleton
    fun provideNativeLibraryManager(
        @ApplicationContext context: Context,
        pluginDirectory: File
    ): NativeLibraryManager {
        return NativeLibraryManager(pluginDirectory)
    }
    
    @Provides
    @Singleton
    fun providePluginNotificationManager(
        @ApplicationContext context: Context
    ): PluginNotificationManager {
        return PluginNotificationManager(context)
    }
    
    @Provides
    @Singleton
    fun providePluginManager(
        @ApplicationContext context: Context,
        pluginDirectory: File,
        moduleRegistry: ModuleRegistry
    ): PluginManagerSandbox {
        // Use PluginManagerSandbox for process isolation (Option 3)
        // This runs plugins in a separate process, providing crash isolation
        // Plugin crashes will NOT crash the main app process
        // Note: PluginManagerSandbox implements same API as PluginManager
        return PluginManagerSandbox(context, pluginDirectory, moduleRegistry)
    }
}
