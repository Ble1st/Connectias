// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.security

import timber.log.Timber

/**
 * ClassLoader that filters access to parent classloader to prevent plugins
 * from accessing internal app classes via reflection.
 *
 * This wraps the parent classloader and blocks access to sensitive packages
 * that plugins should not be able to access.
 *
 * Security Model:
 * - Allow: Android SDK classes (android.*, androidx.*, kotlin.*, kotlinx.*)
 * - Allow: Plugin SDK API (com.ble1st.connectias.plugin.sdk.*)
 * - Block: App internals (com.ble1st.connectias.core.*, com.ble1st.connectias.ui.*)
 * - Block: Direct hardware/file access
 *
 * @param parent The parent classloader to wrap and filter
 */
class FilteredParentClassLoader(
    private val parent: ClassLoader
) : ClassLoader(null) { // null parent = no delegation

    companion object {
        /**
         * Packages that plugins are ALLOWED to access
         */
        private val ALLOWED_PACKAGES = setOf(
            // Android SDK
            "android.",
            "androidx.",
            "com.google.android.",

            // Kotlin stdlib
            "kotlin.",
            "kotlinx.",

            // Java stdlib
            "java.",
            "javax.",

            // Plugin SDK API - plugins can access this
            "com.ble1st.connectias.plugin.sdk.",

            // AIDL parcelables that plugins need
            "com.ble1st.connectias.plugin.messaging.PluginMessage",
            "com.ble1st.connectias.plugin.messaging.MessageResponse",

            // Timber logging (read-only, safe)
            "timber.log.",

            // Jetpack Compose (for plugin UIs)
            "androidx.compose."
        )

        /**
         * Packages that are EXPLICITLY BLOCKED
         */
        private val BLOCKED_PACKAGES = setOf(
            // Core app internals
            "com.ble1st.connectias.core.",

            // UI internals
            "com.ble1st.connectias.ui.",

            // Data layer
            "com.ble1st.connectias.data.",

            // Domain layer
            "com.ble1st.connectias.domain.",

            // Feature modules
            "com.ble1st.connectias.feature.",

            // Hardware bridge internals (must use API)
            "com.ble1st.connectias.hardware.HardwareBridgeService",

            // File system bridge internals
            "com.ble1st.connectias.plugin.IFileSystemBridge",

            // Security internals (don't let plugins access security checks!)
            "com.ble1st.connectias.plugin.security.",

            // Plugin manager internals
            "com.ble1st.connectias.plugin.PluginManager"
        )
    }

    /**
     * Load class with filtering
     *
     * @throws ClassNotFoundException if class is blocked
     * @throws SecurityException if class access is forbidden
     */
    override fun loadClass(name: String): Class<*> {
        return loadClass(name, false)
    }

    /**
     * Load class with resolve flag
     */
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // Check if class is explicitly blocked
        if (isBlocked(name)) {
            val message = "[SECURITY] Plugin attempted to access blocked class: $name"
            Timber.e(message)
            throw SecurityException(message)
        }

        // Check if class is allowed
        if (!isAllowed(name)) {
            val message = "[SECURITY] Plugin attempted to access forbidden package: $name"
            Timber.w(message)
            throw ClassNotFoundException("$name (access denied by security policy)")
        }

        // Delegate to parent classloader
        try {
            val clazz = parent.loadClass(name)
            if (resolve) {
                resolveClass(clazz)
            }
            return clazz
        } catch (e: ClassNotFoundException) {
            // Class not found in parent, throw original exception
            throw e
        }
    }

    /**
     * Check if a class name is explicitly blocked
     */
    private fun isBlocked(className: String): Boolean {
        return BLOCKED_PACKAGES.any { className.startsWith(it) }
    }

    /**
     * Check if a class name is allowed
     */
    private fun isAllowed(className: String): Boolean {
        // Check against allowed packages
        return ALLOWED_PACKAGES.any { className.startsWith(it) }
    }

    /**
     * Find resource - delegate to parent with same filtering
     */
    override fun getResource(name: String): java.net.URL? {
        // Resources are generally safe, delegate to parent
        return parent.getResource(name)
    }

    /**
     * Find resources - delegate to parent
     */
    override fun getResources(name: String): java.util.Enumeration<java.net.URL> {
        return parent.getResources(name)
    }
}
