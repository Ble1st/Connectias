package com.ble1st.connectias.feature.network.topology.ui

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
 * Fragment for Network Topology Mapper feature.
 */
@AndroidEntryPoint
class TopologyFragment : Fragment() {

    private val viewModel: TopologyViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                TopologyScreen(
                    state = uiState,
                    onBuildTopology = { 
                        // TODO: Get devices from Network Dashboard
                        // For now, this is a placeholder
                        viewModel.buildTopology(emptyList())
                    },
                    onResetState = { viewModel.resetState() }
                )
            }
        }
    }
}
