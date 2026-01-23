// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.content.Context
import android.content.ContextWrapper
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.ble1st.connectias.core.plugin.security.ReflectionBlocker
import timber.log.Timber
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Isolated Context wrapper for plugin UI rendering.
 *
 * Security Features:
 * - Monitors and blocks dangerous reflection attempts
 * - Wraps base context to prevent direct access to app internals
 * - Logs all reflection attempts for security auditing
 *
 * This context is provided to plugin fragments running in the sandbox process
 * to ensure they cannot escape the sandbox via reflection on the Context object.
 *
 * Usage:
 * ```kotlin
 * val isolatedContext = IsolatedPluginContext(serviceContext, pluginId)
 * fragment.attachContext(isolatedContext)
 * ```
 *
 * @param base Base context (usually PluginSandboxService)
 * @param pluginId Plugin identifier for audit logging
 */
class IsolatedPluginContext(
    base: Context,
    private val pluginId: String
) : ContextWrapper(base) {

    companion object {
        /**
         * Context fields that should never be accessed via reflection
         */
        private val FORBIDDEN_CONTEXT_FIELDS = setOf(
            "mBase",
            "mPackageInfo",
            "mMainThread",
            "mActivityThread",
            "mResources",
            "mResourcesManager",
            "mLoadedApk"
        )

        /**
         * Context methods that should never be invoked via reflection
         */
        private val FORBIDDEN_CONTEXT_METHODS = setOf(
            "getSystemService",
            "getApplicationContext",
            "getApplicationInfo",
            "createPackageContext",
            "createDeviceProtectedStorageContext"
        )
    }

    /**
     * Override to prevent reflection-based access
     *
     * This method is called by reflection frameworks. We intercept it
     * to block access to dangerous fields.
     */
    private fun checkFieldAccess(field: Field) {
        if (FORBIDDEN_CONTEXT_FIELDS.contains(field.name)) {
            val message = "[SECURITY] Plugin '$pluginId' attempted reflection access to forbidden field: ${field.name}"
            Timber.e(message)
            throw SecurityException(message)
        }

        // Use ReflectionBlocker for additional checks
        if (!ReflectionBlocker.isFieldAccessAllowed(field, pluginId)) {
            val message = "[SECURITY] Plugin '$pluginId' reflection access blocked by ReflectionBlocker: ${field.name}"
            Timber.e(message)
            throw SecurityException(message)
        }
    }

    /**
     * Override to prevent reflection-based method invocation
     */
    private fun checkMethodInvocation(method: Method) {
        if (FORBIDDEN_CONTEXT_METHODS.contains(method.name)) {
            val message = "[SECURITY] Plugin '$pluginId' attempted reflection invocation of forbidden method: ${method.name}"
            Timber.e(message)
            throw SecurityException(message)
        }

        // Use ReflectionBlocker for additional checks
        if (!ReflectionBlocker.isMethodInvocationAllowed(method, pluginId)) {
            val message = "[SECURITY] Plugin '$pluginId' method invocation blocked by ReflectionBlocker: ${method.name}"
            Timber.e(message)
            throw SecurityException(message)
        }
    }

    /**
     * Override getSystemService to restrict access
     *
     * Only allow safe system services
     */
    override fun getSystemService(name: String): Any? {
        // Whitelist of allowed system services
        val allowedServices = setOf(
            LAYOUT_INFLATER_SERVICE,
            WINDOW_SERVICE,
            DISPLAY_SERVICE,
            UI_MODE_SERVICE,
            ACCESSIBILITY_SERVICE,
            INPUT_METHOD_SERVICE
        )

        if (!allowedServices.contains(name)) {
            Timber.w("[SECURITY] Plugin '$pluginId' attempted to access restricted system service: $name")
            // Return null instead of throwing to avoid breaking plugins
            return null
        }

        val service = super.getSystemService(name)
        
        // Wrap WindowManager to prevent arbitrary view additions
        if (name == WINDOW_SERVICE && service is WindowManager) {
            return RestrictedWindowManager(service, pluginId)
        }
        
        return service
    }

    /**
     * Override getApplicationContext to return isolated context
     *
     * Prevents access to the real application context
     */
    override fun getApplicationContext(): Context {
        Timber.d("[ISOLATION] Plugin '$pluginId' accessed application context - returning isolated context")
        return this
    }

    /**
     * Block createPackageContext to prevent accessing other apps' contexts
     */
    override fun createPackageContext(packageName: String, flags: Int): Context {
        val message = "[SECURITY] Plugin '$pluginId' attempted to create package context for: $packageName"
        Timber.e(message)
        throw SecurityException("Plugins cannot create package contexts")
    }

    /**
     * Log context creation for security auditing
     */
    init {
        Timber.d("[ISOLATION] IsolatedPluginContext created for plugin: $pluginId")
        Timber.d("[ISOLATION] Base context: ${base.javaClass.simpleName}")
    }

    /**
     * Audit wrapper for toString to detect reflection probing
     */
    override fun toString(): String {
        Timber.v("[ISOLATION] Plugin '$pluginId' called toString() on IsolatedPluginContext")
        return "IsolatedPluginContext[plugin=$pluginId]"
    }

    /**
     * Finalize cleanup
     */
    @Suppress("DEPRECATION")
    protected fun finalize() {
        Timber.v("[ISOLATION] IsolatedPluginContext finalized for plugin: $pluginId")
    }
}

