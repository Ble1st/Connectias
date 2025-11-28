package com.ble1st.connectias.feature.security.certificate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.security.databinding.FragmentCertificateAnalyzerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Certificate Analyzer.
 */
@AndroidEntryPoint
class CertificateAnalyzerFragment : Fragment() {

    private var _binding: FragmentCertificateAnalyzerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CertificateAnalyzerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCertificateAnalyzerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.certificateState.collect { state ->
                when (state) {
                    is CertificateState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText("")
                    }
                    is CertificateState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.resultText.setText("Analyzing certificate...")
                    }
                    is CertificateState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        displayCertificateInfo(state.info)
                    }
                    is CertificateState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText("")
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.analyzeButton.setOnClickListener {
            val url = binding.urlText.text.toString()
            viewModel.analyzeCertificate(url)
        }
    }

    private fun displayCertificateInfo(info: CertificateInfo) {
        val statusColor = when {
            info.isExpired -> android.graphics.Color.RED
            info.isNotYetValid -> android.graphics.Color.parseColor("#FF9800")
            info.daysUntilExpiry <= 30 -> android.graphics.Color.parseColor("#FF9800")
            else -> android.graphics.Color.parseColor("#4CAF50")
        }

        val statusText = when {
            info.isExpired -> "EXPIRED"
            info.isNotYetValid -> "NOT YET VALID"
            info.daysUntilExpiry <= 30 -> "EXPIRING SOON (${info.daysUntilExpiry} days)"
            else -> "VALID (${info.daysUntilExpiry} days remaining)"
        }

        val result = """
            Status: $statusText
            Subject: ${info.subject}
            Issuer: ${info.issuer}
            Valid From: ${info.notBefore}
            Valid To: ${info.notAfter}
            Days Until Expiry: ${info.daysUntilExpiry}
            Self-Signed: ${if (info.isSelfSigned) "Yes ⚠️" else "No"}
            Signature Algorithm: ${info.signatureAlgorithm}
            Public Key Algorithm: ${info.publicKeyAlgorithm}
            Serial Number: ${info.serialNumber}
            Version: ${info.version}
        """.trimIndent()

        binding.resultText.setText(result)
        binding.statusText.text = statusText
        binding.statusText.setTextColor(statusColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

