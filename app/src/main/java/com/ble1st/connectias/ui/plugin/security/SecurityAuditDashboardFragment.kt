package com.ble1st.connectias.ui.plugin.security

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.plugin.security.SecurityAuditManager
import androidx.compose.material3.MaterialTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Fragment wrapper for SecurityAuditDashboardScreen
 * Provides navigation integration and dependency injection for the Security Audit Dashboard
 */
@AndroidEntryPoint
class SecurityAuditDashboardFragment : Fragment() {
    
    @Inject
    lateinit var securityAuditManager: SecurityAuditManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    SecurityAuditDashboardScreen(
                        auditManager = securityAuditManager,
                        onNavigateBack = {
                            try {
                                findNavController().popBackStack()
                            } catch (e: Exception) {
                                Timber.e(e, "[SECURITY AUDIT] Navigation error")
                                requireActivity().onBackPressed()
                            }
                        }
                    )
                }
            }
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Timber.d("[SECURITY AUDIT] SecurityAuditDashboardFragment created")
        
        // Log that the audit dashboard was accessed
        securityAuditManager.logSecurityEvent(
            eventType = SecurityAuditManager.SecurityEventType.SECURITY_CONFIGURATION_CHANGE,
            severity = SecurityAuditManager.SecuritySeverity.INFO,
            source = "SecurityAuditDashboardFragment",
            pluginId = null,
            message = "Security audit dashboard accessed by administrator"
        )
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        Timber.d("[SECURITY AUDIT] SecurityAuditDashboardFragment destroyed")
    }
}
