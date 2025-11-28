package com.ble1st.connectias.feature.deviceinfo.sensor

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ble1st.connectias.feature.deviceinfo.databinding.FragmentSensorMonitorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Sensor Monitor.
 */
@AndroidEntryPoint
class SensorMonitorFragment : Fragment() {

    private var _binding: FragmentSensorMonitorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SensorMonitorViewModel by viewModels()
    private lateinit var sensorsAdapter: SensorsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSensorMonitorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        // Load available sensors
        viewModel.getAvailableSensors()
    }

    private fun setupRecyclerView() {
        sensorsAdapter = SensorsAdapter { sensorInfo ->
            viewModel.startMonitoring(sensorInfo.type)
        }
        binding.sensorsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.sensorsRecyclerView.adapter = sensorsAdapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sensorState.collect { state ->
                when (state) {
                    is SensorState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                    }
                    is SensorState.Sensors -> {
                        binding.progressBar.visibility = View.GONE
                        sensorsAdapter.submitList(state.sensors)
                    }
                    is SensorState.Data -> {
                        binding.progressBar.visibility = View.GONE
                        displaySensorData(state.data)
                    }
                    is SensorState.Error -> {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            viewModel.getAvailableSensors()
        }

        binding.stopMonitoringButton.setOnClickListener {
            viewModel.stopMonitoring()
            binding.sensorDataText.setText("")
        }
    }

    private fun displaySensorData(data: SensorData) {
        val valuesText = data.values.joinToString(", ") { String.format("%.2f", it) }
        val accuracyText = when (data.accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
            else -> "Unknown"
        }
        
        binding.sensorDataText.setText("""
            Sensor: ${data.sensorName}
            Values: $valuesText
            Accuracy: $accuracyText
            Timestamp: ${data.timestamp}
        """.trimIndent())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopMonitoring()
        _binding = null
    }
}

/**
 * Adapter for displaying sensors.
 */
class SensorsAdapter(
    private val onItemClick: (SensorInfo) -> Unit
) : androidx.recyclerview.widget.ListAdapter<SensorInfo, SensorsAdapter.SensorViewHolder>(SensorDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val binding = com.ble1st.connectias.feature.deviceinfo.databinding.ItemSensorBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SensorViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SensorViewHolder(
        private val binding: com.ble1st.connectias.feature.deviceinfo.databinding.ItemSensorBinding,
        private val onItemClick: (SensorInfo) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(sensor: SensorInfo) {
            binding.sensorNameText.text = sensor.name
            binding.sensorTypeText.text = "Type: ${sensor.type}"
            binding.sensorVendorText.text = "Vendor: ${sensor.vendor}"
            binding.sensorRangeText.text = "Max Range: ${sensor.maxRange}"
            binding.sensorResolutionText.text = "Resolution: ${sensor.resolution}"
            
            binding.root.setOnClickListener {
                onItemClick(sensor)
            }
        }
    }

    class SensorDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<SensorInfo>() {
        override fun areItemsTheSame(oldItem: SensorInfo, newItem: SensorInfo): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: SensorInfo, newItem: SensorInfo): Boolean {
            return oldItem == newItem
        }
    }
}

