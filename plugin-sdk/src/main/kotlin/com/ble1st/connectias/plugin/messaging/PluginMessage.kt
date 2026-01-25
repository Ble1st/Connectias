package com.ble1st.connectias.plugin.messaging

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Message sent between plugins via the message broker.
 *
 * @param senderId Plugin ID of the sender
 * @param receiverId Plugin ID of the receiver
 * @param messageType Type identifier for the message (e.g., "DATA_REQUEST", "EVENT_NOTIFICATION")
 * @param payload Message payload as ByteArray (can be serialized data)
 * @param requestId Unique request ID for request/response matching
 * @param timestamp Timestamp when message was created
 */
@Parcelize
data class PluginMessage(
    val senderId: String,
    val receiverId: String,
    val messageType: String,
    val payload: ByteArray,
    val requestId: String,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PluginMessage

        if (senderId != other.senderId) return false
        if (receiverId != other.receiverId) return false
        if (messageType != other.messageType) return false
        if (!payload.contentEquals(other.payload)) return false
        if (requestId != other.requestId) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = senderId.hashCode()
        result = 31 * result + receiverId.hashCode()
        result = 31 * result + messageType.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + requestId.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

