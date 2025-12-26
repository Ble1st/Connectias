package com.ble1st.connectias.feature.dvd.media

import com.ble1st.connectias.feature.dvd.models.DvdChapter
import com.ble1st.connectias.feature.dvd.models.DvdInfo
import com.ble1st.connectias.feature.dvd.models.DvdTitle
import com.ble1st.connectias.feature.dvd.native.DvdNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles DVD navigation (menus, titles, chapters).
 */
@Singleton
class DvdNavigation @Inject constructor(
    /**
     * Maximum number of concurrent chapter reads.
     * Default: 4
     */
    private val maxConcurrentChapterReads: Int = DEFAULT_MAX_CONCURRENT_CHAPTER_READS,
    
    /**
     * Timeout in milliseconds for each chapter read operation.
     * Default: 5000L (5 seconds)
     */
    private val chapterReadTimeoutMs: Long = DEFAULT_CHAPTER_READ_TIMEOUT_MS
) {
    companion object {
        /**
         * Default maximum number of concurrent chapter reads.
         */
        private const val DEFAULT_MAX_CONCURRENT_CHAPTER_READS = 4
        
        /**
         * Default timeout in milliseconds for each chapter read operation.
         */
        private const val DEFAULT_CHAPTER_READ_TIMEOUT_MS = 5000L
    }
    
    /**
     * Navigates to a specific title and loads all chapters.
     * Chapter reading is performed in parallel with concurrency limits to avoid
     * overwhelming the system on large discs.
     */
    suspend fun navigateToTitle(dvdInfo: DvdInfo, titleNumber: Int): DvdTitle? = withContext(Dispatchers.IO) {
        try {
            Timber.d("Navigating to title $titleNumber")
            val titleNative = DvdNative.dvdReadTitle(dvdInfo.handle, titleNumber)
            val title = titleNative?.let {
                // Load all chapters for this title in parallel
                val chapters = loadChaptersParallel(dvdInfo.handle, titleNumber, it.chapterCount)
                
                DvdTitle(
                    number = it.number,
                    duration = it.duration,
                    chapterCount = it.chapterCount
                )
            }
            if (title != null) {
                Timber.i("Title $titleNumber: ${title.chapterCount} chapters loaded, duration=${title.duration}ms")
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
     * Loads chapters in parallel with concurrency control and timeout protection.
     * Uses a semaphore to limit concurrent reads and withTimeoutOrNull to prevent
     * individual reads from blocking indefinitely.
     */
    private suspend fun loadChaptersParallel(
        handle: Long,
        titleNumber: Int,
        chapterCount: Int
    ): List<DvdChapter> = coroutineScope {
        // Limit concurrent chapter reads to avoid overwhelming the system
        val semaphore = Semaphore(maxConcurrentChapterReads)
        
        // Create async tasks for all chapters
        val chapterDeferreds = (1..chapterCount).map { chapterNum ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    withTimeoutOrNull(chapterReadTimeoutMs) {
                        try {
                            val chapterNative = DvdNative.dvdReadChapter(handle, titleNumber, chapterNum)
                            chapterNative?.let {
                                DvdChapter(
                                    number = it.number,
                                    titleNumber = titleNumber,
                                    startTime = it.startTime,
                                    duration = it.duration
                                )
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to load chapter $chapterNum for title $titleNumber")
                            null
                        }
                    } ?: run {
                        Timber.w("Timeout loading chapter $chapterNum for title $titleNumber")
                        null
                    }
                }
            }
        }
        
        // Collect successful results
        val chapters = mutableListOf<DvdChapter>()
        var completedCount = 0
        
        chapterDeferreds.forEachIndexed { index, deferred ->
            try {
                val chapter = deferred.await()
                if (chapter != null) {
                    chapters.add(chapter)
                }
                completedCount++
                
                // Log progress periodically (every 10 chapters or at completion)
                if (completedCount % 10 == 0 || completedCount == chapterCount) {
                    Timber.d("Loaded $completedCount/$chapterCount chapters for title $titleNumber")
                }
            } catch (e: CancellationException) {
                // Rethrow CancellationException immediately to preserve structured concurrency
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Error awaiting chapter ${index + 1} for title $titleNumber")
                completedCount++
            }
        }
        
        // Sort chapters by number to ensure correct order and return as immutable list
        chapters.sortedBy { it.number }
    }

}
