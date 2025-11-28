package com.ble1st.connectias.feature.deviceinfo.process

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ble1st.connectias.feature.deviceinfo.databinding.FragmentProcessMonitorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Process Monitor.
 */
@AndroidEntryPoint
class ProcessMonitorFragment : Fragment() {

    private var _binding: FragmentProcessMonitorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProcessMonitorViewModel by viewModels()
    private lateinit var processAdapter: ProcessAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProcessMonitorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        // Load initial processes
        viewModel.getRunningProcesses()
    }

    private fun setupRecyclerView() {
        processAdapter = ProcessAdapter()
        binding.processesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.processesRecyclerView.adapter = processAdapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.processState.collect { state ->
                when (state) {
                    is ProcessState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                    }
                    is ProcessState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is ProcessState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        displayMemoryStats(state.memoryStats)
                        processAdapter.submitList(state.processes)
                        binding.processCountText.text = "${state.processes.size} processes"
                    }
                    is ProcessState.Error -> {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            viewModel.getRunningProcesses()
        }
    }

    private fun displayMemoryStats(stats: MemoryStats) {
        binding.totalMemoryText.text = formatBytes(stats.totalMemory)
        binding.usedMemoryText.text = formatBytes(stats.usedMemory)
        binding.availableMemoryText.text = formatBytes(stats.availableMemory)
        binding.thresholdText.text = formatBytes(stats.threshold)
        binding.lowMemoryText.text = if (stats.lowMemory) "Yes ⚠️" else "No"
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Adapter for displaying processes.
 */
class ProcessAdapter : androidx.recyclerview.widget.ListAdapter<ProcessInfo, ProcessAdapter.ProcessViewHolder>(ProcessDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProcessViewHolder {
        val binding = com.ble1st.connectias.feature.deviceinfo.databinding.ItemProcessBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProcessViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProcessViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ProcessViewHolder(
        private val binding: com.ble1st.connectias.feature.deviceinfo.databinding.ItemProcessBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(process: ProcessInfo) {
            binding.appNameText.text = process.appName
            binding.processNameText.text = process.processName
            binding.pidText.text = "PID: ${process.pid}"
            binding.memoryText.text = formatBytes(process.memoryUsage)
            binding.importanceText.text = "Importance: ${process.importance}"
            binding.systemAppText.text = if (process.isSystemApp) "System" else "User"
        }

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
                bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
                else -> "$bytes B"
            }
        }
    }

    class ProcessDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<ProcessInfo>() {
        override fun areItemsTheSame(oldItem: ProcessInfo, newItem: ProcessInfo): Boolean {
            return oldItem.pid == newItem.pid
        }

        override fun areContentsTheSame(oldItem: ProcessInfo, newItem: ProcessInfo): Boolean {
            return oldItem == newItem
        }
    }
}

