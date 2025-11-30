package com.ble1st.connectias.feature.privacy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Fragment displaying privacy dashboard with all privacy categories.
 */
@AndroidEntryPoint
class PrivacyDashboardFragment : Fragment() {

    private val viewModel: PrivacyDashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val state by viewModel.uiState.collectAsState()
                    
                    PrivacyDashboardScreen(
                        state = state,
                        onRefresh = { viewModel.refresh() },
                        onNavigateToTrackerDetection = { navigateTo("nav_tracker_detection") },
                        onNavigateToPermissionsAnalyzer = { navigateTo("nav_permissions_analyzer") },
                        onNavigateToDataLeakage = { navigateTo("nav_data_leakage") }
                    )
                }
            }
        }
    }

    private fun navigateTo(resourceIdName: String) {
        try {
            val navId = resources.getIdentifier(resourceIdName, "id", requireContext().packageName)
            if (navId != 0) {
                findNavController().navigate(navId)
            } else {
                Timber.w("Navigation ID $resourceIdName not found")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to navigate to $resourceIdName")
        }
    }
}

