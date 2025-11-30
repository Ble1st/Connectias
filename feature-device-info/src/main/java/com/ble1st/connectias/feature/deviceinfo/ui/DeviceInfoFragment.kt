package com.ble1st.connectias.feature.deviceinfo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class DeviceInfoFragment : Fragment() {

    private val viewModel: DeviceInfoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val state by viewModel.deviceInfo.collectAsState()
                    
                    DeviceInfoScreen(
                        state = state,
                        onRefresh = { viewModel.refresh() },
                        onNavigateToBatteryAnalyzer = { navigateTo("nav_battery_analyzer") },
                        onNavigateToStorageAnalyzer = { navigateTo("nav_storage_analyzer") },
                        onNavigateToProcessMonitor = { navigateTo("nav_process_monitor") },
                        onNavigateToSensorMonitor = { navigateTo("nav_sensor_monitor") }
                    )
                }
            }
        }
    }

    private fun navigateTo(resourceIdName: String) {
        try {
            val navId = resources.getIdentifier(resourceIdName, "id", requireContext().packageName)
            if (navId != 0) {
                findNavController().navigate(navId)
            } else {
                Timber.w("Navigation ID $resourceIdName not found")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to navigate to $resourceIdName")
        }
    }
}

