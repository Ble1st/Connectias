package com.ble1st.connectias.feature.security.encryption

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
import com.ble1st.connectias.common.ui.theme.ObserveThemeSettings
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment for Encryption Tools.
 */
@AndroidEntryPoint
class EncryptionFragment : Fragment() {

    private val viewModel: EncryptionViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ObserveThemeSettings(settingsRepository) { theme, themeStyle, dynamicColor ->
                    ConnectiasTheme(
                        themePreference = theme,
                        themeStyle = themeStyle,
                        dynamicColor = dynamicColor
                    ) {
                        val state by viewModel.encryptionState.collectAsState()
                        
                        EncryptionScreen(
                            state = state,
                            onEncrypt = { text, pass -> viewModel.encryptText(text, pass) },
                            onDecrypt = { text, iv, salt, pass -> viewModel.decryptText(text, iv, salt, pass) },
                            onGenerateKey = { viewModel.generateKey() },
                            onReset = { viewModel.resetState() }
                        )
                    }
                }
            }
        }
    }
}

