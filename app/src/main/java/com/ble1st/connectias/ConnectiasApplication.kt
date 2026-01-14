// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias

import android.app.Application
import android.os.Looper
import com.ble1st.connectias.core.logging.LoggingTreeEntryPoint
import com.ble1st.connectias.performance.StrictModeConfig
import com.ble1st.connectias.plugin.PluginManagerSandbox
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

@HiltAndroidApp
class ConnectiasApplication : Application() {
    
    /**
     * Check if we're running in the isolated sandbox process
     * Isolated processes have NO access to:
     * - KeyStore / EncryptedSharedPreferences
     * - Database
     * - Normal SharedPreferences
     * - Network (without permission)
     */
    private fun isIsolatedSandboxProcess(): Boolean {
        val processName = getCurrentProcessName()
        return processName.contains(":plugin_sandbox")
    }
    
    private fun getCurrentProcessName(): String {
        return try {
            val pid = android.os.Process.myPid()
            val manager = getSystemService(android.app.ActivityManager::class.java)
            manager?.runningAppProcesses
                ?.find { it.pid == pid }
                ?.processName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Skip full initialization in isolated sandbox process
        // Isolated processes cannot access KeyStore, Database, or SharedPreferences
        if (isIsolatedSandboxProcess()) {
            // Minimal Timber setup - only DebugTree, no database logging
            Timber.plant(Timber.DebugTree())
            Timber.i("[ISOLATED SANDBOX] Running in isolated process - skipping full app initialization")
            Timber.i("[ISOLATED SANDBOX] Hilt is initialized but database/keystore modules are guarded")
            return
        }
        
        // Phase 8: Enable StrictMode for performance monitoring in Debug builds
      //  StrictModeConfig.enableStrictMode(BuildConfig.DEBUG)
        
        // Initialize Timber for logging
        // Get ConnectiasLoggingTree via Hilt EntryPoint (requires Database)
        try {
            val loggingTree = EntryPointAccessors.fromApplication(
                this,
                LoggingTreeEntryPoint::class.java
            ).loggingTree()
            
            // Plant database logging tree first (for production)
            Timber.plant(loggingTree)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize database logging tree - using DebugTree only")
        }
        
        // Also plant debug tree in debug builds for logcat output
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Set up global uncaught exception handler for plugin threads
        setupPluginExceptionHandler()
        
        Timber.d("ConnectiasApplication initialized")
    }
    
    /**
     * Hilt EntryPoint for accessing PluginManager in Application.onCreate().
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PluginManagerEntryPoint {
        fun pluginManager(): PluginManagerSandbox
    }
    
    /**
     * Sets up a global uncaught exception handler that catches exceptions
     * from plugin threads without crashing the app.
     * 
     * This provides an additional safety layer for threads outside of coroutines,
     * such as native callbacks or threads created by plugins.
     */
    private fun setupPluginExceptionHandler() {
        // Store the default handler to delegate non-plugin exceptions
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            // Check if exception originates from plugin code FIRST, before any other processing
            val isPlugin = isPluginException(thread, exception)
            
            if (isPlugin) {
                // Log plugin exception but don't crash the app
                Timber.e(
                    exception,
                    "Uncaught exception in plugin code (thread '${thread.name}'): ${exception.message}"
                )
                Timber.d("Stack trace for plugin exception:")
                exception.printStackTrace()
                
                // Try to notify PluginManager to set plugin state to ERROR
                try {
                    val pluginManagerEntryPoint = EntryPointAccessors.fromApplication(
                        this@ConnectiasApplication,
                        PluginManagerEntryPoint::class.java
                    )
                    val pluginManager = pluginManagerEntryPoint.pluginManager()
                    
                    // Find the plugin ID from the stack trace
                    val pluginId = extractPluginIdFromStackTrace(exception.stackTrace)
                    if (pluginId != null) {
                        val pluginInfo = pluginManager.getPlugin(pluginId)
                        if (pluginInfo != null) {
                            pluginInfo.state = PluginManagerSandbox.PluginState.ERROR
                            Timber.w("Plugin '$pluginId' set to ERROR state due to uncaught exception")
                        }
                    }
                } catch (e: Exception) {
                    // If we can't access PluginManager, just log the error
                    Timber.w(e, "Failed to update plugin state after exception")
                }
                
                // CRITICAL: For plugin exceptions, we MUST NOT call defaultHandler
                // This prevents the app from crashing
                // The exception has already interrupted the event dispatch, but we prevent crash
                if (thread == Looper.getMainLooper().thread) {
                    // For main thread exceptions, we need to IMMEDIATELY restore the main looper
                    // to prevent ANR (Application Not Responding)
                    try {
                        val mainHandler = android.os.Handler(Looper.getMainLooper())
                        
                        // CRITICAL: Post multiple messages to ensure the looper stays alive
                        // and can process input events again
                        mainHandler.post {
                            Timber.d("Main thread resumed after plugin exception - UI should be responsive again")
                        }
                        
                        // Post additional messages to keep the looper active
                        mainHandler.postDelayed({
                            Timber.d("Main thread still alive after plugin exception - checking UI responsiveness")
                        }, 50)
                        
                        mainHandler.postDelayed({
                            Timber.d("Main thread still alive after plugin exception - UI should be fully responsive")
                        }, 100)
                        
                        // CRITICAL: Navigate back to dashboard immediately to prevent ANR
                        // This removes the problematic plugin fragment and restores UI responsiveness
                        mainHandler.post {
                            try {
                                // Get the current activity (MainActivity)
                                val activities = try {
                                    val activityThreadClass = Class.forName("android.app.ActivityThread")
                                    val currentActivityThreadMethod = activityThreadClass.getMethod("currentActivityThread")
                                    val activityThread = currentActivityThreadMethod.invoke(null)
                                    val activitiesField = activityThreadClass.getDeclaredField("mActivities")
                                    activitiesField.isAccessible = true
                                    activitiesField.get(activityThread) as? java.util.Map<*, *>
                                } catch (e: Exception) {
                                    null
                                }
                                
                                activities?.values()?.firstOrNull()?.let { activityClientRecord ->
                                    val activityField = activityClientRecord.javaClass.getDeclaredField("activity")
                                    activityField.isAccessible = true
                                    val activity = activityField.get(activityClientRecord) as? android.app.Activity
                                    
                                    // Check if it's MainActivity and kill the plugin fragment immediately
                                    if (activity is MainActivity) {
                                        Timber.w("Plugin exception detected - killing plugin fragment immediately")
                                        // Extract plugin ID from stack trace
                                        val pluginId = extractPluginIdFromStackTrace(exception.stackTrace)
                                        if (pluginId != null) {
                                            // Immediately kill the plugin fragment (like Linux kill)
                                            activity.killPluginFragment(pluginId)
                                        } else {
                                            // Fallback: Just navigate to dashboard
                                            activity.navigateToDashboard(clearBackStack = true)
                                        }
                                    } else {
                                        // Fallback: Try to force layout pass for other activities
                                        activity?.window?.decorView?.rootView?.requestLayout()
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Could not navigate to dashboard after plugin exception: ${e.message}")
                            }
                        }
                        
                        // Post additional messages to keep the looper active
                        mainHandler.postDelayed({
                            Timber.d("Main thread still alive after plugin exception - UI should be responsive")
                        }, 50)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to resume main thread after plugin exception")
                    }
                    
                    // Log that we prevented the crash
                    Timber.w("Plugin exception on main thread - prevented crash and restored looper, UI should be responsive")
                    
                    // CRITICAL: We must NOT call the default handler for plugin exceptions
                    // This prevents the app from crashing
                    // The exception has been logged and the plugin state has been updated
                    // The main looper will continue to run, allowing the app to stay alive
                    // By not calling defaultHandler, we prevent the ANR from becoming a crash
                    return@setDefaultUncaughtExceptionHandler
                }
                
                // For non-main thread exceptions, also prevent crash
                // EXIT: Return immediately without calling defaultHandler
                // This is the key to preventing the crash
                return@setDefaultUncaughtExceptionHandler
            }
            
            // Only reach here for non-plugin exceptions
            // Delegate to default handler for non-plugin exceptions
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, exception)
            } else {
                // Fallback: use system default behavior (kill process)
                // This should rarely happen as Android usually sets a default handler
                Timber.e(exception, "No default uncaught exception handler, thread will terminate")
                System.exit(1)
            }
        }
    }
    
