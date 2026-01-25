// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.view.Display
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Manages VirtualDisplay instances for plugin UI rendering in UI Process.
 *
 * Three-Process Architecture:
 * - UI Process: Creates VirtualDisplay and renders fragments into it
 * - Main Process: Receives Surface and displays it
 * - Sandbox Process: Provides UI state updates
 *
 * Each plugin gets its own VirtualDisplay with a Surface that can be
 * transmitted to the Main Process for display.
 */
class VirtualDisplayManager(private val context: Context) {

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    // Map: pluginId -> VirtualDisplayInfo
    private val virtualDisplays = ConcurrentHashMap<String, VirtualDisplayInfo>()
    // Prevent concurrent create/recreate for same pluginId (Surface/Insets can trigger rapid callbacks).
    private val creationLocks = ConcurrentHashMap<String, Any>()
    
    private val handler = Handler(Looper.getMainLooper())
    private val debugTouchCounter = ConcurrentHashMap<String, Int>()

    /**
     * Information about a VirtualDisplay for a plugin.
     */
    private data class VirtualDisplayInfo(
        val virtualDisplay: VirtualDisplay,
        val imageReader: ImageReader?,  // Nullable - not needed when using external Surface
        val surface: Surface,
        val width: Int,
        val height: Int,
        val densityDpi: Int,
        val presentation: PluginComposePresentation? = null  // Presentation for rendering on VirtualDisplay
    ) {
        fun release() {
            try {
                // CRITICAL: Release order matters - dismiss presentation first
                presentation?.let { pres ->
                    try {
                        Timber.d("[UI_PROCESS] Dismissing Presentation for VirtualDisplay")
                        pres.dismiss()
                        // Small delay to ensure presentation is fully dismissed
                        Thread.sleep(50)
                    } catch (e: Exception) {
                        Timber.w(e, "[UI_PROCESS] Error dismissing Presentation")
                    }
                }
                
                // Close ImageReader if present
                imageReader?.let { reader ->
                    try {
                        Timber.d("[UI_PROCESS] Closing ImageReader")
                        reader.close()
                    } catch (e: Exception) {
                        Timber.w(e, "[UI_PROCESS] Error closing ImageReader")
                    }
                }
                
                // Release VirtualDisplay - this is critical to prevent SurfaceFlinger errors
                try {
                    Timber.d("[UI_PROCESS] Releasing VirtualDisplay")
                    virtualDisplay.release()
                } catch (e: Exception) {
                    Timber.e(e, "[UI_PROCESS] Error releasing VirtualDisplay")
                }
                
                // Don't release external Surface - it's owned by Main Process
                // surface.release()
                
                Timber.d("[UI_PROCESS] VirtualDisplay resources released successfully")
            } catch (e: Exception) {
                Timber.e(e, "[UI_PROCESS] Error releasing VirtualDisplay resources")
            }
        }
    }

    /**
     * Creates a VirtualDisplay for a plugin UI.
     *
     * @param pluginId Plugin identifier
     * @param width Display width in pixels
     * @param height Display height in pixels
     * @return Surface for rendering, or null on error
     */
    fun createVirtualDisplay(
        pluginId: String,
        width: Int,
        height: Int
    ): Surface? {
        return try {
            // Check if VirtualDisplay already exists
            virtualDisplays[pluginId]?.let { existing ->
                Timber.w("[UI_PROCESS] VirtualDisplay already exists for plugin: $pluginId")
                return existing.surface
            }

            // Get display metrics
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            val densityDpi = metrics.densityDpi

            // Create ImageReader for the VirtualDisplay
            // Use RGB_565 or FLEX_RGBA_8888 depending on Android version
            // Use RGB_565 format which is widely supported
            val imageReader = ImageReader.newInstance(width, height, ImageFormat.RGB_565, 2)
            val surface = imageReader.surface

            // Create VirtualDisplay
            val virtualDisplay = displayManager.createVirtualDisplay(
                "PluginUI_$pluginId",  // Display name
                width,
                height,
                densityDpi,
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                null,  // Callback
                handler
            )

            if (virtualDisplay == null) {
                Timber.e("[UI_PROCESS] Failed to create VirtualDisplay for plugin: $pluginId")
                imageReader.close()
                surface.release()
                return null
            }

            val info = VirtualDisplayInfo(
                virtualDisplay = virtualDisplay,
                imageReader = imageReader,
                surface = surface,
                width = width,
                height = height,
                densityDpi = densityDpi
            )

            virtualDisplays[pluginId] = info

            Timber.i("[UI_PROCESS] VirtualDisplay created for plugin: $pluginId (${width}x${height}, ${densityDpi}dpi)")
            surface
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to create VirtualDisplay for plugin: $pluginId")
            null
        }
    }

