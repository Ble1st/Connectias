package com.ble1st.connectias.feature.deviceinfo.ui

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

@AndroidEntryPoint
class DeviceInfoFragment : Fragment() {

    private val viewModel: DeviceInfoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val state by viewModel.deviceInfo.collectAsState()
                    
                    DeviceInfoScreen(
                        state = state,
                        onRefresh = { viewModel.refresh() }
                    )
                }
            }
        }
    }
}

