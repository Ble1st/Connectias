// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ble1st.connectias.core.plugin.PluginUIProcessProxy
import com.ble1st.connectias.plugin.ui.IPluginUIMainCallback
import com.ble1st.connectias.plugin.ui.MotionEventParcel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
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
 *
 * IME proxy: When a TextField in the plugin UI (VirtualDisplay) gains focus,
 * the IME does not "serve" that window. This fragment provides IPluginUIMainCallback
 * and shows an overlay EditText in the Main Process so the keyboard appears;
 * text is sent to the UI Process via sendImeText().
 */
class PluginUIContainerFragment : Fragment() {

    private val imeMainCallback = object : IPluginUIMainCallback.Stub() {
        override fun requestShowIme(pluginId: String?, componentId: String?, initialText: String?) {
            this@PluginUIContainerFragment.handleRequestShowIme(pluginId, componentId, initialText)
        }
    }

    private var pluginId: String? = null
    private var containerId: Int = -1
    private var surfaceView: PluginSurfaceView? = null
    private var rootContainer: FrameLayout? = null
    private var uiProcessProxy: PluginUIProcessProxy? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastSentWidth: Int = -1
    private var lastSentHeight: Int = -1

    /** IME proxy: overlay EditText shown when plugin TextField requests keyboard */
    private var imeOverlayContainer: FrameLayout? = null
    private var imeOverlayEdit: EditText? = null
    private var imeCurrentComponentId: String? = null

    /**
     * Touch events must be forwarded in-order. Dispatching via coroutines that hop to IO can
     * reorder events and break gestures like scroll/drag in Compose.
     */
    private val touchEventQueue = Channel<MotionEventParcel>(capacity = Channel.BUFFERED)
    private var touchDispatchJob: kotlinx.coroutines.Job? = null

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
        if (imeOverlayContainer != null) registerMainCallback()
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

