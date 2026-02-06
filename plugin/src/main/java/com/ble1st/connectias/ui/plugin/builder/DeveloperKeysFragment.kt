package com.ble1st.connectias.ui.plugin.builder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.plugin.security.DeclarativeDeveloperTrustStore
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DeveloperKeysFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    DeveloperKeysScreen(
                        trustStore = DeclarativeDeveloperTrustStore(requireContext()),
                        onNavigateBack = { findNavController().navigateUp() }
                    )
                }
            }
        }
    }
}

