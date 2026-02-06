package com.ble1st.connectias.ui.plugin.privacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.privacy.PrivacyAggregator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment wrapper for the global PrivacyDashboardScreen.
 */
@AndroidEntryPoint
class PrivacyDashboardFragment : Fragment() {

    @Inject
    lateinit var privacyAggregator: PrivacyAggregator

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        PrivacyDashboardScreen(
                            privacyAggregator = privacyAggregator,
                            onNavigateBack = { findNavController().popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

