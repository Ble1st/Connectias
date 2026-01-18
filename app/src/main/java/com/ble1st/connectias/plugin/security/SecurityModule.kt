package com.ble1st.connectias.plugin.security

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for plugin security components
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    
    @Provides
    @Singleton
    fun providePluginResourceLimiter(
        @ApplicationContext context: Context
    ): PluginResourceLimiter {
        return PluginResourceLimiter(context)
    }
    
    @Provides
    @Singleton
    fun providePluginNetworkPolicy(
        @ApplicationContext context: Context
    ): PluginNetworkPolicy {
        return PluginNetworkPolicy(context)
    }
    
    @Provides
    @Singleton
    fun providePluginPermissionMonitor(
        @ApplicationContext context: Context
    ): PluginPermissionMonitor {
        return PluginPermissionMonitor(context)
    }
    
    @Provides
    @Singleton
    fun provideZeroTrustVerifier(
        @ApplicationContext context: Context,
        gitHubStore: com.ble1st.connectias.plugin.store.GitHubPluginStore
    ): ZeroTrustVerifier {
        return ZeroTrustVerifier(context, gitHubStore)
    }
    
    @Provides
    @Singleton
    fun providePluginBehaviorAnalyzer(
        @ApplicationContext context: Context,
        permissionMonitor: PluginPermissionMonitor,
        networkPolicy: PluginNetworkPolicy
    ): PluginBehaviorAnalyzer {
        return PluginBehaviorAnalyzer(context, permissionMonitor, networkPolicy)
    }
    
    @Provides
    @Singleton
    fun provideAnomalyDetector(): AnomalyDetector {
        return AnomalyDetector()
    }
}