    /**
     * Gets the Surface for a plugin's VirtualDisplay.
     *
     * @param pluginId Plugin identifier
     * @return Surface or null if not found
     */
    fun getSurface(pluginId: String): Surface? {
        return virtualDisplays[pluginId]?.surface
    }

    /**
     * Resizes a VirtualDisplay for a plugin.
     *
     * @param pluginId Plugin identifier
     * @param newWidth New width in pixels
     * @param newHeight New height in pixels
     * @return True if resized successfully
     */
    fun resizeVirtualDisplay(
        pluginId: String,
        newWidth: Int,
        newHeight: Int
    ): Boolean {
        return try {
            val existing = virtualDisplays[pluginId]
            if (existing == null) {
                Timber.w("[UI_PROCESS] VirtualDisplay not found for resize: $pluginId")
                return false
            }

            // Release old VirtualDisplay
            existing.release()
            virtualDisplays.remove(pluginId)

            // Create new VirtualDisplay with new dimensions
            val newSurface = createVirtualDisplay(pluginId, newWidth, newHeight)
            newSurface != null
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to resize VirtualDisplay for plugin: $pluginId")
            false
        }
    }

    /**
     * Releases VirtualDisplay for a plugin.
     *
     * @param pluginId Plugin identifier
     */
    fun releaseVirtualDisplay(pluginId: String) {
        virtualDisplays.remove(pluginId)?.let { info ->
            Timber.i("[UI_PROCESS] Releasing VirtualDisplay for plugin: $pluginId")
            info.release()
        }
        creationLocks.remove(pluginId)
    }

    /**
     * Releases all VirtualDisplays.
     * 
     * CRITICAL: This must be called when the UI Process is destroyed to prevent
     * SurfaceFlinger errors (ANativeWindow::dequeueBuffer failed with error: -32).
     * 
     * When a process dies, VirtualDisplays are not automatically released by Android,
     * causing SurfaceFlinger to continuously attempt to access invalid displays.
     */
    fun releaseAll() {
        val count = virtualDisplays.size
        if (count == 0) {
            Timber.d("[UI_PROCESS] No VirtualDisplays to release")
            return
        }
        
        Timber.i("[UI_PROCESS] Releasing all VirtualDisplays ($count active)")
        
        // Release each VirtualDisplay with error handling
        virtualDisplays.values.forEachIndexed { index, info ->
            try {
                Timber.d("[UI_PROCESS] Releasing VirtualDisplay ${index + 1}/$count")
                info.release()
            } catch (e: Exception) {
                Timber.e(e, "[UI_PROCESS] Error releasing VirtualDisplay ${index + 1}/$count")
            }
        }
        
        virtualDisplays.clear()
        creationLocks.clear()
        Timber.i("[UI_PROCESS] All VirtualDisplays released ($count -> 0)")
    }

