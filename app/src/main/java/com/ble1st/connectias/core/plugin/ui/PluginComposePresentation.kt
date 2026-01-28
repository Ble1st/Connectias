// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
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
) : Presentation(context, display), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private var composeView: ComposeView? = null

    // Lifecycle management for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val mainHandler = Handler(Looper.getMainLooper())

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SavedStateRegistry
        savedStateRegistryController.performRestore(savedInstanceState)

        Timber.i("[UI_PROCESS] Creating ComposePresentation for plugin: $pluginId on VirtualDisplay")

        // Get fragment class for reflection (used in multiple places)
        val fragmentClass = androidx.fragment.app.Fragment::class.java

        // Attach fragment to a minimal host so requireContext() works
        // This is necessary because PluginUIFragment calls requireContext() in onCreateView()
        try {
            // Use reflection to set fragment's mHost field
            val mHostField = fragmentClass.getDeclaredField("mHost")
            mHostField.isAccessible = true

            // Create minimal FragmentHostCallback
            val hostCallback = object : androidx.fragment.app.FragmentHostCallback<PluginComposePresentation>(
                context,
                mainHandler,
                0
            ) {
                override fun onGetHost(): PluginComposePresentation = this@PluginComposePresentation
                override fun onGetLayoutInflater(): android.view.LayoutInflater =
                    android.view.LayoutInflater.from(context)
            }

            mHostField.set(fragment, hostCallback)

            // Also set the fragment state to CREATED so requireContext() doesn't fail
            val mStateField = fragmentClass.getDeclaredField("mState")
            mStateField.isAccessible = true
            mStateField.setInt(fragment, 1) // INITIALIZING = 0, ATTACHED = 1, CREATED = 2

            Timber.d("[UI_PROCESS] Fragment attached to Presentation context for plugin: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to attach fragment to context")
            return
        }

        // Check if fragment already has a view (reusing fragment after dismissWithoutDestroyingFragment)
        val fragmentView = try {
            val viewField = fragmentClass.getDeclaredField("mView")
            viewField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val existingView = viewField.get(fragment) as? View
            
            if (existingView != null && existingView is ComposeView) {
                Timber.d("[UI_PROCESS] Fragment already has a view - reusing for plugin: $pluginId")
                existingView
            } else {
                // Fragment doesn't have a view yet, need to create it
                // Check if fragment was already created (reusing fragment after dismissWithoutDestroyingFragment)
                val mStateField = fragmentClass.getDeclaredField("mState")
                mStateField.isAccessible = true
                val currentState = mStateField.getInt(fragment)
                
                // Only call onCreate() if fragment is not already created (state < CREATED = 2)
                if (currentState < 2) {
                    try {
                        fragment.onCreate(null)
                    } catch (e: Exception) {
                        Timber.w(e, "[UI_PROCESS] Fragment onCreate failed, continuing anyway")
                    }
                } else {
                    Timber.d("[UI_PROCESS] Fragment already created (state: $currentState) - skipping onCreate() for plugin: $pluginId")
                }

                // Get the fragment's view (which is a ComposeView)
                fragment.onCreateView(
                    android.view.LayoutInflater.from(context),
                    null,
                    null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to get/create fragment view for plugin: $pluginId")
            return
        }

        if (fragmentView is ComposeView) {
            composeView = fragmentView

            setContentView(fragmentView)

            // Set lifecycle owners on the view hierarchy using reflection
            // This is needed because ComposeView looks for ViewTreeLifecycleOwner
            setViewTreeOwners(fragmentView)

            Timber.i("[UI_PROCESS] Compose UI set for plugin: $pluginId")
        } else {
            Timber.e("[UI_PROCESS] Fragment view is not a ComposeView for plugin: $pluginId")
        }

        // Set lifecycle to CREATED (onCreate is already on main thread)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    /**
     * Sets lifecycle owners on the view using reflection to work around missing ViewTree* APIs.
     * This propagates the owners to all child views to ensure OkHttp and other libraries can access them.
     */
    private fun setViewTreeOwners(view: View) {
        try {
            // Use reflection to call ViewTreeLifecycleOwner.set(view, this)
            val lifecycleOwnerClass = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
            val setMethod = lifecycleOwnerClass.getMethod("set", View::class.java, LifecycleOwner::class.java)
            setMethod.invoke(null, view, this)

            // Use reflection to call ViewTreeSavedStateRegistryOwner.set(view, this)
            val savedStateOwnerClass = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            val setSavedStateMethod = savedStateOwnerClass.getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
            setSavedStateMethod.invoke(null, view, this)

            // Use reflection to call ViewTreeViewModelStoreOwner.set(view, this)
            val viewModelStoreOwnerClass = Class.forName("androidx.lifecycle.ViewTreeViewModelStoreOwner")
            val setViewModelStoreMethod = viewModelStoreOwnerClass.getMethod("set", View::class.java, ViewModelStoreOwner::class.java)
            setViewModelStoreMethod.invoke(null, view, this)

            // IMPORTANT: Also set on all child views to ensure OkHttp can access ViewTreeSavedStateRegistryOwner
            // OkHttp's Android platform implementation accesses ViewTreeSavedStateRegistryOwner from child views
            if (view is android.view.ViewGroup) {
                propagateViewTreeOwnersToChildren(view, lifecycleOwnerClass, savedStateOwnerClass, viewModelStoreOwnerClass)
            }

            Timber.d("[UI_PROCESS] Successfully set ViewTree owners for plugin: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to set ViewTree owners for plugin: $pluginId")
        }
    }

    /**
     * Recursively propagates ViewTree owners to all child views.
     * This ensures that OkHttp and other libraries can access ViewTreeSavedStateRegistryOwner from any view in the hierarchy.
     */
    private fun propagateViewTreeOwnersToChildren(
        viewGroup: android.view.ViewGroup,
        lifecycleOwnerClass: Class<*>,
        savedStateOwnerClass: Class<*>,
        viewModelStoreOwnerClass: Class<*>
    ) {
        try {
            val setLifecycleMethod = lifecycleOwnerClass.getMethod("set", View::class.java, LifecycleOwner::class.java)
            val setSavedStateMethod = savedStateOwnerClass.getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
            val setViewModelMethod = viewModelStoreOwnerClass.getMethod("set", View::class.java, ViewModelStoreOwner::class.java)

            for (i in 0 until viewGroup.childCount) {
                val child = viewGroup.getChildAt(i)
                
                // Set owners on child view
                setLifecycleMethod.invoke(null, child, this)
                setSavedStateMethod.invoke(null, child, this)
                setViewModelMethod.invoke(null, child, this)

                // Recursively propagate to grandchildren
                if (child is android.view.ViewGroup) {
                    propagateViewTreeOwnersToChildren(child, lifecycleOwnerClass, savedStateOwnerClass, viewModelStoreOwnerClass)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[UI_PROCESS] Failed to propagate ViewTree owners to children for plugin: $pluginId")
        }
    }

    override fun onStart() {
        super.onStart()
        // Move lifecycle to STARTED state (onStart is already on main thread)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        Timber.d("[UI_PROCESS] ComposePresentation started for plugin: $pluginId")
        fragment.onStart()

        // Move to RESUMED after the fragment starts
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStop() {
        // Only move lifecycle if not already DESTROYED
        // This prevents IllegalStateException when dismiss() was already called
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            try {
                lifecycleRegistry.currentState = Lifecycle.State.CREATED
            } catch (e: IllegalStateException) {
                // State transition not allowed (e.g., already DESTROYED)
                Timber.w("[UI_PROCESS] Cannot move lifecycle to CREATED for plugin $pluginId: ${e.message}")
            }
        }
        
        // Only call fragment.onStop() if lifecycle is not DESTROYED
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            try {
                fragment.onStop()
            } catch (e: Exception) {
                Timber.w(e, "[UI_PROCESS] Fragment onStop failed for plugin: $pluginId")
            }
        }
        
        super.onStop()
        Timber.d("[UI_PROCESS] ComposePresentation stopped for plugin: $pluginId")
    }

    /**
     * Dismisses the presentation without destroying the fragment.
     * This is used when recreating VirtualDisplay (e.g., when Surface changes).
     * The fragment can be reused for the new VirtualDisplay.
     */
    fun dismissWithoutDestroyingFragment() {
        // Only stop the presentation, but don't destroy the fragment
        // Check if we're already on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Already on main thread, run synchronously
            try {
                // Move lifecycle to CREATED (not DESTROYED)
                if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                    lifecycleRegistry.currentState = Lifecycle.State.CREATED
                }
                // Call onStop but NOT onDestroy
                if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                    fragment.onStop()
                }
                composeView = null
                super.dismiss()
                Timber.i("[UI_PROCESS] ComposePresentation dismissed (fragment preserved) for plugin: $pluginId")
            } catch (e: Exception) {
                Timber.w(e, "[UI_PROCESS] Error dismissing presentation without destroying fragment for plugin: $pluginId")
                super.dismiss()
            }
        } else {
            // Not on main thread, post to main thread
            mainHandler.post {
                try {
                    if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                        lifecycleRegistry.currentState = Lifecycle.State.CREATED
                    }
                    if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                        fragment.onStop()
                    }
                    composeView = null
                    super.dismiss()
                    Timber.i("[UI_PROCESS] ComposePresentation dismissed (fragment preserved) for plugin: $pluginId")
                } catch (e: Exception) {
                    Timber.w(e, "[UI_PROCESS] Error dismissing presentation without destroying fragment for plugin: $pluginId")
                    super.dismiss()
                }
            }
        }
    }

    override fun dismiss() {
        // Destroy lifecycle (must be on main thread)
        // This is called when the plugin UI is completely destroyed
        // Check if we're already on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Already on main thread, run synchronously
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            fragment.onDestroyView()
            fragment.onDestroy()
            store.clear()
            composeView = null
            super.dismiss()
            Timber.i("[UI_PROCESS] ComposePresentation dismissed for plugin: $pluginId")
        } else {
            // Not on main thread, post to main thread
            mainHandler.post {
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                fragment.onDestroyView()
                fragment.onDestroy()
                store.clear()
                composeView = null
                super.dismiss()
                Timber.i("[UI_PROCESS] ComposePresentation dismissed for plugin: $pluginId")
            }
        }
    }
}
