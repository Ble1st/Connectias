package com.ble1st.connectias.ui.plugin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.plugin.PluginManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PluginManagementFragment : Fragment() {
    
    @Inject
    lateinit var pluginManager: PluginManager
    
    @Inject
    lateinit var moduleRegistry: ModuleRegistry
    
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
                        onNavigateBack = { findNavController().navigateUp() }
                    )
                }
            }
        }
    }
}
