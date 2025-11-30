package com.ble1st.connectias.feature.utilities.log

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
 * Fragment for Log Viewer.
 */
@AndroidEntryPoint
class LogFragment : Fragment() {

    private val viewModel: LogViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val state by viewModel.logState.collectAsState()
                    
                    LogScreen(
                        state = state,
                        onLoadLogs = { filter -> viewModel.loadLogs(filter) },
                        onFilterByLevel = { level -> viewModel.filterByLevel(level) },
                        onFilterByTag = { tag -> viewModel.filterByTag(tag) },
                        onClearLogs = { viewModel.clearLogs() },
                        onExportLogs = { viewModel.exportLogs() }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Load logs on start
        viewModel.loadLogs()
    }
}

