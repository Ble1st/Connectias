package com.ble1st.connectias.plugin.messaging

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Response to a PluginMessage.
 *
 * @param requestId Request ID from the original message
 * @param success Whether the request was successful
 * @param payload Response payload as ByteArray
 * @param errorMessage Error message if success is false
 * @param timestamp Timestamp when response was created
 */
@Parcelize
data class MessageResponse(
    val requestId: String,
    val success: Boolean,
    val payload: ByteArray,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {

    companion object {
        /**
         * Create a successful response.
         */
        fun success(requestId: String, payload: ByteArray = ByteArray(0)): MessageResponse {
            return MessageResponse(
                requestId = requestId,
                success = true,
                payload = payload
            )
        }

        /**
         * Create an error response.
         */
        fun error(requestId: String, errorMessage: String): MessageResponse {
            return MessageResponse(
                requestId = requestId,
                success = false,
                payload = ByteArray(0),
                errorMessage = errorMessage
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageResponse

        if (requestId != other.requestId) return false
        if (success != other.success) return false
        if (!payload.contentEquals(other.payload)) return false
        if (errorMessage != other.errorMessage) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + success.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

