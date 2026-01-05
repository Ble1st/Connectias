#ifndef DVD_WRAPPER_H
#define DVD_WRAPPER_H

#include <jni.h>
#include <cstdint>

// JNI function declarations
extern "C" {
    /**
     * Opens a DVD device/file for reading.
     * @param env JNI environment
     * @param clazz Java class
     * @param path DVD path (must be validated to prevent path traversal attacks)
     * @return Handle to DVD (non-zero on success, 0 on failure). Invalid handle: 0 or negative.
     * @throws IllegalArgumentException if path is invalid or null
     * @throws IOException if DVD cannot be opened (invalid path, I/O errors, unsupported format)
     * Thread-safe: No - handles are not shareable. Each call should use a separate handle.
     * Resource lifecycle: Caller must call dvdClose() to release resources. Leaks if not closed.
     * Performance: Blocks during I/O operations. Expected cost: moderate (file system access).
     */
    JNIEXPORT jlong JNICALL
    Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdOpenNative(JNIEnv *env, jobject clazz, jstring path);
    
    /**
     * Opens a DVD device using a custom stream callback (e.g. for USB Mass Storage).
     * @param env JNI environment
     * @param clazz Java class
     * @param blockDevice UsbBlockDevice instance to read from
     * @return Handle to DVD (non-zero on success, 0 on failure).
     */
    JNIEXPORT jlong JNICALL
    Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdOpenStreamNative(JNIEnv *env, jobject clazz, jobject blockDevice);

    /**
     * Streams title data directly to a file descriptor (Pipe).
     * Blocks until completion or error.
     *
     * @param env JNI environment
     * @param clazz Java class
     * @param handle Handle to opened DVD
     * @param titleNumber Title number to stream (1-based)
     * @param outFd File descriptor to write data to
     * @return Number of bytes written, or -1 on error
     */
    JNIEXPORT jlong JNICALL
    Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdStreamToFdNative(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber, jint outFd);

    /**
     * Closes a DVD handle and releases resources.
     * @param env JNI environment
     * @param clazz Java class
     * @param handle DVD handle (0 or negative is invalid)
     * Error handling: Invalid handle is logged but no exception thrown. Double-close is safe (no-op).
     * Thread-safe: No - handles are not shareable.
     * Resource lifecycle: Releases all resources associated with handle. Safe to call multiple times.
     * Performance: Non-blocking. Expected cost: low (cleanup only).
     */
    JNIEXPORT void JNICALL
    Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdCloseNative(JNIEnv *env, jobject clazz, jlong handle);
    
    /**
     * Gets the number of titles on the DVD.
     * @param env JNI environment
     * @param clazz Java class
     * @param handle DVD handle (must be > 0)
     * @return Number of titles (0 if none, -1 on error/invalid handle)
     * @throws IllegalArgumentException if handle is invalid (0 or negative)
     * Thread-safe: No - handles are not shareable.
     * Input validation: handle must be > 0
     * Performance: Blocks during I/O. Expected cost: low (metadata read).
     */
    JNIEXPORT jint JNICALL
    Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdGetTitleCountNative(JNIEnv *env, jobject clazz, jlong handle);
    
    /**
     * Reads title information from the DVD.
     * @param env JNI environment
     * @param clazz Java class
     * @param handle DVD handle (must be > 0)
     * @param titleNumber Title number (must be >= 1, within valid range)
     * @return Local JNI reference to com.ble1st.connectias.feature.dvd.models.DvdTitle (automatically freed when native method returns), or NULL on error
     * @throws IllegalArgumentException if handle is invalid (0 or negative) or titleNumber is out of bounds (< 1 or > title count)
     * @throws UnsupportedOperationException if not yet implemented
     * Thread-safe: No - handles are not shareable.
     * Input validation: handle > 0, titleNumber >= 1 and <= title count
     * JNI reference: Returns local reference (auto-freed). Do not store globally without NewGlobalRef.
     * Performance: Blocks during I/O. Expected cost: moderate (title metadata read).
     */
    JNIEXPORT jobject JNICALL
    Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdReadTitleNative(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber);
    
    /**
     * Reads chapter information from a specific title.
     * @param env JNI environment
     * @param clazz Java class
     * @param handle DVD handle (must be > 0)
     * @param titleNumber Title number (must be >= 1)
     * @param chapterNumber Chapter number (must be >= 1)
     * @return Local JNI reference to com.ble1st.connectias.feature.dvd.models.DvdChapter (automatically freed when native method returns), or NULL on error
     * @throws IllegalArgumentException if handle is invalid (0 or negative) or titleNumber/chapterNumber is out of bounds (< 1 or > valid range)
     * @throws UnsupportedOperationException if not yet implemented
     * Thread-safe: No - handles are not shareable.
     * Input validation: handle > 0, titleNumber >= 1, chapterNumber >= 1, both within valid ranges
     * JNI reference: Returns local reference (auto-freed). Do not store globally without NewGlobalRef.
     * Performance: Blocks during I/O. Expected cost: moderate (chapter metadata read).
     */

#ifdef ENABLE_DVD_CSS
    /**
     * Decrypts CSS-encrypted DVD content.
     * WARNING: CSS decryption may violate DMCA and other copyright laws. Legal review required before implementation.
     * @param env JNI environment
     * @param clazz Java class
     * @param handle DVD handle (must be > 0)
     * @param titleNumber Title number (must be >= 1)
     * @return Local JNI reference to jbyteArray containing decrypted data (automatically freed when native method returns), or NULL on error
     * @throws IllegalArgumentException if handle is invalid (0 or negative) or titleNumber is out of bounds
     * @throws UnsupportedOperationException if not yet implemented or legal approval not obtained
     * Thread-safe: No - handles are not shareable.
     * Input validation: handle > 0, titleNumber >= 1
     * JNI reference: Returns local reference to jbyteArray (auto-freed). Caller must copy data if needed beyond method scope.
     * Error handling: Returns NULL and throws exception on invalid handle or failed decryption.
     * Legal note: TODO - Verify legal compliance (DMCA, DVD CCA licensing) before implementing.
     * Performance: Blocks during decryption. Expected cost: high (cryptographic operations).
     */
    JNIEXPORT jbyteArray JNICALL
    Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdDecryptCss(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber);
    #endif
    
    /**
     * Extracts video stream from DVD title/chapter.
     * @param env JNI environment
     * @param clazz Java class
     * @param handle DVD handle (must be > 0)
     * @param titleNumber Title number (must be >= 1)
     * @param chapterNumber Chapter number (must be >= 1)
     * @return Local JNI reference to java.io.InputStream or custom VideoStream wrapper (streamed implementation preferred for large videos to avoid single heap buffer allocation). 
     *         Memory ownership: Native side manages stream, caller must close InputStream when done. 
     *         Returns NULL on error.
     * @throws IllegalArgumentException if handle is invalid (0 or negative) or titleNumber/chapterNumber is out of bounds
     * @throws UnsupportedOperationException if not yet implemented
     * @throws IOException if stream extraction fails
     * Thread-safe: No - handles are not shareable.
     * Input validation: handle > 0, titleNumber >= 1, chapterNumber >= 1
     * JNI reference: Returns local reference (auto-freed). For InputStream, caller must call close() to release native resources.
     * Implementation guidance: Prefer streaming via native InputStream-backed implementation over full-buffered allocation for large videos.
     * Legal note: TODO - Verify implementation does not bypass encryption/DRM/CSS. Consult legal before implementing extraction.
     * Performance: Blocks during extraction. Expected cost: high (video decoding/transcoding).
     */
    [[maybe_unused]] JNIEXPORT jobject JNICALL
    Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdExtractVideoStreamNative(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber, jint chapterNumber);
    
    /**
     * Gets the DVD name/title from the VMG.
     * @param env JNI environment
     * @param clazz Java class
     * @param handle DVD handle (must be > 0)
     * @return Local JNI reference to jstring containing DVD name (automatically freed when native method returns), or NULL on error/not available
     * @throws IllegalArgumentException if handle is invalid (0 or negative)
     * Thread-safe: No - handles are not shareable.
     * Input validation: handle must be > 0
     * Performance: Blocks during I/O. Expected cost: low (metadata read).
     */
    JNIEXPORT jstring JNICALL
    Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdGetNameNative(JNIEnv *env, jobject clazz, jlong handle);
    
    /**
     * Ejects an optical drive device.
     * @param env JNI environment
     * @param clazz Java class
     * @param devicePath Device path (e.g., /dev/sg0, /dev/sr0)
     * @return true if eject command was sent successfully, false otherwise
     * @throws IOException if device cannot be accessed or eject fails
     * Thread-safe: Yes - each call operates on a separate device path
     * Performance: Blocks during ioctl operation. Expected cost: low (single system call).
     */
}

#endif // DVD_WRAPPER_H
