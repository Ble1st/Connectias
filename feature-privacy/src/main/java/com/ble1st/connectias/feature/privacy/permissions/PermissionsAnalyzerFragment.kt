package com.ble1st.connectias.feature.privacy.permissions

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

/**
 * Fragment for Permissions Analyzer.
 */
@AndroidEntryPoint
class PermissionsAnalyzerFragment : Fragment() {

    private val viewModel: PermissionsAnalyzerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val state by viewModel.permissionsState.collectAsState()
                    
                    PermissionsAnalyzerScreen(
                        state = state,
                        onAnalyze = { viewModel.analyzePermissions() },
                        onAppClick = { app -> viewModel.getRecommendations(app) }
                    )
                }
            }
        }
    }
}

