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
     * Navigates to a specific title and loads all chapters.
     */
    suspend fun navigateToTitle(dvdInfo: DvdInfo, titleNumber: Int): DvdTitle? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Navigating to title $titleNumber")
            val titleNative = DvdNative.dvdReadTitle(dvdInfo.handle, titleNumber)
            val title = titleNative?.let {
                // Load all chapters for this title
                val chapters = mutableListOf<DvdChapter>()
                for (chapterNum in 1..it.chapterCount) {
                    try {
                        val chapterNative = DvdNative.dvdReadChapter(dvdInfo.handle, titleNumber, chapterNum)
                        chapterNative?.let { ch ->
                            chapters.add(
                                DvdChapter(
                                    number = ch.number,
                                    titleNumber = titleNumber,
                                    startTime = ch.startTime,
                                    duration = ch.duration
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load chapter $chapterNum for title $titleNumber")
                        // Continue loading other chapters
                    }
                }
                
                DvdTitle(
                    number = it.number,
                    duration = it.duration,
                    chapters = chapters.toList() // Create immutable list
                )
            }
            if (title != null) {
                Timber.i("Title $titleNumber: ${title.chapters.size} chapters loaded, duration=${title.duration}ms")
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
            Timber.e(e, "Error navigating to chapter $chapterNumber in title $titleNumber")
            null
        }
    }
}
