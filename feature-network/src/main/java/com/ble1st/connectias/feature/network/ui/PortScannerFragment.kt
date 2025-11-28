package com.ble1st.connectias.feature.network.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ble1st.connectias.feature.network.databinding.FragmentPortScannerBinding
import com.ble1st.connectias.feature.network.databinding.ItemPortScanBinding
import com.ble1st.connectias.feature.network.models.PortScanResult
import com.ble1st.connectias.feature.network.provider.PortScannerProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fragment for Port Scanner.
 */
@AndroidEntryPoint
class PortScannerFragment : Fragment() {

    private var _binding: FragmentPortScannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PortScannerViewModel by viewModels()
    private lateinit var portAdapter: PortScanAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPortScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        portAdapter = PortScanAdapter()
        binding.portsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.portsRecyclerView.adapter = portAdapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.portScanState.collect { state ->
                when (state) {
                    is PortScanState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        portAdapter.submitList(emptyList())
                        binding.scanButton.isEnabled = true
                    }
                    is PortScanState.Scanning -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.scanButton.isEnabled = false
                        binding.statusText.text = "Scanning ports..."
                    }
                    is PortScanState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.scanButton.isEnabled = true
                        val openPorts = state.results.filter { it.isOpen }
                        binding.statusText.text = "Found ${openPorts.size} open ports out of ${state.results.size} scanned"
                        portAdapter.submitList(state.results)
                    }
                    is PortScanState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.scanButton.isEnabled = true
                        binding.statusText.text = ""
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.scanButton.setOnClickListener {
            val host = binding.hostText.text.toString()
            if (host.isBlank()) {
                Toast.makeText(requireContext(), "Host cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val portsText = binding.portsText.text.toString()
            val ports = if (portsText.isBlank()) {
                null // Use common ports
            } else {
                portsText.split(",").mapNotNull { it.trim().toIntOrNull() }
            }
            
            viewModel.scanPorts(host, ports)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * ViewModel for Port Scanner.
 */
@HiltViewModel
class PortScannerViewModel @Inject constructor(
    private val portScannerProvider: PortScannerProvider
) : ViewModel() {

    private val _portScanState = MutableStateFlow<PortScanState>(PortScanState.Idle)
    val portScanState: StateFlow<PortScanState> = _portScanState.asStateFlow()

    fun scanPorts(host: String, ports: List<Int>?) {
        viewModelScope.launch {
            _portScanState.value = PortScanState.Scanning
            val result = portScannerProvider.scanHost(host, ports)
            when (result) {
                is com.ble1st.connectias.feature.network.models.NetworkResult.Success -> {
                    _portScanState.value = PortScanState.Success(result.data.items)
                }
                is com.ble1st.connectias.feature.network.models.NetworkResult.Error -> {
                    _portScanState.value = PortScanState.Error(result.message)
                }
            }
        }
    }
}

/**
 * State representation for port scanning.
 */
sealed class PortScanState {
    object Idle : PortScanState()
    object Scanning : PortScanState()
    data class Success(val results: List<PortScanResult>) : PortScanState()
    data class Error(val message: String) : PortScanState()
}

/**
 * Adapter for displaying port scan results.
 */
class PortScanAdapter : ListAdapter<PortScanResult, PortScanAdapter.PortViewHolder>(PortDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
        val binding = ItemPortScanBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PortViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PortViewHolder(
        private val binding: ItemPortScanBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: PortScanResult) {
            binding.portText.text = "Port ${result.port}"
            binding.statusText.text = if (result.isOpen) "Open" else "Closed"
            binding.serviceText.text = result.service ?: ""
            
            val color = if (result.isOpen) {
                android.graphics.Color.parseColor("#4CAF50")
            } else {
                android.graphics.Color.GRAY
            }
            binding.statusText.setTextColor(color)
        }
    }

    class PortDiffCallback : DiffUtil.ItemCallback<PortScanResult>() {
        override fun areItemsTheSame(oldItem: PortScanResult, newItem: PortScanResult): Boolean {
            return oldItem.host == newItem.host && oldItem.port == newItem.port
        }

        override fun areContentsTheSame(oldItem: PortScanResult, newItem: PortScanResult): Boolean {
            return oldItem == newItem
        }
    }
}

