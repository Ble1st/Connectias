package com.ble1st.connectias.feature.utilities.qrcode

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.utilities.databinding.FragmentQrcodeBinding
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for QR Code generation and scanning.
 */
@AndroidEntryPoint
class QrCodeFragment : Fragment() {

    private var _binding: FragmentQrcodeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: QrCodeViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startQrCodeScanner()
        } else {
            Toast.makeText(
                requireContext(),
                "Camera permission is required for QR code scanning",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val qrCodeScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            binding.scannedText.setText(result.contents)
            Toast.makeText(requireContext(), "QR Code scanned successfully", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQrcodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupQrTypeSpinner()
        setupObservers()
        setupClickListeners()
    }

    private fun setupQrTypeSpinner() {
        val qrTypes = arrayOf("Text", "WiFi", "Contact", "URL")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            qrTypes
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.qrTypeSpinner.adapter = adapter
        binding.qrTypeSpinner.setSelection(0)
        updateInputFields(0)
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.qrCodeState.collect { state ->
                when (state) {
                    is QrCodeState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.qrCodeImage.visibility = View.GONE
                    }
                    is QrCodeState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.qrCodeImage.visibility = View.GONE
                    }
                    is QrCodeState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.qrCodeImage.visibility = View.VISIBLE
                        binding.qrCodeImage.setImageBitmap(state.bitmap)
                    }
                    is QrCodeState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.qrCodeImage.visibility = View.GONE
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.qrTypeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateInputFields(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.generateButton.setOnClickListener {
            val qrType = binding.qrTypeSpinner.selectedItemPosition
            when (qrType) {
                0 -> { // Text
                    val text = binding.inputText.text.toString()
                    viewModel.generateQrCode(text)
                }
                1 -> { // WiFi
                    val ssid = binding.ssidText.text.toString()
                    val password = binding.passwordText.text.toString()
                    val securityType = binding.securityTypeText.text.toString().takeIf { it.isNotBlank() } ?: "WPA"
                    viewModel.generateWifiQrCode(ssid, password, securityType)
                }
                2 -> { // Contact
                    val name = binding.nameText.text.toString()
                    val phone = binding.phoneText.text.toString().takeIf { it.isNotBlank() }
                    val email = binding.emailText.text.toString().takeIf { it.isNotBlank() }
                    val org = binding.orgText.text.toString().takeIf { it.isNotBlank() }
                    viewModel.generateContactQrCode(name, phone, email, org)
                }
                3 -> { // URL
                    val url = binding.urlText.text.toString()
                    viewModel.generateUrlQrCode(url)
                }
            }
        }

        binding.scanButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startQrCodeScanner()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun updateInputFields(position: Int) {
        binding.textInputLayout.visibility = if (position == 0) View.VISIBLE else View.GONE
        binding.wifiInputLayout.visibility = if (position == 1) View.VISIBLE else View.GONE
        binding.contactInputLayout.visibility = if (position == 2) View.VISIBLE else View.GONE
        binding.urlInputLayout.visibility = if (position == 3) View.VISIBLE else View.GONE
    }

    private fun startQrCodeScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.name)
        options.setPrompt("Scan a QR code")
        options.setCameraId(0)
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        qrCodeScannerLauncher.launch(options)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

