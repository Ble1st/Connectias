@file:Suppress("unused")

package com.ble1st.connectias.di

import com.ble1st.connectias.plugin.security.EnhancedPluginNetworkPolicy
import com.ble1st.connectias.plugin.security.PluginNetworkTracker
import com.ble1st.connectias.plugin.security.NetworkUsageAggregator
import com.ble1st.connectias.plugin.security.SecurityAuditManager
import com.ble1st.connectias.plugin.security.EnhancedPluginResourceLimiter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context

/**
 * Hilt module for Security and Network Policy dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkPolicyModule {
    
    @Provides
    @Singleton
    fun provideSecurityAuditManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context
    ): SecurityAuditManager {
        return SecurityAuditManager(context)
    }

    @Provides
    @Singleton
    fun provideEnhancedPluginResourceLimiter(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context
    ): EnhancedPluginResourceLimiter {
        return EnhancedPluginResourceLimiter(context)
    }

    @Provides
    @Singleton
    fun provideEnhancedPluginNetworkPolicy(): EnhancedPluginNetworkPolicy {
        return EnhancedPluginNetworkPolicy()
    }
    
    @Provides
    @Singleton
    fun providePluginNetworkTracker(): PluginNetworkTracker {
        return PluginNetworkTracker
    }
    
    @Provides
    @Singleton
    fun provideNetworkUsageAggregator(): NetworkUsageAggregator {
        return NetworkUsageAggregator
    }
    
    @Provides
    @Singleton
    fun providePluginSandboxProxy(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
        auditManager: SecurityAuditManager,
        pluginLogBridge: com.ble1st.connectias.plugin.logging.PluginLogBridgeImpl
    ): com.ble1st.connectias.core.plugin.PluginSandboxProxy {
        return com.ble1st.connectias.core.plugin.PluginSandboxProxy(
            context = context,
            auditManager = auditManager,
            pluginLogBridge = pluginLogBridge
        )
    }
    
    @Provides
    @Singleton
    fun providePluginThreadMonitor(): com.ble1st.connectias.plugin.security.PluginThreadMonitor {
        return com.ble1st.connectias.plugin.security.PluginThreadMonitor
    }
}
