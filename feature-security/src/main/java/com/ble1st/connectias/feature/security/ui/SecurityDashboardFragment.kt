package com.ble1st.connectias.feature.security.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.security.databinding.FragmentSecurityDashboardBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SecurityDashboardFragment : Fragment() {

    private var _binding: FragmentSecurityDashboardBinding? = null
    private val binding get() = _binding!!
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.securityState.collect { state ->
                    when (state) {
                        is SecurityState.Loading -> {
                            // Show loading
                        }
                        is SecurityState.Success -> {
                            // Update UI with security result
                            binding.textSecurityStatus.text = if (state.result.isSecure) {
                                "Secure"
                            } else {
                                "Threats detected: ${state.result.threats.size}"
                            }
                        }
                        is SecurityState.Error -> {
                            // Show error
                            binding.textSecurityStatus.text = "Error: ${state.message}"
                        }
                    }
                }
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

