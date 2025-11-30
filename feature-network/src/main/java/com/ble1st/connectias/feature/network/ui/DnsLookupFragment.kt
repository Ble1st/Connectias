package com.ble1st.connectias.feature.network.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ble1st.connectias.feature.network.scanner.DnsLookupProvider

/**
 * Fragment for DNS Lookup & Diagnostics.
 */
@AndroidEntryPoint
class DnsLookupFragment : Fragment() {

    private val viewModel: DnsLookupViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val state by viewModel.dnsState.collectAsState()
                    
                    DnsLookupScreen(
                        state = state,
                        onLookup = { domain, type, server -> viewModel.lookup(domain, type, server) },
                        onReverseLookup = { ip, server -> viewModel.reverseLookup(ip, server) },
                        onTestDns = { server -> viewModel.testDnsServer(server) }
                    )
                }
            }
        }
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

