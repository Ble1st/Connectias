package com.ble1st.connectias.feature.privacy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ble1st.connectias.feature.privacy.databinding.FragmentPrivacyDashboardBinding
import com.ble1st.connectias.feature.privacy.models.PrivacyLevel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Fragment displaying privacy dashboard with all privacy categories.
 */
@AndroidEntryPoint
class PrivacyDashboardFragment : Fragment() {

    private var _binding: FragmentPrivacyDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PrivacyDashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrivacyDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup refresh button
        binding.buttonRefresh.setOnClickListener {
            viewModel.refresh()
        }

        // Observe UI state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is PrivacyDashboardState.Loading -> {
                            binding.textPrivacyStatus.text = "Loading privacy information..."
                            binding.textPrivacyDetails.text = ""
                            binding.buttonRefresh.isEnabled = false
                        }
                        is PrivacyDashboardState.Success -> {
                            binding.buttonRefresh.isEnabled = true
                            updatePrivacyStatus(state.data)
                        }
                        is PrivacyDashboardState.Error -> {
                            binding.buttonRefresh.isEnabled = true
                            binding.textPrivacyStatus.text = "Error: ${state.message}"
                            binding.textPrivacyDetails.text = ""
                        }
                    }
                }
            }
        }
    }

    private fun updatePrivacyStatus(uiState: UiState) {
        val overallLevel = uiState.overallStatus.overallLevel
        val statusText = when (overallLevel) {
            PrivacyLevel.SECURE -> "✓ Privacy Status: Secure"
            PrivacyLevel.WARNING -> "⚠ Privacy Status: Warning"
            PrivacyLevel.CRITICAL -> "⚠ Privacy Status: Critical"
            PrivacyLevel.UNKNOWN -> "? Privacy Status: Unknown"
        }
        binding.textPrivacyStatus.text = statusText

        // Build detailed privacy information
        val detailsText = buildString {
            append("Privacy Overview\n\n")
            
            append("Network Privacy: ${formatPrivacyLevel(uiState.overallStatus.networkPrivacy)}\n")
            append("  - DNS: ${uiState.networkPrivacy.dnsStatus}\n")
            append("  - VPN: ${if (uiState.networkPrivacy.vpnActive) "Active" else "Inactive"}\n")
            append("  - Type: ${uiState.networkPrivacy.networkType}\n\n")

            append("Sensor Privacy: ${formatPrivacyLevel(uiState.overallStatus.sensorPrivacy)}\n")
            append("  - Apps with sensor access: ${uiState.sensorPrivacy.totalAppsWithSensorAccess}\n\n")

            append("Location Privacy: ${formatPrivacyLevel(uiState.overallStatus.locationPrivacy)}\n")
            append("  - Mock location: ${if (uiState.locationPrivacy.mockLocationEnabled) "Enabled" else "Disabled"}\n")
            append("  - Location services: ${if (uiState.locationPrivacy.locationServicesEnabled) "Enabled" else "Disabled"}\n")
            append("  - Apps with location access: ${uiState.locationPrivacy.appsWithLocationAccess.size}\n\n")

            append("Permissions Privacy: ${formatPrivacyLevel(uiState.overallStatus.permissionsPrivacy)}\n")
            append("  - Total apps: ${uiState.appPermissions.size}\n")
            val highRiskApps = uiState.appPermissions.count { it.riskLevel == com.ble1st.connectias.feature.privacy.models.PermissionRiskLevel.HIGH }
            append("  - High risk apps: $highRiskApps\n\n")

            append("Background Activity: ${formatPrivacyLevel(uiState.overallStatus.backgroundPrivacy)}\n")
            append("  - Running services: ${uiState.backgroundActivity.totalRunningServices}\n")
            append("  - Apps ignoring battery optimization: ${uiState.backgroundActivity.appsIgnoringBatteryOptimization.size}\n\n")

            append("Storage Privacy: ${formatPrivacyLevel(uiState.overallStatus.storagePrivacy)}\n")
            append("  - Scoped storage: ${if (uiState.storagePrivacy.scopedStorageEnabled) "Enabled" else "Disabled"}\n")
            append("  - Legacy mode: ${if (uiState.storagePrivacy.legacyStorageMode) "Enabled" else "Disabled"}\n")
            append("  - Apps with storage access: ${uiState.storagePrivacy.appsWithStorageAccess.size}\n")
        }

        binding.textPrivacyDetails.text = detailsText
    }

    private fun formatPrivacyLevel(level: PrivacyLevel): String {
        return when (level) {
            PrivacyLevel.SECURE -> "✓ Secure"
            PrivacyLevel.WARNING -> "⚠ Warning"
            PrivacyLevel.CRITICAL -> "⚠ Critical"
            PrivacyLevel.UNKNOWN -> "? Unknown"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

