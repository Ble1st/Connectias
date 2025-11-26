package com.ble1st.connectias.feature.network.exceptions

/**
 * Exception thrown when location permission is required but not granted.
 * 
 * Note: This extends RuntimeException (unchecked exception) rather than Exception (checked)
 * for the following reasons:
 * - Kotlin does not have checked exceptions, so RuntimeException is idiomatic
 * - These are recoverable errors that should be handled at the call site
 * - Java interop: Kotlin functions are called from Java without @Throws annotations
 * - The error is already wrapped in NetworkResult.Error for type-safe error handling
 */
class PermissionDeniedException(
    message: String = "Location permission is required for Wi‑Fi scanning",
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception thrown when network operations fail due to network unavailability.
 * 
 * Note: This extends RuntimeException (unchecked exception) rather than Exception (checked)
 * for the following reasons:
 * - Kotlin does not have checked exceptions, so RuntimeException is idiomatic
 * - These are recoverable errors that should be handled at the call site
 * - Java interop: Kotlin functions are called from Java without @Throws annotations
 * - The error is already wrapped in NetworkResult.Error for type-safe error handling
 */
class NetworkUnavailableException(message: String = "Network is unavailable") : RuntimeException(message)

