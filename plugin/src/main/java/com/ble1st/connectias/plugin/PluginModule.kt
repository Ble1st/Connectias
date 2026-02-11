@file:Suppress("unused")

package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.core.plugin.PluginDependencyResolverV2
import com.ble1st.connectias.core.plugin.PluginSandboxProxy
import com.ble1st.connectias.core.servicestate.ServiceStateRepository
import com.ble1st.connectias.plugin.security.EnhancedPluginResourceLimiter
import com.ble1st.connectias.plugin.security.PluginThreadMonitor
import com.ble1st.connectias.plugin.security.SecurityAuditManager
import com.ble1st.connectias.plugin.store.GitHubPluginStore
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
    fun providePluginPermissionManager(
        @ApplicationContext context: Context
    ): PluginPermissionManager {
        return PluginPermissionManager(context)
    }
    
    @Provides
    @Singleton
    fun providePluginManifestParser(
        @ApplicationContext context: Context,
        permissionManager: PluginPermissionManager
    ): PluginManifestParser {
        return PluginManifestParser(context, permissionManager)
    }
    
    @Provides
    @Singleton
    fun providePluginManagerSandbox(
        @ApplicationContext context: Context,
        sandboxProxy: PluginSandboxProxy,
        serviceStateRepository: ServiceStateRepository,
        moduleRegistry: ModuleRegistry,
        permissionManager: PluginPermissionManager,
        resourceLimiter: EnhancedPluginResourceLimiter,
        auditManager: SecurityAuditManager,
        manifestParser: PluginManifestParser
    ): PluginManagerSandbox {
        val pluginDirectory = File(context.filesDir, "plugins").apply { mkdirs() }
        // Note: PluginManagerSandbox implements same API as PluginManager
        return PluginManagerSandbox(
            context = context,
            pluginDirectory = pluginDirectory,
            sandboxProxy = sandboxProxy,
            serviceStateRepository = serviceStateRepository,
            moduleRegistry = moduleRegistry,
            permissionManager = permissionManager,
            resourceLimiter = resourceLimiter,
            auditManager = auditManager,
            manifestParser = manifestParser
        )
    }
    
    @Provides
    @Singleton
    fun provideGitHubPluginStore(
        @ApplicationContext context: Context,
        pluginManager: PluginManagerSandbox
    ): GitHubPluginStore {
        return GitHubPluginStore(context, pluginManager)
    }
    
    @Provides
    @Singleton
    fun providePluginDependencyResolverV2(
        pluginManager: PluginManagerSandbox,
        gitHubPluginStore: GitHubPluginStore
    ): PluginDependencyResolverV2 {
        return PluginDependencyResolverV2(
            pluginManager = pluginManager,
            pluginStore = gitHubPluginStore
        )
    }
}
