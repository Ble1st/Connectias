package com.ble1st.connectias.feature.wasm.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.ble1st.connectias.feature.wasm.ui.PluginManagerScreen
import com.ble1st.connectias.feature.wasm.ui.rememberPluginFilePicker
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment wrapper for PluginManagerScreen Compose UI.
 */
@AndroidEntryPoint
class PluginManagerFragment : Fragment() {
    
    private val viewModel: PluginManagerViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val filePicker = rememberPluginFilePicker { uri ->
                    viewModel.loadPlugin(uri)
                }
                
                PluginManagerScreen(
                    viewModel = viewModel,
                    onLoadPlugin = { filePicker() }
                )
            }
        }
    }
}

