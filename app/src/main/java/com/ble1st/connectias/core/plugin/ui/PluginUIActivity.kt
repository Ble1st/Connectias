// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import timber.log.Timber

/**
 * Activity in UI Process that hosts PluginUIFragment instances.
 *
 * Three-Process Architecture:
 * - Main Process: Creates this activity via Intent
 * - UI Process: This activity runs here and hosts fragments
 * - Sandbox Process: Provides UI state updates
 *
 * This activity manages the fragment lifecycle and can optionally
 * provide a Surface for rendering to the Main Process.
 */
class PluginUIActivity : FragmentActivity() {

    private var pluginId: String? = null
    private var containerId: Int = -1
    private var surfaceView: SurfaceView? = null
    private var surfaceCallback: SurfaceHolder.Callback? = null

    companion object {
        private const val EXTRA_PLUGIN_ID = "pluginId"
        private const val EXTRA_CONTAINER_ID = "containerId"
        private const val EXTRA_CREATE_SURFACE = "createSurface"

        /**
         * Creates an Intent to start PluginUIActivity.
         *
         * @param pluginId Plugin identifier
         * @param containerId Container ID from UI Host
         * @param createSurface If true, create SurfaceView for Main Process
         */
        fun createIntent(
            pluginId: String,
            containerId: Int,
            createSurface: Boolean = false
        ): Intent {
            return Intent().apply {
                setClassName("com.ble1st.connectias", "com.ble1st.connectias.core.plugin.ui.PluginUIActivity")
                putExtra(EXTRA_PLUGIN_ID, pluginId)
                putExtra(EXTRA_CONTAINER_ID, containerId)
                putExtra(EXTRA_CREATE_SURFACE, createSurface)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FULLSCREEN MODE (THREE_PROCESS_UI_PLAN.md):
        // - Enable edge-to-edge rendering for immersive plugin UI
        // - Hide system bars (status bar and navigation bar) for true fullscreen
        // - Allow plugins to use the entire screen
        enableEdgeToEdge()

        // Hide system bars for immersive fullscreen experience
        val windowInsetsController = ViewCompat.getWindowInsetsController(window.decorView)
        windowInsetsController?.let {
            // Hide both system bars (status bar and navigation bar)
            it.hide(WindowInsetsCompat.Type.systemBars())
            // Configure behavior when user swipes to reveal bars
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Keep screen on while plugin is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        containerId = intent.getIntExtra(EXTRA_CONTAINER_ID, -1)
        val createSurface = intent.getBooleanExtra(EXTRA_CREATE_SURFACE, false)

        Timber.i("[UI_PROCESS] PluginUIActivity created for plugin: $pluginId (containerId: $containerId) - FULLSCREEN MODE")

        if (pluginId == null || containerId == -1) {
            Timber.e("[UI_PROCESS] Invalid arguments - missing pluginId or containerId")
            finish()
            return
        }

        // Get fragment from PluginUIService
        val uiService = PluginUIService.getInstance()
        val fragment = uiService?.getFragment(pluginId!!)
        
        if (fragment == null) {
            Timber.e("[UI_PROCESS] Fragment not found for plugin: $pluginId")
            finish()
            return
        }

        // Create a container layout for the fragment
        val containerLayout = android.widget.FrameLayout(this).apply {
            id = containerId
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Create SurfaceView if requested (for Main Process display)
        if (createSurface) {
            surfaceView = SurfaceView(this).apply {
                surfaceCallback = object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Timber.d("[UI_PROCESS] Surface created for plugin: $pluginId")
                        // Surface is ready - Main Process will receive it via setUISurface()
                        // The Main Process calls setUISurface() when it's ready to receive the Surface
                        // No additional notification needed as the flow is:
                        // 1. Main Process creates SurfaceView
                        // 2. Main Process calls setUISurface() with the Surface
                        // 3. UI Process creates VirtualDisplay with that Surface
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        Timber.d("[UI_PROCESS] Surface changed: ${width}x${height} for plugin: $pluginId")
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Timber.d("[UI_PROCESS] Surface destroyed for plugin: $pluginId")
                    }
                }
                holder.addCallback(surfaceCallback)
            }
            
            // Add SurfaceView to container
            containerLayout.addView(surfaceView)
        }

        // Set content view with container
        setContentView(containerLayout)

        // Add fragment to activity
        supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .commitNow()

        Timber.i("[UI_PROCESS] Fragment added to activity for plugin: $pluginId")
    }

    override fun onResume() {
        super.onResume()
        Timber.d("[UI_PROCESS] PluginUIActivity onResume for plugin: $pluginId")
    }

    override fun onPause() {
        super.onPause()
        Timber.d("[UI_PROCESS] PluginUIActivity onPause for plugin: $pluginId")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("[UI_PROCESS] PluginUIActivity onDestroy for plugin: $pluginId")
        
        surfaceView?.holder?.removeCallback(surfaceCallback)
        surfaceView = null
        surfaceCallback = null
    }

    /**
     * Gets the Surface for rendering (if SurfaceView was created).
     * Main Process can use this to display the UI.
     */
    fun getSurface(): Surface? {
        return surfaceView?.holder?.surface
    }
}
