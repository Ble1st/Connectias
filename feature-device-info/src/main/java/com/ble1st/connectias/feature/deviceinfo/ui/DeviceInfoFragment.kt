package com.ble1st.connectias.feature.deviceinfo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.deviceinfo.databinding.FragmentDeviceInfoBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

        viewLifecycleOwner.lifecycleScope.launch {
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

        binding.buttonRefresh.setOnClickListener {
            viewModel.refresh()
        }
    }

    private fun updateUI(info: com.ble1st.connectias.feature.deviceinfo.provider.DeviceInfo) {
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
            
            RAM: ${formatBytes(ramInfo.used)} / ${formatBytes(ramInfo.total)} (${String.format("%.1f", ramInfo.percentageUsed)}%)
            
            Storage: ${formatBytes(storageInfo.used)} / ${formatBytes(storageInfo.total)} (${String.format("%.1f", storageInfo.percentageUsed)}%)
            
            Network:
            IP: ${networkInfo.ipAddress ?: "Not available"}
            MAC: ${networkInfo.macAddress ?: "Not available"}
        """.trimIndent()

        binding.textDeviceInfo.text = deviceInfoText
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

