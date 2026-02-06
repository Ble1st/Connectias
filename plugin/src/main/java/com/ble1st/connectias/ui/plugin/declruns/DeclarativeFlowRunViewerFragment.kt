package com.ble1st.connectias.ui.plugin.declruns

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DeclarativeFlowRunViewerFragment : Fragment() {

    private val viewModel: DeclarativeFlowRunViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    DeclarativeFlowRunViewerScreen(
                        viewModel = viewModel,
                        onNavigateBack = { findNavController().navigateUp() }
                    )
                }
            }
        }
    }
}

