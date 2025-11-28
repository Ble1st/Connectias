package com.ble1st.connectias.feature.utilities.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.feature.utilities.databinding.FragmentUtilitiesDashboardBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Dashboard fragment for Utilities module.
 * Provides navigation to all utility tools.
 */
@AndroidEntryPoint
class UtilitiesDashboardFragment : Fragment() {

    private var _binding: FragmentUtilitiesDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUtilitiesDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Navigation will be handled via navigation graph
        // Individual tool fragments will be accessible via navigation
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

