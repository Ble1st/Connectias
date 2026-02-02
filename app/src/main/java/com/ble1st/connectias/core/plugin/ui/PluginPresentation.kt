// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import timber.log.Timber

/**
 * Presentation that displays a PluginUIFragment on a VirtualDisplay.
 *
 * This is used in the Three-Process Architecture to render plugin UI
 * on a VirtualDisplay which is then shown in the Main Process.
 */
class PluginPresentation(
    context: Context,
    display: Display,
    private val pluginId: String,
    private val fragment: PluginUIFragment,
    private val fragmentManager: FragmentManager
) : Presentation(context, display) {

    private var container: FragmentContainerView? = null
    private val containerId = pluginId.hashCode()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("[UI_PROCESS] Creating Presentation for plugin: $pluginId on VirtualDisplay")

        // Create container for the fragment
        val fragmentContainer = FragmentContainerView(context).apply {
            id = containerId
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container = fragmentContainer

        setContentView(fragmentContainer)  // Use non-null local variable

        // Add fragment to the presentation
        fragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .commitNow()

        Timber.i("[UI_PROCESS] Fragment added to Presentation for plugin: $pluginId")
    }

    override fun onStart() {
        super.onStart()
        Timber.d("[UI_PROCESS] Presentation started for plugin: $pluginId")
    }

    override fun onStop() {
        super.onStop()
        Timber.d("[UI_PROCESS] Presentation stopped for plugin: $pluginId")
    }

    override fun dismiss() {
        super.dismiss()
        Timber.i("[UI_PROCESS] Presentation dismissed for plugin: $pluginId")
        container = null
    }
}
