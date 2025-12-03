package com.ble1st.connectias.feature.settings.ui

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
import androidx.fragment.app.viewModels
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment for Settings screen.
 * Wraps the Compose SettingsScreen for Navigation Component integration.
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

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
                // Observe theme and dynamic color changes reactively
                val theme by settingsRepository.observeTheme().collectAsState(initial = settingsRepository.getTheme())
                val dynamicColor by settingsRepository.observeDynamicColor().collectAsState(initial = settingsRepository.getDynamicColor())
                
                ConnectiasTheme(
                    themePreference = theme,
                    dynamicColor = dynamicColor
                ) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            // Navigation is handled by Navigation Component
                            // This callback can be used for custom back handling if needed
                        }
                    )
                }
            }
        }
    }
}

