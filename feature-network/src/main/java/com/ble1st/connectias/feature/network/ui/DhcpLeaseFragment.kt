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
 * Fragment for DHCP Lease Viewer feature.
 */
@AndroidEntryPoint
class DhcpLeaseFragment : Fragment() {

    private val viewModel: DhcpLeaseViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                DhcpLeaseScreen(
                    state = uiState,
                    onAnalyzeLeases = { 
                        // TODO: Get devices from Network Dashboard
                        viewModel.analyzeLeases(emptyList())
                    },
                    onResetState = { viewModel.resetState() }
                )
            }
        }
    }
}
