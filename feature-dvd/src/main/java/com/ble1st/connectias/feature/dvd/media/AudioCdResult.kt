package com.ble1st.connectias.feature.dvd.media

import com.ble1st.connectias.feature.dvd.models.AudioTrack

/**
 * Result wrapper for Audio CD operations that can fail.
 * Distinguishes between successful track retrieval and errors.
 */
sealed class AudioCdResult {
    /**
     * Successfully retrieved audio tracks.
     */
    data class Success(val tracks: List<AudioTrack>) : AudioCdResult()
    
    /**
     * Operation failed with an error.
     */
    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : AudioCdResult() {
        companion object {
            /**
             * Creates an Error from a Throwable.
             */
            fun fromThrowable(throwable: Throwable): Error {
                return Error(
                    message = throwable.message ?: throwable.toString(),
                    throwable = throwable
                )
            }
        }
    }
}
