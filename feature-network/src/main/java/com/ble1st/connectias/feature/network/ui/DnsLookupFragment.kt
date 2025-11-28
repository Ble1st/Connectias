package com.ble1st.connectias.feature.network.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.network.databinding.FragmentDnsLookupBinding
import com.ble1st.connectias.feature.network.scanner.DnsLookupProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fragment for DNS Lookup & Diagnostics.
 */
@AndroidEntryPoint
class DnsLookupFragment : Fragment() {

    private var _binding: FragmentDnsLookupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DnsLookupViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDnsLookupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecordTypeSpinner()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecordTypeSpinner() {
        val recordTypes = arrayOf("A", "AAAA", "MX", "TXT", "CNAME", "NS", "PTR")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            recordTypes
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.recordTypeSpinner.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.dnsState.collect { state ->
                when (state) {
                    is DnsState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText("")
                    }
                    is DnsState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.resultText.setText("Looking up...")
                    }
                    is DnsState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText(state.results.joinToString("\n"))
                    }
                    is DnsState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText("")
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.lookupButton.setOnClickListener {
            val domain = binding.domainText.text.toString()
            if (domain.isBlank()) {
                Toast.makeText(requireContext(), "Domain cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val recordType = getSelectedRecordType()
            val dnsServer = binding.dnsServerText.text.toString().takeIf { it.isNotBlank() }
            viewModel.lookup(domain, recordType, dnsServer)
        }

        binding.reverseLookupButton.setOnClickListener {
            val ip = binding.domainText.text.toString()
            if (ip.isBlank()) {
                Toast.makeText(requireContext(), "IP address cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val dnsServer = binding.dnsServerText.text.toString().takeIf { it.isNotBlank() }
            viewModel.reverseLookup(ip, dnsServer)
        }

        binding.testDnsButton.setOnClickListener {
            val dnsServer = binding.dnsServerText.text.toString()
            if (dnsServer.isBlank()) {
                Toast.makeText(requireContext(), "DNS server cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            viewModel.testDnsServer(dnsServer)
        }
    }

    private fun getSelectedRecordType(): DnsLookupProvider.RecordType {
        return when (binding.recordTypeSpinner.selectedItemPosition) {
            0 -> DnsLookupProvider.RecordType.A
            1 -> DnsLookupProvider.RecordType.AAAA
            2 -> DnsLookupProvider.RecordType.MX
            3 -> DnsLookupProvider.RecordType.TXT
            4 -> DnsLookupProvider.RecordType.CNAME
            5 -> DnsLookupProvider.RecordType.NS
            6 -> DnsLookupProvider.RecordType.PTR
            else -> DnsLookupProvider.RecordType.A
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * ViewModel for DNS Lookup.
 */
@HiltViewModel
class DnsLookupViewModel @Inject constructor(
    private val dnsLookupProvider: DnsLookupProvider
) : ViewModel() {

    private val _dnsState = MutableStateFlow<DnsState>(DnsState.Idle)
    val dnsState: StateFlow<DnsState> = _dnsState.asStateFlow()

    fun lookup(domain: String, recordType: DnsLookupProvider.RecordType, dnsServer: String?) {
        viewModelScope.launch {
            _dnsState.value = DnsState.Loading
            val results = dnsLookupProvider.lookup(domain, recordType, dnsServer)
            if (results.isNotEmpty()) {
                _dnsState.value = DnsState.Success(results)
            } else {
                _dnsState.value = DnsState.Error("No records found")
            }
        }
    }

    fun reverseLookup(ipAddress: String, dnsServer: String?) {
        viewModelScope.launch {
            _dnsState.value = DnsState.Loading
            val hostname = dnsLookupProvider.reverseLookup(ipAddress, dnsServer)
            if (hostname != null) {
                _dnsState.value = DnsState.Success(listOf(hostname))
            } else {
                _dnsState.value = DnsState.Error("Reverse lookup failed")
            }
        }
    }

    fun testDnsServer(dnsServer: String) {
        viewModelScope.launch {
            _dnsState.value = DnsState.Loading
            val responseTime = dnsLookupProvider.testDnsServer(dnsServer)
            if (responseTime != null) {
                _dnsState.value = DnsState.Success(listOf("Response time: ${responseTime}ms"))
            } else {
                _dnsState.value = DnsState.Error("DNS server test failed")
            }
        }
    }
}

/**
 * State representation for DNS operations.
 */
sealed class DnsState {
    object Idle : DnsState()
    object Loading : DnsState()
    data class Success(val results: List<String>) : DnsState()
    data class Error(val message: String) : DnsState()
}

