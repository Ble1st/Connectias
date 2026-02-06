// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.app.Service
import android.content.Intent
import android.os.IBinder
import timber.log.Timber

/**
 * Service running in UI Process (:plugin_ui).
 *
 * Three-Process Architecture:
 * - Main Process: Orchestrates plugin lifecycle
 * - Sandbox Process (isolated): Plugin business logic
 * - UI Process (non-isolated): Plugin UI rendering
 *
 * This service manages plugin UI fragments and renders based on state updates
 * from the Sandbox Process.
 *
 * IMPORTANT: This service runs in separate process (android:process=":plugin_ui")
 * with isolatedProcess="false", but has only UI-relevant permissions.
 */
class PluginUIService : Service() {

    private lateinit var uiHostImpl: PluginUIHostImpl
    private lateinit var uiController: PluginUIControllerUIProcess
    private lateinit var virtualDisplayManager: VirtualDisplayManager
    private val activeFragments = mutableMapOf<String, PluginUIFragment>()

    companion object {
        @Volatile
        private var instance: PluginUIService? = null

        /**
         * Gets the singleton instance of PluginUIService.
         * Note: This only works within the UI Process.
         */
        fun getInstance(): PluginUIService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Timber.i("[UI_PROCESS] PluginUIService created (PID: ${android.os.Process.myPid()})")

        // Create VirtualDisplay manager
        virtualDisplayManager = VirtualDisplayManager(this)

        // Create UI Controller that receives updates from Sandbox
        uiController = PluginUIControllerUIProcess(activeFragments)

        uiHostImpl = PluginUIHostImpl(
            context = this,
            fragmentRegistry = activeFragments,
            uiController = uiController,
            virtualDisplayManager = virtualDisplayManager
        )
    }

    /**
     * Gets the UI Controller for receiving state updates from Sandbox Process.
     * This is exposed via IPluginUIHost.getUIController().
     */
    fun getUIController(): PluginUIControllerUIProcess = uiController

    /**
     * Gets the VirtualDisplay manager.
     */
    fun getVirtualDisplayManager(): VirtualDisplayManager = virtualDisplayManager

    override fun onBind(intent: Intent?): IBinder {
        Timber.i("[UI_PROCESS] PluginUIService bound by ${intent?.component?.packageName}")
        return uiHostImpl.asBinder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.i("[UI_PROCESS] PluginUIService unbound")
        
        // CRITICAL: Release VirtualDisplays when service is unbound
        // This handles cases where the Main Process dies and unbinds the service
        // Without this, VirtualDisplays remain active and cause SurfaceFlinger errors
        try {
            Timber.i("[UI_PROCESS] Releasing VirtualDisplays on service unbind")
            virtualDisplayManager.releaseAll()
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Error releasing VirtualDisplays on unbind")
        }
        
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("[UI_PROCESS] PluginUIService destroyed")
        instance = null

        // CRITICAL: Release all VirtualDisplays first to prevent SurfaceFlinger errors
        // This prevents "ANativeWindow::dequeueBuffer failed" errors when process dies
        try {
            Timber.i("[UI_PROCESS] Releasing all VirtualDisplays before service destruction")
            virtualDisplayManager.releaseAll()
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Error releasing VirtualDisplays in onDestroy")
        }

        // Cleanup all active fragments
        activeFragments.values.forEach { fragment ->
            try {
                fragment.destroy()
            } catch (e: Exception) {
                Timber.e(e, "[UI_PROCESS] Error destroying fragment: ${fragment.getPluginId()}")
            }
        }
        activeFragments.clear()
    }

    /**
     * Gets a fragment for a plugin (for internal use).
     */
    fun getFragment(pluginId: String): PluginUIFragment? {
        return activeFragments[pluginId]
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("[UI_PROCESS] PluginUIService started (startId: $startId)")
        return START_STICKY // Restart if killed by system
    }
}
