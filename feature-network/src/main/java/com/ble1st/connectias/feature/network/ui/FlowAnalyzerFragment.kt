package com.ble1st.connectias.feature.network.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.network.models.NetworkResult
import com.ble1st.connectias.feature.network.repository.NetworkRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fragment for Network Flow Analyzer feature.
 */
@AndroidEntryPoint
class FlowAnalyzerFragment : Fragment() {

    private val viewModel: FlowAnalyzerViewModel by viewModels()
    
    @Inject
    lateinit var networkRepository: NetworkRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                FlowAnalyzerScreen(
                    state = uiState,
                    onAnalyzeFlows = { 
                        // Fetch devices from Network Repository
                        lifecycleScope.launch {
                            val devicesResult = networkRepository.getLocalNetworkDevices()
                            val devices = when (devicesResult) {
                                is NetworkResult.Success -> {
                                    devicesResult.data.items
                                }
                                is NetworkResult.Error -> {
                                    emptyList() // Fallback to empty list on error
                                }
                            }
                            viewModel.analyzeFlows(devices)
                        }
                    },
                    onResetState = { viewModel.resetState() }
                )
            }
        }
    }
}
