package com.ble1st.connectias.plugin

import androidx.fragment.app.Fragment
import timber.log.Timber

/**
 * Central exception handler for plugin operations.
 * Ensures that plugin crashes don't crash the main app while providing
 * detailed logging and state management for faulty plugins.
 */
object PluginExceptionHandler {
    
    /**
     * Generic safe wrapper for plugin calls that return a value.
     * 
     * @param pluginId The ID of the plugin making the call
     * @param operationName Name of the operation (e.g., "onLoad", "onEnable")
     * @param onError Optional callback when an exception occurs
     * @param block The plugin operation to execute
     * @return The result of the operation, or null if an exception occurred
     */
    inline fun <T> safePluginCall(
        pluginId: String,
        operationName: String,
        noinline onError: ((Throwable) -> Unit)? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: OutOfMemoryError) {
            logException(pluginId, operationName, e, "OutOfMemoryError")
            onError?.invoke(e)
            null
        } catch (e: LinkageError) {
            logException(pluginId, operationName, e, "LinkageError")
            onError?.invoke(e)
            null
        } catch (e: ClassCastException) {
            logException(pluginId, operationName, e, "ClassCastException")
            onError?.invoke(e)
            null
        } catch (e: NullPointerException) {
            logException(pluginId, operationName, e, "NullPointerException")
            onError?.invoke(e)
            null
        } catch (e: IllegalArgumentException) {
            logException(pluginId, operationName, e, "IllegalArgumentException")
            onError?.invoke(e)
            null
        } catch (e: IllegalStateException) {
            logException(pluginId, operationName, e, "IllegalStateException")
            onError?.invoke(e)
            null
        } catch (e: RuntimeException) {
            logException(pluginId, operationName, e, "RuntimeException")
            onError?.invoke(e)
            null
        } catch (e: Error) {
            logException(pluginId, operationName, e, "Error")
            onError?.invoke(e)
            null
        } catch (e: Exception) {
            logException(pluginId, operationName, e, "Exception")
            onError?.invoke(e)
            null
        }
    }
    
    /**
     * Safe wrapper for plugin calls that return Boolean.
     * Used for lifecycle methods like onLoad, onEnable, onDisable.
     * 
     * @param pluginId The ID of the plugin making the call
     * @param operationName Name of the operation (e.g., "onLoad", "onEnable")
     * @param onError Optional callback when an exception occurs
     * @param block The plugin operation to execute
     * @return true if operation succeeded, false if exception occurred or returned false
     */
    inline fun safePluginBooleanCall(
        pluginId: String,
        operationName: String,
        noinline onError: ((Throwable) -> Unit)? = null,
        block: () -> Boolean
    ): Boolean {
        return safePluginCall(pluginId, operationName, onError, block) ?: false
    }
    
    /**
     * Safe wrapper for plugin calls that return a Fragment.
     * Used for creating plugin fragments.
     * 
     * @param pluginId The ID of the plugin making the call
     * @param operationName Name of the operation (typically "createFragment")
     * @param onError Optional callback when an exception occurs
     * @param block The plugin operation to execute
     * @return The Fragment instance, or null if an exception occurred
     */
    inline fun <T : Fragment> safePluginFragmentCall(
        pluginId: String,
        operationName: String,
        noinline onError: ((Throwable) -> Unit)? = null,
        block: () -> T?
    ): T? {
        return safePluginCall(pluginId, operationName, onError, block)
    }
    
    /**
     * Safe wrapper for Compose click handlers and event handlers.
     * Catches exceptions in event handlers and prevents app crashes.
     * 
     * @param pluginId The ID of the plugin
     * @param operationName Name of the operation (e.g., "onClick", "onEvent")
     * @param onError Optional callback when an exception occurs
     * @param block The event handler to execute
     */
    inline fun <T> safePluginEventHandler(
        pluginId: String,
        operationName: String,
        noinline onError: ((Throwable) -> Unit)? = null,
        block: () -> T
    ): T? {
        return safePluginCall(pluginId, operationName, onError, block)
    }
    
    /**
     * Safe wrapper for Compose click handlers that return Unit.
     * 
     * @param pluginId The ID of the plugin
     * @param operationName Name of the operation (e.g., "onClick")
     * @param onError Optional callback when an exception occurs
     * @param block The click handler to execute
     */
    inline fun safePluginClickHandler(
        pluginId: String,
        operationName: String = "onClick",
        noinline onError: ((Throwable) -> Unit)? = null,
        block: () -> Unit
    ) {
        safePluginCall<Unit>(pluginId, operationName, onError, block)
    }
    
    /**
     * Safe wrapper for Compose click handlers that can be used as a lambda.
     * This is a convenience function that wraps a click handler with exception handling.
     * 
     * Usage in Compose:
     * ```
     * Button(
     *     onClick = PluginExceptionHandler.safeComposeClickHandler(pluginId, "buttonClick") {
     *         // Your click handler code here
     *     }
     * )
     * ```
     * 
     * @param pluginId The ID of the plugin
     * @param operationName Name of the operation (e.g., "onClick")
     * @param onError Optional callback when an exception occurs
     * @param block The click handler to execute
     * @return A lambda that can be used as an onClick handler
     */
    inline fun safeComposeClickHandler(
        pluginId: String,
        operationName: String = "onClick",
        noinline onError: ((Throwable) -> Unit)? = null,
        noinline block: () -> Unit
    ): () -> Unit {
        return {
            safePluginClickHandler(pluginId, operationName, onError, block)
        }
    }
    
    /**
     * Logs an exception with detailed information.
     * 
     * @param pluginId The ID of the plugin
     * @param operationName Name of the operation that failed
     * @param exception The exception that occurred
     * @param exceptionType Type of exception for logging
     */
    fun logException(
        pluginId: String,
        operationName: String,
        exception: Throwable,
        exceptionType: String
    ) {
        Timber.e(
            exception,
            "Plugin exception in $pluginId.$operationName: [$exceptionType] ${exception.message}"
        )
        
        // Log stack trace for debugging
        Timber.d("Stack trace for plugin $pluginId.$operationName:")
        exception.printStackTrace()
    }
}
