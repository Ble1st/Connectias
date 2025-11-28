package com.ble1st.connectias.feature.utilities.hash

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.utilities.databinding.FragmentHashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Hash & Checksum Tools.
 */
@AndroidEntryPoint
class HashFragment : Fragment() {

    private var _binding: FragmentHashBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HashViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val filePath = it.path ?: return@let
            val algorithm = getSelectedAlgorithm()
            viewModel.calculateFileHash(filePath, algorithm)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinner()
        setupObservers()
        setupClickListeners()
    }

    private fun setupSpinner() {
        val algorithms = arrayOf("MD5", "SHA-1", "SHA-256", "SHA-512")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            algorithms
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.algorithmSpinner.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hashState.collect { state ->
            when (state) {
                is HashState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.resultText.setText("")
                }
                is HashState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.resultText.setText("Calculating...")
                }
                is HashState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.resultText.setText(state.hash)
                }
                is HashState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.resultText.setText("")
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
                is HashState.VerificationSuccess -> {
                    binding.progressBar.visibility = View.GONE
                    binding.resultText.setText(state.message)
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
                is HashState.VerificationFailed -> {
                    binding.progressBar.visibility = View.GONE
                    binding.resultText.setText(state.message)
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
            }
        }
    }

    private fun setupClickListeners() {
        binding.calculateButton.setOnClickListener {
            val text = binding.inputText.text.toString()
            val algorithm = getSelectedAlgorithm()
            viewModel.calculateTextHash(text, algorithm)
        }

        binding.selectFileButton.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        binding.verifyButton.setOnClickListener {
            val text = binding.inputText.text.toString()
            val expectedHash = binding.expectedHashText.text.toString()
            val algorithm = getSelectedAlgorithm()
            viewModel.verifyTextHash(text, expectedHash, algorithm)
        }

        binding.copyButton.setOnClickListener {
            val hash = binding.resultText.text.toString()
            if (hash.isNotBlank()) {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Hash", hash)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Hash copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getSelectedAlgorithm(): HashProvider.HashAlgorithm {
        return when (binding.algorithmSpinner.selectedItemPosition) {
            0 -> HashProvider.HashAlgorithm.MD5
            1 -> HashProvider.HashAlgorithm.SHA1
            2 -> HashProvider.HashAlgorithm.SHA256
            3 -> HashProvider.HashAlgorithm.SHA512
            else -> HashProvider.HashAlgorithm.SHA256
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

