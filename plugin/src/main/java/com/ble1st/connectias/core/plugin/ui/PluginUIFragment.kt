// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.ble1st.connectias.analytics.ui.PluginUiActionLogger
import com.ble1st.connectias.analytics.model.SessionEventType
import com.ble1st.connectias.plugin.ui.IPluginUIBridge
import com.ble1st.connectias.plugin.ui.UIEventParcel
import com.ble1st.connectias.plugin.ui.UIStateParcel
import com.ble1st.connectias.plugin.ui.UserActionParcel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Fragment running in UI Process that renders plugin UI based on state.
 *
 * Three-Process Architecture:
 * - Receives UIStateParcel from Sandbox Process via IPluginUIController
 * - Renders UI using Jetpack Compose
 * - Forwards user interactions to Sandbox via IPluginUIBridge
 *
 * This fragment does NOT contain business logic - it only renders state
 * and forwards events. All logic resides in the Sandbox Process.
 */
class PluginUIFragment : Fragment() {

    private lateinit var pluginId: String
    private var configuration: Bundle? = null
    private var uiBridge: IPluginUIBridge? = null

    // UI state (updated by Sandbox via IPluginUIController)
    private var uiState by mutableStateOf<UIStateParcel?>(null)
    
    // Dialog state
    private var dialogState by mutableStateOf<DialogState?>(null)
    
    // Toast state
    private var toastState by mutableStateOf<ToastState?>(null)
    
    // Loading state
    private var loadingState by mutableStateOf<LoadingState?>(null)
    
    // Navigation state
    private var navigationState by mutableStateOf<NavigationState?>(null)
    
    // UI Event handler
    private var uiEventHandler: ((UIEventParcel) -> Unit)? = null

    // IME proxy: text from Main Process overlay (componentId -> text)
    private val imeTextByComponent = mutableStateMapOf<String, String>()
    private var imeRequestHandler: ((componentId: String, initialText: String) -> Unit)? = null
    
    /**
     * Dialog state data class
     */
    data class DialogState(
        val title: String,
        val message: String,
        val dialogType: Int // 0=INFO, 1=WARNING, 2=ERROR, 3=CONFIRM
    )
    
    /**
     * Toast state data class
     */
    data class ToastState(
        val message: String,
        val duration: Int // 0=SHORT, 1=LONG
    )
    
    /**
     * Loading state data class
     */
    data class LoadingState(
        val loading: Boolean,
        val message: String?
    )
    
    /**
     * Navigation state data class
     */
    data class NavigationState(
        val screenId: String,
        val args: Bundle?
    )

