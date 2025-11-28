package com.ble1st.connectias.feature.network.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ble1st.connectias.feature.network.databinding.FragmentWifiAnalyzerBinding
import com.ble1st.connectias.feature.network.databinding.ItemWifiChannelBinding
import com.ble1st.connectias.feature.network.scanner.WifiAnalyzerProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fragment for WiFi Channel Analyzer.
 */
@AndroidEntryPoint
class WifiAnalyzerFragment : Fragment() {

    private var _binding: FragmentWifiAnalyzerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WifiAnalyzerViewModel by viewModels()
    private lateinit var channelAdapter: WifiChannelAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.analyzeWifiNetworks()
        } else {
            Toast.makeText(requireContext(), "Location permission required for WiFi scanning", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWifiAnalyzerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        channelAdapter = WifiChannelAdapter()
        binding.channelsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.channelsRecyclerView.adapter = channelAdapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.analyzerState.collect { state ->
                when (state) {
                    is WifiAnalyzerState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        channelAdapter.submitList(emptyList())
                        binding.analyzeButton.isEnabled = true
                    }
                    is WifiAnalyzerState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.analyzeButton.isEnabled = false
                    }
                    is WifiAnalyzerState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.analyzeButton.isEnabled = true
                        channelAdapter.submitList(state.channelInfos)
                        
                        val bestChannel = state.bestChannel
                        if (bestChannel != null) {
                            binding.recommendationText.text = "Recommended Channel: $bestChannel"
                            binding.recommendationText.visibility = View.VISIBLE
                        } else {
                            binding.recommendationText.visibility = View.GONE
                        }
                        
                        if (state.overlaps.isNotEmpty()) {
                            val overlapText = state.overlaps.joinToString("\n") { overlap ->
                                "Channels ${overlap.channel1} and ${overlap.channel2}: ${overlap.severity.name}"
                            }
                            binding.overlapText.text = "Overlaps detected:\n$overlapText"
                            binding.overlapText.visibility = View.VISIBLE
                        } else {
                            binding.overlapText.visibility = View.GONE
                        }
                    }
                    is WifiAnalyzerState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.analyzeButton.isEnabled = true
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.analyzeButton.setOnClickListener {
            if (checkLocationPermission()) {
                viewModel.analyzeWifiNetworks()
            } else {
                requestLocationPermissionIfNeeded()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * ViewModel for WiFi Analyzer.
 */
@HiltViewModel
class WifiAnalyzerViewModel @Inject constructor(
    private val wifiAnalyzerProvider: WifiAnalyzerProvider,
    private val wlanScanner: com.ble1st.connectias.feature.network.scanner.WlanScanner
) : ViewModel() {

    private val _analyzerState = MutableStateFlow<WifiAnalyzerState>(WifiAnalyzerState.Idle)
    val analyzerState: StateFlow<WifiAnalyzerState> = _analyzerState.asStateFlow()

    fun analyzeWifiNetworks() {
        viewModelScope.launch {
            _analyzerState.value = WifiAnalyzerState.Loading
            
            try {
                val wifiNetworks = wlanScanner.scan()
                
                if (wifiNetworks.isEmpty()) {
                    _analyzerState.value = WifiAnalyzerState.Error("No WiFi networks found")
                    return@launch
                }
                
                val channelInfos = wifiAnalyzerProvider.analyzeChannels(wifiNetworks)
                val overlaps = wifiAnalyzerProvider.detectChannelOverlap(channelInfos)
                
                // Determine band (simplified - check first network)
                val band = if (wifiNetworks.first().frequency in 2400..2499) {
                    WifiAnalyzerProvider.WifiBand.BAND_2_4_GHZ
                } else {
                    WifiAnalyzerProvider.WifiBand.BAND_5_GHZ
                }
                
                val bestChannel = wifiAnalyzerProvider.recommendBestChannel(band, channelInfos)
                
                _analyzerState.value = WifiAnalyzerState.Success(
                    channelInfos = channelInfos,
                    overlaps = overlaps,
                    bestChannel = bestChannel
                )
            } catch (e: Exception) {
                _analyzerState.value = WifiAnalyzerState.Error(e.message ?: "Analysis failed")
            }
        }
    }
}

/**
 * State representation for WiFi analyzer operations.
 */
sealed class WifiAnalyzerState {
    object Idle : WifiAnalyzerState()
    object Loading : WifiAnalyzerState()
    data class Success(
        val channelInfos: List<com.ble1st.connectias.feature.network.scanner.WifiChannelInfo>,
        val overlaps: List<com.ble1st.connectias.feature.network.scanner.ChannelOverlap>,
        val bestChannel: Int?
    ) : WifiAnalyzerState()
    data class Error(val message: String) : WifiAnalyzerState()
}

/**
 * Adapter for displaying WiFi channel information.
 */
class WifiChannelAdapter : ListAdapter<com.ble1st.connectias.feature.network.scanner.WifiChannelInfo, WifiChannelAdapter.ChannelViewHolder>(ChannelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemWifiChannelBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChannelViewHolder(
        private val binding: ItemWifiChannelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(channelInfo: com.ble1st.connectias.feature.network.scanner.WifiChannelInfo) {
            binding.channelText.text = "Channel ${channelInfo.channel}"
            binding.frequencyText.text = "${channelInfo.frequency} MHz"
            binding.networkCountText.text = "${channelInfo.networkCount} networks"
            binding.avgSignalText.text = "${channelInfo.avgSignalStrength} dBm"
            
            // Color code based on signal strength
            val color = when {
                channelInfo.avgSignalStrength >= -50 -> android.graphics.Color.parseColor("#4CAF50") // Green
                channelInfo.avgSignalStrength >= -70 -> android.graphics.Color.parseColor("#FF9800") // Orange
                else -> android.graphics.Color.parseColor("#F44336") // Red
            }
            binding.avgSignalText.setTextColor(color)
        }
    }

    class ChannelDiffCallback : DiffUtil.ItemCallback<com.ble1st.connectias.feature.network.scanner.WifiChannelInfo>() {
        override fun areItemsTheSame(
            oldItem: com.ble1st.connectias.feature.network.scanner.WifiChannelInfo,
            newItem: com.ble1st.connectias.feature.network.scanner.WifiChannelInfo
        ): Boolean {
            return oldItem.channel == newItem.channel
        }

        override fun areContentsTheSame(
            oldItem: com.ble1st.connectias.feature.network.scanner.WifiChannelInfo,
            newItem: com.ble1st.connectias.feature.network.scanner.WifiChannelInfo
        ): Boolean {
            return oldItem == newItem
        }
    }
}

