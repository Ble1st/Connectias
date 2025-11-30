package com.ble1st.connectias.feature.utilities.text

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
 * Fragment for Text Tools.
 */
@AndroidEntryPoint
class TextFragment : Fragment() {

    private val viewModel: TextViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    val state by viewModel.textState.collectAsState()
                    
                    TextScreen(
                        state = state,
                        onConvertCase = { text, type -> viewModel.convertCase(text, type) },
                        onCountStats = { text -> viewModel.countWordsAndChars(text) },
                        onTestRegex = { text, pattern -> viewModel.testRegex(text, pattern) },
                        onFormatJson = { text -> viewModel.formatJson(text) },
                        onValidateJson = { text -> viewModel.validateJson(text) }
                    )
                }
            }
        }
    }
}

