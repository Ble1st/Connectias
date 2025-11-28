package com.ble1st.connectias.feature.utilities.api

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.utilities.databinding.FragmentApiTesterBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for API Tester.
 */
@AndroidEntryPoint
class ApiTesterFragment : Fragment() {

    private var _binding: FragmentApiTesterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ApiTesterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApiTesterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMethodSpinner()
        setupObservers()
        setupClickListeners()
    }

    private fun setupMethodSpinner() {
        val methods = arrayOf("GET", "POST", "PUT", "DELETE", "PATCH")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            methods
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.methodSpinner.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.apiState.collect { state ->
                when (state) {
                    is ApiState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.responseText.setText("")
                        binding.statusText.setText("")
                    }
                    is ApiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.statusText.setText("Sending request...")
                    }
                    is ApiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        val response = state.response
                        binding.statusText.setText("${response.statusCode} ${response.statusMessage} (${response.duration}ms)")
                        binding.responseText.setText(response.body)
                        binding.responseHeadersText.setText(formatHeaders(response.headers))
                    }
                    is ApiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.statusText.setText("")
                        binding.responseText.setText("")
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.sendButton.setOnClickListener {
            val url = binding.urlText.text.toString()
            val method = getSelectedMethod()
            val headers = parseHeaders(binding.headersText.text.toString())
            val body = binding.bodyText.text.toString().takeIf { it.isNotBlank() }
            viewModel.executeRequest(url, method, headers, body)
        }
    }

    private fun getSelectedMethod(): ApiTesterProvider.HttpMethod {
        return when (binding.methodSpinner.selectedItemPosition) {
            0 -> ApiTesterProvider.HttpMethod.GET
            1 -> ApiTesterProvider.HttpMethod.POST
            2 -> ApiTesterProvider.HttpMethod.PUT
            3 -> ApiTesterProvider.HttpMethod.DELETE
            4 -> ApiTesterProvider.HttpMethod.PATCH
            else -> ApiTesterProvider.HttpMethod.GET
        }
    }

    private fun parseHeaders(headersText: String): Map<String, String> {
        if (headersText.isBlank()) return emptyMap()
        
        return headersText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(":") }
            .associate { line ->
                val parts = line.split(":", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
    }

    private fun formatHeaders(headers: Map<String, List<String>>): String {
        if (headers.isEmpty()) return "No headers"
        
        return headers.entries.joinToString("\n") { (key, values) ->
            "$key: ${values.joinToString(", ")}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

