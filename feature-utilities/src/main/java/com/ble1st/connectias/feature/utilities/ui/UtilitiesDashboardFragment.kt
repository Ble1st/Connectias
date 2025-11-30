package com.ble1st.connectias.feature.utilities.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Dashboard fragment for Utilities module.
 * Provides navigation to all utility tools.
 */
@AndroidEntryPoint
class UtilitiesDashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    UtilitiesDashboardScreen(
                        onNavigateToHash = { navigateTo("nav_hash_tool") },
                        onNavigateToEncoding = { navigateTo("nav_encoding_tool") },
                        onNavigateToQrCode = { navigateTo("nav_qrcode_tool") },
                        onNavigateToText = { navigateTo("nav_text_tool") },
                        onNavigateToColor = { navigateTo("nav_color_tool") },
                        onNavigateToLog = { navigateTo("nav_log_viewer") },
                        onNavigateToApiTester = { navigateTo("nav_api_tester") },
                        onNavigateToUsbAndMedia = { navigateTo("nav_usb_dashboard") }
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

