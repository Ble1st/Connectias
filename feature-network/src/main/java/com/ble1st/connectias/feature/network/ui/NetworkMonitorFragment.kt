package com.ble1st.connectias.feature.network.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.network.databinding.FragmentNetworkMonitorBinding
import com.ble1st.connectias.feature.network.monitor.NetworkMonitorProvider
import com.ble1st.connectias.feature.network.monitor.NetworkTraffic
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fragment for Network Traffic Monitor.
 */
@AndroidEntryPoint
class NetworkMonitorFragment : Fragment() {

    private var _binding: FragmentNetworkMonitorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NetworkMonitorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNetworkMonitorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupClickListeners()
        
        // Start monitoring
        viewModel.startMonitoring()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.trafficFlow.collect { traffic ->
                updateTrafficDisplay(traffic)
            }
        }
    }

    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            viewModel.refreshCurrentTraffic()
        }
    }

    private fun updateTrafficDisplay(traffic: NetworkTraffic) {
        binding.connectionTypeText.text = "Connection: ${traffic.connectionType.name}"
        binding.rxBytesText.text = formatBytes(traffic.rxBytes)
        binding.txBytesText.text = formatBytes(traffic.txBytes)
        binding.totalBytesText.text = formatBytes(traffic.rxBytes + traffic.txBytes)
        
        if (traffic.rxRate > 0 || traffic.txRate > 0) {
            binding.rxRateText.text = "${formatBytesPerSecond(traffic.rxRate)}/s"
            binding.txRateText.text = "${formatBytesPerSecond(traffic.txRate)}/s"
        } else {
            binding.rxRateText.text = "0 B/s"
            binding.txRateText.text = "0 B/s"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    private fun formatBytesPerSecond(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1_000_000_000 -> String.format("%.2f GB", bytesPerSecond / 1_000_000_000.0)
            bytesPerSecond >= 1_000_000 -> String.format("%.2f MB", bytesPerSecond / 1_000_000.0)
            bytesPerSecond >= 1_000 -> String.format("%.2f KB", bytesPerSecond / 1_000.0)
            else -> String.format("%.0f B", bytesPerSecond)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopMonitoring()
        _binding = null
    }
}

/**
 * ViewModel for Network Monitor.
 */
@HiltViewModel
class NetworkMonitorViewModel @Inject constructor(
    private val networkMonitorProvider: NetworkMonitorProvider
) : ViewModel() {

    private val _trafficFlow = MutableStateFlow<NetworkTraffic>(
        NetworkTraffic(0, 0, 0.0, 0.0, com.ble1st.connectias.feature.network.monitor.ConnectionType.NONE, System.currentTimeMillis())
    )
    val trafficFlow: StateFlow<NetworkTraffic> = _trafficFlow.asStateFlow()

    private var monitoringJob: Job? = null

    fun startMonitoring() {
        if (monitoringJob == null || monitoringJob?.isCompleted == true) {
            monitoringJob = viewModelScope.launch {
                networkMonitorProvider.monitorTraffic(intervalMs = 1000).collect { traffic ->
                    _trafficFlow.value = traffic
                }
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    fun refreshCurrentTraffic() {
        viewModelScope.launch {
            val traffic = networkMonitorProvider.getCurrentTraffic()
            _trafficFlow.value = traffic
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}

