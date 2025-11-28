package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import com.ble1st.connectias.feature.network.exceptions.NetworkUnavailableException
import com.ble1st.connectias.feature.network.exceptions.PermissionDeniedException
import kotlinx.parcelize.Parcelize
import java.io.IOException
import java.net.UnknownHostException

/**
 * Wrapper for Parcelable lists.
 * Kotlin's List is not Parcelable by default. The @Parcelize plugin handles serialization.
 */
@Parcelize
data class ParcelableList<T : Parcelable>(val items: List<T>) : Parcelable {
    constructor() : this(emptyList())
}
/**
 * Result wrapper for network operations that can fail.
 * Distinguishes between successful data retrieval and errors.
 */
sealed class NetworkResult<out T> : Parcelable {
    @Parcelize
    data class Success<T : Parcelable>(val data: T) : NetworkResult<T>()
    
    @Parcelize
    data class Error(
        val message: String,
        val errorType: ErrorType
    ) : NetworkResult<Nothing>() {
        companion object {
            /**
             * Creates an Error from a Throwable.
             * Maps common exceptions to ErrorType enum values.
             */
            fun fromThrowable(throwable: Throwable): Error {
                val errorType = when (throwable) {
                    is PermissionDeniedException -> ErrorType.PermissionDenied
                    is NetworkUnavailableException -> ErrorType.NetworkError
                    is IOException -> ErrorType.NetworkError
                    is java.util.concurrent.TimeoutException -> ErrorType.Timeout
                    is kotlinx.coroutines.TimeoutCancellationException -> ErrorType.Timeout
                    else -> ErrorType.Unknown
                }
                return Error(
                    message = throwable.message ?: throwable.toString(),
                    errorType = errorType
                )
            }
        }
    }
}

/**
 * Represents the type of error that occurred during network operations.
 * Used for type-safe error handling instead of string matching.
 */
@Parcelize
enum class ErrorType : Parcelable {
    /**
     * Location permission is required but not granted.
     */
    PermissionDenied,
    
    /**
     * Network operation failed due to network unavailability or connectivity issues.
     */
    NetworkError,
    
    /**
     * Gateway or network configuration is unavailable (e.g., for LAN scans).
     */
    ConfigurationUnavailable,
    
    /**
     * Scan operation timed out.
     */
    Timeout,
    
    /**
     * Unknown or unexpected error occurred.
     */
    Unknown
}

