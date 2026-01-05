package com.ble1st.connectias.feature.settings.ui

import android.annotation.SuppressLint
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
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.common.ui.theme.ThemeStyle
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
                // Observe theme, theme style, and dynamic color changes reactively
                val theme by settingsRepository.observeTheme().collectAsState(initial = settingsRepository.getTheme())
                val themeStyleString by settingsRepository.observeThemeStyle().collectAsState(initial = settingsRepository.getThemeStyle())
                val dynamicColor by settingsRepository.observeDynamicColor().collectAsState(initial = settingsRepository.getDynamicColor())
                val themeStyle = remember(themeStyleString) { ThemeStyle.fromString(themeStyleString) }
                
                ConnectiasTheme(
                    themePreference = theme,
                    themeStyle = themeStyle,
                    dynamicColor = dynamicColor
                ) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            findNavController().popBackStack()
                        },
                        onNavigateToLogViewer = {
                            // Note: Using getIdentifier to avoid circular dependency with app module.
                            // The navigation ID is defined in app module's nav_graph.xml, so we cannot
                            // directly reference R.id.nav_log_viewer from this feature module.
                            @SuppressLint("DiscouragedApi")
                            val navId = resources.getIdentifier("nav_log_viewer", "id", requireContext().packageName)
                            if (navId != 0) {
                                findNavController().navigate(navId)
                            }
                        }
                    )
                }
            }
        }
    }
}

