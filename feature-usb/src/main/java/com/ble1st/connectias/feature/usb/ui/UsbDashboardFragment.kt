package com.ble1st.connectias.feature.usb.ui

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
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.common.ui.theme.ObserveThemeSettings
import com.ble1st.connectias.core.settings.SettingsRepository
import com.ble1st.connectias.feature.usb.detection.UsbDeviceDetector
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Fragment for USB Dashboard feature.
 */
@AndroidEntryPoint
class UsbDashboardFragment : Fragment() {
    
    private val viewModel: UsbDashboardViewModel by viewModels()

    @Inject lateinit var deviceDetector: UsbDeviceDetector
    @Inject lateinit var settingsRepository: SettingsRepository
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Register BroadcastReceiver for auto-detection
        try {
            deviceDetector.registerReceiver()
            viewModel.onReceiverRegistered()
        } catch (e: Exception) {
            Timber.e(e, "Failed to register USB BroadcastReceiver")
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Unregister BroadcastReceiver
        try {
            deviceDetector.unregisterReceiver()
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering USB BroadcastReceiver")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ObserveThemeSettings(settingsRepository) { theme, themeStyle, dynamicColor ->
                    ConnectiasTheme(
                        themePreference = theme,
                        themeStyle = themeStyle,
                        dynamicColor = dynamicColor
                    ) {
                        val state by viewModel.uiState.collectAsState()
                        
                        UsbDashboardScreen(
                            usbDevices = state.devices,
                            selectedDevice = state.selectedDevice,
                            onDeviceClick = { device -> viewModel.selectDevice(device) },
                            onDismissDialog = { viewModel.clearSelection() },
                            onViewDetails = { device ->
                                // TODO: Navigate to details
                            },
                            onRefresh = { viewModel.refreshDevices() }
                        )
                    }
                }
            }
        }
    }
}