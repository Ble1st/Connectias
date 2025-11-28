package com.ble1st.connectias.feature.utilities.color

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.utilities.databinding.FragmentColorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Color Tools.
 */
@AndroidEntryPoint
class ColorFragment : Fragment() {

    private var _binding: FragmentColorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ColorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentColorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupConversionTypeSpinner()
        setupObservers()
        setupClickListeners()
    }

    private fun setupConversionTypeSpinner() {
        val types = arrayOf("RGB to HEX", "HEX to RGB", "RGB to HSL", "HSL to RGB", "RGB to HSV", "Contrast Checker")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            types
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.conversionTypeSpinner.adapter = adapter
        binding.conversionTypeSpinner.setSelection(0)
        updateInputFields(0)
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.colorState.collect { state ->
                when (state) {
                    is ColorState.Idle -> {
                        binding.resultText.setText("")
                    }
                    is ColorState.HexResult -> {
                        binding.resultText.setText("HEX: ${state.hex}")
                        updateColorPreview(state.hex)
                    }
                    is ColorState.RgbResult -> {
                        binding.resultText.setText("RGB: (${state.rgb.r}, ${state.rgb.g}, ${state.rgb.b})")
                        // Convert RGB to HEX for preview
                        val hex = String.format("#%02X%02X%02X", state.rgb.r, state.rgb.g, state.rgb.b)
                        updateColorPreview(hex)
                    }
                    is ColorState.HslResult -> {
                        binding.resultText.setText("HSL: (${state.hsl.h}°, ${state.hsl.s}%, ${state.hsl.l}%)")
                    }
                    is ColorState.HsvResult -> {
                        binding.resultText.setText("HSV: (${state.hsv.h}°, ${state.hsv.s}%, ${state.hsv.v}%)")
                    }
                    is ColorState.ContrastResult -> {
                        val aaStatus = if (state.meetsAA) "✓" else "✗"
                        val aaaStatus = if (state.meetsAAA) "✓" else "✗"
                        binding.resultText.setText("Contrast Ratio: ${String.format("%.2f", state.ratio)}:1\n" +
                                "WCAG AA (4.5:1): $aaStatus\n" +
                                "WCAG AAA (7:1): $aaaStatus")
                    }
                    is ColorState.Error -> {
                        binding.resultText.setText("")
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.conversionTypeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateInputFields(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.convertButton.setOnClickListener {
            val type = binding.conversionTypeSpinner.selectedItemPosition
            when (type) {
                0 -> { // RGB to HEX
                    val r = binding.rText.text.toString().toIntOrNull() ?: 0
                    val g = binding.gText.text.toString().toIntOrNull() ?: 0
                    val b = binding.bText.text.toString().toIntOrNull() ?: 0
                    viewModel.convertRgbToHex(r, g, b)
                }
                1 -> { // HEX to RGB
                    val hex = binding.hexText.text.toString()
                    viewModel.convertHexToRgb(hex)
                }
                2 -> { // RGB to HSL
                    val r = binding.rText.text.toString().toIntOrNull() ?: 0
                    val g = binding.gText.text.toString().toIntOrNull() ?: 0
                    val b = binding.bText.text.toString().toIntOrNull() ?: 0
                    viewModel.convertRgbToHsl(r, g, b)
                }
                3 -> { // HSL to RGB
                    val h = binding.hText.text.toString().toIntOrNull() ?: 0
                    val s = binding.sText.text.toString().toIntOrNull() ?: 0
                    val l = binding.lText.text.toString().toIntOrNull() ?: 0
                    viewModel.convertHslToRgb(h, s, l)
                }
                4 -> { // RGB to HSV
                    val r = binding.rText.text.toString().toIntOrNull() ?: 0
                    val g = binding.gText.text.toString().toIntOrNull() ?: 0
                    val b = binding.bText.text.toString().toIntOrNull() ?: 0
                    viewModel.convertRgbToHsv(r, g, b)
                }
                5 -> { // Contrast Checker
                    val color1 = binding.color1Text.text.toString()
                    val color2 = binding.color2Text.text.toString()
                    viewModel.calculateContrast(color1, color2)
                }
            }
        }
    }

    private fun updateInputFields(position: Int) {
        binding.rgbInputLayout.visibility = if (position in listOf(0, 2, 4)) View.VISIBLE else View.GONE
        binding.hexInputLayout.visibility = if (position == 1) View.VISIBLE else View.GONE
        binding.hslInputLayout.visibility = if (position == 3) View.VISIBLE else View.GONE
        binding.contrastInputLayout.visibility = if (position == 5) View.VISIBLE else View.GONE
    }

    private fun updateColorPreview(hex: String) {
        try {
            val color = Color.parseColor(hex)
            binding.colorPreview.setBackgroundColor(color)
        } catch (e: Exception) {
            // Invalid color
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

