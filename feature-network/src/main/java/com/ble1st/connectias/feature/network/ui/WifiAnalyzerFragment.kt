package com.ble1st.connectias.feature.network.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.common.ui.theme.ObserveThemeSettings
import com.ble1st.connectias.core.settings.SettingsRepository
import com.ble1st.connectias.feature.network.scanner.ChannelOverlap
import com.ble1st.connectias.feature.network.scanner.WifiAnalyzerProvider
import com.ble1st.connectias.feature.network.scanner.WifiChannelInfo
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

/**
 * Fragment for WiFi Channel Analyzer.
 */
@AndroidEntryPoint
class WifiAnalyzerFragment : Fragment() {

    private val viewModel: WifiAnalyzerViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.analyzeWifiNetworks()
        } else {
            Toast.makeText(requireContext(), "Location permission required for WiFi scanning", Toast.LENGTH_SHORT).show()
        }
    }

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
                        val state by viewModel.analyzerState.collectAsState()
                        
                        WifiAnalyzerScreen(
                            state = state,
                            onAnalyze = {
                                if (checkLocationPermission()) {
                                    viewModel.analyzeWifiNetworks()
                                } else {
                                    requestLocationPermissionIfNeeded()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

/**
 * ViewModel for WiFi Analyzer.
 */
@HiltViewModel
class WifiAnalyzerViewModel @Inject constructor(
    private val wifiAnalyzerProvider: WifiAnalyzerProvider,
    private val wlanScanner: com.ble1st.connectias.feature.network.scanner.WlanScanner
) : ViewModel() {

    private val _analyzerState = MutableStateFlow<WifiAnalyzerState>(WifiAnalyzerState.Idle)
    val analyzerState: StateFlow<WifiAnalyzerState> = _analyzerState.asStateFlow()

    fun analyzeWifiNetworks() {
        viewModelScope.launch {
            _analyzerState.value = WifiAnalyzerState.Loading
            
            try {
                val wifiNetworks = wlanScanner.scan()
                
                if (wifiNetworks.isEmpty()) {
                    _analyzerState.value = WifiAnalyzerState.Error("No WiFi networks found")
                    return@launch
                }
                
                val channelInfos = wifiAnalyzerProvider.analyzeChannels(wifiNetworks)
                val overlaps = wifiAnalyzerProvider.detectChannelOverlap(channelInfos)
                
                // Determine band (simplified - check first network)
                val band = if (wifiNetworks.first().frequency in 2400..2499) {
                    WifiAnalyzerProvider.WifiBand.BAND_2_4_GHZ
                } else {
                    WifiAnalyzerProvider.WifiBand.BAND_5_GHZ
                }
                
                val bestChannel = wifiAnalyzerProvider.recommendBestChannel(band, channelInfos)
                
                _analyzerState.value = WifiAnalyzerState.Success(
                    channelInfos = channelInfos,
                    overlaps = overlaps,
                    bestChannel = bestChannel
                )
            } catch (e: Exception) {
                _analyzerState.value = WifiAnalyzerState.Error(e.message ?: "Analysis failed")
            }
        }
    }
}

/**
 * State representation for WiFi analyzer operations.
 */
sealed class WifiAnalyzerState {
    object Idle : WifiAnalyzerState()
    object Loading : WifiAnalyzerState()
    data class Success(
        val channelInfos: List<WifiChannelInfo>,
        val overlaps: List<ChannelOverlap>,
        val bestChannel: Int?
    ) : WifiAnalyzerState()
    data class Error(val message: String) : WifiAnalyzerState()
}

