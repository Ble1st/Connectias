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
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.feature.privacy.R
import com.ble1st.connectias.feature.privacy.databinding.FragmentPrivacyDashboardBinding
import com.ble1st.connectias.feature.privacy.models.PermissionRiskLevel
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
    private val binding get() = requireNotNull(_binding) { "Binding accessed after onDestroyView" }
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

        // Setup privacy tools navigation
        // Note: Navigation IDs are defined in app module's nav_graph.xml
        // Resolve navigation IDs at runtime to avoid compile-time dependency on app module's R class
        binding.buttonTrackerDetection.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_tracker_detection", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_tracker_detection not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to tracker detection")
            }
        }

        binding.buttonPermissionsAnalyzer.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_permissions_analyzer", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_permissions_analyzer not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to permissions analyzer")
            }
        }

        binding.buttonDataLeakage.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_data_leakage", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_data_leakage not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to data leakage")
            }
        }

        // Observe UI state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is PrivacyDashboardState.Loading -> {
                            binding.textPrivacyStatus.text = getString(R.string.privacy_loading)
                            binding.textPrivacyDetails.text = ""
                            binding.buttonRefresh.isEnabled = false
                        }
                        is PrivacyDashboardState.Success -> {
                            binding.buttonRefresh.isEnabled = true
                            updatePrivacyStatus(state.data)
                        }
                        is PrivacyDashboardState.Error -> {
                            Timber.e("Privacy dashboard error: %s", state.message)
                            binding.buttonRefresh.isEnabled = true
                            binding.textPrivacyStatus.text = getString(R.string.privacy_error_generic)
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
            PrivacyLevel.SECURE -> getString(R.string.privacy_level_secure)
            PrivacyLevel.WARNING -> getString(R.string.privacy_level_warning)
            PrivacyLevel.CRITICAL -> getString(R.string.privacy_level_critical)
            PrivacyLevel.UNKNOWN -> getString(R.string.privacy_level_unknown)
        }
        binding.textPrivacyStatus.text = statusText
        // Build detailed privacy information
        val detailsText = buildString {
            append(getString(R.string.privacy_overview))
            append("\n\n")
            
            append("${getString(R.string.privacy_network)}: ${formatPrivacyLevel(uiState.overallStatus.networkPrivacy)}\n")
            append("  - ${getString(R.string.privacy_dns)}: ${uiState.networkPrivacy.dnsStatus}\n")
            append("  - ${getString(R.string.privacy_vpn)}: ${if (uiState.networkPrivacy.vpnActive) getString(R.string.privacy_active) else getString(R.string.privacy_inactive)}\n")
            append("  - ${getString(R.string.privacy_type)}: ${uiState.networkPrivacy.networkType}\n\n")

            append("${getString(R.string.privacy_sensor)}: ${formatPrivacyLevel(uiState.overallStatus.sensorPrivacy)}\n")
            append("  - ${getString(R.string.privacy_apps_with_sensor_access)}: ${uiState.sensorPrivacy.totalAppsWithSensorAccess}\n\n")

            append("${getString(R.string.privacy_location)}: ${formatPrivacyLevel(uiState.overallStatus.locationPrivacy)}\n")
            append("  - ${getString(R.string.privacy_mock_location)}: ${if (uiState.locationPrivacy.mockLocationEnabled) getString(R.string.privacy_enabled) else getString(R.string.privacy_disabled)}\n")
            append("  - ${getString(R.string.privacy_location_services)}: ${if (uiState.locationPrivacy.locationServicesEnabled) getString(R.string.privacy_enabled) else getString(R.string.privacy_disabled)}\n")
            append("  - ${getString(R.string.privacy_apps_with_location_access)}: ${uiState.locationPrivacy.appsWithLocationAccess.size}\n\n")

            append("${getString(R.string.privacy_permissions)}: ${formatPrivacyLevel(uiState.overallStatus.permissionsPrivacy)}\n")
            append("  - ${getString(R.string.privacy_total_apps)}: ${uiState.appPermissions.size}\n")
            val highRiskApps = uiState.appPermissions.count { it.riskLevel == PermissionRiskLevel.HIGH }
            append("  - ${getString(R.string.privacy_high_risk_apps)}: $highRiskApps\n\n")
            append("${getString(R.string.privacy_background)}: ${formatPrivacyLevel(uiState.overallStatus.backgroundPrivacy)}\n")
            append("  - ${getString(R.string.privacy_running_services)}: ${uiState.backgroundActivity.totalRunningServices}\n")
            append("  - ${getString(R.string.privacy_apps_ignoring_battery)}: ${uiState.backgroundActivity.appsIgnoringBatteryOptimization.size}\n\n")

            append("${getString(R.string.privacy_storage)}: ${formatPrivacyLevel(uiState.overallStatus.storagePrivacy)}\n")
            append("  - ${getString(R.string.privacy_scoped_storage)}: ${if (uiState.storagePrivacy.scopedStorageEnabled) getString(R.string.privacy_enabled) else getString(R.string.privacy_disabled)}\n")
            append("  - ${getString(R.string.privacy_legacy_mode)}: ${if (uiState.storagePrivacy.legacyStorageMode) getString(R.string.privacy_enabled) else getString(R.string.privacy_disabled)}\n")
            append("  - ${getString(R.string.privacy_apps_with_storage_access)}: ${uiState.storagePrivacy.appsWithStorageAccess.size}\n")
        }

        binding.textPrivacyDetails.text = detailsText
    }

    private fun formatPrivacyLevel(level: PrivacyLevel): String {
        return when (level) {
            PrivacyLevel.SECURE -> getString(R.string.privacy_level_secure)
            PrivacyLevel.WARNING -> getString(R.string.privacy_level_warning)
            PrivacyLevel.CRITICAL -> getString(R.string.privacy_level_critical)
            PrivacyLevel.UNKNOWN -> getString(R.string.privacy_level_unknown)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

