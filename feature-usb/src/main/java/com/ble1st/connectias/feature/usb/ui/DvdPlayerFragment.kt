package com.ble1st.connectias.feature.usb.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.usb.media.DvdPlayer
import com.ble1st.connectias.feature.usb.models.VideoStream
import com.ble1st.connectias.feature.usb.settings.DvdSettings
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Fragment for Video DVD player screen.
 */
@AndroidEntryPoint
class DvdPlayerFragment : Fragment() {
    
    @Inject lateinit var dvdPlayer: DvdPlayer
    @Inject lateinit var dvdSettings: DvdSettings
    
    override fun onDestroyView() {
        super.onDestroyView()
        Timber.d("DvdPlayerFragment: onDestroyView - releasing player")
        dvdPlayer.release()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.d("DvdPlayerFragment: onCreateView")
        
        // TODO: Get video stream from arguments
        val videoStream = VideoStream(
            codec = "MPEG-2",
            width = 720,
            height = 480,
            bitrate = 5000000,
            frameRate = 29.97,
            uri = null
        )
        
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    DvdPlayerScreen(
                        videoStream = videoStream,
                        dvdPlayer = dvdPlayer,
                        dvdSettings = dvdSettings,
                        onBack = {
                            Timber.d("Back button clicked")
                            findNavController().popBackStack()
                        }
                    )
                }
            }
        }
    }
}
