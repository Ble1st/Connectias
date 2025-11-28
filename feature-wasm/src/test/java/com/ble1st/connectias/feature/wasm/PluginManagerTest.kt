package com.ble1st.connectias.feature.wasm

import com.ble1st.connectias.core.eventbus.EventBus
import com.ble1st.connectias.feature.wasm.plugin.PluginLoadException
import com.ble1st.connectias.feature.wasm.plugin.PluginManager
import com.ble1st.connectias.feature.wasm.plugin.PluginZipParser
import com.ble1st.connectias.feature.wasm.plugin.ResourceMonitor
import com.ble1st.connectias.feature.wasm.plugin.models.ResourceLimits
import com.ble1st.connectias.feature.wasm.security.PluginPublicKeyManager
import com.ble1st.connectias.feature.wasm.security.PluginSignatureVerifier
import com.ble1st.connectias.feature.wasm.wasm.WasmRuntime
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for PluginManager.
 */
class PluginManagerTest {
    
    private lateinit var pluginManager: PluginManager
    private lateinit var wasmRuntime: WasmRuntime
    private lateinit var pluginZipParser: PluginZipParser
    private lateinit var signatureVerifier: PluginSignatureVerifier
    private lateinit var publicKeyManager: PluginPublicKeyManager
    private lateinit var eventBus: EventBus
    
    @Before
    fun setup() {
        wasmRuntime = mockk()
        pluginZipParser = mockk()
        signatureVerifier = mockk()
        publicKeyManager = mockk()
        eventBus = mockk(relaxed = true)
        
        val resourceMonitor = mockk<ResourceMonitor>(relaxed = true)
        val pluginExecutor = com.ble1st.connectias.feature.wasm.plugin.PluginExecutor(resourceMonitor)
        
        pluginManager = PluginManager(
            wasmRuntime = wasmRuntime,
            pluginZipParser = pluginZipParser,
            pluginExecutor = pluginExecutor,
            signatureVerifier = signatureVerifier,
            publicKeyManager = publicKeyManager,
            eventBus = eventBus
        )
    }
    
    @Test
    fun `getAllPlugins should return empty list initially`() {
        val plugins = pluginManager.getAllPlugins()
        assertTrue(plugins.isEmpty())
    }
    
    @Test
    fun `getPlugin should return null for non-existent plugin`() {
        val plugin = pluginManager.getPlugin("non-existent")
        assertNull(plugin)
    }
    
    @Test
    fun `unloadPlugin should throw exception for non-existent plugin`() = runTest {
        try {
            pluginManager.unloadPlugin("non-existent")
            fail("Should have thrown exception")
        } catch (e: com.ble1st.connectias.feature.wasm.plugin.PluginUnloadException) {
            assertTrue(e.message?.contains("not found") == true)
        }
    }
}

