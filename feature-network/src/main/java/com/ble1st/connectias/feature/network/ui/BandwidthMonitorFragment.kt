package com.ble1st.connectias.feature.network.ui

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
 * Fragment for Bandwidth Monitor feature.
 */
@AndroidEntryPoint
class BandwidthMonitorFragment : Fragment() {

    private val viewModel: BandwidthMonitorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val interfaceStats by viewModel.interfaceStats.collectAsStateWithLifecycle()
                    val deviceStats by viewModel.deviceStats.collectAsStateWithLifecycle()
                    val trafficPattern by viewModel.trafficPattern.collectAsStateWithLifecycle()

                    BandwidthMonitorScreen(
                        interfaceStats = interfaceStats,
                        deviceStats = deviceStats,
                        trafficPattern = trafficPattern,
                        onRefresh = { viewModel.refreshStats() }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.refreshStats()
    }
}
