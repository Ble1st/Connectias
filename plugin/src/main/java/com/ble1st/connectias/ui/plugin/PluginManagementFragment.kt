package com.ble1st.connectias.ui.plugin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.plugin.navigation.PluginNavigator
import com.ble1st.connectias.plugin.PluginManagerSandbox
import com.ble1st.connectias.plugin.PluginPermissionManager
import com.ble1st.connectias.plugin.PluginManifestParser
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PluginManagementFragment : Fragment() {

    @Inject
    lateinit var pluginManager: PluginManagerSandbox

    @Inject
    lateinit var moduleRegistry: ModuleRegistry

    @Inject
    lateinit var permissionManager: PluginPermissionManager

    @Inject
    lateinit var manifestParser: PluginManifestParser

    @Inject
    lateinit var pluginNavigator: PluginNavigator

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    PluginManagementScreen(
                        pluginManager = pluginManager,
                        moduleRegistry = moduleRegistry,
                        permissionManager = permissionManager,
                        manifestParser = manifestParser,
                        onNavigateBack = { pluginNavigator.navigateBack() },
                        onNavigateToPermissions = { pluginId ->
                            pluginNavigator.navigateToPermissionDetail(pluginId)
                        },
                        onNavigateToSecurity = { _ ->
                            pluginNavigator.navigateToPluginSecurityDashboard()
                        },
                        onNavigateToNetworkPolicy = { _ ->
                            pluginNavigator.navigateToNetworkPolicy()
                        },
                        onNavigateToSecurityAudit = {
                            pluginNavigator.navigateToSecurityAudit()
                        },
                        onNavigateToStore = {
                            pluginNavigator.navigateToPluginStore()
                        },
                        onNavigateToSecurityDashboard = {
                            pluginNavigator.navigateToPluginSecurityDashboard()
                        },
                        onNavigateToPrivacyDashboard = {
                            pluginNavigator.navigateToPrivacyDashboard()
                        },
                        onNavigateToAnalyticsDashboard = {
                            pluginNavigator.navigateToPluginAnalytics()
                        },
                        onNavigateToDeclarativeBuilder = {
                            pluginNavigator.navigateToDeclarativeBuilder()
                        },
                        onNavigateToDeveloperKeys = {
                            pluginNavigator.navigateToDeveloperKeys()
                        },
                        onNavigateToDeclarativeRuns = {
                            pluginNavigator.navigateToFlowRunViewer("")
                        }
                    )
                }
            }
        }
    }
}
