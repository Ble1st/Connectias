package com.ble1st.connectias.feature.network.ui

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.network.monitor.NetworkMonitorProvider
import com.ble1st.connectias.feature.network.monitor.NetworkTraffic
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fragment for Network Traffic Monitor.
 */
@AndroidEntryPoint
class NetworkMonitorFragment : Fragment() {

    private val viewModel: NetworkMonitorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val traffic by viewModel.trafficFlow.collectAsState()
                    
                    NetworkMonitorScreen(
                        traffic = traffic,
                        onRefresh = { viewModel.refreshCurrentTraffic() }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Start monitoring
        viewModel.startMonitoring()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopMonitoring()
    }
}

/**
 * ViewModel for Network Monitor.
 */
@HiltViewModel
class NetworkMonitorViewModel @Inject constructor(
    private val networkMonitorProvider: NetworkMonitorProvider
) : ViewModel() {

    private val _trafficFlow = MutableStateFlow<NetworkTraffic>(
        NetworkTraffic(0, 0, 0.0, 0.0, com.ble1st.connectias.feature.network.monitor.ConnectionType.NONE, System.currentTimeMillis())
    )
    val trafficFlow: StateFlow<NetworkTraffic> = _trafficFlow.asStateFlow()

    private var monitoringJob: Job? = null

    fun startMonitoring() {
        if (monitoringJob == null || monitoringJob?.isCompleted == true) {
            monitoringJob = viewModelScope.launch {
                networkMonitorProvider.monitorTraffic(intervalMs = 1000).collect { traffic ->
                    _trafficFlow.value = traffic
                }
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    fun refreshCurrentTraffic() {
        viewModelScope.launch {
            val traffic = networkMonitorProvider.getCurrentTraffic()
            _trafficFlow.value = traffic
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}

