package com.ble1st.connectias.feature.deviceinfo.ui

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
import com.ble1st.connectias.feature.deviceinfo.databinding.FragmentDeviceInfoBinding
import com.ble1st.connectias.feature.deviceinfo.provider.DeviceInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

@AndroidEntryPoint
class DeviceInfoFragment : Fragment() {

    private var _binding: FragmentDeviceInfoBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeviceInfoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Lifecycle-aware collection: only collect when view is STARTED
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deviceInfo.collect { state ->
                    when (state) {
                        is DeviceInfoState.Loading -> {
                            binding.textDeviceInfo.text = "Loading device information..."
                        }
                        is DeviceInfoState.Success -> {
                            updateUI(state.info)
                        }
                        is DeviceInfoState.Error -> {
                            binding.textDeviceInfo.text = "Error: ${state.message}"
                        }
                    }
                }
            }
        }

        binding.buttonRefresh.setOnClickListener {
            viewModel.refresh()
        }

        // Setup device info tools navigation
        // Note: Navigation IDs are defined in app module's nav_graph.xml
        // Resolve navigation IDs at runtime to avoid compile-time dependency on app module's R class
        binding.buttonBatteryAnalyzer.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_battery_analyzer", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_battery_analyzer not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to battery analyzer")
            }
        }

        binding.buttonStorageAnalyzer.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_storage_analyzer", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_storage_analyzer not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to storage analyzer")
            }
        }

        binding.buttonProcessMonitor.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_process_monitor", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_process_monitor not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to process monitor")
            }
        }

        binding.buttonSensorMonitor.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_sensor_monitor", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_sensor_monitor not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to sensor monitor")
            }
        }
    }

    private fun updateUI(info: DeviceInfo) {
        val osInfo = info.osInfo
        val cpuInfo = info.cpuInfo
        val ramInfo = info.ramInfo
        val storageInfo = info.storageInfo
        val networkInfo = info.networkInfo

        val deviceInfoText = """
            OS: ${osInfo.version} (SDK ${osInfo.sdkVersion})
            Manufacturer: ${osInfo.manufacturer}
            Model: ${osInfo.model}
            
            CPU: ${cpuInfo.cores} cores, ${cpuInfo.architecture}
            Frequency: ${cpuInfo.frequency} MHz
            
            RAM: ${formatBytes(ramInfo.used)} / ${formatBytes(ramInfo.total)} (${String.format(Locale.getDefault(), "%.1f", ramInfo.percentageUsed)}%)
            
            Storage: ${formatBytes(storageInfo.used)} / ${formatBytes(storageInfo.total)} (${String.format(Locale.getDefault(), "%.1f", storageInfo.percentageUsed)}%)
            
            Network:
            IP: ${networkInfo.ipAddress ?: "Not available"}
            Android ID: ${networkInfo.androidId ?: "Not available"}
        """.trimIndent()

        binding.textDeviceInfo.text = deviceInfoText
    }

    private fun formatBytes(bytes: Long): String {
        return android.text.format.Formatter.formatShortFileSize(requireContext(), bytes)
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