        // Start a single consumer that forwards touch events sequentially.
        // This prevents gesture breakage caused by concurrent IPC calls.
        touchDispatchJob = scope.launch {
            for (event in touchEventQueue) {
                val currentPluginId = this@PluginUIContainerFragment.pluginId
                val proxy = this@PluginUIContainerFragment.uiProcessProxy
                if (currentPluginId == null || proxy == null) continue

                try {
                    proxy.dispatchTouchEvent(currentPluginId, event)
                } catch (e: Exception) {
                    Timber.e(e, "[MAIN] Failed to dispatch touch event for plugin: $currentPluginId")
                }
            }
        }
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
        surfaceView = PluginSurfaceView(requireContext()).apply {
            // Set Z-order to media overlay level (below normal views)
            // This ensures FAB overlay from MainActivity appears above the SurfaceView
            setZOrderMediaOverlay(false)
            // Set low elevation to ensure it stays below FAB overlay
            elevation = 0f
            z = 0f
            // Mark as clickable for accessibility support
            isClickable = true
            
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
                                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                                root?.post {
                                    // Find ComposeView by tag or by type
                                    var composeView: View? = null
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
                    
                    // IMPORTANT: Do NOT release VirtualDisplay when Surface is destroyed during pause
                    // (e.g., when SAF dialog is shown). The Surface will be recreated when Activity resumes,
                    // and we'll set it again via setUISurface(). VirtualDisplayManager handles Surface changes
                    // by checking if the Surface reference changed and recreating the VirtualDisplay if needed.
                    //
                    // Only release VirtualDisplay if the fragment is being completely destroyed.
                    // This prevents conflicts when the Surface is temporarily destroyed (pause/resume cycle).
                    if (isRemoving || isDetached) {
                        val currentPluginId = pluginId
                        val currentProxy = uiProcessProxy
                        if (currentPluginId != null && currentProxy != null) {
                            scope.launch {
                                try {
                                    Timber.d("[MAIN] Releasing VirtualDisplay due to fragment destruction for plugin: $currentPluginId")
                                    currentProxy.destroyPluginUI(currentPluginId)
                                } catch (e: Exception) {
                                    Timber.e(e, "[MAIN] Failed to release VirtualDisplay on fragment destruction")
                                }
                            }
                        }
                    } else {
                        Timber.d("[MAIN] Surface destroyed but fragment still active - VirtualDisplay will be updated when new Surface is set")
                    }
                }
            })

            // Enable touch events
            this.setOnTouchListener { view, event ->
                val handled = handleTouchEvent(event)
                // Call performClick for accessibility when a click is detected
                if (event.action == MotionEvent.ACTION_UP && handled) {
                    view.performClick()
                }
                handled
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
        // IME proxy: add overlay and register callback with UI Process
        setupImeOverlay()
        registerMainCallback()
    }

    /**
     * IME proxy: overlay EditText so keyboard can be shown in Main Process.
     * When plugin TextField gains focus, we show this overlay and focus it.
     */
    private fun setupImeOverlay() {
        val container = rootContainer ?: return
        val ctx = context ?: return

        imeOverlayContainer = FrameLayout(ctx).apply {
            visibility = View.GONE
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener { hideImeOverlay() }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        imeOverlayEdit = EditText(ctx).apply {
            setPadding(48, 48, 48, 48)
            setBackgroundResource(android.R.drawable.edit_text)
            hint = "Type here…"
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 200
                leftMargin = 48
                rightMargin = 48
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideImeOverlay()
                    true
                } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val pid = pluginId ?: return
                    val cid = imeCurrentComponentId ?: return
                    val proxy = uiProcessProxy ?: return
                    scope.launch {
                        proxy.sendImeText(pid, cid, s?.toString() ?: "")
                    }
                }
            })
        }

        imeOverlayContainer!!.addView(imeOverlayEdit)
        container.addView(imeOverlayContainer)
    }

    private fun registerMainCallback() {
        val proxy = uiProcessProxy ?: return
        scope.launch {
            val ok = proxy.registerMainCallback(imeMainCallback)
            if (ok) Timber.d("[MAIN] IME proxy callback registered with UI Process")
            else Timber.w("[MAIN] Failed to register IME proxy callback")
        }
    }

    private fun handleRequestShowIme(pluginId: String?, componentId: String?, initialText: String?) {
        val pid = pluginId ?: return
        val cid = componentId ?: return
        val text = initialText ?: ""

        requireActivity().runOnUiThread {
            imeCurrentComponentId = cid
            imeOverlayEdit?.setText(text)
            imeOverlayEdit?.setSelection(text.length)
            imeOverlayContainer?.visibility = View.VISIBLE
            imeOverlayEdit?.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(imeOverlayEdit, InputMethodManager.SHOW_IMPLICIT)
            Timber.d("[MAIN] IME overlay shown for plugin: $pid component: $cid")
        }
    }

    private fun hideImeOverlay() {
        val pid = pluginId ?: return
        val cid = imeCurrentComponentId ?: return
        val proxy = uiProcessProxy

        imeOverlayContainer?.visibility = View.GONE
        imeOverlayEdit?.clearFocus()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(imeOverlayEdit?.windowToken, 0)
        imeCurrentComponentId = null

        if (proxy != null) {
            scope.launch {
                proxy.onImeDismissed(pid, cid)
            }
        }
        Timber.d("[MAIN] IME overlay hidden for plugin: $pid component: $cid")
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

        // Stop touch forwarding.
        try {
            touchEventQueue.close()
            touchDispatchJob?.cancel()
        } catch (e: Exception) {
            Timber.w(e, "[MAIN] Failed to stop touch dispatch job cleanly")
        }
        
        // Cleanup
        surfaceView = null
        uiProcessProxy = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Timber.d("[MAIN] PluginUIContainerFragment onDestroyView for plugin: $pluginId")
        hideImeOverlay()
        imeOverlayEdit = null
        imeOverlayContainer = null
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
        // We always "handle" the event for the SurfaceView, and forward it to UI Process.
        // If proxy/pluginId are not ready yet, we still consume to prevent unexpected propagation.

        // Convert MotionEvent to MotionEventParcel
        val motionEventParcel = MotionEventParcel().apply {
            // Use actionMasked to avoid pointer index bits corrupting ACTION_* values.
            action = event.actionMasked
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

        // Debug trace (throttled): confirm we actually receive MOVE events in main process.
        try {
            val action = motionEventParcel.action
            if (action != MotionEvent.ACTION_MOVE || (motionEventParcel.eventTime % 100L) == 0L) {
                Timber.d(
                    "[MAIN] [TOUCH_TRACE] plugin=%s action=%d x=%.1f y=%.1f t=%d",
                    pluginId,
                    action,
                    motionEventParcel.x,
                    motionEventParcel.y,
                    motionEventParcel.eventTime
                )
            }
        } catch (_: Exception) {
            // Never block touch due to debug logging.
        }

        // Enqueue for sequential forwarding. If the buffer is full, drop MOVE events (best effort),
        // but never block the UI thread.
        val offered = touchEventQueue.trySend(motionEventParcel)
        if (offered.isFailure) {
            if (motionEventParcel.action == MotionEvent.ACTION_MOVE) {
                // Drop noisy MOVE events under pressure; gestures still work with subsequent moves.
                return true
            }
            scope.launch {
                // For DOWN/UP/CANCEL ensure delivery.
                touchEventQueue.send(motionEventParcel)
            }
        }

        // Return true to indicate we're handling the event
        return true
    }
}

/**
 * SurfaceView subclass that overrides [performClick] for accessibility.
 * When [setOnTouchListener] is used, Android requires the view to override performClick()
 * so that accessibility services can trigger the same action as touch.
 */
private class PluginSurfaceView(context: Context) : SurfaceView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
