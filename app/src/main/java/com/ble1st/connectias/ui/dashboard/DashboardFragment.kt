package com.ble1st.connectias.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.R
import com.ble1st.connectias.common.ui.dashboard.DashboardItem
import com.ble1st.connectias.common.ui.dashboard.DashboardScreen
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.common.ui.theme.ThemeStyle
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.core.settings.SettingsRepository
import com.ble1st.connectias.plugin.PluginManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var moduleRegistry: ModuleRegistry
    
    @Inject
    lateinit var pluginManager: PluginManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // Use default values as initial state - Flow will emit actual value immediately
                // This avoids synchronous SharedPreferences access on main thread (StrictMode violation)
                val theme by settingsRepository.observeTheme().collectAsState(initial = "system")
                val themeStyleString by settingsRepository.observeThemeStyle().collectAsState(initial = "standard")
                val dynamicColor by settingsRepository.observeDynamicColor().collectAsState(initial = true)
                val themeStyle = remember(themeStyleString) { ThemeStyle.fromString(themeStyleString) }

                ConnectiasTheme(
                    themePreference = theme,
                    themeStyle = themeStyle,
                    dynamicColor = dynamicColor
                ) {
                    // Get plugin modules from registry - recalculate on each recomposition
                    val pluginModules = moduleRegistry.getActiveModules()
                        .filter { moduleInfo ->
                            // Check if this is a plugin (not a core module)
                            pluginManager.getPlugin(moduleInfo.id) != null
                        }
                        .map { moduleInfo ->
                            DashboardItem(
                                title = moduleInfo.name,
                                icon = Icons.Default.Extension,
                                onClick = {
                                    // Navigate to plugin using MainActivity
                                    (requireActivity() as? com.ble1st.connectias.MainActivity)
                                        ?.navigateToPlugin(moduleInfo.id)
                                }
                            )
                        }
                    
                    DashboardScreen(
                        onNavigateToSettings = { findNavController().navigate(R.id.nav_settings) },
                        pluginItems = pluginModules
                    )
                }
            }
        }
    }
}
