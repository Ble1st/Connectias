package com.ble1st.connectias.feature.wasm.di

import com.ble1st.connectias.core.eventbus.EventBus
import com.ble1st.connectias.feature.wasm.plugin.PluginExecutor
import com.ble1st.connectias.feature.wasm.plugin.PluginManager
import com.ble1st.connectias.feature.wasm.plugin.PluginZipParser
import com.ble1st.connectias.feature.wasm.plugin.ResourceMonitor
import com.ble1st.connectias.feature.wasm.security.PluginSignatureVerifier
import com.ble1st.connectias.feature.wasm.wasm.WasmRuntime
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for WASM feature dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object WasmModule {
    
    @Provides
    @Singleton
    fun provideWasmRuntime(): WasmRuntime {
        return WasmRuntime()
    }
    
    @Provides
    @Singleton
    fun providePluginZipParser(): PluginZipParser {
        return PluginZipParser()
    }
    
    @Provides
    @Singleton
    fun providePluginSignatureVerifier(): PluginSignatureVerifier {
        return PluginSignatureVerifier()
    }
    
    @Provides
    @Singleton
    fun provideResourceMonitor(): ResourceMonitor {
        return ResourceMonitor()
    }
    
    @Provides
    @Singleton
    fun providePluginExecutor(
        resourceMonitor: ResourceMonitor
    ): PluginExecutor {
        return PluginExecutor(resourceMonitor)
    }
    
    @Provides
    @Singleton
    fun providePluginPublicKeyManager(
        @ApplicationContext context: android.content.Context,
        signatureVerifier: PluginSignatureVerifier
    ): com.ble1st.connectias.feature.wasm.security.PluginPublicKeyManager {
        return com.ble1st.connectias.feature.wasm.security.PluginPublicKeyManager(
            context = context,
            signatureVerifier = signatureVerifier
        )
    }
    
    @Provides
    @Singleton
    fun providePluginManager(
        wasmRuntime: WasmRuntime,
        pluginZipParser: PluginZipParser,
        pluginExecutor: PluginExecutor,
        signatureVerifier: PluginSignatureVerifier,
        publicKeyManager: com.ble1st.connectias.feature.wasm.security.PluginPublicKeyManager,
        eventBus: EventBus
    ): PluginManager {
        return PluginManager(
            wasmRuntime = wasmRuntime,
            pluginZipParser = pluginZipParser,
            pluginExecutor = pluginExecutor,
            signatureVerifier = signatureVerifier,
            publicKeyManager = publicKeyManager,
            eventBus = eventBus
        )
    }
}

