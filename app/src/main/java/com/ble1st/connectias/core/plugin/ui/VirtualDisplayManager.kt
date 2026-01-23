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
import android.view.Surface
import android.view.WindowManager
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

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
    
    private val handler = Handler(Looper.getMainLooper())

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
                presentation?.dismiss()
                imageReader?.close()  // Nullable - only close if present
                virtualDisplay.release()
                // Don't release external Surface - it's owned by Main Process
                // surface.release()
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
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
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
    }

    /**
     * Releases all VirtualDisplays.
     */
    fun releaseAll() {
        Timber.i("[UI_PROCESS] Releasing all VirtualDisplays (${virtualDisplays.size} active)")
        virtualDisplays.values.forEach { it.release() }
        virtualDisplays.clear()
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
        return try {
            // Check if VirtualDisplay already exists
            virtualDisplays[pluginId]?.let { existing ->
                Timber.w("[UI_PROCESS] VirtualDisplay already exists for plugin: $pluginId - releasing old one")
                existing.release()
                virtualDisplays.remove(pluginId)
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
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
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
                            var delay = 100L
                            var readyDisplay: Display? = null
                            
                            repeat(20) { attempt -> // Up to 20 attempts
                                val display = virtualDisplay.display
                                if (display != null && display.displayId != Display.INVALID_DISPLAY) {
                                    // Verify display is actually accessible by checking its state
                                    try {
                                        val state = display.state
                                        if (state == Display.STATE_ON || state == Display.STATE_UNKNOWN) {
                                            // Try to actually use the display by checking if WindowManager can see it
                                            try {
                                                val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                                                val displays = displayManager.displays
                                                val foundDisplay = displays.find { it.displayId == display.displayId }
                                                if (foundDisplay != null) {
                                                    readyDisplay = display
                                                    Timber.d("[UI_PROCESS] VirtualDisplay ready after ${attempt + 1} attempts (delay: ${delay}ms)")
                                                    return@repeat
                                                }
                                            } catch (e: Exception) {
                                                Timber.w(e, "[UI_PROCESS] Error checking display in WindowManager, retrying...")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Timber.w(e, "[UI_PROCESS] Error checking display state, retrying...")
                                    }
                                }
                                
                                if (attempt < 19) {
                                    Thread.sleep(delay)
                                    delay = (delay * 1.2).toLong().coerceAtMost(500) // Exponential backoff, max 500ms
                                }
                            }
                            
                            // Post back to main thread to create and show Presentation
                            handler.post {
                                try {
                                    if (readyDisplay != null) {
                                        presentation = PluginComposePresentation(context, readyDisplay, pluginId, fragment)
                                        presentation?.show()
                                        Timber.i("[UI_PROCESS] ComposePresentation created and shown for plugin: $pluginId")
                                    } else {
                                        Timber.e("[UI_PROCESS] VirtualDisplay not ready for Presentation for plugin: $pluginId after 20 attempts")
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "[UI_PROCESS] Failed to create/show ComposePresentation for plugin: $pluginId")
                                } finally {
                                    latch.countDown()
                                }
                            }
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

    /**
     * Gets the number of active VirtualDisplays.
     */
    fun getActiveDisplayCount(): Int = virtualDisplays.size

    /**
     * Checks if a VirtualDisplay exists for a plugin.
     */
    fun hasVirtualDisplay(pluginId: String): Boolean = virtualDisplays.containsKey(pluginId)
}
