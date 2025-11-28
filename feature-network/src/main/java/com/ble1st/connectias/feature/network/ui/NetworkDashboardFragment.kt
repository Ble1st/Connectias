package com.ble1st.connectias.feature.network.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.feature.network.R
import com.ble1st.connectias.feature.network.databinding.FragmentNetworkDashboardBinding
import com.ble1st.connectias.feature.network.models.DeviceType
import com.ble1st.connectias.feature.network.models.NetworkAnalysis
import com.ble1st.connectias.feature.network.models.NetworkDevice
import com.ble1st.connectias.feature.network.models.WifiNetwork
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Fragment for Network Dashboard.
 * Displays WiFi networks, LAN devices, and network analysis.
 */
@AndroidEntryPoint
class NetworkDashboardFragment : Fragment() {

    private var _binding: FragmentNetworkDashboardBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException(
        "Binding is null. Fragment view not created yet or already destroyed."
    )
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
        _binding = FragmentNetworkDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()

        // Lifecycle-aware collection: only collect when view is STARTED
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.networkState.collect { state ->
                    updateUI(state)
                }            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonRefreshWifi.setOnClickListener {
            if (checkLocationPermission()) {
                viewModel.refreshWifiNetworks()
            } else {
                requestLocationPermissionIfNeeded()
            }
        }

        binding.buttonRefreshLan.setOnClickListener {
            viewModel.refreshLocalDevices()
        }

        binding.buttonRefreshAnalysis.setOnClickListener {
            viewModel.refreshAnalysis()
        }

        // Setup network tools navigation
        // Note: Navigation IDs are defined in app module's nav_graph.xml
        // Resolve navigation IDs at runtime to avoid compile-time dependency on app module's R class
        binding.buttonPortScanner.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_port_scanner", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_port_scanner not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to port scanner")
            }
        }

        binding.buttonDnsLookup.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_dns_lookup", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_dns_lookup not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to DNS lookup")
            }
        }

        binding.buttonNetworkMonitor.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_network_monitor", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_network_monitor not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to network monitor")
            }
        }

        binding.buttonWifiAnalyzer.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_wifi_analyzer", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_wifi_analyzer not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to WiFi analyzer")
            }
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
        Snackbar.make(
            binding.root,
            getString(R.string.location_permission_required),
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun updateUI(state: NetworkState) {
        when (state) {
            is NetworkState.Loading -> {
                binding.textWifiNetworks.text = getString(R.string.loading_wifi_networks)
                binding.textLanDevices.text = getString(R.string.loading_lan_devices)
                binding.textNetworkAnalysis.text = getString(R.string.loading_network_analysis)
            }
            is NetworkState.Success -> {
                updateWifiNetworks(state.wifiNetworks)
                updateLanDevices(state.devices)
                updateNetworkAnalysis(state.analysis)
            }
            is NetworkState.PartialSuccess -> {
                updateWifiNetworks(state.wifiNetworks)
                updateLanDevices(state.devices)
                updateNetworkAnalysis(state.analysis)
                // Show error message as snackbar with localized prefix
                val errorMessage = state.errorMessage
                val displayMessage = if (errorMessage.isBlank()) {
                    getString(R.string.unknown_error)
                } else {
                    getString(R.string.partial_success_prefix, errorMessage)
                }
                Snackbar.make(
                    binding.root,
                    displayMessage,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            is NetworkState.Error -> {
                val errorText = getString(R.string.error_prefix, state.message)
                binding.textWifiNetworks.text = errorText
                binding.textLanDevices.text = errorText
                binding.textNetworkAnalysis.text = errorText
            }
        }
    }

    private fun updateWifiNetworks(networks: List<WifiNetwork>) {
        if (networks.isEmpty()) {
            binding.textWifiNetworks.text = getString(R.string.no_wifi_networks)
        } else {
            val wifiText = networks.joinToString("\n") { network ->
                "${network.ssid} (${network.signalStrength} dBm, ${network.encryptionType.displayName})"
            }
            binding.textWifiNetworks.text = wifiText
        }
    }

    private fun updateLanDevices(devices: List<NetworkDevice>) {
        if (devices.isEmpty()) {
            binding.textLanDevices.text = getString(R.string.no_lan_devices)
        } else {
            val devicesText = devices.joinToString("\n") { device ->
                "${device.ipAddress} - ${device.hostname} (${device.deviceType.name})"
            }
            binding.textLanDevices.text = devicesText
        }
    }

    private fun updateNetworkAnalysis(analysis: NetworkAnalysis) {
        val analysisText = """
            Connection Type: ${analysis.connectionType.name}
            Connected: ${if (analysis.isConnected) "Yes" else "No"}
            Gateway: ${analysis.gateway ?: getString(R.string.gateway_not_available)}
            DNS Servers: ${if (analysis.dnsServers.isEmpty()) getString(R.string.dns_servers_not_available) else analysis.dnsServers.joinToString(", ")}
        """.trimIndent()
        binding.textNetworkAnalysis.text = analysisText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

