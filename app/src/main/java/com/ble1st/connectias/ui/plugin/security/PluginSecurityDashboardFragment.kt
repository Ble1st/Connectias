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
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.plugin.security.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment wrapper for PluginSecurityDashboard Compose screen
 * Integrates with Android Navigation Component
 */
@AndroidEntryPoint
class PluginSecurityDashboardFragment : Fragment() {
    
    @Inject
    lateinit var zeroTrustVerifier: ZeroTrustVerifier
    
    @Inject
    lateinit var resourceLimiter: PluginResourceLimiter
    
    @Inject
    lateinit var behaviorAnalyzer: PluginBehaviorAnalyzer
    
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
                        PluginSecurityDashboard(
                            pluginId = pluginId,
                            zeroTrustVerifier = zeroTrustVerifier,
                            resourceLimiter = resourceLimiter,
                            behaviorAnalyzer = behaviorAnalyzer
                        )
                    }
                }
            }
        }
    }
}
