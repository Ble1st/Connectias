package com.ble1st.connectias.feature.deviceinfo.battery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.deviceinfo.databinding.FragmentBatteryAnalyzerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Battery Analyzer.
 */
@AndroidEntryPoint
class BatteryAnalyzerFragment : Fragment() {

    private var _binding: FragmentBatteryAnalyzerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BatteryAnalyzerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBatteryAnalyzerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupClickListeners()
        
        // Load initial battery info
        viewModel.getBatteryInfo()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.batteryState.collect { state ->
                when (state) {
                    is BatteryState.Idle -> {
                        // Initial state
                    }
                    is BatteryState.Info -> {
                        displayBatteryInfo(state.info)
                    }
                    is BatteryState.TimeEstimate -> {
                        displayTimeEstimate(state.timeMs, state.isCharging)
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            viewModel.getBatteryInfo()
        }

        binding.startMonitoringButton.setOnClickListener {
            viewModel.startMonitoring()
        }

        binding.stopMonitoringButton.setOnClickListener {
            viewModel.stopMonitoring()
        }

        binding.estimateTimeButton.setOnClickListener {
            viewModel.estimateTimeToFullCharge()
            viewModel.estimateTimeToEmpty()
        }
    }

    private fun displayBatteryInfo(info: BatteryInfo) {
        binding.percentageText.text = "${info.percentage}%"
        binding.chargingStatusText.text = if (info.isCharging) "Charging" else "Not Charging"
        binding.healthText.text = "Health: ${info.healthStatus.name}"
        binding.chargeTypeText.text = "Charge Type: ${info.chargeType.name}"
        binding.voltageText.text = "${info.voltage} mV"
        binding.temperatureText.text = "${info.temperature}°C"
        binding.capacityText.text = if (info.capacity > 0) "${info.capacity / 1000} mAh" else "Unknown"
        binding.currentText.text = "${info.currentAverage / 1000} mA"
    }

    private fun displayTimeEstimate(timeMs: Long?, isCharging: Boolean) {
        if (timeMs != null) {
            val hours = timeMs / (1000 * 60 * 60)
            val minutes = (timeMs % (1000 * 60 * 60)) / (1000 * 60)
            val timeText = if (isCharging) {
                "Time to full charge: ${hours}h ${minutes}m"
            } else {
                "Time to empty: ${hours}h ${minutes}m"
            }
            binding.timeEstimateText.text = timeText
        } else {
            binding.timeEstimateText.text = "Unable to estimate"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopMonitoring()
        _binding = null
    }
}