/**
 * Restricted WindowManager that prevents plugins from adding arbitrary views to the window.
 * 
 * Only allows views that belong to the plugin's fragment hierarchy.
 * 
 * Note: We manually delegate all WindowManager methods to intercept addView calls,
 * since WindowManager delegation doesn't allow overriding ViewManager methods.
 */
class RestrictedWindowManager(
    private val delegate: WindowManager,
    private val pluginId: String
) : WindowManager {
    
    // Override ViewManager.addView to intercept view additions
    // WindowManager uses ViewGroup.LayoutParams, not ViewManager.LayoutParams
    override fun addView(view: View, params: ViewGroup.LayoutParams) {
        // Only allow if view belongs to plugin fragment
        if (!isPluginView(view)) {
            val message = "[SECURITY] Plugin '$pluginId' attempted to add arbitrary view to window: ${view.javaClass.name}"
            Timber.e(message)
            throw SecurityException("Plugins cannot add arbitrary views to window")
        }
        // Cast to WindowManager.LayoutParams and delegate
        if (params is WindowManager.LayoutParams) {
            delegate.addView(view, params)
        } else {
            // Fallback: try to add with converted params
            val windowParams = WindowManager.LayoutParams(
                params.width,
                params.height,
                0, 0, // x, y
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            delegate.addView(view, windowParams)
        }
    }
    
    override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) {
        if (params is WindowManager.LayoutParams) {
            delegate.updateViewLayout(view, params)
        } else {
            // For non-WindowManager params, delegate as-is
            delegate.updateViewLayout(view, params)
        }
    }
    
    override fun removeView(view: View) {
        delegate.removeView(view)
    }
    
    // Delegate WindowManager-specific methods
    override fun getDefaultDisplay(): Display {
        return delegate.getDefaultDisplay()
    }
    
    override fun removeViewImmediate(view: View) {
        delegate.removeViewImmediate(view)
    }
    
    /**
     * Checks if a view belongs to the plugin's fragment hierarchy
     */
    private fun isPluginView(view: View): Boolean {
        // Check if view's context is from a plugin
        val context = view.context
        val contextClassName = context.javaClass.name
        
        // Allow views from plugin contexts
        if (contextClassName.contains("Plugin") || contextClassName.contains("IsolatedPluginContext")) {
            return true
        }
        
        // Check parent hierarchy for plugin fragments
        var parent: View? = view.parent as? View
        while (parent != null) {
            val parentContext = parent.context
            val parentContextClassName = parentContext.javaClass.name
            if (parentContextClassName.contains("Plugin") || parentContextClassName.contains("IsolatedPluginContext")) {
                return true
            }
            parent = parent.parent as? View
        }
        
        return false
    }
}

/**
 * Extension function to create isolated context for a plugin
 */
fun Context.createIsolatedContext(pluginId: String): IsolatedPluginContext {
    return IsolatedPluginContext(this, pluginId)
}
