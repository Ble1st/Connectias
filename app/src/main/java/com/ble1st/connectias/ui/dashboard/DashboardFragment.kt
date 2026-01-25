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
import com.ble1st.connectias.plugin.PluginManagerSandbox
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var moduleRegistry: ModuleRegistry
    
    @Inject
    lateinit var pluginManager: PluginManagerSandbox

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
                    // Observe module registry changes reactively
                    val allModules by moduleRegistry.modulesFlow.collectAsState()
                    // Observe plugin state changes to update dashboard when plugins error/recover
                    val allPlugins by pluginManager.pluginsFlow.collectAsState()
                    
                    val pluginModules = remember(allModules, allPlugins) {
                        allModules
                            .filter { moduleInfo ->
                                // Only show active plugins that are not in ERROR state
                                if (!moduleInfo.isActive) return@filter false
                                
                                val pluginInfo = pluginManager.getPlugin(moduleInfo.id)
                                if (pluginInfo == null) return@filter false
                                
                                // Hide plugins in ERROR state from dashboard
                                // They remain visible in plugin management for restart
                                pluginInfo.state != PluginManagerSandbox.PluginState.ERROR
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
                    }
                    
                    DashboardScreen(
                        onNavigateToSettings = { findNavController().navigate(R.id.nav_settings) },
                        onNavigateToSecurityDashboard = { findNavController().navigate(R.id.nav_security_dashboard) },
                        onNavigateToPrivacyDashboard = { findNavController().navigate(R.id.nav_privacy_dashboard) },
                        onNavigateToAnalyticsDashboard = { findNavController().navigate(R.id.nav_plugin_analytics_dashboard) },
                        pluginItems = pluginModules
                    )
                }
            }
        }
    }
}
