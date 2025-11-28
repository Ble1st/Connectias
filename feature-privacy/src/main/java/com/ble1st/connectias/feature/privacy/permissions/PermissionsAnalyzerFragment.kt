package com.ble1st.connectias.feature.privacy.permissions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ble1st.connectias.feature.privacy.databinding.FragmentPermissionsAnalyzerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Permissions Analyzer.
 */
@AndroidEntryPoint
class PermissionsAnalyzerFragment : Fragment() {

    private var _binding: FragmentPermissionsAnalyzerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PermissionsAnalyzerViewModel by viewModels()
    private lateinit var riskyAppsAdapter: RiskyAppsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsAnalyzerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        riskyAppsAdapter = RiskyAppsAdapter { appPermissions ->
            viewModel.getRecommendations(appPermissions)
        }
        binding.riskyAppsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.riskyAppsRecyclerView.adapter = riskyAppsAdapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.permissionsState.collect { state ->
                when (state) {
                    is PermissionsState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.summaryText.text = ""
                        riskyAppsAdapter.submitList(emptyList())
                    }
                    is PermissionsState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.summaryText.text = "Analyzing permissions..."
                    }
                    is PermissionsState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        val summary = "Total apps: ${state.allApps.size}\n" +
                                "Apps with risky permissions: ${state.riskyApps.size}"
                        binding.summaryText.text = summary
                        riskyAppsAdapter.submitList(state.riskyApps)
                    }
                    is PermissionsState.Recommendations -> {
                        binding.progressBar.visibility = View.GONE
                        // Show recommendations in a dialog or expandable view
                        val recommendationsText = state.recommendations.joinToString("\n\n") { rec ->
                            "${rec.permission}\n${rec.recommendation}\nRisk: ${rec.riskLevel.name}"
                        }
                        binding.recommendationsText.text = recommendationsText
                        binding.recommendationsText.visibility = View.VISIBLE
                    }
                    is PermissionsState.Error -> {
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
            viewModel.analyzePermissions()
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
class RiskyAppsAdapter(
    private val onItemClick: (AppPermissions) -> Unit
) : androidx.recyclerview.widget.ListAdapter<AppPermissions, RiskyAppsAdapter.RiskyAppViewHolder>(RiskyAppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RiskyAppViewHolder {
        val binding = com.ble1st.connectias.feature.privacy.databinding.ItemRiskyPermissionsBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RiskyAppViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: RiskyAppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RiskyAppViewHolder(
        private val binding: com.ble1st.connectias.feature.privacy.databinding.ItemRiskyPermissionsBinding,
        private val onItemClick: (AppPermissions) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(appPermissions: AppPermissions) {
            binding.appNameText.text = appPermissions.appName
            binding.packageNameText.text = appPermissions.packageName
            binding.riskyPermissionsText.text = appPermissions.riskyPermissions.joinToString(", ")
            binding.totalPermissionsText.text = "Total permissions: ${appPermissions.grantedPermissions.size}"
            
            binding.root.setOnClickListener {
                onItemClick(appPermissions)
            }
        }
    }

    class RiskyAppDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<AppPermissions>() {
        override fun areItemsTheSame(oldItem: AppPermissions, newItem: AppPermissions): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppPermissions, newItem: AppPermissions): Boolean {
            return oldItem == newItem
        }
    }
}

