package com.ble1st.connectias.feature.deviceinfo.storage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ble1st.connectias.feature.deviceinfo.databinding.FragmentStorageAnalyzerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Storage Analyzer.
 */
@AndroidEntryPoint
class StorageAnalyzerFragment : Fragment() {

    private var _binding: FragmentStorageAnalyzerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StorageAnalyzerViewModel by viewModels()
    private lateinit var largeFilesAdapter: LargeFilesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStorageAnalyzerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        // Load initial storage info
        viewModel.getStorageInfo()
    }

    private fun setupRecyclerView() {
        largeFilesAdapter = LargeFilesAdapter()
        binding.largeFilesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.largeFilesRecyclerView.adapter = largeFilesAdapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.storageState.collect { state ->
                when (state) {
                    is StorageState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                    }
                    is StorageState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is StorageState.Info -> {
                        binding.progressBar.visibility = View.GONE
                        displayStorageInfo(state.info)
                    }
                    is StorageState.LargeFiles -> {
                        binding.progressBar.visibility = View.GONE
                        largeFilesAdapter.submitList(state.files)
                        binding.largeFilesCountText.text = "Found ${state.files.size} large files"
                    }
                    is StorageState.Error -> {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            viewModel.getStorageInfo()
        }

        binding.findLargeFilesButton.setOnClickListener {
            val minSize = binding.minSizeText.text.toString().toIntOrNull() ?: 10
            viewModel.findLargeFiles(minSize)
        }
    }

    private fun displayStorageInfo(info: StorageInfo) {
        binding.internalTotalText.text = formatBytes(info.internalStorage.totalBytes)
        binding.internalUsedText.text = formatBytes(info.internalStorage.usedBytes)
        binding.internalFreeText.text = formatBytes(info.internalStorage.freeBytes)
        
        if (info.externalStorage != null) {
            binding.externalTotalText.text = formatBytes(info.externalStorage.totalBytes)
            binding.externalUsedText.text = formatBytes(info.externalStorage.usedBytes)
            binding.externalFreeText.text = formatBytes(info.externalStorage.freeBytes)
            binding.externalStorageLayout.visibility = View.VISIBLE
        } else {
            binding.externalStorageLayout.visibility = View.GONE
        }
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
 * Adapter for displaying large files.
 */
class LargeFilesAdapter : androidx.recyclerview.widget.ListAdapter<LargeFile, LargeFilesAdapter.LargeFileViewHolder>(LargeFileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LargeFileViewHolder {
        val binding = com.ble1st.connectias.feature.deviceinfo.databinding.ItemLargeFileBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LargeFileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LargeFileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LargeFileViewHolder(
        private val binding: com.ble1st.connectias.feature.deviceinfo.databinding.ItemLargeFileBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(file: LargeFile) {
            binding.fileNameText.text = file.name
            binding.filePathText.text = file.path
            binding.fileSizeText.text = formatBytes(file.size)
            binding.lastModifiedText.text = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(file.lastModified))
        }

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
                bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
                bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
                else -> "$bytes B"
            }
        }
    }

    class LargeFileDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<LargeFile>() {
        override fun areItemsTheSame(oldItem: LargeFile, newItem: LargeFile): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: LargeFile, newItem: LargeFile): Boolean {
            return oldItem == newItem
        }
    }
}

