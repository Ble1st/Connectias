package com.ble1st.connectias.ui.plugin.security

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.plugin.security.EnhancedPluginNetworkPolicy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment wrapper for NetworkPolicyConfigurationScreen
 * Handles navigation integration and dependency injection
 */
@AndroidEntryPoint
class NetworkPolicyConfigurationFragment : Fragment() {
    
    @Inject
    lateinit var networkPolicy: EnhancedPluginNetworkPolicy
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val pluginId = arguments?.getString("pluginId") 
            ?: throw IllegalArgumentException("pluginId is required")
        
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        NetworkPolicyConfigurationScreen(
                            pluginId = pluginId,
                            networkPolicy = networkPolicy,
                            onNavigateBack = { 
                                findNavController().navigateUp()
                            }
                        )
                    }
                }
            }
        }
    }
}
