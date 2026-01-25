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
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    private var rootContainer: FrameLayout? = null
    private var uiProcessProxy: PluginUIProcessProxy? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastSentWidth: Int = -1
    private var lastSentHeight: Int = -1

    /**
     * Gets the plugin ID for this fragment.
     */
    fun getPluginId(): String? = pluginId

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

        // Edge-to-edge: MainActivity consumes insets at root.
        // This fragment must apply its own insets so the SurfaceView matches the visible viewport
        // (otherwise content can render under status/navigation bars and look "too big").
        rootContainer = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer!!) { v, insets ->
            // Plugin UI should be truly fullscreen (immersive): do NOT reserve space for system bars.
            // System bars can appear transiently (swipe); they will overlay content.
            v.setPadding(0, 0, 0, 0)
            insets
        }

        // Create SurfaceView to display UI from UI Process
        // IMPORTANT: Set Z-order so SurfaceView appears BELOW FAB overlay
        // By default, SurfaceView renders in its own layer above other views
        surfaceView = SurfaceView(requireContext()).apply {
            // Set Z-order to media overlay level (below normal views)
            // This ensures FAB overlay from MainActivity appears above the SurfaceView
            setZOrderMediaOverlay(false)
            // Set low elevation to ensure it stays below FAB overlay
            elevation = 0f
            z = 0f
            
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
                    val viewWidth = this@apply.width
                    val viewHeight = this@apply.height

                    Timber.d("[MAIN] Surface changed: buffer=${width}x${height}, view=${viewWidth}x${viewHeight}")

                    // Ensure Surface buffer matches the actual view size to avoid scaling/cropping.
                    // If buffer differs, request a fixed size and wait for next surfaceChanged.
                    if (viewWidth > 0 && viewHeight > 0 && (width != viewWidth || height != viewHeight)) {
                        Timber.d("[MAIN] Adjusting Surface buffer size to match view: ${viewWidth}x${viewHeight}")
                        holder.setFixedSize(viewWidth, viewHeight)
                        return
                    }

                    // Send surface when we have valid dimensions (and whenever size changes).
                    if (width > 0 && height > 0 && (width != lastSentWidth || height != lastSentHeight)) {
                        lastSentWidth = width
                        lastSentHeight = height
                        requestUISurface(width, height)
                        
                        // IMPORTANT: Ensure FAB stays on top after SurfaceView is created
                        // Post to ensure it happens after SurfaceView is laid out
                        view?.post {
                            val activity = activity
                            if (activity != null) {
                                val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                                root?.post {
                                    // Find ComposeView by tag or by type
                                    var composeView: android.view.View? = null
                                    for (i in 0 until root.childCount) {
                                        val child = root.getChildAt(i)
                                        if (child.tag == "fab_overlay" || 
                                            child.javaClass.simpleName == "ComposeView") {
                                            composeView = child
                                            break
                                        }
                                    }
                                    
                                    composeView?.let {
                                        it.bringToFront()
                                        it.z = Float.MAX_VALUE
                                        Timber.d("[MAIN] FAB brought to front after SurfaceView created")
                                    }
                                }
                            }
                        }
                    }
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Timber.d("[MAIN] Surface destroyed")
                    lastSentWidth = -1
                    lastSentHeight = -1
                }
            })

            // Enable touch events
            setOnTouchListener { _, event ->
                handleTouchEvent(event)
            }
        }

        rootContainer?.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        return rootContainer
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Ensure insets dispatch happens after the view is attached.
        rootContainer?.let { ViewCompat.requestApplyInsets(it) }
    }

    /**
     * Requests UI Surface from UI Process and displays it.
     * Sends the SurfaceView's Surface to UI Process for VirtualDisplay rendering.
     */
    private fun requestUISurface(surfaceWidth: Int, surfaceHeight: Int) {
        val pluginId = this.pluginId ?: return
        val surface = surfaceView?.holder?.surface ?: return
        val proxy = uiProcessProxy ?: run {
            Timber.w("[MAIN] UI Process proxy not set - cannot send Surface")
            return
        }

        scope.launch {
            try {
                Timber.d("[MAIN] Sending Surface to UI Process for plugin: $pluginId")

                // Send Surface to UI Process
                val success = proxy.setUISurface(pluginId, surface, surfaceWidth, surfaceHeight)
                
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

        // Immersive fullscreen for plugin UI (hide status + navigation bars).
        ViewCompat.getWindowInsetsController(requireActivity().window.decorView)?.let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // Notify UI Process about lifecycle
        notifyUILifecycle("onResume")
    }

    override fun onPause() {
        super.onPause()
        Timber.d("[MAIN] PluginUIContainerFragment onPause for plugin: $pluginId")

        // Restore system bars when leaving plugin UI.
        ViewCompat.getWindowInsetsController(requireActivity().window.decorView)?.show(WindowInsetsCompat.Type.systemBars())
        
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
        
        val currentPluginId = this.pluginId
        val currentProxy = this.uiProcessProxy
        
        // CRITICAL: Completely destroy plugin UI in UI Process
        // This ensures VirtualDisplay, Fragment, and Activity are all cleaned up
        if (currentPluginId != null && currentProxy != null) {
            scope.launch {
                try {
                    Timber.i("[MAIN] Completely destroying plugin UI: $currentPluginId")
                    val destroyed = currentProxy.destroyPluginUI(currentPluginId)
                    if (destroyed) {
                        Timber.i("[MAIN] Plugin UI completely destroyed: $currentPluginId")
                    } else {
                        Timber.w("[MAIN] Failed to destroy plugin UI: $currentPluginId")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[MAIN] Error destroying plugin UI: $currentPluginId")
                }
            }
        }
        
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
        rootContainer = null
        lastSentWidth = -1
        lastSentHeight = -1
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
