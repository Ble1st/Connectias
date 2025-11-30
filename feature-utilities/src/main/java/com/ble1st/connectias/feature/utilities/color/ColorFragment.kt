package com.ble1st.connectias.feature.utilities.color

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
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment for Color Tools.
 */
@AndroidEntryPoint
class ColorFragment : Fragment() {

    private val viewModel: ColorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val state by viewModel.colorState.collectAsState()
                    
                    ColorScreen(
                        state = state,
                        onRgbToHex = { r, g, b -> viewModel.convertRgbToHex(r, g, b) },
                        onHexToRgb = { hex -> viewModel.convertHexToRgb(hex) },
                        onRgbToHsl = { r, g, b -> viewModel.convertRgbToHsl(r, g, b) },
                        onHslToRgb = { h, s, l -> viewModel.convertHslToRgb(h, s, l) },
                        onRgbToHsv = { r, g, b -> viewModel.convertRgbToHsv(r, g, b) },
                        onContrast = { c1, c2 -> viewModel.calculateContrast(c1, c2) }
                    )
                }
            }
        }
    }
}

