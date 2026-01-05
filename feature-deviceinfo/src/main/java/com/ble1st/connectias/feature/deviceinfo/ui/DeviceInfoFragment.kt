package com.ble1st.connectias.feature.deviceinfo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.common.ui.theme.ThemeStyle
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment that displays device information as a dialog-style bottom sheet.
 * This fragment is used when navigating to the Device Info feature.
 */
@AndroidEntryPoint
class DeviceInfoFragment : Fragment() {

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
                val theme = settingsRepository.getTheme()
                val themeStyleString = settingsRepository.getThemeStyle()
                val dynamicColor = settingsRepository.getDynamicColor()
                val themeStyle = ThemeStyle.fromString(themeStyleString)
                
                ConnectiasTheme(
                    themePreference = theme,
                    themeStyle = themeStyle,
                    dynamicColor = dynamicColor
                ) {
                    DeviceInformationDialog(
                        onDismissRequest = {
                            // Navigate back when dialog is dismissed
                            parentFragmentManager.popBackStack()
                        }
                    )
                }
            }
        }
    }
}

