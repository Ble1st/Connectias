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
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment for Network Flow Analyzer feature.
 */
@AndroidEntryPoint
class FlowAnalyzerFragment : Fragment() {

    private val viewModel: FlowAnalyzerViewModel by viewModels()

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
                        // TODO: Get devices from Network Dashboard
                        viewModel.analyzeFlows(emptyList())
                    },
                    onResetState = { viewModel.resetState() }
                )
            }
        }
    }
}
