package com.ble1st.connectias.feature.usb.media

import com.ble1st.connectias.feature.usb.models.DvdChapter
import com.ble1st.connectias.feature.usb.models.DvdInfo
import com.ble1st.connectias.feature.usb.models.DvdTitle
import com.ble1st.connectias.feature.usb.native.DvdChapterNative
import com.ble1st.connectias.feature.usb.native.DvdNative
import com.ble1st.connectias.feature.usb.native.DvdTitleNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles DVD navigation (menus, titles, chapters).
 */
@Singleton
class DvdNavigation @Inject constructor() {
    
    /**
     * Navigates to a specific title.
     */
    suspend fun navigateToTitle(dvdInfo: DvdInfo, titleNumber: Int): DvdTitle? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Navigating to title $titleNumber")
            val titleNative = DvdNative.dvdReadTitle(dvdInfo.handle, titleNumber)
            val title = titleNative?.let {
                DvdTitle(
                    number = it.number,
                    chapterCount = it.chapterCount,
                    duration = it.duration,
                    chapters = emptyList() // TODO: Load chapters
                )
            }
            if (title != null) {
                Timber.i("Title $titleNumber: ${title.chapterCount} chapters, duration=${title.duration}ms")
            } else {
                Timber.w("Title $titleNumber not found")
            }
            title
        } catch (e: Exception) {
            Timber.e(e, "Error navigating to title $titleNumber")
            null
        }
    }
    
    /**
     * Navigates to a specific chapter.
     */
    suspend fun navigateToChapter(dvdInfo: DvdInfo, titleNumber: Int, chapterNumber: Int): DvdChapter? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Navigating to chapter $chapterNumber in title $titleNumber")
            val chapterNative = DvdNative.dvdReadChapter(dvdInfo.handle, titleNumber, chapterNumber)
            val chapter = chapterNative?.let {
                DvdChapter(
                    number = it.number,
                    titleNumber = titleNumber,
                    startTime = it.startTime,
                    duration = it.duration
                )
            }
            if (chapter != null) {
                Timber.d("Chapter $chapterNumber: startTime=${chapter.startTime}ms, duration=${chapter.duration}ms")
            } else {
                Timber.w("Chapter $chapterNumber not found in title $titleNumber")
            }
            chapter
        } catch (e: Exception) {
            Timber.e(e, "Error navigating to chapter")
            null
        }
    }
}
