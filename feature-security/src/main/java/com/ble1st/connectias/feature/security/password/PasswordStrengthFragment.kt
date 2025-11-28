package com.ble1st.connectias.feature.security.password

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.security.databinding.FragmentPasswordStrengthBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Password Strength Checker.
 */
@AndroidEntryPoint
class PasswordStrengthFragment : Fragment() {

    private var _binding: FragmentPasswordStrengthBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PasswordStrengthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPasswordStrengthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.passwordState.collect { state ->
                when (state) {
                    is PasswordState.Idle -> {
                        binding.feedbackText.setText("")
                        binding.scoreText.text = ""
                    }
                    is PasswordState.Analyzed -> {
                        displayPasswordStrength(state.strength)
                    }
                    is PasswordState.Generated -> {
                        binding.passwordText.setText(state.password)
                        // Auto-analyze generated password
                        viewModel.analyzePassword(state.password)
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.analyzeButton.setOnClickListener {
            val password = binding.passwordText.text.toString()
            viewModel.analyzePassword(password)
        }

        binding.generateButton.setOnClickListener {
            val length = binding.lengthText.text.toString().toIntOrNull() ?: 16
            val includeSpecial = binding.includeSpecialCheckbox.isChecked
            viewModel.generatePassword(length, includeSpecial)
        }
    }

    private fun displayPasswordStrength(strength: PasswordStrength) {
        val strengthColor = when (strength.strength) {
            Strength.VERY_WEAK -> android.graphics.Color.RED
            Strength.WEAK -> android.graphics.Color.parseColor("#FF9800")
            Strength.MODERATE -> android.graphics.Color.parseColor("#FFC107")
            Strength.STRONG -> android.graphics.Color.parseColor("#4CAF50")
            Strength.VERY_STRONG -> android.graphics.Color.parseColor("#2E7D32")
        }

        binding.scoreText.text = "Score: ${strength.score}/10"
        binding.scoreText.setTextColor(strengthColor)
        
        binding.strengthText.text = "Strength: ${strength.strength.name.replace("_", " ")}"
        binding.strengthText.setTextColor(strengthColor)
        
        binding.entropyText.text = "Entropy: ${String.format("%.2f", strength.entropy)} bits"
        
        binding.feedbackText.setText(strength.feedback.joinToString("\n"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

