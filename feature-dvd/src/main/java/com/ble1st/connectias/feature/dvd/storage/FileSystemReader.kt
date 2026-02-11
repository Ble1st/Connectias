package com.ble1st.connectias.feature.dvd.storage

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads file system information from mounted optical drives.
 * Uses OS-level file system queries via /proc/mounts for reliable detection.
 */
@Singleton
class FileSystemReader @Inject constructor() {

}