    /**
     * Creates a VirtualDisplay using an existing Surface from Main Process.
     *
     * @param pluginId Plugin identifier
     * @param surface Surface from Main Process (SurfaceView)
     * @param width Display width in pixels
     * @param height Display height in pixels
     * @param fragment PluginUIFragment to display (optional)
     * @return VirtualDisplay or null on error
     */
    fun createVirtualDisplayWithSurface(
        pluginId: String,
        surface: Surface,
        width: Int,
        height: Int,
        fragment: PluginUIFragment? = null
    ): VirtualDisplay? {
        val lock = creationLocks.getOrPut(pluginId) { Any() }
        synchronized(lock) {
            return try {
                // If we already have an active VirtualDisplay that matches this request, keep it.
                // This avoids double-rendering caused by rapid repeated setUISurface() calls.
                virtualDisplays[pluginId]?.let { existing ->
                    if (existing.surface == surface && existing.width == width && existing.height == height) {
                        Timber.d("[UI_PROCESS] VirtualDisplay already matches request for plugin: $pluginId (${width}x${height})")
                        return existing.virtualDisplay
                    }
                }

            // Check if VirtualDisplay already exists and release it properly
            virtualDisplays[pluginId]?.let { existing ->
                Timber.w("[UI_PROCESS] VirtualDisplay already exists for plugin: $pluginId - releasing old one")
                try {
                    // Dismiss presentation first
                    existing.presentation?.dismiss()
                    // Wait a bit for presentation to fully dismiss
                    Thread.sleep(100)
                } catch (e: Exception) {
                    Timber.w(e, "[UI_PROCESS] Error dismissing old presentation")
                }
                // Release all resources
                existing.release()
                virtualDisplays.remove(pluginId)
                // Wait a bit more to ensure VirtualDisplay is fully released
                Thread.sleep(50)
            }

            // Get display metrics
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            val densityDpi = metrics.densityDpi

            // Create VirtualDisplay using the provided Surface
            val virtualDisplay = displayManager.createVirtualDisplay(
                "PluginUI_$pluginId",  // Display name
                width,
                height,
                densityDpi,
                surface,  // Use Surface from Main Process
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                null,  // Callback
                handler
            )

            if (virtualDisplay == null) {
                Timber.e("[UI_PROCESS] Failed to create VirtualDisplay with Surface for plugin: $pluginId")
                return null
            }

            // Create Presentation to display the fragment's Compose UI on the VirtualDisplay
            // IMPORTANT: Presentation must be created on the main thread
            // CRITICAL: VirtualDisplay needs time to initialize before Presentation can be shown
            // We use a background thread to wait for the display, then post back to main thread
            val presentation = if (fragment != null) {
                try {
                    var presentation: PluginComposePresentation? = null
                    val latch = java.util.concurrent.CountDownLatch(1)

                    // Wait for display in background thread to avoid blocking main thread
                    Thread {
                        try {
                            // Wait for display to be ready with retry mechanism
                            var delay = 50L
                            var readyDisplay: Display? = null
                            var displayReady = false

                            for (attempt in 0 until 20) {
                                val display = virtualDisplay.display
                                if (display != null && display.displayId != Display.INVALID_DISPLAY) {
                                    // Verify display is actually accessible by checking its state
                                    try {
                                        val state = display.state
                                        if (state == Display.STATE_ON || state == Display.STATE_UNKNOWN) {
                                            // Try to actually use the display by checking if WindowManager can see it
                                            try {
                                                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                                                val displays = dm.displays
                                                val foundDisplay = displays.find { it.displayId == display.displayId }
                                                if (foundDisplay != null) {
                                                    readyDisplay = display
                                                    displayReady = true
                                                    Timber.d("[UI_PROCESS] VirtualDisplay ready after ${attempt + 1} attempts (delay: ${delay}ms)")
                                                    break  // Exit loop when display is ready
                                                }
                                            } catch (e: Exception) {
                                                Timber.w(e, "[UI_PROCESS] Error checking display in WindowManager, retrying...")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Timber.w(e, "[UI_PROCESS] Error checking display state, retrying...")
                                    }
                                }

                                if (!displayReady && attempt < 19) {
                                    Thread.sleep(delay)
                                    delay = (delay * 1.5).toLong().coerceAtMost(500) // Exponential backoff, max 500ms
                                }
                            }

                            // Post back to main thread to create and show Presentation
                            // Add small delay to ensure VirtualDisplay is fully initialized
                            handler.postDelayed({
                                try {
                                    if (readyDisplay != null) {
                                        presentation = PluginComposePresentation(context, readyDisplay, pluginId, fragment)
                                        Timber.i("[UI_PROCESS] ComposePresentation created for plugin: $pluginId")
                                        presentation?.show()
                                        Timber.i("[UI_PROCESS] ComposePresentation shown for plugin: $pluginId")
                                    } else {
                                        Timber.e("[UI_PROCESS] VirtualDisplay not ready for Presentation for plugin: $pluginId after 20 attempts")
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "[UI_PROCESS] Failed to create/show ComposePresentation for plugin: $pluginId")
                                } finally {
                                    latch.countDown()
                                }
                            }, 100) // 100ms delay to ensure display is fully ready
                        } catch (e: Exception) {
                            Timber.e(e, "[UI_PROCESS] Error in display wait thread for plugin: $pluginId")
                            latch.countDown()
                        }
                    }.start()

                    // Wait for presentation creation (with timeout)
                    if (!latch.await(15, java.util.concurrent.TimeUnit.SECONDS)) {
                        Timber.e("[UI_PROCESS] Timeout waiting for ComposePresentation creation for plugin: $pluginId")
                    }

                    presentation
                } catch (e: Exception) {
                    Timber.e(e, "[UI_PROCESS] Failed to create ComposePresentation for plugin: $pluginId")
                    null
                }
            } else {
                Timber.w("[UI_PROCESS] Fragment not provided - Presentation not created")
                null
            }

            // No ImageReader needed when using external Surface from Main Process
            // The VirtualDisplay renders directly into the provided Surface
            val info = VirtualDisplayInfo(
                virtualDisplay = virtualDisplay,
                imageReader = null,  // Not needed with external Surface
                surface = surface,  // Use provided Surface
                width = width,
                height = height,
                densityDpi = densityDpi,
                presentation = presentation
            )

            virtualDisplays[pluginId] = info

            Timber.i("[UI_PROCESS] VirtualDisplay created with external Surface for plugin: $pluginId (${width}x${height}, ${densityDpi}dpi)")
            virtualDisplay
            } catch (e: Exception) {
                Timber.e(e, "[UI_PROCESS] Failed to create VirtualDisplay with Surface for plugin: $pluginId")
                null
            }
        }
    }

    /**
     * Gets the number of active VirtualDisplays.
     */
    fun getActiveDisplayCount(): Int = virtualDisplays.size

    /**
     * Checks if a VirtualDisplay exists for a plugin.
     */
    fun hasVirtualDisplay(pluginId: String): Boolean = virtualDisplays.containsKey(pluginId)

    /**
     * Injects a touch event into the Presentation (VirtualDisplay) so Compose can handle clicks.
     *
     * IMPORTANT:
     * - The user touches the SurfaceView in the Main Process.
     * - To make Compose click handlers work, we must dispatch MotionEvents into the UI Process
     *   Presentation/ComposeView. Forwarding "touch_down" to the sandbox is not sufficient.
     */
    fun dispatchTouchEventToPresentation(pluginId: String, motionEvent: com.ble1st.connectias.plugin.ui.MotionEventParcel): Boolean {
        val info = virtualDisplays[pluginId]
        val presentation = info?.presentation ?: return false

        // Lightweight, throttled tracing for scroll/drag debugging.
        // Scroll requires correct DOWN->MOVE sequencing; this helps confirm injection is happening.
        try {
            val count = (debugTouchCounter[pluginId] ?: 0) + 1
            debugTouchCounter[pluginId] = count
            val action = motionEvent.action
            val isMove = action == MotionEvent.ACTION_MOVE
            if (!isMove || count % 20 == 0) {
                Timber.d(
                    "[UI_PROCESS] [TOUCH_TRACE] plugin=%s action=%d x=%.1f y=%.1f t=%d down=%d",
                    pluginId,
                    action,
                    motionEvent.x,
                    motionEvent.y,
                    motionEvent.eventTime,
                    motionEvent.downTime
                )
            }
        } catch (_: Exception) {
            // Never break touch dispatch due to debug logging.
        }

        // Always dispatch on the main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return dispatchOnMainThread(pluginId, presentation, motionEvent)
        }

        var handled = false
        val latch = CountDownLatch(1)
        handler.post {
            try {
                handled = dispatchOnMainThread(pluginId, presentation, motionEvent)
            } finally {
                latch.countDown()
            }
        }

        // Best-effort: don't block IPC for long. We still dispatch even if timeout happens.
        latch.await(50, TimeUnit.MILLISECONDS)
        return handled
    }

    private fun dispatchOnMainThread(
        pluginId: String,
        presentation: PluginComposePresentation,
        motionEvent: com.ble1st.connectias.plugin.ui.MotionEventParcel
    ): Boolean {
        return try {
            val decor = presentation.window?.decorView ?: return false

            // Create a single-pointer MotionEvent.
            // Note: Multi-touch is not supported by MotionEventParcel.
            val properties = arrayOf(MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            })
            val coords = arrayOf(MotionEvent.PointerCoords().apply {
                x = motionEvent.x
                y = motionEvent.y
                pressure = motionEvent.pressure
                size = motionEvent.size
            })

            val event = MotionEvent.obtain(
                motionEvent.downTime,
                motionEvent.eventTime,
                motionEvent.action,
                1, // pointerCount
                properties,
                coords,
                motionEvent.metaState,
                motionEvent.buttonState,
                1f, // xPrecision
                1f, // yPrecision
                motionEvent.deviceId,
                motionEvent.edgeFlags,
                motionEvent.source,
                motionEvent.flags
            )

            try {
                val handled = decor.dispatchTouchEvent(event)
                val action = motionEvent.action
                val isMove = action == MotionEvent.ACTION_MOVE
                val count = debugTouchCounter[pluginId] ?: 0
                if (!isMove || count % 20 == 0) {
                    Timber.d(
                        "[UI_PROCESS] [TOUCH_TRACE] injected handled=%s plugin=%s action=%d",
                        handled,
                        pluginId,
                        action
                    )
                }
                handled
            } finally {
                event.recycle()
            }
        } catch (e: Exception) {
            Timber.w(e, "[UI_PROCESS] Failed to inject touch into Presentation for plugin: $pluginId")
            false
        }
    }
}
