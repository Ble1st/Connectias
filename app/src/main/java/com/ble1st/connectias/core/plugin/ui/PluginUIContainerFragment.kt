// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ble1st.connectias.core.plugin.PluginUIProcessProxy
import com.ble1st.connectias.plugin.ui.MotionEventParcel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Container Fragment in Main Process that displays UI from UI Process.
 *
 * Three-Process Architecture:
 * - Main Process: This fragment acts as a container
 * - UI Process: Renders actual plugin UI via PluginUIFragment
 * - Sandbox Process: Provides UI state updates
 *
 * This fragment doesn't render UI itself - it's a placeholder that will
 * eventually display the UI from the UI Process (e.g., via VirtualDisplay or Surface).
 *
 * For now, this is a placeholder implementation. The actual UI rendering
 * happens in PluginUIFragment in the UI Process.
 */
class PluginUIContainerFragment : Fragment() {

    private var pluginId: String? = null
    private var containerId: Int = -1
    private var surfaceView: SurfaceView? = null
    private var uiProcessProxy: PluginUIProcessProxy? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var surfaceInitialized = false  // Track if surface was already sent

    /**
     * Sets the UI Process proxy for Surface communication.
     * This should be called before the fragment is displayed.
     */
    fun setUIProcessProxy(proxy: PluginUIProcessProxy) {
        this.uiProcessProxy = proxy
    }

    companion object {
        private const val ARG_PLUGIN_ID = "pluginId"
        private const val ARG_CONTAINER_ID = "containerId"

        /**
         * Creates a new instance of PluginUIContainerFragment.
         *
         * @param pluginId Plugin identifier
         * @param containerId Container ID from UI Process
         */
        fun newInstance(pluginId: String, containerId: Int): PluginUIContainerFragment {
            return PluginUIContainerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLUGIN_ID, pluginId)
                    putInt(ARG_CONTAINER_ID, containerId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pluginId = arguments?.getString(ARG_PLUGIN_ID)
        containerId = arguments?.getInt(ARG_CONTAINER_ID, -1) ?: -1
        Timber.i("[MAIN] PluginUIContainerFragment created for plugin: $pluginId (containerId: $containerId)")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.d("[MAIN] PluginUIContainerFragment onCreateView for plugin: $pluginId")
        
        // Create SurfaceView to display UI from UI Process
        surfaceView = SurfaceView(requireContext()).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    Timber.d("[MAIN] Surface created - waiting for surfaceChanged callback")
                    // Don't call requestUISurface() here - wait for surfaceChanged with actual dimensions
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    Timber.d("[MAIN] Surface changed: ${width}x${height}")
                    // Only send surface once when we have valid dimensions
                    if (width > 0 && height > 0 && !surfaceInitialized) {
                        surfaceInitialized = true
                        requestUISurface()
                    }
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Timber.d("[MAIN] Surface destroyed")
                    surfaceInitialized = false
                }
            })

            // Enable touch events
            setOnTouchListener { _, event ->
                handleTouchEvent(event)
            }
        }
        
        return surfaceView
    }

    /**
     * Requests UI Surface from UI Process and displays it.
     * Sends the SurfaceView's Surface to UI Process for VirtualDisplay rendering.
     */
    private fun requestUISurface() {
        val pluginId = this.pluginId ?: return
        val surface = surfaceView?.holder?.surface ?: return
        val proxy = uiProcessProxy ?: run {
            Timber.w("[MAIN] UI Process proxy not set - cannot send Surface")
            return
        }

        scope.launch {
            try {
                Timber.d("[MAIN] Sending Surface to UI Process for plugin: $pluginId")
                
                // Get Surface dimensions
                val width = surfaceView?.width ?: 1080
                val height = surfaceView?.height ?: 1920

                // Send Surface to UI Process
                val success = proxy.setUISurface(pluginId, surface, width, height)
                
                if (success) {
                    Timber.i("[MAIN] Surface sent to UI Process successfully for plugin: $pluginId")
                } else {
                    Timber.e("[MAIN] Failed to send Surface to UI Process for plugin: $pluginId")
                }
            } catch (e: Exception) {
                Timber.e(e, "[MAIN] Failed to send Surface to UI Process for plugin: $pluginId")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("[MAIN] PluginUIContainerFragment onResume for plugin: $pluginId")
        
        // Notify UI Process about lifecycle
        notifyUILifecycle("onResume")
    }

    override fun onPause() {
        super.onPause()
        Timber.d("[MAIN] PluginUIContainerFragment onPause for plugin: $pluginId")
        
        // Notify UI Process about lifecycle
        notifyUILifecycle("onPause")
    }

    override fun onStart() {
        super.onStart()
        Timber.d("[MAIN] PluginUIContainerFragment onStart for plugin: $pluginId")
        notifyUILifecycle("onStart")
    }

    override fun onStop() {
        super.onStop()
        Timber.d("[MAIN] PluginUIContainerFragment onStop for plugin: $pluginId")
        notifyUILifecycle("onStop")
    }

    /**
     * Notifies UI Process about lifecycle events.
     */
    private fun notifyUILifecycle(event: String) {
        val pluginId = this.pluginId ?: return
        val proxy = uiProcessProxy ?: return

        scope.launch {
            try {
                proxy.notifyUILifecycle(pluginId, event)
            } catch (e: Exception) {
                Timber.e(e, "[MAIN] Failed to notify UI lifecycle event: $event for plugin: $pluginId")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("[MAIN] PluginUIContainerFragment onDestroy for plugin: $pluginId")
        
        // Notify UI Process about destruction
        notifyUILifecycle("onDestroy")
        
        // Cleanup
        surfaceView = null
        uiProcessProxy = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Timber.d("[MAIN] PluginUIContainerFragment onDestroyView for plugin: $pluginId")
        surfaceView = null
        surfaceInitialized = false
    }

    /**
     * Handles touch events and forwards them to UI Process.
     *
     * @param event MotionEvent from SurfaceView
     * @return True if event was handled
     */
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        val pluginId = this.pluginId ?: return false
        val proxy = uiProcessProxy ?: return false

        // Convert MotionEvent to MotionEventParcel
        val motionEventParcel = MotionEventParcel().apply {
            action = event.action
            x = event.x
            y = event.y
            eventTime = event.eventTime
            downTime = event.downTime
            pressure = event.pressure
            size = event.size
            metaState = event.metaState
            buttonState = event.buttonState
            deviceId = event.deviceId
            edgeFlags = event.edgeFlags
            source = event.source
            flags = event.flags
        }

        // Forward to UI Process asynchronously
        scope.launch {
            try {
                val consumed = proxy.dispatchTouchEvent(pluginId, motionEventParcel)
                if (consumed) {
                    Timber.v("[MAIN] Touch event consumed by plugin: $pluginId")
                }
            } catch (e: Exception) {
                Timber.e(e, "[MAIN] Failed to dispatch touch event for plugin: $pluginId")
            }
        }

        // Return true to indicate we're handling the event
        return true
    }
}
