package com.ble1st.connectias.ui.plugin.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.R
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.plugin.store.GitHubPluginStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment for the Plugin Store
 * Displays available plugins from GitHub releases and allows installation
 */
@AndroidEntryPoint
class PluginStoreFragment : Fragment() {
    
    @Inject
    lateinit var pluginStore: GitHubPluginStore
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Injection is handled by @AndroidEntryPoint annotation
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ConnectiasTheme {
                    PluginStoreScreen(
                        pluginStore = pluginStore,
                        onNavigateBack = { findNavController().navigateUp() },
                        onNavigateToPluginDetails = { pluginId ->
                            // TODO: Navigate to plugin details
                        },
                        onNavigateToPluginManagement = {
                            findNavController().navigate(R.id.nav_plugin_management)
                        }
                    )
                }
            }
        }
    }
}
