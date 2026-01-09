package com.ble1st.connectias.ui.logging

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment that displays system logs from the database.
 */
@AndroidEntryPoint
class LogViewerFragment : Fragment() {
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    private val viewModel: LogEntryViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                // Observe theme settings reactively
                // Use default values as initial state - Flow will emit actual value immediately
                // This avoids synchronous SharedPreferences access on main thread (StrictMode violation)
                val theme by settingsRepository.observeTheme().collectAsState(initial = "system")
                val themeStyleString by settingsRepository.observeThemeStyle().collectAsState(initial = "standard")
                val dynamicColor by settingsRepository.observeDynamicColor().collectAsState(initial = true)
                val themeStyle = remember(themeStyleString) { 
                    com.ble1st.connectias.common.ui.theme.ThemeStyle.fromString(themeStyleString) 
                }
                
                ConnectiasTheme(
                    themePreference = theme,
                    themeStyle = themeStyle,
                    dynamicColor = dynamicColor
                ) {
                    LogViewerScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            findNavController().popBackStack()
                        }
                    )
                }
            }
        }
    }
}
