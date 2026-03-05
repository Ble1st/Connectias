package com.bleist.connectias.connectias

/**
 * JNI bridge to Rust library for SCSI/NTFS operations.
 * Rust calls back to Kotlin via BulkTransferHandler for USB bulk transfers.
 */
object NativeBridge {
    init {
        System.loadLibrary("connectias_rust")
    }

    /**
     * Opens an NTFS volume on the given session.
     * @param sessionId USB session ID from openDevice
     * @param handler BulkTransferHandler that performs bulk_out/bulk_in
     * @return volume ID (>0) on success, negative error code on failure
     */
    external fun openVolume(sessionId: Long, handler: BulkTransferHandler): Long

    /**
     * Closes the volume and releases resources.
     * @return ERR_OK (0) on success, negative error code on failure
     */
    external fun closeVolume(volumeId: Long): Int

    /**
     * Lists directory entries as JSON array.
     * @param path Path (e.g. "" or "/" for root, "folder" for subdir)
     * @return JSON string like [{"n":"name","d":true,"s":0},...] or null on error
     */
    external fun listDirectory(volumeId: Long, path: String): String?

    /**
     * Reads file content.
     * @param path File path
     * @param offset Byte offset
     * @param length Max bytes to read
     * @return Byte array or null on error
     */
    external fun readFile(volumeId: Long, path: String, offset: Long, length: Int): ByteArray?

    /**
     * Returns last error message from Rust.
     */
    external fun lastError(): String?

    // DVD operations (requires libdvdread)
    external fun getDeviceType(sessionId: Long, handler: BulkTransferHandler): String?
    external fun openDvd(sessionId: Long, handler: BulkTransferHandler): Long
    external fun closeDvd(dvdHandle: Long)
    external fun dvdListTitles(dvdHandle: Long): String?
    external fun dvdListChapters(dvdHandle: Long, titleId: Int): String?
    external fun dvdOpenTitleStream(dvdHandle: Long, titleId: Int): Long
    external fun dvdReadStream(streamId: Long, buffer: ByteArray): Int
    external fun dvdSeekStream(streamId: Long, offset: Long): Boolean
    external fun dvdCloseStream(streamId: Long)
}
