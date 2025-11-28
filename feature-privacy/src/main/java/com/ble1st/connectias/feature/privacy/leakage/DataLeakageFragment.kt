package com.ble1st.connectias.feature.privacy.leakage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ble1st.connectias.feature.privacy.databinding.FragmentDataLeakageBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Data Leakage Scanner.
 */
@AndroidEntryPoint
class DataLeakageFragment : Fragment() {

    private var _binding: FragmentDataLeakageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DataLeakageViewModel by viewModels()
    private lateinit var clipboardAdapter: ClipboardAdapter
    private lateinit var appsAdapter: AppsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDataLeakageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerViews() {
        clipboardAdapter = ClipboardAdapter()
        binding.clipboardRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.clipboardRecyclerView.adapter = clipboardAdapter

        appsAdapter = AppsAdapter()
        binding.appsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.appsRecyclerView.adapter = appsAdapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.leakageState.collect { state ->
                when (state) {
                    is DataLeakageState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.monitoringStatusText.text = ""
                    }
                    is DataLeakageState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is DataLeakageState.Monitoring -> {
                        binding.progressBar.visibility = View.GONE
                        binding.monitoringStatusText.text = if (state.isMonitoring) {
                            "Monitoring clipboard..."
                        } else {
                            "Monitoring stopped"
                        }
                    }
                    is DataLeakageState.ClipboardEntryState -> {
                        binding.progressBar.visibility = View.GONE
                        val currentList = clipboardAdapter.currentList.toMutableList()
                        currentList.add(0, state.entry)
                        clipboardAdapter.submitList(currentList)
                    }
                    is DataLeakageState.AppsWithAccess -> {
                        binding.progressBar.visibility = View.GONE
                        appsAdapter.submitList(state.apps)
                    }
                    is DataLeakageState.SensitivityAnalysis -> {
                        binding.progressBar.visibility = View.GONE
                        val color = when (state.sensitivity) {
                            SensitivityLevel.CRITICAL -> android.graphics.Color.RED
                            SensitivityLevel.HIGH -> android.graphics.Color.parseColor("#FF9800")
                            SensitivityLevel.MEDIUM -> android.graphics.Color.parseColor("#FFC107")
                            SensitivityLevel.LOW -> android.graphics.Color.parseColor("#4CAF50")
                            SensitivityLevel.NONE -> android.graphics.Color.GRAY
                        }
                        binding.sensitivityText.text = "Sensitivity: ${state.sensitivity.name}"
                        binding.sensitivityText.setTextColor(color)
                    }
                    is DataLeakageState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.monitoringStatusText.text = state.message
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.startMonitoringButton.setOnClickListener {
            viewModel.startClipboardMonitoring()
        }

        binding.stopMonitoringButton.setOnClickListener {
            viewModel.stopClipboardMonitoring()
        }

        binding.getAppsButton.setOnClickListener {
            viewModel.getAppsWithClipboardAccess()
        }

        binding.analyzeButton.setOnClickListener {
            val text = binding.inputText.text.toString()
            if (text.isNotBlank()) {
                viewModel.analyzeText(text)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopClipboardMonitoring()
        _binding = null
    }
}

/**
 * Adapter for displaying clipboard entries.
 */
class ClipboardAdapter : androidx.recyclerview.widget.ListAdapter<ClipboardEntry, ClipboardAdapter.ClipboardViewHolder>(ClipboardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardViewHolder {
        val binding = com.ble1st.connectias.feature.privacy.databinding.ItemClipboardBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ClipboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClipboardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ClipboardViewHolder(
        private val binding: com.ble1st.connectias.feature.privacy.databinding.ItemClipboardBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: ClipboardEntry) {
            binding.textText.text = entry.text.take(100) + if (entry.text.length > 100) "..." else ""
            binding.timestampText.text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))
            binding.sensitivityText.text = "Sensitivity: ${entry.sensitivity.name}"
            
            val color = when (entry.sensitivity) {
                SensitivityLevel.CRITICAL -> android.graphics.Color.RED
                SensitivityLevel.HIGH -> android.graphics.Color.parseColor("#FF9800")
                SensitivityLevel.MEDIUM -> android.graphics.Color.parseColor("#FFC107")
                SensitivityLevel.LOW -> android.graphics.Color.parseColor("#4CAF50")
                SensitivityLevel.NONE -> android.graphics.Color.GRAY
            }
            binding.sensitivityText.setTextColor(color)
        }
    }

    class ClipboardDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<ClipboardEntry>() {
        override fun areItemsTheSame(oldItem: ClipboardEntry, newItem: ClipboardEntry): Boolean {
            return oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: ClipboardEntry, newItem: ClipboardEntry): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Adapter for displaying apps with clipboard access.
 */
class AppsAdapter : androidx.recyclerview.widget.ListAdapter<AppClipboardAccess, AppsAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = com.ble1st.connectias.feature.privacy.databinding.ItemAppClipboardBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppViewHolder(
        private val binding: com.ble1st.connectias.feature.privacy.databinding.ItemAppClipboardBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppClipboardAccess) {
            binding.appNameText.text = app.appName
            binding.packageNameText.text = app.packageName
            binding.systemAppText.text = if (app.isSystemApp) "System App" else "User App"
        }
    }

    class AppDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<AppClipboardAccess>() {
        override fun areItemsTheSame(oldItem: AppClipboardAccess, newItem: AppClipboardAccess): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppClipboardAccess, newItem: AppClipboardAccess): Boolean {
            return oldItem == newItem
        }
    }
}

