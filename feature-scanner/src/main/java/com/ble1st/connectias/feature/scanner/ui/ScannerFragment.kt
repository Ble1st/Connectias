package com.ble1st.connectias.feature.scanner.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ScannerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    ScannerScreen(
                    )
                }
            }
        }
    }
}
