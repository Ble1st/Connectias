// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import timber.log.Timber

/**
 * Presentation that renders Plugin Compose UI directly on a VirtualDisplay.
 *
 * This bypasses the need for Fragments and directly renders the Compose UI
 * from PluginUIFragment onto the VirtualDisplay.
 */
class PluginComposePresentation(
    context: Context,
    display: Display,
    private val pluginId: String,
    private val fragment: PluginUIFragment
) : Presentation(context, display) {

    private var composeView: ComposeView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("[UI_PROCESS] Creating ComposePresentation for plugin: $pluginId on VirtualDisplay")

        // Get the fragment's view (which is a ComposeView)
        val fragmentView = try {
            fragment.onCreateView(
                android.view.LayoutInflater.from(context),
                null,
                null
            )
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to create fragment view for plugin: $pluginId")
            return
        }

        if (fragmentView is ComposeView) {
            composeView = fragmentView
            setContentView(fragmentView)  // Use non-null fragmentView directly
            Timber.i("[UI_PROCESS] Compose UI set for plugin: $pluginId")
        } else {
            Timber.e("[UI_PROCESS] Fragment view is not a ComposeView for plugin: $pluginId")
        }
    }

    override fun onStart() {
        super.onStart()
        Timber.d("[UI_PROCESS] ComposePresentation started for plugin: $pluginId")
        fragment.onStart()
    }

    override fun onStop() {
        fragment.onStop()
        super.onStop()
        Timber.d("[UI_PROCESS] ComposePresentation stopped for plugin: $pluginId")
    }

    override fun dismiss() {
        fragment.onDestroyView()
        fragment.onDestroy()
        super.dismiss()
        Timber.i("[UI_PROCESS] ComposePresentation dismissed for plugin: $pluginId")
        composeView = null
    }
}
