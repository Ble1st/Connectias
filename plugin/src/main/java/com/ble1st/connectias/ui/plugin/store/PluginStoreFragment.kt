package com.ble1st.connectias.ui.plugin.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.plugin.navigation.PluginNavigator
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

    @Inject
    lateinit var pluginNavigator: PluginNavigator

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
                        onNavigateBack = { pluginNavigator.navigateBack() },
                        onNavigateToPluginDetails = { pluginId ->
                            // TODO: Navigate to plugin details
                        },
                        onNavigateToPluginManagement = {
                            pluginNavigator.navigateToPluginManagement()
                        }
                    )
                }
            }
        }
    }
}
