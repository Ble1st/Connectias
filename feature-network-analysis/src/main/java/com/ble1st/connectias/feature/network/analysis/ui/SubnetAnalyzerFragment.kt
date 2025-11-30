package com.ble1st.connectias.feature.network.analysis.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment for Subnet Analyzer feature.
 */
@AndroidEntryPoint
class SubnetAnalyzerFragment : Fragment() {

    private val viewModel: SubnetAnalyzerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    SubnetAnalyzerScreen(
                        state = uiState,
                        onAnalyzeCidr = { viewModel.analyzeCidr(it) },
                        onResetState = { viewModel.resetState() }
                    )
                }
            }
        }
    }
}
