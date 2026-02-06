// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.security

import timber.log.Timber
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Runtime reflection blocker for plugin security.
 *
 * This utility helps detect and block dangerous reflection attempts by plugins
 * trying to access app internals, modify security managers, or escape the sandbox.
 *
 * Usage in plugin context:
 * ```kotlin
 * class IsolatedPluginContext(
 *     ...
 * ) : PluginContext {
 *     override fun getApplicationContext(): Context {
 *         return ReflectionBlocker.wrapContext(secureContext, pluginId)
 *     }
 * }
 * ```
 *
 * Blocked reflection targets:
 * - ClassLoader hierarchy
 * - SecurityManager
 * - ActivityThread (app internals)
 * - Package internals (com.ble1st.connectias.core.*)
 *
 * @since 2.1.0 (Security hardening)
 */
object ReflectionBlocker {

    /**
     * Packages that are forbidden for reflection access
     */
    private val FORBIDDEN_REFLECTION_PACKAGES = setOf(
        "com.ble1st.connectias.core",
        "com.ble1st.connectias.ui",
        "com.ble1st.connectias.data",
        "com.ble1st.connectias.domain",
        "com.ble1st.connectias.hardware.HardwareBridgeService",
        "com.ble1st.connectias.plugin.security"
    )

    /**
     * Classes that are forbidden for reflection access
     */
    private val FORBIDDEN_REFLECTION_CLASSES = setOf(
        "dalvik.system.DexClassLoader",
        "dalvik.system.PathClassLoader",
        "dalvik.system.InMemoryDexClassLoader",
        "java.lang.ClassLoader",
        "java.lang.Runtime",
        "java.lang.ProcessBuilder",
        "android.app.ActivityThread",
        "android.app.ContextImpl",
        "android.app.LoadedApk"
    )

    /**
     * Check if reflection access to a class is allowed
     *
     * @param targetClass The class being accessed via reflection
     * @param pluginId Plugin identifier for audit logging
     * @return true if allowed, false if blocked
     */
    fun isReflectionAllowed(targetClass: Class<*>, pluginId: String): Boolean {
        val className = targetClass.name

        // Check forbidden classes
        if (FORBIDDEN_REFLECTION_CLASSES.contains(className)) {
            Timber.w("[SECURITY] Plugin '$pluginId' attempted reflection on forbidden class: $className")
            return false
        }

        // Check forbidden packages
        for (forbiddenPackage in FORBIDDEN_REFLECTION_PACKAGES) {
            if (className.startsWith(forbiddenPackage)) {
                Timber.w("[SECURITY] Plugin '$pluginId' attempted reflection on forbidden package: $className")
                return false
            }
        }

        return true
    }

    /**
     * Check if reflection access to a field is allowed
     *
     * @param field The field being accessed
     * @param pluginId Plugin identifier for audit logging
     * @return true if allowed, false if blocked
     */
    fun isFieldAccessAllowed(field: Field, pluginId: String): Boolean {
        val declaringClass = field.declaringClass

        if (!isReflectionAllowed(declaringClass, pluginId)) {
            return false
        }

        // Block access to certain sensitive fields
        val fieldName = field.name
        if (fieldName in setOf("mBase", "mPackageInfo", "mMainThread", "sCurrentActivityThread")) {
            Timber.w("[SECURITY] Plugin '$pluginId' attempted access to sensitive field: ${declaringClass.name}.$fieldName")
            return false
        }

        return true
    }

    /**
     * Check if reflection method invocation is allowed
     *
     * @param method The method being invoked
     * @param pluginId Plugin identifier for audit logging
     * @return true if allowed, false if blocked
     */
    fun isMethodInvocationAllowed(method: Method, pluginId: String): Boolean {
        val declaringClass = method.declaringClass

        if (!isReflectionAllowed(declaringClass, pluginId)) {
            return false
        }

        // Block dangerous method invocations
        val methodName = method.name
        val dangerousMethods = setOf(
            "setAccessible",
            "forName",
            "getDeclaredClasses",
            "getDeclaredField",
            "getDeclaredMethod",
            "exec",
            "loadClass",
            "defineClass"
        )

        if (methodName in dangerousMethods && declaringClass.name.startsWith("com.ble1st.connectias")) {
            Timber.w("[SECURITY] Plugin '$pluginId' attempted dangerous method invocation: ${declaringClass.name}.$methodName")
            return false
        }

        return true
    }

    /**
     * Wrap a context to monitor reflection attempts
     *
     * Note: This is a placeholder for future bytecode instrumentation
     * Currently, blocking happens at ClassLoader level
     *
     * @param context The context to wrap
     * @param pluginId Plugin identifier
     * @return Wrapped context (currently returns original)
     */
    fun wrapContext(context: android.content.Context, pluginId: String): android.content.Context {
        // Future: Use dynamic proxy or bytecode instrumentation to intercept reflection
        // For now, rely on ClassLoader-level blocking
        return context
    }

    /**
     * Check if a class name indicates a reflection attack attempt
     *
     * @param className Class name to check
     * @return true if this looks like an attack attempt
     */
    fun isLikelyAttackAttempt(className: String): Boolean {
        val suspiciousPatterns = listOf(
            "sun.misc.Unsafe",
            "java.lang.reflect.Proxy",
            "android.os.Binder",
            "android.app.ActivityThread",
            "com.android.internal"
        )

        return suspiciousPatterns.any { className.contains(it) }
    }

    /**
     * Log a reflection attempt for security auditing
     *
     * @param pluginId Plugin identifier
     * @param targetClass Class being accessed
     * @param accessType Type of access (field, method, constructor)
     * @param allowed Whether the access was allowed
     */
    fun logReflectionAttempt(
        pluginId: String,
        targetClass: Class<*>,
        accessType: String,
        allowed: Boolean
    ) {
        val status = if (allowed) "ALLOWED" else "BLOCKED"
        Timber.i("[REFLECTION AUDIT] Plugin '$pluginId' - $status $accessType access to ${targetClass.name}")
    }
}