    companion object {
        /**
         * Creates a new instance of PluginUIFragment.
         *
         * @param pluginId Plugin identifier
         * @param config UI configuration from Main Process
         */
        fun newInstance(pluginId: String, config: Bundle): PluginUIFragment {
            return PluginUIFragment().apply {
                arguments = Bundle().apply {
                    putString("pluginId", pluginId)
                    putBundle("config", config)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Only call super.onCreate() if fragment is attached to a FragmentManager
        // When used in Presentation context, there's no FragmentManager
        try {
            super.onCreate(savedInstanceState)
        } catch (e: Exception) {
            // FragmentManager not available (e.g., in Presentation context)
            // This is expected and we can continue without it
            Timber.d("[UI_PROCESS] Fragment.onCreate() called without FragmentManager (expected in Presentation context)")
        }

        pluginId = arguments?.getString("pluginId")
            ?: throw IllegalArgumentException("PluginUIFragment requires pluginId")
        configuration = arguments?.getBundle("config")

        Timber.i("[UI_PROCESS] Fragment created for plugin: $pluginId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.d("[UI_PROCESS] onCreateView for plugin: $pluginId")

        // Notify Sandbox: Fragment created
        try {
            uiBridge?.onLifecycleEvent(pluginId, "onCreate")
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to notify onCreate to sandbox")
        }

        return ComposeView(requireContext()).apply {
            // CRITICAL: Make ComposeView focusable so it can receive IME focus
            isFocusable = true
            isFocusableInTouchMode = true

            setContent {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                
                // Observe toast state and show snackbar
                LaunchedEffect(toastState) {
                    toastState?.let { toast ->
                        scope.launch {
                            val duration = if (toast.duration == 1) 
                                androidx.compose.material3.SnackbarDuration.Long 
                            else 
                                androidx.compose.material3.SnackbarDuration.Short
                            
                            snackbarHostState.showSnackbar(
                                message = toast.message,
                                duration = duration
                            )
                            // Clear toast state after showing
                            toastState = null
                        }
                    }
                }
                
                ConnectiasPluginUiTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PluginUIComposable(
                            pluginId = pluginId,
                            uiState = uiState,
                            dialogState = dialogState,
                            loadingState = loadingState,
                            imeTextByComponent = imeTextByComponent,
                            onRequestShowIme = { componentId, initialText ->
                                imeRequestHandler?.invoke(componentId, initialText)
                            },
                            onUserAction = { action ->
                                handleUserAction(action)
                            },
                            onDismissDialog = {
                                dialogState = null
                            }
                        )
                        
                        // Snackbar host for toasts
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("[UI_PROCESS] onResume for plugin: $pluginId")

        // Record session start for analytics
        try {
            PluginUiActionLogger.recordSession(
                context = requireContext(),
                pluginId = pluginId,
                eventType = SessionEventType.FOREGROUND_START
            )
        } catch (e: Exception) {
            Timber.w(e, "[ANALYTICS] Failed to record session start for plugin: $pluginId")
        }

        try {
            uiBridge?.onLifecycleEvent(pluginId, "onResume")
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to notify onResume to sandbox")
        }
    }

    override fun onPause() {
        super.onPause()
        Timber.d("[UI_PROCESS] onPause for plugin: $pluginId")

        // Record session end for analytics
        try {
            PluginUiActionLogger.recordSession(
                context = requireContext(),
                pluginId = pluginId,
                eventType = SessionEventType.FOREGROUND_END
            )
        } catch (e: Exception) {
            Timber.w(e, "[ANALYTICS] Failed to record session end for plugin: $pluginId")
        }

        try {
            uiBridge?.onLifecycleEvent(pluginId, "onPause")
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to notify onPause to sandbox")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("[UI_PROCESS] onDestroy for plugin: $pluginId")

        try {
            uiBridge?.onLifecycleEvent(pluginId, "onDestroy")
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to notify onDestroy to sandbox")
        }

        // Cleanup
        uiBridge = null
        uiState = null
    }

    /**
     * Updates UI state from Sandbox Process.
     * Called by UI-Controller implementation.
     *
     * @param newState New UI state to render
     */
    fun updateState(newState: UIStateParcel) {
        // Get pluginId from arguments if not yet initialized (may be called before onCreate)
        val currentPluginId = if (::pluginId.isInitialized) {
            pluginId
        } else {
            arguments?.getString("pluginId") ?: run {
                Timber.e("[UI_PROCESS] Cannot update state - pluginId not available")
                return
            }
        }
        
        Timber.d("[UI_PROCESS] Update UI state for $currentPluginId: ${newState.screenId}")
        uiState = newState
        // Compose will automatically recompose when state changes
    }

    /**
     * Sets UI bridge for communication with Sandbox Process.
     *
     * @param bridge IPluginUIBridge instance
     */
    fun setUIBridge(bridge: IPluginUIBridge) {
        // pluginId might not be initialized yet if called before onCreate()
        val currentPluginId = arguments?.getString("pluginId") ?: pluginId
        Timber.d("[UI_PROCESS] Set UI bridge for plugin: $currentPluginId")
        this.uiBridge = bridge
    }

    /**
     * Handles user actions and forwards them to Sandbox.
     *
     * @param action User action data
     */
    private fun handleUserAction(action: UserActionParcel) {
        Timber.d("[UI_PROCESS] User action: ${action.actionType} on ${action.targetId}")

        try {
            // Record for admin analytics (best-effort).
            PluginUiActionLogger.record(
                context = requireContext(),
                pluginId = pluginId,
                actionType = action.actionType ?: "unknown",
                targetId = action.targetId ?: "unknown"
            )
            uiBridge?.onUserAction(pluginId, action)
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to send user action to sandbox")
        }
    }

    /**
     * Sets fragment visibility.
     *
     * @param visible true to show, false to hide
     */
    fun setVisibility(visible: Boolean) {
        view?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Destroys fragment and cleans up resources.
     */
    fun destroy() {
        Timber.d("[UI_PROCESS] Destroying fragment for plugin: $pluginId")

        // Finish the hosting Activity if it exists
        try {
            val activity = activity
            if (activity != null && activity is PluginUIActivity) {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    Timber.d("[UI_PROCESS] Finishing PluginUIActivity for plugin: $pluginId")
                    activity.finish()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[UI_PROCESS] Could not finish Activity for plugin: $pluginId")
        }

        // Remove fragment from FragmentManager if attached
        try {
            if (isAdded && fragmentManager != null) {
                fragmentManager?.beginTransaction()
                    ?.remove(this)
                    ?.commitAllowingStateLoss()
            }
        } catch (e: Exception) {
            Timber.w(e, "[UI_PROCESS] Could not remove fragment from FragmentManager for plugin: $pluginId")
        }

        // Cleanup references
        uiBridge = null
        uiState = null
        configuration = null
    }

    /**
     * Gets current UI state (for debugging).
     */
    fun getCurrentState(): UIStateParcel? = uiState

    /**
     * Gets plugin ID.
     */
    fun getPluginId(): String = pluginId
    
    /**
     * Shows a dialog.
     */
    fun showDialog(title: String, message: String, dialogType: Int) {
        Timber.d("[UI_PROCESS] Show dialog: $title")
        dialogState = DialogState(title, message, dialogType)
    }
    
    /**
     * Shows a toast.
     */
    fun showToast(message: String, duration: Int) {
        Timber.d("[UI_PROCESS] Show toast: $message")
        toastState = ToastState(message, duration)
    }
    
    /**
     * Sets loading state.
     */
    fun setLoading(loading: Boolean, message: String?) {
        Timber.d("[UI_PROCESS] Set loading: $loading")
        loadingState = if (loading) LoadingState(true, message) else null
    }
    
    /**
     * Navigates to a screen.
     */
    fun navigateToScreen(screenId: String, args: Bundle?) {
        Timber.d("[UI_PROCESS] Navigate to: $screenId")
        navigationState = NavigationState(screenId, args)
        // Navigation is handled by updating UI state, so we just log it
        // The sandbox should send a new UIStateParcel with the new screen
    }
    
    /**
     * Navigates back.
     */
    fun navigateBack() {
        Timber.d("[UI_PROCESS] Navigate back")
        // Back navigation is handled by updating UI state
        // The sandbox should send a new UIStateParcel with the previous screen
    }
    
    /**
     * Handles UI events.
     */
    fun handleUIEvent(event: UIEventParcel) {
        Timber.d("[UI_PROCESS] Handle UI event: ${event.eventType}")
        uiEventHandler?.invoke(event)
    }
    
    /**
     * Sets UI event handler.
     */
    fun setUIEventHandler(handler: (UIEventParcel) -> Unit) {
        uiEventHandler = handler
    }

    /**
     * Sets handler for IME (keyboard) requests. When a TextField gains focus,
     * this is called so the host can request the Main Process to show the keyboard.
     */
    fun setImeRequestHandler(handler: (componentId: String, initialText: String) -> Unit) {
        imeRequestHandler = handler
    }

    /**
     * Updates text for a component (from Main Process overlay).
     * Also forwards a text_changed action to the sandbox so the plugin receives the new value.
     */
    fun updateImeText(componentId: String, text: String) {
        val bridge = uiBridge
        val pid = pluginId
        // Update UI state (must run on main thread for Compose)
        view?.post {
            imeTextByComponent[componentId] = text
        } ?: run { imeTextByComponent[componentId] = text }
        // Notify sandbox so the plugin works with the new value (same as typing in the field)
        try {
            val action = UserActionParcel().apply {
                actionType = "text_changed"
                targetId = componentId
                data = Bundle().apply { putString("value", text) }
                timestamp = System.currentTimeMillis()
            }
            bridge?.onUserAction(pid, action)
            view?.post {
                try {
                    PluginUiActionLogger.record(
                        context = requireContext(),
                        pluginId = pid,
                        actionType = "text_changed",
                        targetId = componentId
                    )
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to forward IME text to sandbox for component: $componentId")
        }
    }

    /**
     * Called when IME was dismissed in Main Process.
     */
    fun onImeDismissed(componentId: String) {
        // Optional: clear focus state; text is already in imeTextByComponent
    }
}
