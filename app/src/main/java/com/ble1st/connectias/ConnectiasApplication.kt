package com.ble1st.connectias

import android.app.Application
import com.ble1st.connectias.core.logging.LoggingTreeEntryPoint
import com.ble1st.connectias.performance.StrictModeConfig
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

@HiltAndroidApp
class ConnectiasApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Phase 8: Enable StrictMode for performance monitoring in Debug builds
        StrictModeConfig.enableStrictMode(BuildConfig.DEBUG)
        
        // Initialize Timber for logging
        // Get ConnectiasLoggingTree via Hilt EntryPoint
        val loggingTree = EntryPointAccessors.fromApplication(
            this,
            LoggingTreeEntryPoint::class.java
        ).loggingTree()
        
        // Plant database logging tree first (for production)
        Timber.plant(loggingTree)
        
        // Also plant debug tree in debug builds for logcat output
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Set up global uncaught exception handler for plugin threads
        setupPluginExceptionHandler()
        
        Timber.d("ConnectiasApplication initialized")
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
            // Check if exception originates from plugin code
            if (isPluginException(thread, exception)) {
                // Log plugin exception but don't crash the app
                Timber.e(
                    exception,
                    "Uncaught exception in plugin thread '${thread.name}': ${exception.message}"
                )
                Timber.d("Stack trace for plugin thread '${thread.name}':")
                exception.printStackTrace()
                
                // Optionally, you could notify the PluginManager here to set plugin state to ERROR
                // For now, we just log and continue
            } else {
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
    }
    
    /**
     * Determines if an exception originates from plugin code.
     * 
     * Checks:
     * 1. Thread name contains "plugin" (case-insensitive)
     * 2. Stack trace contains plugin-related package names
     * 3. Stack trace contains classes loaded by DexClassLoader (plugin classes)
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
        
        // Check stack trace for plugin-related packages
        val stackTrace = exception.stackTrace
        for (element in stackTrace) {
            val className = element.className
            
            // Check for plugin package patterns
            // Plugins might use package names like:
            // - com.ble1st.connectias.plugin.* (plugin SDK)
            // - Custom plugin packages (hard to detect, but we can check for DexClassLoader usage)
            if (className.contains("com.ble1st.connectias.plugin") &&
                !className.contains("com.ble1st.connectias.plugin.sdk") &&
                !className.contains("com.ble1st.connectias.plugin.PluginManager") &&
                !className.contains("com.ble1st.connectias.plugin.PluginExceptionHandler") &&
                !className.contains("com.ble1st.connectias.plugin.PluginContextImpl")) {
                // This is likely plugin code (not the plugin system itself)
                return true
            }
            
            // Check if class was loaded by DexClassLoader (indicates plugin class)
            // This is harder to detect, but we can check for common plugin patterns
            // Note: We can't directly check the ClassLoader, but we can infer from package structure
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

