// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.security

import dalvik.system.InMemoryDexClassLoader
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Restricted ClassLoader for plugin DEX files with security enforcement.
 *
 * This classloader:
 * 1. Loads plugin DEX from memory via delegate InMemoryDexClassLoader
 * 2. Uses FilteredParentClassLoader to block access to app internals
 * 3. Tracks loaded classes for auditing
 * 4. Prevents ClassLoader escape attacks
 *
 * Security Features:
 * - Parent filtering (FilteredParentClassLoader)
 * - Class load auditing
 * - Forbidden class blocking
 *
 * @param dexBuffers DEX file bytecode buffers
 * @param filteredParent Filtered parent classloader
 * @param pluginId Plugin identifier for audit logging
 */
class RestrictedClassLoader(
    private val dexBuffers: Array<ByteBuffer>,
    filteredParent: FilteredParentClassLoader,
    private val pluginId: String
) : ClassLoader(filteredParent) {

    // Delegate to InMemoryDexClassLoader for actual DEX loading
    private val delegate = InMemoryDexClassLoader(dexBuffers, filteredParent)

    // Track loaded classes for security auditing
    private val loadedClasses = mutableSetOf<String>()

    companion object {
        private const val MAX_LOADED_CLASSES = 10000 // DoS protection

        /**
         * Classes that should never be loaded by plugins
         */
        private val FORBIDDEN_CLASSES = setOf(
            "dalvik.system.DexClassLoader",
            "dalvik.system.PathClassLoader",
            "dalvik.system.InMemoryDexClassLoader",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder"
        )
    }

    /**
     * Load class with security enforcement
     */
    override fun loadClass(name: String): Class<*> {
        return loadClass(name, false)
    }

    /**
     * Load class with resolve flag and security enforcement
     */
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // Check if class is explicitly forbidden
        if (FORBIDDEN_CLASSES.contains(name)) {
            val message = "[SECURITY] Plugin '$pluginId' attempted to load forbidden class: $name"
            Timber.e(message)
            throw SecurityException(message)
        }

        // DoS protection: limit number of loaded classes
        if (loadedClasses.size >= MAX_LOADED_CLASSES) {
            val message = "[SECURITY] Plugin '$pluginId' exceeded max loaded classes limit ($MAX_LOADED_CLASSES)"
            Timber.e(message)
            throw SecurityException(message)
        }

        // Try to load class (will go through parent filtering)
        try {
            val clazz = delegate.loadClass(name)

            // Track loaded class
            loadedClasses.add(name)

            // Log plugin classes (not SDK classes) for debugging
            if (name.startsWith("com.") && !name.startsWith("com.ble1st.connectias.plugin.sdk.")) {
                Timber.d("[CLASSLOADER] Plugin '$pluginId' loaded class: $name")
            }

            if (resolve) {
                resolveClass(clazz)
            }

            return clazz
        } catch (e: SecurityException) {
            // Re-throw security exceptions from parent classloader
            Timber.w("[CLASSLOADER] Plugin '$pluginId' blocked from loading: $name")
            throw e
        } catch (e: ClassNotFoundException) {
            // Class not found
            throw e
        }
    }

    /**
     * Get count of loaded classes for monitoring
     */
    fun getLoadedClassCount(): Int {
        return loadedClasses.size
    }

    /**
     * Get list of loaded plugin classes (for debugging)
     */
    fun getLoadedPluginClasses(): List<String> {
        return loadedClasses
            .filter { it.startsWith("com.") && !it.startsWith("com.ble1st.connectias.plugin.sdk.") }
            .sorted()
    }

    /**
     * Clear tracking data when plugin is unloaded
     */
    fun cleanup() {
        loadedClasses.clear()
        Timber.d("[CLASSLOADER] Cleaned up classloader for plugin: $pluginId")
    }
}
