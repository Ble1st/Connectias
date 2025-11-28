package com.ble1st.connectias.feature.privacy.tracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ble1st.connectias.feature.privacy.databinding.FragmentTrackerDetectionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Tracker Detection.
 */
@AndroidEntryPoint
class TrackerDetectionFragment : Fragment() {

    private var _binding: FragmentTrackerDetectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrackerDetectionViewModel by viewModels()
    private lateinit var trackerAdapter: TrackerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackerDetectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        trackerAdapter = TrackerAdapter()
        binding.trackersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.trackersRecyclerView.adapter = trackerAdapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.trackerState.collect { state ->
                when (state) {
                    is TrackerState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.summaryText.text = ""
                        trackerAdapter.submitList(emptyList())
                    }
                    is TrackerState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.summaryText.text = "Scanning for trackers..."
                    }
                    is TrackerState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        val summary = "Found ${state.trackers.size} apps with trackers\n" +
                                "Known tracker domains: ${state.domains.size}"
                        binding.summaryText.text = summary
                        trackerAdapter.submitList(state.trackers)
                    }
                    is TrackerState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.summaryText.text = ""
                        trackerAdapter.submitList(emptyList())
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.scanButton.setOnClickListener {
            viewModel.detectTrackers()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Adapter for displaying tracker information.
 */
class TrackerAdapter : androidx.recyclerview.widget.ListAdapter<TrackerInfo, TrackerAdapter.TrackerViewHolder>(TrackerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackerViewHolder {
        val binding = com.ble1st.connectias.feature.privacy.databinding.ItemTrackerBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TrackerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TrackerViewHolder(
        private val binding: com.ble1st.connectias.feature.privacy.databinding.ItemTrackerBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(tracker: TrackerInfo) {
            binding.appNameText.text = tracker.appName
            binding.packageNameText.text = tracker.packageName
            binding.trackersText.text = tracker.trackerDomains.joinToString(", ")
            binding.systemAppText.text = if (tracker.isSystemApp) "System App" else "User App"
        }
    }

    class TrackerDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<TrackerInfo>() {
        override fun areItemsTheSame(oldItem: TrackerInfo, newItem: TrackerInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: TrackerInfo, newItem: TrackerInfo): Boolean {
            return oldItem == newItem
        }
    }
}

