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
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.usb.detection.UsbDeviceDetector
import com.ble1st.connectias.feature.usb.models.UsbDevice
import com.ble1st.connectias.feature.usb.permission.UsbPermissionManager
import com.ble1st.connectias.feature.usb.provider.UsbProvider
import dagger.hilt.android.AndroidEntryPoint
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
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("UsbDashboardFragment: onViewCreated")
        
        // BroadcastReceiver für automatische Erkennung registrieren
        try {
            deviceDetector.registerReceiver(requireActivity())
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
                ConnectiasTheme {
                    UsbDashboardScreen(
                        usbProvider = usbProvider,
                        permissionManager = permissionManager,
                        deviceDetector = deviceDetector,
                        onDeviceClick = { device ->
                            Timber.d("Navigating to device details: ${device.product}")
                            // For non-mass storage devices, show device info
                            Timber.d("Device clicked: ${device.product}")
                        },
                                    findNavController().navigate(R.id.nav_dvd_cd_detail)
                                    Timber.d("Navigated to DVD/CD detail screen")
                                        findNavController().navigate(navId)
                                        Timber.d("Navigated to DVD/CD detail screen")
                                    } else {
                                        Timber.w("Navigation destination nav_dvd_cd_detail not found")
                                    }
                                } else {
                                    Timber.w("Device is not mass storage: ${device.product}")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error navigating to DVD/CD details")
                            }
                        },
                        activity = requireActivity()
                    )
                }
            }
        }
    }
}
