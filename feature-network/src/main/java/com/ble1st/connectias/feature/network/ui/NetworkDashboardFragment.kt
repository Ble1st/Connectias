package com.ble1st.connectias.feature.network.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.network.R
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Fragment for Network Dashboard.
 * Displays WiFi networks, LAN devices, and network analysis.
 */
@AndroidEntryPoint
class NetworkDashboardFragment : Fragment() {

    private val viewModel: NetworkDashboardViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.refreshWifiNetworks()
        } else {
            showPermissionDeniedMessage()
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
                ConnectiasTheme {
                    val state by viewModel.networkState.collectAsState()
                    
                    NetworkDashboardScreen(
                        state = state,
                        onRefreshWifi = {
                            if (checkLocationPermission()) {
                                viewModel.refreshWifiNetworks()
                            } else {
                                requestLocationPermissionIfNeeded()
                            }
                        },
                        onRefreshLan = { viewModel.refreshLocalDevices() },
                        onRefreshAnalysis = { viewModel.refreshAnalysis() },
                        onNavigateToPortScanner = { navigateTo("nav_port_scanner") },
                        onNavigateToDnsLookup = { navigateTo("nav_dns_lookup") },
                        onNavigateToNetworkMonitor = { navigateTo("nav_network_monitor") },
                        onNavigateToWifiAnalyzer = { navigateTo("nav_wifi_analyzer") },
                        onNavigateToMacAnalyzer = { navigateTo("nav_mac_analyzer") },
                        onNavigateToSubnetAnalyzer = { navigateTo("nav_subnet_analyzer") },
                        onNavigateToVlanAnalyzer = { navigateTo("nav_vlan_analyzer") },
                        onNavigateToTopology = { navigateTo("nav_topology") },
                        onNavigateToBandwidthMonitor = { navigateTo("nav_bandwidth_monitor") },
                        onNavigateToFlowAnalyzer = { navigateTo("nav_flow_analyzer") },
                        onNavigateToDhcpLease = { navigateTo("nav_dhcp_lease") },
                        onNavigateToHypervisorDetector = { navigateTo("nav_hypervisor_detector") }
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

    private fun checkLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    
    private fun showPermissionDeniedMessage() {
        // We need a view to show Snackbar. using the fragment's view (which is ComposeView)
        view?.let {
            Snackbar.make(
                it,
                getString(R.string.location_permission_required),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }
}

