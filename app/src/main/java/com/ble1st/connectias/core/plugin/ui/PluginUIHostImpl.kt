// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import com.ble1st.connectias.plugin.ui.IPluginUIHost
import com.ble1st.connectias.plugin.ui.MotionEventParcel
import timber.log.Timber

/**
 * Implementation of IPluginUIHost.
 *
 * Manages plugin UI lifecycle in the UI Process.
 * Called by Main Process to initialize/destroy plugin UI.
 */
class PluginUIHostImpl(
    private val context: Context,
    private val fragmentRegistry: MutableMap<String, PluginUIFragment>,
    private val uiController: PluginUIControllerUIProcess,
    private val virtualDisplayManager: VirtualDisplayManager
) : IPluginUIHost.Stub() {

    private var uiCallback: IBinder? = null
    private val initialized = mutableSetOf<String>()

    override fun initializePluginUI(
        pluginId: String,
        configuration: Bundle
    ): Int {
        Timber.i("[UI_PROCESS] Initialize UI for plugin: $pluginId")

        try {
            // Check if already initialized - if so, clean up and reinitialize
            val existingFragment = fragmentRegistry[pluginId]
            if (initialized.contains(pluginId) || existingFragment != null) {
                Timber.i("[UI_PROCESS] Plugin UI already initialized: $pluginId - cleaning up and reinitializing")

                // Clean up old state completely
                try {
                    // Release VirtualDisplay first
                    virtualDisplayManager.releaseVirtualDisplay(pluginId)

                    // Destroy fragment (this will also finish the Activity)
                    existingFragment?.destroy()

                    // Remove from registries
                    fragmentRegistry.remove(pluginId)
                    initialized.remove(pluginId)

                    // Wait a bit for Activity to finish and resources to be released
                    Thread.sleep(100)

                    Timber.d("[UI_PROCESS] Old plugin UI state cleaned up for: $pluginId")
                } catch (e: Exception) {
                    Timber.e(e, "[UI_PROCESS] Error cleaning up old plugin UI state: $pluginId")
                }
            }

            // Create new fragment for plugin
            val fragment = PluginUIFragment.newInstance(pluginId, configuration)
            fragmentRegistry[pluginId] = fragment
            initialized.add(pluginId)

            // Set UI Bridge if already registered
            if (uiCallback != null) {
                try {
                    val uiBridge = com.ble1st.connectias.plugin.ui.IPluginUIBridge.Stub.asInterface(uiCallback)
                    fragment.setUIBridge(uiBridge)
                    Timber.d("[UI_PROCESS] UI Bridge set for new fragment: $pluginId")
                } catch (e: Exception) {
                    Timber.e(e, "[UI_PROCESS] Failed to set UI Bridge for fragment: $pluginId")
                }
            }

            // Apply any pending UI state that arrived before fragment creation
            uiController.applyPendingState(pluginId, fragment)

            // Start Activity to host the fragment
            // Note: Activity will be started in background and fragment will be added there
            val activityIntent = PluginUIActivity.createIntent(
                pluginId = pluginId,
                containerId = pluginId.hashCode(),
                createSurface = false
            )
            activityIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(activityIntent)

            // Generate unique fragment container ID
            val containerId = pluginId.hashCode()

            Timber.d("[UI_PROCESS] Plugin UI initialized: $pluginId -> containerId=$containerId (Activity started)")
            return containerId
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to initialize plugin UI: $pluginId")
            return -1
        }
    }

    override fun destroyPluginUI(pluginId: String) {
        Timber.i("[UI_PROCESS] Destroy UI for plugin: $pluginId")

        // Release VirtualDisplay
        virtualDisplayManager.releaseVirtualDisplay(pluginId)

        fragmentRegistry.remove(pluginId)?.let { fragment ->
            try {
                // IMPORTANT: Save current UI state before destroying fragment
                // This allows re-initialization to use the last known state
                val currentState = fragment.getCurrentState()
                if (currentState != null) {
                    // State will be cached in PluginUIControllerUIProcess.lastUIStates
                    // via updateUIState calls, so we don't need to do anything here
                    Timber.d("[UI_PROCESS] Fragment has current state - will be available for re-initialization: $pluginId")
                }
                
                fragment.destroy()
                initialized.remove(pluginId)
                Timber.d("[UI_PROCESS] Plugin UI destroyed: $pluginId")
            } catch (e: Exception) {
                Timber.e(e, "[UI_PROCESS] Error destroying plugin UI: $pluginId")
            }
        } ?: run {
            Timber.w("[UI_PROCESS] Plugin UI not found: $pluginId")
        }
    }

    override fun setUIVisibility(pluginId: String, visible: Boolean) {
        Timber.d("[UI_PROCESS] Set UI visibility: $pluginId -> $visible")

        fragmentRegistry[pluginId]?.let { fragment ->
            fragment.setVisibility(visible)
        } ?: run {
            Timber.w("[UI_PROCESS] Cannot set visibility - plugin UI not found: $pluginId")
        }
    }

    override fun isUIProcessReady(): Boolean {
        // Service is running, so UI process is ready
        val ready = true
        Timber.v("[UI_PROCESS] UI process ready check: $ready")
        return ready
    }

    override fun registerUICallback(callback: IBinder?) {
        Timber.d("[UI_PROCESS] Register UI callback: ${callback != null}")
        this.uiCallback = callback
        
        // Convert IBinder to IPluginUIBridge and set it for all active fragments
        if (callback != null) {
            try {
                val uiBridge = com.ble1st.connectias.plugin.ui.IPluginUIBridge.Stub.asInterface(callback)
                fragmentRegistry.values.forEach { fragment ->
                    fragment.setUIBridge(uiBridge)
                }
                Timber.i("[UI_PROCESS] UI Bridge set for ${fragmentRegistry.size} active fragments")
            } catch (e: Exception) {
                Timber.e(e, "[UI_PROCESS] Failed to convert UI callback to IPluginUIBridge")
            }
        }
    }

    override fun getUIController(): IBinder {
        Timber.d("[UI_PROCESS] Get UI Controller")
        return uiController.asBinder()
    }

    override fun setUISurface(
        pluginId: String,
        surface: android.view.Surface,
        width: Int,
        height: Int
    ): Boolean {
        Timber.d("[UI_PROCESS] Set UI Surface for plugin: $pluginId (${width}x${height})")

        return try {
            val fragment = fragmentRegistry[pluginId]
            if (fragment == null) {
                Timber.w("[UI_PROCESS] Fragment not found for plugin: $pluginId")
                return false
            }

            // Create VirtualDisplay using the Surface from Main Process and render fragment on it
            val virtualDisplay = virtualDisplayManager.createVirtualDisplayWithSurface(
                pluginId,
                surface,
                width,
                height,
                fragment  // Pass fragment to render its Compose UI on VirtualDisplay
            )

            if (virtualDisplay == null) {
                Timber.e("[UI_PROCESS] Failed to create VirtualDisplay with Surface for plugin: $pluginId")
                return false
            }

            Timber.i("[UI_PROCESS] Surface set and VirtualDisplay created for plugin: $pluginId")
            true
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to set Surface for plugin: $pluginId")
            false
        }
    }

    override fun getUISurface(pluginId: String, width: Int, height: Int): android.view.Surface? {
        Timber.w("[UI_PROCESS] getUISurface() is deprecated - use setUISurface() instead")
        // Return null - Surface cannot be transferred via IPC
        return null
    }

    /**
     * Get fragment for a plugin (internal use).
     */
    fun getFragment(pluginId: String): PluginUIFragment? {
        return fragmentRegistry[pluginId]
    }

    /**
     * Get all active plugin IDs.
     */
    fun getActivePlugins(): Set<String> {
        return fragmentRegistry.keys.toSet()
    }

    override fun dispatchTouchEvent(pluginId: String, motionEvent: MotionEventParcel): Boolean {
        Timber.d("[UI_PROCESS] Dispatch touch event for plugin: $pluginId (action: ${motionEvent.action})")

        return try {
            // Forward touch event to Sandbox Process via UI Bridge
            val uiBridge = uiCallback?.let {
                try {
                    com.ble1st.connectias.plugin.ui.IPluginUIBridge.Stub.asInterface(it)
                } catch (e: Exception) {
                    Timber.e(e, "[UI_PROCESS] Failed to convert UI callback to IPluginUIBridge")
                    null
                }
            }

            if (uiBridge != null) {
                // Convert MotionEventParcel to UserActionParcel for Sandbox
                val userAction = com.ble1st.connectias.plugin.ui.UserActionParcel().apply {
                    actionType = when (motionEvent.action) {
                        android.view.MotionEvent.ACTION_DOWN -> "touch_down"
                        android.view.MotionEvent.ACTION_UP -> "touch_up"
                        android.view.MotionEvent.ACTION_MOVE -> "touch_move"
                        android.view.MotionEvent.ACTION_CANCEL -> "touch_cancel"
                        else -> "touch_unknown"
                    }
                    targetId = "surface" // Touch on surface
                    data = Bundle().apply {
                        putFloat("x", motionEvent.x)
                        putFloat("y", motionEvent.y)
                        putInt("action", motionEvent.action)
                        putLong("eventTime", motionEvent.eventTime)
                        putLong("downTime", motionEvent.downTime)
                        putFloat("pressure", motionEvent.pressure)
                    }
                    timestamp = motionEvent.eventTime
                }

                uiBridge.onUserAction(pluginId, userAction)
                Timber.v("[UI_PROCESS] Touch event forwarded to Sandbox for plugin: $pluginId")
                true
            } else {
                Timber.w("[UI_PROCESS] UI Bridge not available - touch event dropped")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to dispatch touch event for plugin: $pluginId")
            false
        }
    }

    override fun notifyUILifecycle(pluginId: String, event: String) {
        Timber.d("[UI_PROCESS] Notify UI lifecycle: $pluginId -> $event")

        try {
            // Forward lifecycle event to Sandbox Process via UI Bridge
            val uiBridge = uiCallback?.let {
                try {
                    com.ble1st.connectias.plugin.ui.IPluginUIBridge.Stub.asInterface(it)
                } catch (e: Exception) {
                    Timber.e(e, "[UI_PROCESS] Failed to convert UI callback to IPluginUIBridge")
                    null
                }
            }

            if (uiBridge != null) {
                uiBridge.onLifecycleEvent(pluginId, event)
                Timber.v("[UI_PROCESS] Lifecycle event forwarded to Sandbox: $pluginId -> $event")
            } else {
                Timber.w("[UI_PROCESS] UI Bridge not available - lifecycle event dropped")
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to notify lifecycle event for plugin: $pluginId")
        }
    }
}
