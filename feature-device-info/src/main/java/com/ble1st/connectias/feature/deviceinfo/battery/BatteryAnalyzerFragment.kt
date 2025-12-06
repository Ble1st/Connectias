package com.ble1st.connectias.feature.deviceinfo.battery

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
import com.ble1st.connectias.common.ui.theme.ObserveThemeSettings
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment for Battery Analyzer.
 */
@AndroidEntryPoint
class BatteryAnalyzerFragment : Fragment() {

    private val viewModel: BatteryAnalyzerViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ObserveThemeSettings(settingsRepository) { theme, themeStyle, dynamicColor ->
                    ConnectiasTheme(
                        themePreference = theme,
                        themeStyle = themeStyle,
                        dynamicColor = dynamicColor
                    ) {
                        val state by viewModel.batteryState.collectAsState()
                        
                        BatteryAnalyzerScreen(
                            state = state,
                            onGetInfo = { viewModel.getBatteryInfo() },
                            onStartMonitoring = { viewModel.startMonitoring() },
                            onStopMonitoring = { viewModel.stopMonitoring() },
                            onEstimateTime = { 
                                viewModel.estimateTimeToFullCharge()
                                viewModel.estimateTimeToEmpty()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Load initial battery info
        viewModel.getBatteryInfo()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopMonitoring()
    }
}

