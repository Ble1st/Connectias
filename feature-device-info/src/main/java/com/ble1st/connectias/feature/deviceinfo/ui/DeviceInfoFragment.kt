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
import com.ble1st.connectias.feature.deviceinfo.databinding.FragmentDeviceInfoBinding
import com.ble1st.connectias.feature.deviceinfo.provider.DeviceInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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

