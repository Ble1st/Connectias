// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.ui.services

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.core.settings.SettingsRepository
import com.ble1st.connectias.core.servicestate.ServiceStateRepository
import com.ble1st.connectias.plugin.PluginManagerSandbox
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Fragment that displays the Services Dashboard: toggles for Plugin Sandbox,
 * Hardware Bridge, File System Bridge, Plugin Messaging, and Plugin UI.
 * Toggling updates ServiceStateRepository atomically with bind/unbind via applyServiceState.
 * Rolls back state on failure and shows a Snackbar error message.
 */
@AndroidEntryPoint
class ServicesDashboardFragment : Fragment() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var serviceStateRepository: ServiceStateRepository

    @Inject
    lateinit var pluginManager: PluginManagerSandbox

    private val _loadingServices = MutableStateFlow<Set<String>>(emptySet())
    private val _errorMessage = MutableStateFlow<String?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                val theme by settingsRepository.observeTheme().collectAsState(initial = "system")
                val themeStyleString by settingsRepository.observeThemeStyle().collectAsState(initial = "standard")
                val dynamicColor by settingsRepository.observeDynamicColor().collectAsState(initial = true)
                val themeStyle = remember(themeStyleString) {
                    com.ble1st.connectias.common.ui.theme.ThemeStyle.fromString(themeStyleString)
                }
                val state by serviceStateRepository.observeState.collectAsState(initial = emptyMap())
                val loadingServiceIds by _loadingServices.asStateFlow().collectAsState()
                val errorMessage by _errorMessage.asStateFlow().collectAsState()

                ConnectiasTheme(
                    themePreference = theme,
                    themeStyle = themeStyle,
                    dynamicColor = dynamicColor
                ) {
                    ServicesDashboardScreen(
                        state = state,
                        onToggle = { serviceId, enabled ->
                            toggleService(serviceId, enabled)
                        },
                        onNavigateBack = { findNavController().popBackStack() },
                        loadingServiceIds = loadingServiceIds,
                        errorMessage = errorMessage
                    )
                }
            }
        }
    }

    private fun toggleService(serviceId: String, enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Mark as loading — disables the Switch in UI
            _loadingServices.value = _loadingServices.value + serviceId
            _errorMessage.value = null

            // Optimistic state update
            serviceStateRepository.setEnabled(serviceId, enabled)

            try {
                pluginManager.applyServiceState(serviceId, enabled)
            } catch (e: Exception) {
                // Rollback on failure
                Timber.e(e, "[ServicesDashboard] Failed to apply service state for $serviceId")
                serviceStateRepository.setEnabled(serviceId, !enabled)
                _errorMessage.value = "Failed to ${if (enabled) "enable" else "disable"} ${serviceIdToLabel(serviceId)}"
            } finally {
                _loadingServices.value = _loadingServices.value - serviceId
            }
        }
    }
}
