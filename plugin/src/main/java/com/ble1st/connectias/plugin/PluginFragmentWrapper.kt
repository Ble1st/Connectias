package com.ble1st.connectias.plugin

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.ble1st.connectias.plugin.security.PluginThreadMonitor
import com.ble1st.connectias.plugin.security.PluginDataLeakageProtector
import timber.log.Timber

/**
 * Wrapper Fragment that protects the app from exceptions thrown by plugin fragments.
 * 
 * This wrapper catches all exceptions that occur in:
 * - Fragment lifecycle methods (onCreate, onCreateView, etc.)
 * - View creation and interaction
 * - Compose event handlers (click handlers, etc.)
 * 
 * When an exception occurs, it:
 * 1. Logs the exception
 * 2. Sets the plugin state to ERROR
 * 3. Shows an error message to the user
 * 4. Prevents the app from crashing
 * 
 * SECURITY: Before creating the plugin UI, checks if all required permissions are granted.
 * If permissions are missing, shows an error view instead of loading the plugin.
 */
class PluginFragmentWrapper(
    private val pluginId: String,
    private val wrappedFragment: Fragment,
    private val requiredPermissions: List<String> = emptyList(), // Permissions required by this plugin
    private val permissionManager: PluginPermissionManager? = null, // Permission manager for checking
    private val onError: ((Throwable) -> Unit)? = null,
    private val onCriticalError: (() -> Unit)? = null // Callback to navigate to dashboard
) : Fragment() {
    
    private var hasError = false
    private var lastException: Throwable? = null
    private var containerView: ViewGroup? = null
    
    // Unique container ID for the child fragment
    private val containerId = View.generateViewId()
    
    // Watchdog for detecting long-running operations (prevents ANR)
    // Use a separate thread to avoid being blocked by main thread
    private val watchdogThread = android.os.HandlerThread("PluginWatchdog-$pluginId").apply { start() }
    private val watchdogHandler = android.os.Handler(watchdogThread.looper)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val watchdogTimeoutMs = 3000L // 3 seconds (before ANR at 5 seconds)
    
    private val ongoingTouchEvents = java.util.concurrent.atomic.AtomicInteger(0)
    
    // Recursion detection to prevent stack overflow
    // Track recursion depth per thread to detect infinite loops
    private val recursionDepth = ThreadLocal.withInitial { 0 }
    private val maxRecursionDepth = 50 // Maximum allowed recursion depth before aborting
    
    // Flag to block all events when recursion limit is exceeded
    @Volatile
    private var blockAllEvents = false
    
    // Memory monitoring
    private val runtime = Runtime.getRuntime()
    private val memoryWarningThreshold = 0.85 // 85% heap usage
    private val memoryCriticalThreshold = 0.95 // 95% heap usage
    
    // Process management
    private var sandboxProcessId: Int? = null
    
    // Security monitoring
    private var securityMonitoringActive = false
    private val originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    private fun findSandboxProcess() {
        if (sandboxProcessId != null) return // Already found
        
        try {
            val context = requireContext()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.runningAppProcesses?.forEach { process ->
                if (process.processName.endsWith(":plugin_sandbox")) {
                    sandboxProcessId = process.pid
                    Timber.d("[WATCHDOG] Sandbox process found: PID ${process.pid}")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[WATCHDOG] Failed to get sandbox process ID")
        }
    }
    
    /**
     * Kills the sandbox process (hard kill)
     * WARNING: This forcibly terminates the sandbox process
     */
    private fun killSandboxProcess() {
        sandboxProcessId?.let { pid ->
            try {
                Timber.e("[WATCHDOG] KILLING sandbox process: PID $pid")
                android.os.Process.killProcess(pid)
                Timber.i("[WATCHDOG] Sandbox process killed successfully")
            } catch (e: Exception) {
                Timber.e(e, "[WATCHDOG] Failed to kill sandbox process")
            }
        } ?: run {
            Timber.w("[WATCHDOG] Cannot kill sandbox - PID unknown")
        }
    }
    
    /**
     * Emergency cleanup - destroys plugin UI and frees memory
     * This is the PRIMARY defense against OOM in main process
     * After cleanup, navigates back to dashboard
     */
    private fun emergencyCleanup(reason: String) {
        Timber.e("[WATCHDOG] EMERGENCY CLEANUP: $reason")
        
        try {
            // 1. Remove all views immediately to free UI memory
            containerView?.let { container ->
                try {
                    container.removeAllViews()
                    Timber.d("[WATCHDOG] Removed all views from container")
                } catch (e: Exception) {
                    Timber.e(e, "[WATCHDOG] Failed to remove views")
                }
            }
            
            // 2. Destroy child fragment to free fragment memory
            try {
                val childFragment = childFragmentManager.findFragmentByTag("wrapped_plugin_fragment")
                if (childFragment != null) {
                    childFragmentManager.beginTransaction()
                        .remove(childFragment)
                        .commitNowAllowingStateLoss()
                    Timber.d("[WATCHDOG] Removed child fragment")
                }
            } catch (e: Exception) {
                Timber.e(e, "[WATCHDOG] Failed to remove child fragment")
            }
            
            // 3. Clear wrapped fragment reference
            try {
                wrappedFragment.arguments = null
                Timber.d("[WATCHDOG] Cleared wrapped fragment args")
            } catch (e: Exception) {
                // Ignore
            }
            
            // 4. Force garbage collection in main process
            System.gc()
            System.runFinalization()
            Timber.d("[WATCHDOG] Forced GC in main process")
            
            // 5. Kill sandbox process (secondary defense)
            killSandboxProcess()
            
            // 6. Log memory state after cleanup
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val maxMemory = runtime.maxMemory() / (1024 * 1024)
            Timber.i("[WATCHDOG] Memory after cleanup: ${usedMemory}MB / ${maxMemory}MB")
            
            // 7. Notify MainActivity to navigate to dashboard
            Timber.i("[WATCHDOG] Requesting navigation to dashboard after cleanup")
            onCriticalError?.invoke()
            
        } catch (e: Exception) {
            Timber.e(e, "[WATCHDOG] Emergency cleanup failed")
        }
    }
    
    /**
     * Checks memory pressure and performs emergency cleanup if critical
     */
    private fun checkMemoryPressure(): Boolean {
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val usageRatio = usedMemory.toDouble() / maxMemory.toDouble()
        
        return when {
            usageRatio >= memoryCriticalThreshold -> {
                Timber.e("[WATCHDOG] CRITICAL memory pressure: ${(usageRatio * 100).toInt()}% (${usedMemory/(1024*1024)}MB / ${maxMemory/(1024*1024)}MB)")
                emergencyCleanup("Memory pressure critical: ${(usageRatio * 100).toInt()}%")
                true
            }
            usageRatio >= memoryWarningThreshold -> {
                Timber.w("[WATCHDOG] WARNING memory pressure: ${(usageRatio * 100).toInt()}% (${usedMemory/(1024*1024)}MB / ${maxMemory/(1024*1024)}MB)")
                false
            }
            else -> false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Initialize security monitoring
            initializeSecurityMonitoring()
            
            // Find the sandbox process for emergency shutdown
            findSandboxProcess()
            
            // Pass arguments to wrapped fragment
            wrappedFragment.arguments = arguments
        } catch (e: Exception) {
            handleException("onCreate", e)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            if (hasError) {
                // Show error view if plugin has crashed
                createErrorView()
            } else {
                // SECURITY: Check permissions BEFORE creating plugin UI
                // This prevents plugins from being displayed when they don't have required permissions
                val missingPermissions = checkPermissions()
                if (missingPermissions.isNotEmpty()) {
                    Timber.w("Plugin $pluginId: Cannot create UI - missing permissions: $missingPermissions")
                    
                    // Create a SecurityException with details about missing permissions
                    val securityException = SecurityException(
                        "Plugin '$pluginId' requires the following permissions: ${missingPermissions.joinToString(", ")}\n\n" +
                        "Please enable the plugin in Plugin Management to grant these permissions."
                    )
                    handleException("onCreateView", securityException)
                    return createErrorView()
                }
                
                // All permissions granted - proceed with UI creation
                Timber.d("Plugin $pluginId: All permissions granted, creating UI")
                
                // Create a container for the child fragment
                val fragmentContainer = android.widget.FrameLayout(requireContext()).apply {
                    id = containerId
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                
                // Add wrapped fragment as child fragment with container
                // This ensures the fragment has access to context
                if (savedInstanceState == null) {
                    childFragmentManager.beginTransaction()
                        .add(containerId, wrappedFragment, "wrapped_plugin_fragment")
                        .commitNowAllowingStateLoss()
                }
                
                containerView = fragmentContainer
                
                // Wrap the container to catch exceptions in event handlers
                wrapViewWithExceptionHandler(fragmentContainer)
            }
        } catch (e: Exception) {
            handleException("onCreateView", e)
            createErrorView()
        }
    }
    
    /**
     * Check if all required permissions are granted for this plugin
     * Returns list of missing permissions (empty if all granted)
     * 
     * SECURITY: This is a critical check that runs BEFORE the plugin UI is created
     * 
     * NOTE: Custom Permissions (plugin-specific permissions starting with plugin package name)
     * are IGNORED because they cannot be declared in the host app and are only relevant
     * to the plugin itself.
     */
    private fun checkPermissions(): List<String> {
        if (requiredPermissions.isEmpty()) {
            Timber.d("Plugin $pluginId: No permissions required")
            return emptyList()
        }
        
        val manager = permissionManager
        if (manager == null) {
            Timber.w("Plugin $pluginId: PermissionManager not provided - cannot check permissions")
            // If no permission manager, assume all permissions are missing (fail-safe)
            // But filter out custom permissions
            return requiredPermissions.filter { !isCustomPermission(it) }
        }
        
        // Filter out custom permissions (plugin-specific permissions)
        // Custom permissions start with the plugin's package name and cannot be declared in host app
        val standardPermissions = requiredPermissions.filter { !isCustomPermission(it) }
        
        if (standardPermissions.isEmpty()) {
            Timber.d("Plugin $pluginId: Only custom permissions requested - no check needed")
            return emptyList()
        }
        
        // Check each standard permission (ignore custom permissions)
        val missing = mutableListOf<String>()
        for (permission in standardPermissions) {
            if (!manager.isPermissionAllowed(pluginId, permission)) {
                Timber.d("Plugin $pluginId: Missing permission: $permission")
                missing.add(permission)
            }
        }
        
        return missing
    }
    
    /**
     * Check if a permission is a custom (plugin-specific) permission
     * Custom permissions typically start with the plugin's package name
     * and cannot be declared in the host app's manifest
     */
    private fun isCustomPermission(permission: String): Boolean {
        // Custom permissions are those that start with the plugin's package name
        // or contain the plugin ID in their name
        // Examples:
        // - com.ble1st.connectias.networkinfoplugin.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
        // - com.ble1st.connectias.testplugin.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
        return permission.contains(pluginId) || 
               permission.startsWith("com.ble1st.connectias.") && 
               !permission.startsWith("android.permission.")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        containerView = null
    }
    
    override fun onDestroy() {
        // Cleanup security monitoring
        cleanupSecurityMonitoring()
        
        // Stop watchdog
        watchdogHandler.removeCallbacksAndMessages(null)
        watchdogThread.quitSafely()
        
        super.onDestroy()
        
        Timber.d("[PLUGIN WRAPPER] Fragment destroyed: $pluginId")
    }
    
    /**
     * Initializes security monitoring for plugin UI fragments
     */
    private fun initializeSecurityMonitoring() {
        if (securityMonitoringActive) return
        
        try {
            // Register plugin for thread monitoring
            PluginThreadMonitor.registerPlugin(pluginId)
            
            // Register plugin for data leakage protection
            PluginDataLeakageProtector.registerPlugin(pluginId)
            
            // Set up thread monitoring with custom exception handler
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                // Track this thread for the plugin
                PluginThreadMonitor.trackThread(pluginId, thread)
                
                // Log the exception
                Timber.e(throwable, "[SECURITY] Uncaught exception in plugin thread: ${thread.name}")
                
                // Handle the exception
                handleException("uncaughtException-${thread.name}", throwable)
                
                // Call original handler if available
                originalUncaughtExceptionHandler?.uncaughtException(thread, throwable)
            }
            
            securityMonitoringActive = true
            Timber.i("[SECURITY] Security monitoring initialized for plugin: $pluginId")
            
        } catch (e: Exception) {
            Timber.e(e, "[SECURITY] Failed to initialize security monitoring for plugin: $pluginId")
        }
    }
    
    /**
     * Cleans up security monitoring for plugin UI fragments
     */
    private fun cleanupSecurityMonitoring() {
        if (!securityMonitoringActive) return
        
        try {
            // Unregister plugin from thread monitoring (this will kill background threads)
            PluginThreadMonitor.unregisterPlugin(pluginId)
            
            // Generate security report before cleanup
            val securityReport = PluginDataLeakageProtector.getSecurityReport(pluginId)
            if (securityReport.contains("SUSPICIOUS")) {
                Timber.w("[SECURITY] Plugin $pluginId had suspicious activity:\n$securityReport")
            }
            
            // Unregister plugin from data leakage protection
            PluginDataLeakageProtector.unregisterPlugin(pluginId)
            
            // Restore original exception handler
            Thread.setDefaultUncaughtExceptionHandler(originalUncaughtExceptionHandler)
            
            securityMonitoringActive = false
            Timber.i("[SECURITY] Security monitoring cleaned up for plugin: $pluginId")
            
        } catch (e: Exception) {
            Timber.e(e, "[SECURITY] Failed to cleanup security monitoring for plugin: $pluginId")
        }
    }
    
    /**
     * Wraps a view with an exception handler that catches exceptions in event handlers.
     * For ComposeView, we intercept touch events to catch exceptions before they crash the app.
     * 
     * This wraps the view in a custom ViewGroup that catches all exceptions from touch events,
     * including Compose click handlers and other event handlers.
     */
    private fun wrapViewWithExceptionHandler(view: View?): View? {
        if (view == null) return null
        
        // Create a wrapper ViewGroup that intercepts touch events
        val wrapper = object : android.widget.FrameLayout(requireContext()) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
                // If already in error state or events are blocked, consume all events immediately
                if (hasError || blockAllEvents) {
                    return true
                }
                
                // Check recursion depth to prevent stack overflow BEFORE it happens
                val currentDepth = recursionDepth.get() ?: 0
                
                // If recursion depth exceeds maximum, block all events and show error
                if (currentDepth >= maxRecursionDepth) {
                    Timber.e("Plugin $pluginId: Recursion depth exceeded ($currentDepth/$maxRecursionDepth) - preventing stack overflow")
                    
                    // Block all future events immediately
                    blockAllEvents = true
                    
                    // Emergency cleanup to free memory and stop plugin
                    emergencyCleanup("Recursion limit exceeded")
                    
                    // Handle error immediately on main thread
                    handleException("touchEvent", 
                        StackOverflowError("Recursion depth exceeded: $currentDepth (max: $maxRecursionDepth)"))
                    
                    // Show error view immediately
                    if (!hasError) {
                        hasError = true
                        try {
                            removeAllViews()
                            val errorView = createErrorView()
                            addView(errorView)
                            Timber.i("Plugin $pluginId: Error view displayed after recursion limit")
                        } catch (ex: Exception) {
                            Timber.e(ex, "Failed to show error view after recursion limit")
                        }
                    }
                    
                    // Consume event and prevent further propagation
                    return true
                }
                
                // Increment recursion depth
                recursionDepth.set(currentDepth + 1)
                
                // Track ongoing touch events
                val touchEventId = ongoingTouchEvents.incrementAndGet()
                
                // Capture reference to this wrapper for use in callback
                val wrapperView = this
                
                // Check memory pressure BEFORE processing event
                val memoryPressureCritical = checkMemoryPressure()
                if (memoryPressureCritical) {
                    blockAllEvents = true
                    hasError = true
                    emergencyCleanup("Memory pressure critical")
                    return true
                }
                
                // Start watchdog timer on separate thread to detect long-running operations (prevents ANR)
                val watchdog = Runnable {
                    // Check if this touch event is still ongoing
                    if (ongoingTouchEvents.get() == touchEventId) {
                        Timber.e("Plugin $pluginId: Touch event timeout (>${watchdogTimeoutMs}ms) - preventing ANR")
                        
                        // Check memory pressure
                        checkMemoryPressure()
                        
                        // Block all future events immediately
                        blockAllEvents = true
                        
                        // Post to main thread to update UI
                        mainHandler.post {
                            handleException("touchEvent", 
                                java.util.concurrent.TimeoutException("Plugin touch event exceeded ${watchdogTimeoutMs}ms"))
                            
                            // Emergency cleanup to free memory in main process
                            emergencyCleanup("Watchdog timeout")
                            
                            // Show error view immediately
                            if (!hasError) {
                                hasError = true
                                try {
                                    wrapperView.removeAllViews()
                                    val errorView = createErrorView()
                                    wrapperView.addView(errorView)
                                    Timber.i("Plugin $pluginId: Error view displayed after timeout")
                                } catch (ex: Exception) {
                                    Timber.e(ex, "Failed to show error view after timeout")
                                }
                            }
                        }
                    }
                }
                
                // Schedule watchdog on separate thread
                watchdogHandler.postDelayed(watchdog, watchdogTimeoutMs)
                
                return try {
                    val result = super.dispatchTouchEvent(ev)
                    // Cancel watchdog if event completed successfully
                    watchdogHandler.removeCallbacks(watchdog)
                    ongoingTouchEvents.set(0) // Reset
                    result
                } catch (e: Throwable) {
                    // Cancel watchdog
                    watchdogHandler.removeCallbacks(watchdog)
                    ongoingTouchEvents.set(0) // Reset
                    
                    // Block all future events immediately
                    blockAllEvents = true
                    
                    // Check if this is an OOM error
                    val isOOM = e is OutOfMemoryError || 
                                e.cause is OutOfMemoryError ||
                                e.message?.contains("OutOfMemory", ignoreCase = true) == true
                    
                    if (isOOM) {
                        Timber.e(e, "[WATCHDOG] OOM detected in plugin $pluginId - EMERGENCY CLEANUP")
                        emergencyCleanup("OutOfMemoryError detected")
                    } else {
                        Timber.e(e, "Exception in plugin touch event handler: $pluginId")
                        // Still do cleanup for other critical errors
                        emergencyCleanup("Exception: ${e.javaClass.simpleName}")
                    }
                    
                    handleException("touchEvent", e)
                    
                    // Show error view immediately
                    if (!hasError) {
                        hasError = true
                        // Replace the view with error view
                        try {
                            removeAllViews()
                            val errorView = createErrorView()
                            addView(errorView)
                        } catch (ex: Exception) {
                            Timber.e(ex, "Failed to show error view after touch event exception")
                        }
                    }
                    
                    // Return true to consume the event and prevent further propagation
                    true
                } finally {
                    // Decrement recursion depth after event is processed
                    val newDepth = (recursionDepth.get() ?: 1) - 1
                    if (newDepth <= 0) {
                        recursionDepth.set(0)
                    } else {
                        recursionDepth.set(newDepth)
                    }
                }
            }
            
            override fun onInterceptTouchEvent(ev: android.view.MotionEvent?): Boolean {
                // Don't intercept, just let dispatchTouchEvent handle exceptions
                return false
            }
        }
        
        // Add the original view to the wrapper
        wrapper.addView(view, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        
        return wrapper
    }
    
    /**
     * Creates an error view to display when the plugin crashes.
     */
    private fun createErrorView(): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // Simple error message - we can't use ConnectiasTheme here as it might
                // cause dependency issues. Just show a basic error message.
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val errorMessage = when (lastException) {
                            is SecurityException -> {
                                val permissionError = lastException as SecurityException
                                "Permission Denied\n\nThe plugin '$pluginId' tried to access a feature without permission.\n\n${permissionError.message}\n\nPlease grant the required permissions in Plugin Management."
                            }
                            else -> {
                                "Plugin Error\n\nThe plugin '$pluginId' encountered an error and has been disabled."
                            }
                        }
                        
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Handles an exception by logging it and notifying the error callback.
     * SecurityException gets special treatment with user-friendly messaging.
     */
    private fun handleException(operation: String, exception: Throwable) {
        hasError = true
        lastException = exception
        
        // Special handling for SecurityException (permission errors)
        if (exception is SecurityException) {
            Timber.e(exception, "Plugin $pluginId: Permission denied during $operation")
            PluginExceptionHandler.logException(
                pluginId, 
                operation, 
                exception, 
                "SecurityException (Permission Denied)"
            )
        } else {
            PluginExceptionHandler.logException(
                pluginId, 
                operation, 
                exception, 
                exception.javaClass.simpleName
            )
        }
        
        onError?.invoke(exception)
    }
}
