package com.ble1st.connectias.feature.usb.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.common.ui.theme.ObserveThemeSettings
import com.ble1st.connectias.core.settings.SettingsRepository
import com.ble1st.connectias.feature.usb.detection.UsbDeviceDetector
import com.ble1st.connectias.feature.usb.models.UsbDevice
import com.ble1st.connectias.feature.usb.permission.UsbPermissionManager
import com.ble1st.connectias.feature.usb.provider.UsbProvider
import com.ble1st.connectias.feature.usb.provider.UsbResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Fragment for USB Dashboard feature.
 */
@AndroidEntryPoint
class UsbDashboardFragment : Fragment() {
    
    @Inject lateinit var usbProvider: UsbProvider
    @Inject lateinit var permissionManager: UsbPermissionManager
    @Inject lateinit var deviceDetector: UsbDeviceDetector
    @Inject lateinit var settingsRepository: SettingsRepository
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("UsbDashboardFragment: onViewCreated")
        
        // BroadcastReceiver für automatische Erkennung registrieren
        try {
            deviceDetector.registerReceiver()
            Timber.d("USB BroadcastReceiver registered")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register USB BroadcastReceiver")
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        Timber.d("UsbDashboardFragment: onDestroyView")
        
        // BroadcastReceiver unregistrieren
        try {
            deviceDetector.unregisterReceiver()
            Timber.d("USB BroadcastReceiver unregistered")
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering USB BroadcastReceiver")
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.d("UsbDashboardFragment: onCreateView")
        
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ObserveThemeSettings(settingsRepository) { theme, themeStyle, dynamicColor ->
                    ConnectiasTheme(
                        themePreference = theme,
                        themeStyle = themeStyle,
                        dynamicColor = dynamicColor
                    ) {
                        // Local state for devices
                        var usbDevices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }
                        var selectedDevice by remember { mutableStateOf<UsbDevice?>(null) }
                        var isRefreshing by remember { mutableStateOf(false) }
                        
                        // Function to refresh devices
                        fun refreshDevices() {
                            isRefreshing = true
                            lifecycleScope.launch {
                                when (val result = usbProvider.enumerateDevices()) {
                                    is UsbResult.Success -> {
                                        usbDevices = result.data
                                        Timber.d("Refreshed: ${usbDevices.size} devices")
                                    }
                                    is UsbResult.Failure -> {
                                        Timber.e(result.error, "Failed to refresh devices")
                                        usbDevices = emptyList()
                                    }
                                }
                                isRefreshing = false
                            }
                        }
                        
                        // Initial load
                        LaunchedEffect(Unit) {
                            refreshDevices()
                        }
                        
                        UsbDashboardScreen(
                            usbDevices = usbDevices,
                            selectedDevice = selectedDevice,
                            onDeviceClick = { device ->
                                Timber.d("Device clicked: ${device.product}")
                                selectedDevice = device
                            },
                            onDismissDialog = {
                                selectedDevice = null
                            },
                            onViewDetails = { device ->
                                Timber.d("View details for: ${device.product}")
                                // Show details (not implemented yet)
                            },
                            onRefresh = {
                                refreshDevices()
                            }
                        )
                    }
                }
            }
        }
    }
}