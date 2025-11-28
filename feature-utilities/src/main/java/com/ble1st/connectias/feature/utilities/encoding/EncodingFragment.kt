package com.ble1st.connectias.feature.utilities.encoding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.utilities.databinding.FragmentEncodingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Encoding/Decoding Tools.
 */
@AndroidEntryPoint
class EncodingFragment : Fragment() {

    private var _binding: FragmentEncodingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EncodingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEncodingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinner()
        setupObservers()
        setupClickListeners()
    }

    private fun setupSpinner() {
        val encodingTypes = arrayOf("Base64", "Base32", "Hex", "URL", "HTML Entity", "Unicode")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            encodingTypes
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.encodingTypeSpinner.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.encodingState.collect { state ->
                when (state) {
                    is EncodingState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText("")
                    }
                    is EncodingState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.resultText.setText("Processing...")
                    }
                    is EncodingState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText(state.result)
                    }
                    is EncodingState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText("")
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.encodeButton.setOnClickListener {
            val text = binding.inputText.text.toString()
            val encodingType = getSelectedEncodingType()
            viewModel.encode(text, encodingType)
        }

        binding.decodeButton.setOnClickListener {
            val text = binding.inputText.text.toString()
            val encodingType = getSelectedEncodingType()
            viewModel.decode(text, encodingType)
        }

        binding.copyButton.setOnClickListener {
            val result = binding.resultText.text.toString()
            if (result.isNotBlank()) {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Encoded/Decoded", result)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Result copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getSelectedEncodingType(): EncodingProvider.EncodingType {
        return when (binding.encodingTypeSpinner.selectedItemPosition) {
            0 -> EncodingProvider.EncodingType.BASE64
            1 -> EncodingProvider.EncodingType.BASE32
            2 -> EncodingProvider.EncodingType.HEX
            3 -> EncodingProvider.EncodingType.URL
            4 -> EncodingProvider.EncodingType.HTML_ENTITY
            5 -> EncodingProvider.EncodingType.UNICODE
            else -> EncodingProvider.EncodingType.BASE64
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

