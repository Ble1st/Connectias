package com.ble1st.connectias.feature.security.firewall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ble1st.connectias.feature.security.databinding.FragmentFirewallAnalyzerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Firewall Analyzer.
 */
@AndroidEntryPoint
class FirewallAnalyzerFragment : Fragment() {

    private var _binding: FragmentFirewallAnalyzerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirewallAnalyzerViewModel by viewModels()
    private lateinit var riskyAppsAdapter: RiskyAppsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirewallAnalyzerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        riskyAppsAdapter = RiskyAppsAdapter()
        binding.riskyAppsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.riskyAppsRecyclerView.adapter = riskyAppsAdapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.firewallState.collect { state ->
                when (state) {
                    is FirewallState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.summaryText.text = ""
                        riskyAppsAdapter.submitList(emptyList())
                    }
                    is FirewallState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.summaryText.text = "Analyzing apps..."
                    }
                    is FirewallState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        val summary = "Total apps with network permissions: ${state.apps.size}\n" +
                                "Risky apps found: ${state.riskyApps.size}"
                        binding.summaryText.text = summary
                        riskyAppsAdapter.submitList(state.riskyApps)
                    }
                    is FirewallState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.summaryText.text = ""
                        riskyAppsAdapter.submitList(emptyList())
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.analyzeButton.setOnClickListener {
            viewModel.analyzeApps()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Adapter for displaying risky apps.
 */
class RiskyAppsAdapter : androidx.recyclerview.widget.ListAdapter<RiskyApp, RiskyAppsAdapter.RiskyAppViewHolder>(RiskyAppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiskyAppViewHolder {
        val binding = com.ble1st.connectias.feature.security.databinding.ItemRiskyAppBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RiskyAppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RiskyAppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RiskyAppViewHolder(
        private val binding: com.ble1st.connectias.feature.security.databinding.ItemRiskyAppBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(riskyApp: RiskyApp) {
            binding.appNameText.text = riskyApp.appInfo.appName
            binding.packageNameText.text = riskyApp.appInfo.packageName
            binding.riskLevelText.text = "Risk: ${riskyApp.riskLevel.name}"
            binding.reasonsText.text = riskyApp.reasons.joinToString(", ")
            
            val color = when (riskyApp.riskLevel) {
                RiskLevel.HIGH -> android.graphics.Color.RED
                RiskLevel.MEDIUM -> android.graphics.Color.parseColor("#FF9800")
                RiskLevel.LOW -> android.graphics.Color.parseColor("#4CAF50")
            }
            binding.riskLevelText.setTextColor(color)
        }
    }

    class RiskyAppDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<RiskyApp>() {
        override fun areItemsTheSame(oldItem: RiskyApp, newItem: RiskyApp): Boolean {
            return oldItem.appInfo.packageName == newItem.appInfo.packageName
        }

        override fun areContentsTheSame(oldItem: RiskyApp, newItem: RiskyApp): Boolean {
            return oldItem == newItem
        }
    }
}