    /**
     * Extracts the plugin ID from the stack trace by finding plugin classes.
     * 
     * @param stackTrace The stack trace to analyze
     * @return The plugin ID if found, null otherwise
     */
    private fun extractPluginIdFromStackTrace(stackTrace: Array<StackTraceElement>): String? {
        for (element in stackTrace) {
            val className = element.className
            
            // Check for test plugin
            if (className.contains("com.ble1st.connectias.testplugin")) {
                return "com.ble1st.connectias.testplugin"
            }
            
            // Check for other plugin packages
            // Pattern: com.ble1st.connectias.*plugin* where the package name might be the plugin ID
            val pluginPackageMatch = Regex("com\\.ble1st\\.connectias\\.([^.]+plugin[^.]*)").find(className)
            if (pluginPackageMatch != null) {
                val packagePart = pluginPackageMatch.groupValues[1]
                // The plugin ID is typically the package name
                return "com.ble1st.connectias.$packagePart"
            }
        }
        return null
    }
    
    /**
     * Determines if an exception originates from plugin code.
     * 
     * Checks:
     * 1. Thread name contains "plugin" (case-insensitive)
     * 2. Stack trace contains plugin-related package names
     * 3. Stack trace contains classes that are not part of the main app
     * 4. Exception message contains plugin-related keywords
     * 
     * @param thread The thread where the exception occurred
     * @param exception The exception that occurred
     * @return true if the exception appears to originate from plugin code
     */
    private fun isPluginException(thread: Thread, exception: Throwable): Boolean {
        // Check thread name
        val threadName = thread.name.lowercase()
        if (threadName.contains("plugin")) {
            return true
        }
        
        // Check exception message for plugin-related keywords
        val exceptionMessage = exception.message?.lowercase() ?: ""
        if (exceptionMessage.contains("plugin") || exceptionMessage.contains("testplugin")) {
            return true
        }
        
        // Main app package prefixes (exclude these from plugin detection)
        // These are the known packages that are part of the main app, not plugins
        val mainAppPackages = setOf(
            "com.ble1st.connectias.core",
            "com.ble1st.connectias.ui",
            "com.ble1st.connectias.feature",
            "com.ble1st.connectias.common",
            "com.ble1st.connectias.di",
            "com.ble1st.connectias.performance",
            "com.ble1st.connectias.plugin.sdk", // Plugin SDK is part of main app
            // Plugin system classes (not plugin code itself)
            "com.ble1st.connectias.plugin.PluginManagerSandbox",
            "com.ble1st.connectias.plugin.PluginExceptionHandler",
            "com.ble1st.connectias.plugin.PluginContextImpl",
            "com.ble1st.connectias.plugin.PluginImportHandler",
            "com.ble1st.connectias.plugin.PluginNotificationManager",
            "com.ble1st.connectias.plugin.NativeLibraryManager",
            "com.ble1st.connectias.plugin.PluginModule"
        )
        
        // Check stack trace for plugin-related packages
        val stackTrace = exception.stackTrace
        for (element in stackTrace) {
            val className = element.className
            
            // Skip Android framework and library classes
            if (className.startsWith("android.") ||
                className.startsWith("androidx.") ||
                className.startsWith("kotlin.") ||
                className.startsWith("kotlinx.") ||
                className.startsWith("java.") ||
                className.startsWith("javax.") ||
                className.startsWith("dalvik.") ||
                className.startsWith("sun.") ||
                className.startsWith("jdk.")) {
                continue
            }
            
            // Check if this is a known main app package
            val isMainAppPackage = mainAppPackages.any { className.startsWith(it) }
            if (isMainAppPackage) {
                continue
            }
            
            // Check for plugin package patterns
            // Plugins might use package names like:
            // - com.ble1st.connectias.plugin.* (but not the plugin system itself)
            // - com.ble1st.connectias.testplugin (test plugin)
            // - com.ble1st.connectias.*plugin* (any package with "plugin" in name)
            if (className.contains("com.ble1st.connectias")) {
                // Check for test plugin specifically
                if (className.contains("com.ble1st.connectias.testplugin")) {
                    return true
                }
                
                // Check if it's plugin code (not plugin system)
                // Pattern: com.ble1st.connectias.*plugin* where *plugin* is not part of main app
                val pluginPackagePattern = Regex("com\\.ble1st\\.connectias\\.([^.]+plugin[^.]*)")
                val pluginMatch = pluginPackagePattern.find(className)
                if (pluginMatch != null) {
                    val packagePart = pluginMatch.groupValues[1]
                    // Make sure it's not a known main app package
                    val fullPackage = "com.ble1st.connectias.$packagePart"
                    if (!mainAppPackages.any { fullPackage.startsWith(it) }) {
                        return true
                    }
                }
            }
            
            // Check if class name contains "plugin" (case-insensitive) and is not part of main app
            if (className.lowercase().contains("plugin") && !isMainAppPackage) {
                // Additional check: make sure it's not a known main app class
                val isKnownMainAppClass = mainAppPackages.any { className.startsWith(it) }
                if (!isKnownMainAppClass) {
                    // Also check that it's not a standard library class
                    if (!className.startsWith("android.") &&
                        !className.startsWith("androidx.") &&
                        !className.startsWith("kotlin.") &&
                        !className.startsWith("java.")) {
                        return true
                    }
                }
            }
        }
        
        // Check cause chain
        var cause = exception.cause
        while (cause != null) {
            if (isPluginException(thread, cause)) {
                return true
            }
            cause = cause.cause
        }
        
        return false
    }
}

