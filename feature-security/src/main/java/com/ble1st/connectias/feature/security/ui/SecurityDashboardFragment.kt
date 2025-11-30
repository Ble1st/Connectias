package com.ble1st.connectias.feature.security.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class SecurityDashboardFragment : Fragment() {

    private val viewModel: SecurityDashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose of the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // Using Connectias Theme
                ConnectiasTheme {
                    val state by viewModel.securityState.collectAsState()
                    
                    SecurityDashboardScreen(
                        state = state,
                        onRefresh = { viewModel.performSecurityCheck() },
                        onNavigateToCertificateAnalyzer = { navigateTo("nav_certificate_analyzer") },
                        onNavigateToPasswordStrength = { navigateTo("nav_password_strength") },
                        onNavigateToEncryptionTools = { navigateTo("nav_encryption_tools") },
                        onNavigateToFirewallAnalyzer = { navigateTo("nav_firewall_analyzer") }
                    )
                }
            }
        }
    }

    private fun navigateTo(resourceIdName: String) {
        try {
            val navId = resources.getIdentifier(resourceIdName, "id", requireContext().packageName)
            if (navId != 0) {
                findNavController().navigate(navId)
            } else {
                Timber.w("Navigation ID $resourceIdName not found")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to navigate to $resourceIdName")
        }
    }
}

