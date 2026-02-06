package com.ble1st.connectias.ui.plugin.analytics

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
import com.ble1st.connectias.analytics.repo.PluginAnalyticsRepository
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PluginAnalyticsDashboardFragment : Fragment() {

    @Inject
    lateinit var repo: PluginAnalyticsRepository

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
                        PluginAnalyticsDashboardScreen(
                            repo = repo,
                            onNavigateBack = { findNavController().popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

