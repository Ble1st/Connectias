package com.ble1st.connectias.feature.security.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.core.security.models.SecurityThreat
import com.ble1st.connectias.feature.security.databinding.FragmentSecurityDashboardBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
@AndroidEntryPoint
class SecurityDashboardFragment : Fragment() {

    private var _binding: FragmentSecurityDashboardBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException(
        "Binding is null. Fragment view not created yet or already destroyed."
    )
    private val viewModel: SecurityDashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecurityDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup refresh button
        binding.buttonRefresh.setOnClickListener {
            viewModel.performSecurityCheck()
        }

        // Setup security tools navigation
        // Note: Navigation IDs are defined in app module's nav_graph.xml
        // Resolve navigation IDs at runtime to avoid compile-time dependency on app module's R class
        binding.buttonCertificateAnalyzer.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_certificate_analyzer", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_certificate_analyzer not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to certificate analyzer")
            }
        }

        binding.buttonPasswordStrength.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_password_strength", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_password_strength not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to password strength")
            }
        }

        binding.buttonEncryptionTools.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_encryption_tools", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_encryption_tools not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to encryption tools")
            }
        }

        binding.buttonFirewallAnalyzer.setOnClickListener {
            try {
                val navId = resources.getIdentifier("nav_firewall_analyzer", "id", requireContext().packageName)
                if (navId != 0) {
                    findNavController().navigate(navId)
                } else {
                    Timber.w("Navigation ID nav_firewall_analyzer not found")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to navigate to firewall analyzer")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.securityState.collect { state ->
                    when (state) {
                        is SecurityState.Loading -> {
                            binding.textSecurityStatus.text = "Checking security..."
                            binding.textThreatsDetails.text = ""
                            binding.buttonRefresh.isEnabled = false
                        }
                        is SecurityState.Success -> {
                            binding.buttonRefresh.isEnabled = true
                            updateSecurityStatus(state.result)
                        }
                        is SecurityState.Error -> {
                            binding.buttonRefresh.isEnabled = true
                            binding.textSecurityStatus.text = "Error: ${state.message}"
                            binding.textThreatsDetails.text = ""
                        }
                    }
                }
            }
        }
    }

    private fun updateSecurityStatus(result: com.ble1st.connectias.core.security.models.SecurityCheckResult) {
        // Lifecycle guarantees binding is non-null when view is created
        // But add defensive check for safety
        val binding = _binding ?: run {
            Timber.w("Binding is null in updateSecurityStatus - view may be destroyed")
            return
        }
        
        // Update status text
        binding.textSecurityStatus.text = if (result.isSecure) {
            "✓ Secure - No threats detected"
        } else {
            "⚠ Threats detected: ${result.threats.size}"
        }

        // Update threats details
        if (result.threats.isEmpty()) {
            binding.textThreatsDetails.text = "No threats detected.\nAll security checks passed."
        } else {
            val threatsText = buildString {
                append("Detected Threats:\n\n")
                result.threats.forEachIndexed { index, threat ->
                    append("${index + 1}. ${formatThreat(threat)}\n")
                }
                
                if (result.failedChecks.isNotEmpty()) {
                    append("\nFailed Checks:\n")
                    result.failedChecks.forEachIndexed { index, failedCheck ->
                        append("${index + 1}. $failedCheck\n")
                    }
                }
            }
            binding.textThreatsDetails.text = threatsText
        }
    }

    private fun formatThreat(threat: SecurityThreat): String {
        return when (threat) {
            is SecurityThreat.RootDetected -> "Root Detected - Method: ${threat.method}"
            is SecurityThreat.DebuggerDetected -> "Debugger Detected - Method: ${threat.method}"
            is SecurityThreat.EmulatorDetected -> "Emulator Detected - Method: ${threat.method}"
            is SecurityThreat.TamperDetected -> "Tamper Detected - Method: ${threat.method}"
            is SecurityThreat.HookDetected -> "Hook Detected - Method: ${threat.method}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

