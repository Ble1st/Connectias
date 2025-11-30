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
 * Fragment for Hypervisor/VM Detector feature.
 */
@AndroidEntryPoint
class HypervisorDetectorFragment : Fragment() {

    private val viewModel: HypervisorDetectorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                HypervisorDetectorScreen(
                    state = uiState,
                    onDetectHypervisors = { 
                        // TODO: Get devices and MAC manufacturers from Network Dashboard
                        viewModel.detectHypervisors(emptyList())
                    },
                    onResetState = { viewModel.resetState() }
                )
            }
        }
    }
}
