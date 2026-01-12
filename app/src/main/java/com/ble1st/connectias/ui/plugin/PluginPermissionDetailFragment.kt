package com.ble1st.connectias.ui.plugin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.plugin.PluginManagerSandbox
import com.ble1st.connectias.plugin.PluginPermissionManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PluginPermissionDetailFragment : Fragment() {
    
    @Inject
    lateinit var pluginManager: PluginManagerSandbox
    
    @Inject
    lateinit var permissionManager: PluginPermissionManager
    
    private val args: PluginPermissionDetailFragmentArgs by navArgs()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val plugin = pluginManager.getPlugin(args.pluginId)
                    
                    if (plugin != null) {
                        PluginPermissionDetailScreen(
                            plugin = plugin,
                            permissionManager = permissionManager,
                            onDismiss = { findNavController().navigateUp() },
                            onPermissionsChanged = {
                                // Plugin state refresh is handled by StateFlow
                                Timber.d("Permissions changed for plugin: ${args.pluginId}")
                            }
                        )
                    } else {
                        // Plugin not found, navigate back
                        Timber.w("Plugin not found: ${args.pluginId}")
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }
}
