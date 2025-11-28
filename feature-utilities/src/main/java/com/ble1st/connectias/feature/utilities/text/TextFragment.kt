package com.ble1st.connectias.feature.utilities.text

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ble1st.connectias.feature.utilities.databinding.FragmentTextBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Text Tools.
 */
@AndroidEntryPoint
class TextFragment : Fragment() {

    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TextViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCaseTypeSpinner()
        setupObservers()
        setupClickListeners()
    }

    private fun setupCaseTypeSpinner() {
        val caseTypes = arrayOf("UPPER", "lower", "Title", "Sentence")
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            caseTypes
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.caseTypeSpinner.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.textState.collect { state ->
                when (state) {
                    is TextState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText("")
                        binding.countText.setText("")
                    }
                    is TextState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is TextState.Converted -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText(state.text)
                    }
                    is TextState.Counted -> {
                        binding.countText.setText("Words: ${state.count.wordCount}\n" +
                                "Characters: ${state.count.charCount}\n" +
                                "Characters (no spaces): ${state.count.charCountNoSpaces}\n" +
                                "Lines: ${state.count.lineCount}")
                    }
                    is TextState.RegexTested -> {
                        binding.progressBar.visibility = View.GONE
                        if (state.result.isValid) {
                            binding.resultText.setText(
                                "Matches (${state.result.matchCount}):\n" +
                                        state.result.matches.joinToString("\n")
                            )
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Invalid regex: ${state.result.errorMessage}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    is TextState.JsonFormatted -> {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.setText(state.json)
                    }
                    is TextState.JsonValid -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                    is TextState.JsonInvalid -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                    is TextState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.convertCaseButton.setOnClickListener {
            val text = binding.inputText.text.toString()
            val caseType = getSelectedCaseType()
            viewModel.convertCase(text, caseType)
        }

        binding.countButton.setOnClickListener {
            val text = binding.inputText.text.toString()
            viewModel.countWordsAndChars(text)
        }

        binding.regexTestButton.setOnClickListener {
            val text = binding.inputText.text.toString()
            val pattern = binding.regexPatternText.text.toString()
            viewModel.testRegex(text, pattern)
        }

        binding.formatJsonButton.setOnClickListener {
            val text = binding.inputText.text.toString()
            viewModel.formatJson(text)
        }

        binding.validateJsonButton.setOnClickListener {
            val text = binding.inputText.text.toString()
            viewModel.validateJson(text)
        }
    }

    private fun getSelectedCaseType(): TextProvider.CaseType {
        return when (binding.caseTypeSpinner.selectedItemPosition) {
            0 -> TextProvider.CaseType.UPPER
            1 -> TextProvider.CaseType.LOWER
            2 -> TextProvider.CaseType.TITLE
            3 -> TextProvider.CaseType.SENTENCE
            else -> TextProvider.CaseType.UPPER
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

