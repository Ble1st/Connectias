package com.ble1st.connectias.ui.plugin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import com.ble1st.connectias.R
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.core.module.ModuleRegistry
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
                        onNavigateBack = { findNavController().navigateUp() },
                        onNavigateToPermissions = { pluginId ->
                            findNavController().navigate(
                                R.id.nav_plugin_permission_detail,
                                bundleOf("pluginId" to pluginId)
                            )
                        },
                        onNavigateToSecurity = { pluginId ->
                            findNavController().navigate(
                                R.id.nav_plugin_security,
                                bundleOf("pluginId" to pluginId)
                            )
                        },
                        onNavigateToNetworkPolicy = { pluginId ->
                            findNavController().navigate(
                                R.id.action_pluginManagement_to_networkPolicyConfig,
                                bundleOf("pluginId" to pluginId)
                            )
                        },
                        onNavigateToSecurityAudit = {
                            findNavController().navigate(R.id.action_pluginManagement_to_securityAudit)
                        },
                        onNavigateToStore = {
                            findNavController().navigate(R.id.nav_plugin_store)
                        },
                        onNavigateToSecurityDashboard = {
                            findNavController().navigate(R.id.nav_security_dashboard)
                        },
                        onNavigateToPrivacyDashboard = {
                            findNavController().navigate(R.id.nav_privacy_dashboard)
                        },
                        onNavigateToAnalyticsDashboard = {
                            findNavController().navigate(R.id.nav_plugin_analytics_dashboard)
                        },
                        onNavigateToDeclarativeBuilder = {
                            findNavController().navigate(R.id.action_pluginManagement_to_declarativeBuilder)
                        },
                        onNavigateToDeveloperKeys = {
                            findNavController().navigate(R.id.action_pluginManagement_to_developerKeys)
                        },
                        onNavigateToDeclarativeRuns = {
                            findNavController().navigate(R.id.action_pluginManagement_to_declarativeRuns)
                        }
                    )
                }
            }
        }
    }
}
