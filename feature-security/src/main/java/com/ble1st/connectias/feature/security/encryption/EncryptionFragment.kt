package com.ble1st.connectias.feature.security.encryption

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.security.databinding.FragmentEncryptionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Encryption Tools.
 */
@AndroidEntryPoint
class EncryptionFragment : Fragment() {

    private var _binding: FragmentEncryptionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EncryptionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEncryptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupOperationSpinner()
        setupObservers()
        setupClickListeners()
    }

    private fun setupOperationSpinner() {
        val operations = arrayOf("Encrypt", "Decrypt", "Generate Key")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            operations
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.operationSpinner.adapter = adapter
        binding.operationSpinner.setSelection(0)
        updateInputFields(0)
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.encryptionState.collect { state ->
                when (state) {
                    is EncryptionState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText("")
                        binding.ivText.setText("")
                    }
                    is EncryptionState.Processing -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is EncryptionState.Encrypted -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText(state.encryptedData)
                        binding.ivText.setText(state.iv)
                    }
                    is EncryptionState.Decrypted -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText(state.plaintext)
                        binding.ivText.setText("")
                    }
                    is EncryptionState.KeyGenerated -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText(state.key)
                        binding.ivText.setText("")
                    }
                    is EncryptionState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText("")
                        binding.ivText.setText("")
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.operationSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateInputFields(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.executeButton.setOnClickListener {
            val operation = binding.operationSpinner.selectedItemPosition
            val password = binding.passwordText.text.toString()
            
            when (operation) {
                0 -> { // Encrypt
                    val plaintext = binding.inputText.text.toString()
                    viewModel.encryptText(plaintext, password)
                }
                1 -> { // Decrypt
                    val encryptedData = binding.inputText.text.toString()
                    val iv = binding.ivText.text.toString()
                    viewModel.decryptText(encryptedData, iv, password)
                }
                2 -> { // Generate Key
                    viewModel.generateKey()
                }
            }
        }
    }

    private fun updateInputFields(position: Int) {
        binding.inputLayout.visibility = if (position == 2) View.GONE else View.VISIBLE
        binding.passwordLayout.visibility = if (position == 2) View.GONE else View.VISIBLE
        binding.ivLayout.visibility = if (position == 1) View.VISIBLE else View.GONE
        
        binding.inputText.hint = when (position) {
            0 -> "Text to encrypt"
            1 -> "Encrypted data (Base64)"
            else -> ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

