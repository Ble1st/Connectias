package com.ble1st.connectias.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.R
import com.ble1st.connectias.common.ui.dashboard.DashboardScreen
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.common.ui.theme.ThemeStyle
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment() {

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
                val theme by settingsRepository.observeTheme().collectAsState(initial = settingsRepository.getTheme())
                val themeStyleString by settingsRepository.observeThemeStyle().collectAsState(initial = settingsRepository.getThemeStyle())
                val dynamicColor by settingsRepository.observeDynamicColor().collectAsState(initial = settingsRepository.getDynamicColor())
                val themeStyle = remember(themeStyleString) { ThemeStyle.fromString(themeStyleString) }

                ConnectiasTheme(
                    themePreference = theme,
                    themeStyle = themeStyle,
                    dynamicColor = dynamicColor
                ) {
                    DashboardScreen(
                        onNavigateToNetwork = { /* Feature disabled */ },
                        onNavigateToBluetooth = { /* Feature disabled */ },
                        onNavigateToSecureNotes = { /* Feature disabled */ },
                        onNavigateToSettings = { findNavController().navigate(R.id.nav_settings) }
                    )
                }
            }
        }
    }
}
