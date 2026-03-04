package com.bleist.connectias.connectias

/**
 * Interface for bulk USB transfers. Implemented by UsbPlugin to perform
 * actual bulkTransferOut/bulkTransferIn. Passed to Rust via JNI.
 */
interface BulkTransferHandler {
    fun bulkOut(data: ByteArray): Int
    fun bulkIn(maxLength: Int): ByteArray
}
