// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import com.ble1st.connectias.core.plugin.ui.PluginUIService
import com.ble1st.connectias.plugin.ui.IPluginUIBridge
import com.ble1st.connectias.plugin.ui.IPluginUIMainCallback
import com.ble1st.connectias.plugin.ui.IPluginUIHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Proxy for communicating with the Plugin UI Process via IPC.
 *
 * Three-Process Architecture:
 * - Main Process: Uses this proxy to manage UI Process lifecycle
 * - UI Process: Runs PluginUIService, renders plugin UI fragments
 * - Sandbox Process: Sends UI state updates via IPluginUIController
 *
 * This proxy:
 * - Manages connection to PluginUIService (in :plugin_ui process)
 * - Provides methods to initialize/destroy plugin UI
 * - Handles IPC errors and reconnection
 *
 * Connection lifecycle:
 * 1. connect() binds to PluginUIService
 * 2. initializePluginUI() creates fragment for plugin
 * 3. destroyPluginUI() removes fragment
 * 4. disconnect() unbinds service
 */
class PluginUIProcessProxy(
    private val context: Context
) {

    private var uiHostService: IPluginUIHost? = null
    private val isConnected = AtomicBoolean(false)
    private val connectionLock = Any()

    companion object {
        private const val BIND_TIMEOUT_MS = 5000L
        private const val IPC_TIMEOUT_MS = 3000L
    }

    /**
     * ServiceConnection for UI Process.
     */
    private val uiServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Timber.i("[MAIN] Connected to Plugin UI Process")
            uiHostService = IPluginUIHost.Stub.asInterface(service)
            isConnected.set(true)
            synchronized(connectionLock) {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                (connectionLock as Object).notifyAll()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.w("[MAIN] Disconnected from Plugin UI Process")
            uiHostService = null
            isConnected.set(false)
        }

        override fun onBindingDied(name: ComponentName) {
            Timber.e("[MAIN] Plugin UI Process binding died")
            uiHostService = null
            isConnected.set(false)
        }
    }

    /**
     * Connects to the UI Process.
     *
     * @return Result indicating success or failure
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isConnected.get()) {
                Timber.d("[MAIN] UI Process already connected")
                return@withContext Result.success(Unit)
            }

            Timber.i("[MAIN] Connecting to UI Process...")

            val intent = Intent(context, PluginUIService::class.java)
            val bindSuccess = context.bindService(
                intent,
                uiServiceConnection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
            )

            if (!bindSuccess) {
                val error = Exception("Failed to bind to UI Process")
                Timber.e(error, "[MAIN] UI Process bind failed")
                return@withContext Result.failure(error)
            }

            // Wait for connection with timeout
            val startTime = System.currentTimeMillis()
            synchronized(connectionLock) {
                while (!isConnected.get()) {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime >= BIND_TIMEOUT_MS) {
                        val error = Exception("UI Process connection timeout")
                        Timber.e(error, "[MAIN] Connection timeout after ${BIND_TIMEOUT_MS}ms")
                        context.unbindService(uiServiceConnection)
                        return@withContext Result.failure(error)
                    }
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    (connectionLock as Object).wait(BIND_TIMEOUT_MS - elapsedTime)
                }
            }

            Timber.i("[MAIN] UI Process connected successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Failed to connect to UI Process")
            Result.failure(e)
        }
    }

    /**
     * Disconnects from the UI Process.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            if (isConnected.get()) {
                context.unbindService(uiServiceConnection)
                uiHostService = null
                isConnected.set(false)
                Timber.i("[MAIN] Disconnected from UI Process")
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error disconnecting from UI Process")
        }
    }

    /**
     * Initializes plugin UI in the UI Process.
     *
     * Creates a PluginUIFragment for the specified plugin.
     *
     * @param pluginId Plugin identifier
     * @param configuration Bundle with UI configuration (theme, locale, etc.)
     * @return Container ID for the fragment, or -1 on error
     */
    suspend fun initializePluginUI(
        pluginId: String,
        configuration: Bundle = Bundle.EMPTY
    ): Int = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) {
                Timber.e("[MAIN] Cannot initialize plugin UI - not connected to UI Process")
                return@withContext -1
            }

            Timber.d("[MAIN] Initializing plugin UI: $pluginId")

            withTimeout(IPC_TIMEOUT_MS) {
                val containerId = uiHostService?.initializePluginUI(pluginId, configuration) ?: -1
                if (containerId != -1) {
                    Timber.i("[MAIN] Plugin UI initialized: $pluginId (container: $containerId)")
                } else {
                    Timber.e("[MAIN] Failed to initialize plugin UI: $pluginId")
                }
                containerId
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error initializing plugin UI: $pluginId")
            -1
        }
    }

    /**
     * Destroys plugin UI in the UI Process.
     *
     * Removes the PluginUIFragment for the specified plugin.
     *
     * @param pluginId Plugin identifier
     * @return True if destroyed successfully
     */
    suspend fun destroyPluginUI(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) {
                Timber.w("[MAIN] Cannot destroy plugin UI - not connected to UI Process")
                return@withContext false
            }

            Timber.d("[MAIN] Destroying plugin UI: $pluginId")

            withTimeout(IPC_TIMEOUT_MS) {
                uiHostService?.destroyPluginUI(pluginId)
                Timber.i("[MAIN] Plugin UI destroyed: $pluginId")
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error destroying plugin UI: $pluginId")
            false
        }
    }

    /**
     * Sets UI visibility for a plugin.
     *
     * @param pluginId Plugin identifier
     * @param visible True to show, false to hide
     * @return True if visibility changed successfully
     */
    suspend fun setUIVisibility(pluginId: String, visible: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) {
                Timber.w("[MAIN] Cannot set UI visibility - not connected to UI Process")
                return@withContext false
            }

            Timber.d("[MAIN] Setting UI visibility: $pluginId -> $visible")

            withTimeout(IPC_TIMEOUT_MS) {
                uiHostService?.setUIVisibility(pluginId, visible)
                Timber.v("[MAIN] UI visibility set: $pluginId -> $visible")
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error setting UI visibility: $pluginId")
            false
        }
    }

    /**
     * Checks if the UI Process is ready to accept requests.
     *
     * @return True if ready
     */
    suspend fun isUIProcessReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) {
                return@withContext false
            }

            withTimeout(IPC_TIMEOUT_MS) {
                uiHostService?.isUIProcessReady() ?: false
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error checking UI Process ready state")
            false
        }
    }

    /**
     * Sets the UI Bridge that receives user events from UI Process.
     *
     * This bridge is forwarded to the Sandbox Process.
     *
     * @param uiBridge IPluginUIBridge from Sandbox Process
     * @return True if registered successfully
     */
    suspend fun registerUIBridge(uiBridge: IPluginUIBridge): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) {
                Timber.w("[MAIN] Cannot register UI Bridge - not connected to UI Process")
                return@withContext false
            }

            Timber.d("[MAIN] Registering UI Bridge with UI Process")

            withTimeout(IPC_TIMEOUT_MS) {
                uiHostService?.registerUICallback(uiBridge.asBinder())
                Timber.i("[MAIN] UI Bridge registered with UI Process")
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error registering UI Bridge")
            false
        }
    }

    /**
     * Sets a Surface from Main Process for UI rendering in UI Process.
     * The UI Process will use this Surface for VirtualDisplay rendering.
     *
     * @param pluginId Plugin identifier
     * @param surface Surface from Main Process (SurfaceView)
     * @param width Surface width in pixels
     * @param height Surface height in pixels
     * @return True if Surface was set successfully
     */
    suspend fun setUISurface(
        pluginId: String,
        surface: android.view.Surface,
        width: Int,
        height: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) {
                Timber.w("[MAIN] Cannot set UI Surface - not connected to UI Process")
                return@withContext false
            }

            Timber.d("[MAIN] Setting UI Surface for plugin: $pluginId (${width}x${height})")

            withTimeout(IPC_TIMEOUT_MS) {
                val success = uiHostService?.setUISurface(pluginId, surface, width, height) ?: false
                if (success) {
                    Timber.i("[MAIN] UI Surface set successfully for plugin: $pluginId")
                } else {
                    Timber.e("[MAIN] Failed to set UI Surface for plugin: $pluginId")
                }
                success
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error setting UI Surface for plugin: $pluginId")
            false
        }
    }

    /**
     * Dispatches a touch event to the UI Process.
     * The UI Process will forward it to the Sandbox Process.
     *
     * @param pluginId Plugin identifier
     * @param motionEvent MotionEvent data as parcel
     * @return True if event was consumed
     */
    suspend fun dispatchTouchEvent(
        pluginId: String,
        motionEvent: com.ble1st.connectias.plugin.ui.MotionEventParcel
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) {
                Timber.w("[MAIN] Cannot dispatch touch event - not connected to UI Process")
                return@withContext false
            }

            Timber.v("[MAIN] Dispatching touch event for plugin: $pluginId (action: ${motionEvent.action})")

            withTimeout(IPC_TIMEOUT_MS) {
                uiHostService?.dispatchTouchEvent(pluginId, motionEvent) ?: false
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error dispatching touch event for plugin: $pluginId")
            false
        }
    }

    /**
     * Notifies UI Process about lifecycle events from Main Process.
     *
     * @param pluginId Plugin identifier
     * @param event Lifecycle event (onStart, onResume, onPause, onStop, onDestroy)
     */
    suspend fun notifyUILifecycle(
        pluginId: String,
        event: String
    ): Unit = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) {
                Timber.w("[MAIN] Cannot notify UI lifecycle - not connected to UI Process")
                return@withContext
            }

            Timber.v("[MAIN] Notifying UI lifecycle: $pluginId -> $event")

            withTimeout(IPC_TIMEOUT_MS) {
                uiHostService?.notifyUILifecycle(pluginId, event)
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error notifying UI lifecycle for plugin: $pluginId")
        }
    }

    /**
     * Registers the Main Process callback for IME (keyboard) proxy.
     * When a TextField in the UI Process gains focus, the UI Process will call
     * requestShowIme() on this callback so the Main Process can show the keyboard.
     *
     * @param callback IPluginUIMainCallback implemented by Main Process (e.g. PluginUIContainerFragment)
     * @return True if registered successfully
     */
    suspend fun registerMainCallback(callback: IPluginUIMainCallback): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) {
                Timber.w("[MAIN] Cannot register Main callback - not connected to UI Process")
                return@withContext false
            }
            Timber.d("[MAIN] Registering Main callback (IME proxy) with UI Process")
            withTimeout(IPC_TIMEOUT_MS) {
                uiHostService?.registerMainCallback(callback.asBinder())
                Timber.i("[MAIN] Main callback registered with UI Process")
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error registering Main callback")
            false
        }
    }

    /**
     * Sends text from Main Process to UI Process (user typed in overlay EditText).
     * Updates the TextField state in the plugin UI.
     *
     * @param pluginId Plugin identifier
     * @param componentId UI component ID (TextField id)
     * @param text Current text from the overlay
     */
    suspend fun sendImeText(pluginId: String, componentId: String, text: String): Unit = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) return@withContext
            withTimeout(IPC_TIMEOUT_MS) {
                uiHostService?.sendImeText(pluginId, componentId, text)
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error sending IME text for plugin: $pluginId component: $componentId")
        }
    }

    /**
     * Notifies UI Process that the IME was dismissed in Main Process.
     *
     * @param pluginId Plugin identifier
     * @param componentId UI component ID (TextField id)
     */
    suspend fun onImeDismissed(pluginId: String, componentId: String): Unit = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) return@withContext
            withTimeout(IPC_TIMEOUT_MS) {
                uiHostService?.onImeDismissed(pluginId, componentId)
            }
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Error notifying IME dismissed for plugin: $pluginId component: $componentId")
        }
    }

    /**
     * Checks if proxy is connected to UI Process.
     */
    fun isConnected(): Boolean = isConnected.get()

    /**
     * Gets the UI Host service interface (for advanced usage).
     */
    fun getUIHost(): IPluginUIHost? = uiHostService
}
