package com.ble1st.connectias.feature.utilities.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.ble1st.connectias.feature.utilities.databinding.FragmentLogBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Log Viewer.
 */
@AndroidEntryPoint
class LogFragment : Fragment() {

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LogViewModel by viewModels()
    private lateinit var logAdapter: LogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupLevelSpinner()
        setupObservers()
        setupClickListeners()
        
        // Load logs on start
        viewModel.loadLogs()
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter()
        binding.logsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.logsRecyclerView.adapter = logAdapter
    }

    private fun setupLevelSpinner() {
        val levels = arrayOf("All", "Verbose", "Debug", "Info", "Warn", "Error")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            levels
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.levelSpinner.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.logState.collect { state ->
                when (state) {
                    is LogState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        logAdapter.submitList(emptyList())
                    }
                    is LogState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is LogState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        logAdapter.submitList(state.logs)
                        binding.logCountText.text = "${state.logs.size} logs"
                    }
                    is LogState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            val filter = binding.filterText.text.toString().takeIf { it.isNotBlank() }
            viewModel.loadLogs(filter)
        }

        binding.levelSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val level = when (position) {
                    0 -> null
                    1 -> LogProvider.LogLevel.VERBOSE
                    2 -> LogProvider.LogLevel.DEBUG
                    3 -> LogProvider.LogLevel.INFO
                    4 -> LogProvider.LogLevel.WARN
                    5 -> LogProvider.LogLevel.ERROR
                    else -> null
                }
                viewModel.filterByLevel(level)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.filterButton.setOnClickListener {
            val tag = binding.tagFilterText.text.toString()
            viewModel.filterByTag(tag)
        }

        binding.clearButton.setOnClickListener {
            viewModel.clearLogs()
        }

        binding.exportButton.setOnClickListener {
            val logs = viewModel.exportLogs()
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Logs", logs)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Adapter for displaying log entries.
 */
class LogAdapter : androidx.recyclerview.widget.ListAdapter<LogEntry, LogAdapter.LogViewHolder>(LogDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = com.ble1st.connectias.feature.utilities.databinding.ItemLogBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LogViewHolder(
        private val binding: com.ble1st.connectias.feature.utilities.databinding.ItemLogBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: LogEntry) {
            binding.timestampText.text = entry.timestamp
            binding.levelText.text = entry.level.tag
            binding.tagText.text = entry.tag
            binding.messageText.text = entry.message

            // Set color based on level
            val color = when (entry.level) {
                LogProvider.LogLevel.ERROR -> android.graphics.Color.RED
                LogProvider.LogLevel.WARN -> android.graphics.Color.parseColor("#FF9800")
                LogProvider.LogLevel.INFO -> android.graphics.Color.BLUE
                LogProvider.LogLevel.DEBUG -> android.graphics.Color.GRAY
                LogProvider.LogLevel.VERBOSE -> android.graphics.Color.DKGRAY
            }
            binding.levelText.setTextColor(color)
        }
    }

    class LogDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.message == newItem.message
        }

        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }
}

