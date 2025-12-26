package com.ble1st.connectias.feature.securenotes.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.common.ui.theme.ThemeStyle
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment wrapper for the Secure Notes screen to integrate with the Navigation Component.
 */
@AndroidEntryPoint
class SecureNotesFragment : Fragment() {

    private val viewModel: SecureNotesViewModel by viewModels()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBiometricPrompt()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme by settingsRepository.observeTheme().collectAsState(initial = settingsRepository.getTheme())
                val themeStyleString by settingsRepository.observeThemeStyle().collectAsState(initial = settingsRepository.getThemeStyle())
                val dynamicColor by settingsRepository.observeDynamicColor().collectAsState(initial = settingsRepository.getDynamicColor())
                val themeStyle = remember(themeStyleString) { ThemeStyle.fromString(themeStyleString) }

                ConnectiasTheme(
                    themePreference = theme,
                    themeStyle = themeStyle,
                    dynamicColor = dynamicColor
                ) {
                    SecureNotesScreen(
                        viewModel = viewModel,
                        onNavigateBack = { findNavController().popBackStack() },
                        onRequestUnlock = { showBiometricPrompt() }
                    )
                }
            }
        }
    }

    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(requireContext())
        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    viewModel.onAuthenticationSucceeded()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    viewModel.onAuthenticationError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    viewModel.onAuthenticationError("Authentication failed. Try again.")
                }
            }
        )

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Secure Notes")
            .setSubtitle("Authenticate to access your encrypted notes")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    }

    private fun showBiometricPrompt() {
        if (::biometricPrompt.isInitialized && ::promptInfo.isInitialized) {
            biometricPrompt.authenticate(promptInfo)
        } else {
            viewModel.onAuthenticationError("Biometric prompt not available.")
        }
    }
}
