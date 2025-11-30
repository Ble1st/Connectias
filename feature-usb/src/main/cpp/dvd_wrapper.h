#ifndef DVD_WRAPPER_H
#define DVD_WRAPPER_H

#include <jni.h>
#include <cstdint>

// DVD Title structure
struct DvdTitleNative {
    int number;
    int chapterCount;
    int64_t duration; // milliseconds (fixed-width for cross-platform consistency)
};

// DVD Chapter structure
struct DvdChapterNative {
    int number;
    int64_t startTime; // milliseconds (fixed-width for cross-platform consistency)
    int64_t duration; // milliseconds (fixed-width for cross-platform consistency)
};

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
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdOpen(JNIEnv *env, jclass clazz, jstring path);
    
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
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdClose(JNIEnv *env, jclass clazz, jlong handle);
    
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
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdGetTitleCount(JNIEnv *env, jclass clazz, jlong handle);
    
    /**
     * Reads title information from the DVD.
     * @param env JNI environment
     * @param clazz Java class
     * @param handle DVD handle (must be > 0)
     * @param titleNumber Title number (must be >= 1, within valid range)
     * @return Local JNI reference to com.ble1st.connectias.feature.usb.models.DvdTitle (automatically freed when native method returns), or NULL on error
     * @throws IllegalArgumentException if handle is invalid (0 or negative) or titleNumber is out of bounds (< 1 or > title count)
     * @throws UnsupportedOperationException if not yet implemented
     * Thread-safe: No - handles are not shareable.
     * Input validation: handle > 0, titleNumber >= 1 and <= title count
     * JNI reference: Returns local reference (auto-freed). Do not store globally without NewGlobalRef.
     * Performance: Blocks during I/O. Expected cost: moderate (title metadata read).
     */
    JNIEXPORT jobject JNICALL
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdReadTitle(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber);
    
    /**
     * Reads chapter information from a specific title.
     * @param env JNI environment
     * @param clazz Java class
     * @param handle DVD handle (must be > 0)
     * @param titleNumber Title number (must be >= 1)
     * @param chapterNumber Chapter number (must be >= 1)
     * @return Local JNI reference to com.ble1st.connectias.feature.usb.models.DvdChapter (automatically freed when native method returns), or NULL on error
     * @throws IllegalArgumentException if handle is invalid (0 or negative) or titleNumber/chapterNumber is out of bounds (< 1 or > valid range)
     * @throws UnsupportedOperationException if not yet implemented
     * Thread-safe: No - handles are not shareable.
     * Input validation: handle > 0, titleNumber >= 1, chapterNumber >= 1, both within valid ranges
     * JNI reference: Returns local reference (auto-freed). Do not store globally without NewGlobalRef.
     * Performance: Blocks during I/O. Expected cost: moderate (chapter metadata read).
     */
    JNIEXPORT jobject JNICALL
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdReadChapter(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber, jint chapterNumber);
    
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
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdDecryptCss(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber);
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
    JNIEXPORT jobject JNICALL
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdExtractVideoStream(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber, jint chapterNumber);
}

#endif // DVD_WRAPPER_H
