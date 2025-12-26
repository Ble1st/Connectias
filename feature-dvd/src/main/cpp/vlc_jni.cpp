#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string>
#include <vector>
#include <cstring>
#include "include/vlc/vlc_stub.h"

#define LOG_TAG "VlcJni"

#ifndef VLC_LOG_VERBOSE
#define VLC_LOG_VERBOSE 0
#endif

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#if VLC_LOG_VERBOSE
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...) ((void)0)
#endif

// Global JavaVM reference
static JavaVM *g_vm = nullptr;

// Function pointers for LibVLC
static void *g_libvlc_handle = nullptr;
static libvlc_media_new_callbacks_t g_libvlc_media_new_callbacks = nullptr;
static libvlc_media_release_t g_libvlc_media_release = nullptr;
static libvlc_media_player_set_media_t g_libvlc_media_player_set_media = nullptr;
static libvlc_media_add_option_t g_libvlc_media_add_option = nullptr;

// Struct to hold state for the callbacks
struct JavaCallbackData {
    jobject callbackObject; // Global ref to the Kotlin VlcDvdPlayer instance
    jmethodID readMethod;
    jmethodID seekMethod;
    jmethodID closeMethod;
    jmethodID getSizeMethod;
    jmethodID openMethod;
    
    uint64_t size; // Cached size
};

// --- Callback Implementations ---

static int media_open_cb(void *opaque, void **datap, uint64_t *sizep) {
    // CRITICAL: This is the FIRST line - log immediately to confirm callback is called
    __android_log_print(ANDROID_LOG_ERROR, "VlcJni", "media_open_cb() CALLED - opaque: %p", (void*)opaque);
    fflush(stdout);
    fflush(stderr);
    
    LOGI("VlcJni: media_open_cb() CALLED - opaque: %p", (void*)opaque);
    fflush(stdout);
    fflush(stderr);
    
    auto *data = static_cast<JavaCallbackData*>(opaque);
    if (!data) {
        LOGE("VlcJni: media_open_cb() - data is null");
        fflush(stdout);
        fflush(stderr);
        return -1;
    }
    if (!g_vm) {
        LOGE("VlcJni: media_open_cb() - g_vm is null");
        fflush(stdout);
        fflush(stderr);
        return -1;
    }
    LOGI("VlcJni: media_open_cb() - data and g_vm are valid");
    fflush(stdout);
    fflush(stderr);

    LOGD("VlcJni: media_open_cb() - Getting JNI environment");
    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGD("VlcJni: media_open_cb() - Attaching thread to JVM");
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("VlcJni: media_open_cb() - Failed to attach thread");
            return -1;
        }
        attached = true;
        LOGD("VlcJni: media_open_cb() - Thread attached successfully");
    } else {
        LOGD("VlcJni: media_open_cb() - Already attached to JVM");
    }

    // Call open()
    LOGD("VlcJni: media_open_cb() - Calling Java ioOpen() method");
    bool openSuccess = env->CallBooleanMethod(data->callbackObject, data->openMethod);
    LOGD("VlcJni: media_open_cb() - ioOpen() returned: %s", openSuccess ? "true" : "false");
    if (!openSuccess) {
        LOGE("VlcJni: media_open_cb() - ioOpen() failed");
        if (attached) {
            LOGD("VlcJni: media_open_cb() - Detaching thread");
            g_vm->DetachCurrentThread();
        }
        return -1;
    }

    // Call getSize()
    LOGD("VlcJni: media_open_cb() - Calling Java ioGetSize() method");
    jlong size = env->CallLongMethod(data->callbackObject, data->getSizeMethod);
    data->size = (uint64_t)size;
    *sizep = data->size;
    *datap = data; // Pass our data struct to other callbacks
    LOGD("VlcJni: media_open_cb() - ioGetSize() returned: %llu bytes", (unsigned long long)data->size);
    LOGD("VlcJni: media_open_cb() - Setting datap to: %p", (void*)data);

    if (attached) {
        LOGD("VlcJni: media_open_cb() - Detaching thread");
        g_vm->DetachCurrentThread();
    }
    LOGD("VlcJni: media_open_cb() - Success, returning 0");
    return 0;
}

static ssize_t media_read_cb(void *opaque, unsigned char *buf, size_t len) {
    LOGD("VlcJni: media_read_cb() called - requested size: %zu bytes", len);
    auto *data = static_cast<JavaCallbackData*>(opaque);
    if (!data) {
        LOGE("VlcJni: media_read_cb() - data is null");
        return -1;
    }
    if (!g_vm) {
        LOGE("VlcJni: media_read_cb() - g_vm is null");
        return -1;
    }

    LOGD("VlcJni: media_read_cb() - Getting JNI environment");
    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGD("VlcJni: media_read_cb() - Attaching thread to JVM");
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("VlcJni: media_read_cb() - Failed to attach thread");
            return -1;
        }
        attached = true;
        LOGD("VlcJni: media_read_cb() - Thread attached successfully");
    }

    // Create a ByteArray to receive data
    // Note: Creating a new array every read is inefficient. 
    // Optimization: Pass a direct ByteBuffer or reuse a buffer if possible.
    // For now, we stick to the simple JNI contract.
    LOGD("VlcJni: media_read_cb() - Creating Java byte array of size: %zu", len);
    jbyteArray javaBuf = env->NewByteArray(len);
    if (javaBuf == nullptr) {
        LOGE("VlcJni: media_read_cb() - Failed to create Java byte array");
        if (attached) g_vm->DetachCurrentThread();
        return -1;
    }
    LOGD("VlcJni: media_read_cb() - Java byte array created successfully");
    
    LOGD("VlcJni: media_read_cb() - Calling Java ioRead() method");
    jint bytesRead = env->CallIntMethod(data->callbackObject, data->readMethod, javaBuf, (jint)len);
    LOGD("VlcJni: media_read_cb() - ioRead() returned: %d bytes", bytesRead);

    if (bytesRead > 0) {
        LOGD("VlcJni: media_read_cb() - Copying %d bytes from Java array to C buffer", bytesRead);
        env->GetByteArrayRegion(javaBuf, 0, bytesRead, (jbyte*)buf);
        LOGD("VlcJni: media_read_cb() - Data copied successfully");
    } else if (bytesRead < 0) {
        LOGE("VlcJni: media_read_cb() - ioRead() returned error: %d", bytesRead);
    } else {
        LOGD("VlcJni: media_read_cb() - ioRead() returned 0 (EOF or no data)");
    }

    LOGD("VlcJni: media_read_cb() - Deleting local reference to Java byte array");
    env->DeleteLocalRef(javaBuf);
    if (attached) {
        LOGD("VlcJni: media_read_cb() - Detaching thread");
        g_vm->DetachCurrentThread();
    }
    
    int result = (bytesRead < 0) ? -1 : bytesRead;
    LOGD("VlcJni: media_read_cb() - Returning: %d bytes", result);
    return result;
}

static int media_seek_cb(void *opaque, uint64_t offset) {
    LOGD("VlcJni: media_seek_cb() called - seeking to offset: %llu", (unsigned long long)offset);
    auto *data = static_cast<JavaCallbackData*>(opaque);
    if (!data) {
        LOGE("VlcJni: media_seek_cb() - data is null");
        return -1;
    }
    if (!g_vm) {
        LOGE("VlcJni: media_seek_cb() - g_vm is null");
        return -1;
    }

    LOGD("VlcJni: media_seek_cb() - Getting JNI environment");
    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGD("VlcJni: media_seek_cb() - Attaching thread to JVM");
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("VlcJni: media_seek_cb() - Failed to attach thread");
            return -1;
        }
        attached = true;
        LOGD("VlcJni: media_seek_cb() - Thread attached successfully");
    }

    LOGD("VlcJni: media_seek_cb() - Calling Java ioSeek() method with offset: %llu", (unsigned long long)offset);
    jboolean result = env->CallBooleanMethod(data->callbackObject, data->seekMethod, (jlong)offset);
    LOGD("VlcJni: media_seek_cb() - ioSeek() returned: %s", (result == JNI_TRUE) ? "true" : "false");

    if (attached) {
        LOGD("VlcJni: media_seek_cb() - Detaching thread");
        g_vm->DetachCurrentThread();
    }
    int returnValue = (result == JNI_TRUE) ? 0 : -1;
    LOGD("VlcJni: media_seek_cb() - Returning: %d", returnValue);
    return returnValue;
}

static void media_close_cb(void *opaque) {
    LOGD("VlcJni: media_close_cb() called");
    auto *data = static_cast<JavaCallbackData*>(opaque);
    if (!data) {
        LOGE("VlcJni: media_close_cb() - data is null");
        return;
    }
    if (!g_vm) {
        LOGE("VlcJni: media_close_cb() - g_vm is null");
        return;
    }

    LOGD("VlcJni: media_close_cb() - Getting JNI environment");
    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGD("VlcJni: media_close_cb() - Attaching thread to JVM");
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("VlcJni: media_close_cb() - Failed to attach thread");
            return;
        }
        attached = true;
        LOGD("VlcJni: media_close_cb() - Thread attached successfully");
    }

    LOGD("VlcJni: media_close_cb() - Calling Java ioClose() method");
    env->CallVoidMethod(data->callbackObject, data->closeMethod);
    LOGD("VlcJni: media_close_cb() - ioClose() completed");
    
    LOGD("VlcJni: media_close_cb() - Deleting global reference to callback object");
    env->DeleteGlobalRef(data->callbackObject);
    LOGD("VlcJni: media_close_cb() - Global reference deleted");
    
    LOGD("VlcJni: media_close_cb() - Deleting JavaCallbackData structure");
    delete data;
    LOGD("VlcJni: media_close_cb() - JavaCallbackData deleted");

    if (attached) {
        LOGD("VlcJni: media_close_cb() - Detaching thread");
        g_vm->DetachCurrentThread();
    }
    LOGD("VlcJni: media_close_cb() - Complete");
}

// --- JNI Implementations ---


/**
 * Initialize LibVLC function pointers by loading libvlc.so dynamically.
 * This avoids link-time dependencies.
 * 
 * IMPORTANT: We no longer create our own libvlc_instance_t because VLC Android
 * requires the JavaVM to be registered first (done by libvlcjni.so).
 * Instead, we will get the instance from the Java LibVLC object's native pointer.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ble1st_connectias_feature_dvd_media_VlcDvdPlayer_nativeInit(JNIEnv *env, jobject clazz) {
    LOGD("VlcJni: nativeInit() called");
    if (g_libvlc_handle) {
        LOGD("VlcJni: nativeInit() - Already initialized, returning true");
        return JNI_TRUE;
    }

    // Initialize g_vm from env
    if (!g_vm) {
        if (env->GetJavaVM(&g_vm) != JNI_OK) {
            LOGE("VlcJni: nativeInit() - Failed to get JavaVM from env");
            return JNI_FALSE;
        }
        LOGI("VlcJni: nativeInit() - g_vm initialized: %p", (void*)g_vm);
    }

    LOGD("VlcJni: nativeInit() - Attempting to load libvlc.so");
    // Try to load libvlc.so - it should already be loaded by Java LibVLC
    g_libvlc_handle = dlopen("libvlc.so", RTLD_LAZY | RTLD_NOLOAD);
    if (!g_libvlc_handle) {
        LOGD("VlcJni: nativeInit() - libvlc.so not already loaded, loading now");
        g_libvlc_handle = dlopen("libvlc.so", RTLD_LAZY);
    }
    if (!g_libvlc_handle) {
        const char* error = dlerror();
        LOGE("VlcJni: nativeInit() - Failed to load libvlc.so: %s", error ? error : "unknown error");
        return JNI_FALSE;
    }
    LOGD("VlcJni: nativeInit() - libvlc.so loaded successfully, handle: %p", (void*)g_libvlc_handle);

    LOGD("VlcJni: nativeInit() - Looking up libvlc_media_new_callbacks symbol");
    g_libvlc_media_new_callbacks = (libvlc_media_new_callbacks_t)dlsym(g_libvlc_handle, "libvlc_media_new_callbacks");
    if (g_libvlc_media_new_callbacks) {
        LOGD("VlcJni: nativeInit() - libvlc_media_new_callbacks found: %p", (void*)g_libvlc_media_new_callbacks);
    } else {
        LOGE("VlcJni: nativeInit() - libvlc_media_new_callbacks not found: %s", dlerror());
    }

    LOGD("VlcJni: nativeInit() - Looking up libvlc_media_release symbol");
    g_libvlc_media_release = (libvlc_media_release_t)dlsym(g_libvlc_handle, "libvlc_media_release");
    if (g_libvlc_media_release) {
        LOGD("VlcJni: nativeInit() - libvlc_media_release found: %p", (void*)g_libvlc_media_release);
    } else {
        LOGE("VlcJni: nativeInit() - libvlc_media_release not found: %s", dlerror());
    }

    LOGD("VlcJni: nativeInit() - Looking up libvlc_media_player_set_media symbol");
    g_libvlc_media_player_set_media = (libvlc_media_player_set_media_t)dlsym(g_libvlc_handle, "libvlc_media_player_set_media");
    if (g_libvlc_media_player_set_media) {
        LOGD("VlcJni: nativeInit() - libvlc_media_player_set_media found: %p", (void*)g_libvlc_media_player_set_media);
    } else {
        LOGW("VlcJni: nativeInit() - libvlc_media_player_set_media not found: %s", dlerror());
    }

    LOGD("VlcJni: nativeInit() - Looking up libvlc_media_add_option symbol");
    g_libvlc_media_add_option = (libvlc_media_add_option_t)dlsym(g_libvlc_handle, "libvlc_media_add_option");
    if (g_libvlc_media_add_option) {
        LOGD("VlcJni: nativeInit() - libvlc_media_add_option found: %p", (void*)g_libvlc_media_add_option);
    } else {
        LOGW("VlcJni: nativeInit() - libvlc_media_add_option not found: %s", dlerror());
    }

    // Check required symbols
    if (!g_libvlc_media_new_callbacks || !g_libvlc_media_release) {
        LOGE("VlcJni: nativeInit() - Failed to load required LibVLC symbols");
        if (!g_libvlc_media_new_callbacks) {
            LOGE("VlcJni: nativeInit() -   - libvlc_media_new_callbacks symbol not found");
        }
        if (!g_libvlc_media_release) {
            LOGE("VlcJni: nativeInit() -   - libvlc_media_release symbol not found");
        }
        dlclose(g_libvlc_handle);
        g_libvlc_handle = nullptr;
        return JNI_FALSE;
    }

    LOGD("VlcJni: nativeInit() - Successfully loaded LibVLC symbols");
    LOGD("VlcJni: nativeInit() -   - libvlc_media_new_callbacks: %p", (void*)g_libvlc_media_new_callbacks);
    LOGD("VlcJni: nativeInit() -   - libvlc_media_release: %p", (void*)g_libvlc_media_release);
    LOGD("VlcJni: nativeInit() -   - libvlc_media_add_option: %p", (void*)g_libvlc_media_add_option);
    LOGD("VlcJni: nativeInit() - Initialization complete, returning true");
    
    __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
        "=== nativeInit() SUCCESS - Will use Java LibVLC's native instance ===");
    
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ble1st_connectias_feature_dvd_media_VlcDvdPlayer_nativeCreateMedia(
        JNIEnv *env, 
        jobject thiz, 
        jlong libVlcInstance) {

    // Initialize g_vm from env if not already set (fallback)
    if (!g_vm) {
        LOGW("VlcJni: g_vm not initialized, attempting to get from env");
        if (env->GetJavaVM(&g_vm) != JNI_OK) {
            LOGE("VlcJni: Failed to get JavaVM from env");
            return 0;
        }
        LOGI("VlcJni: g_vm initialized from env: %p", (void*)g_vm);
    }

    if (!g_libvlc_media_new_callbacks) {
        LOGE("LibVLC not initialized - nativeInit() must be called first");
        return 0;
    }

    if (libVlcInstance == 0) {
        LOGE("Invalid libVLC instance handle: 0");
        return 0;
    }

    // Log the Java handle information
    __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
        "=== Java provided handle: 0x%llx (decimal: %lld) ===", 
        (unsigned long long)libVlcInstance, (long long)libVlcInstance);

    LOGD("Creating media with callbacks, instance handle: 0x%llx", (unsigned long long)libVlcInstance);

    // 1. Prepare Callback Data
    auto *data = new JavaCallbackData();
    data->callbackObject = env->NewGlobalRef(thiz);
    
    jclass clazz = env->GetObjectClass(thiz);
    data->readMethod = env->GetMethodID(clazz, "ioRead", "([BI)I");
    data->seekMethod = env->GetMethodID(clazz, "ioSeek", "(J)Z");
    data->closeMethod = env->GetMethodID(clazz, "ioClose", "()V");
    data->getSizeMethod = env->GetMethodID(clazz, "ioGetSize", "()J");
    data->openMethod = env->GetMethodID(clazz, "ioOpen", "()Z");

    if (!data->readMethod || !data->seekMethod || !data->closeMethod || !data->getSizeMethod || !data->openMethod) {
        LOGE("Failed to find callback methods in Java class");
        if (!data->readMethod) LOGE("  - ioRead method not found");
        if (!data->seekMethod) LOGE("  - ioSeek method not found");
        if (!data->closeMethod) LOGE("  - ioClose method not found");
        if (!data->getSizeMethod) LOGE("  - ioGetSize method not found");
        if (!data->openMethod) LOGE("  - ioOpen method not found");
        env->DeleteGlobalRef(data->callbackObject);
        delete data;
        return 0;
    }

    LOGD("All callback methods found successfully");

    // 2. Use the Java-provided instance handle
    // The mInstance field from VLCObject might be a pointer to VLCJniObject wrapper,
    // or the libvlc_instance_t* itself.
    // We suspect it is a wrapper because direct usage crashes.
    // VLCJniObject layout (from libvlcjni source):
    // struct VLCJniObject { jobject thiz; libvlc_instance_t *p_libvlc; ... }
    
    auto *inst = reinterpret_cast<libvlc_instance_t*>(libVlcInstance);
    
    // Heuristic probe:
    // If libVlcInstance is a pointer to a heap structure (VLCJniObject), 
    // the first member is a jobject (pointer sized), and second is p_libvlc (pointer sized).
    // We will try to read the second member and see if it looks like a valid pointer.
    // Note: This is risky (segfault) if the pointer is invalid, but we are crashing anyway.
    
    if (libVlcInstance != 0) {
        void** ptrs = reinterpret_cast<void**>(libVlcInstance);
        
        __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
            "=== HANDLE PROBE: ptr=%p ===", ptrs);
            
        // We only probe if the pointer is aligned (which it should be)
        if ((libVlcInstance % sizeof(void*)) == 0) {
            // Try to read the potential 'p_libvlc' at offset 1 (second pointer)
            // We assume standard struct padding/alignment for 64-bit.
            // struct { jobject; libvlc_instance_t*; }
            
            // CAUTION: Reading memory we don't own.
            // We rely on the fact that Java gave us a valid pointer to SOMETHING.
            
            void* p0 = ptrs[0]; // jobject thiz?
            void* p1 = ptrs[1]; // libvlc_instance_t* ?
            
            __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
                "=== HANDLE PROBE: [0]=%p, [1]=%p ===", p0, p1);
                
            // Heuristic: p1 should be non-null and look like a heap pointer
            // If p1 is valid, we use it.
            if (p1 != nullptr) {
                 __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
                    "=== DETECTED WRAPPER: Using dereferenced pointer [1] as instance ===");
                 inst = reinterpret_cast<libvlc_instance_t*>(p1);
            } else {
                 __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
                    "=== PROBE FAILED or NULL: Using original pointer ===");
            }
        }
    }
    
    LOGD("VlcJni: nativeCreateMedia() - Calling libvlc_media_new_callbacks()");
    LOGD("VlcJni: nativeCreateMedia() -   - instance: %p", (void*)inst);
    LOGD("VlcJni: nativeCreateMedia() -   - open_cb: %p", (void*)media_open_cb);
    LOGD("VlcJni: nativeCreateMedia() -   - read_cb: %p", (void*)media_read_cb);
    LOGD("VlcJni: nativeCreateMedia() -   - seek_cb: %p", (void*)media_seek_cb);
    LOGD("VlcJni: nativeCreateMedia() -   - close_cb: %p", (void*)media_close_cb);
    LOGD("VlcJni: nativeCreateMedia() -   - opaque (data): %p", (void*)data);
    
    // CRITICAL: Log with ERROR level to ensure visibility
    __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
        "=== CRITICAL: About to call libvlc_media_new_callbacks() ===");
    __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
        "=== Instance ptr used: %p ===", (void*)inst);
    
    libvlc_media_t *media = nullptr;
    
    // IMPORTANT: libvlc_media_new_callbacks() may call media_open_cb() SYNCHRONOUSLY
    __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
        "=== CALLING libvlc_media_new_callbacks() NOW ===");
    
    media = g_libvlc_media_new_callbacks(
        inst,
        media_open_cb,
        media_read_cb,
        media_seek_cb,
        media_close_cb,
        data
    );
    
    // CRITICAL: Log immediately after call - if we don't see this, call crashed
    __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
        "=== libvlc_media_new_callbacks() RETURNED: %p ===", (void*)media);
    
    if (media == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
            "=== libvlc_media_new_callbacks() returned NULL - FAILED ===");
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
            "=== libvlc_media_new_callbacks() returned valid media: %p ===", (void*)media);
    }
    if (!media) {
        __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
            "=== CLEANUP: libvlc_media_new_callbacks failed, cleaning up... ===");
        env->DeleteGlobalRef(data->callbackObject);
        delete data;
        __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
            "=== CLEANUP DONE, returning 0 ===");
        return 0;
    }

    // Add demux hint so PS is used for VOB data
    if (g_libvlc_media_add_option) {
        g_libvlc_media_add_option(media, ":demux=ps");
        g_libvlc_media_add_option(media, ":ps-trust-timestamps");
        __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
            "=== media_add_option applied: :demux=ps, :ps-trust-timestamps ===");
    } else {
        __android_log_print(ANDROID_LOG_WARN, "VlcJni", 
            "=== libvlc_media_add_option not available; cannot force demux ===");
    }

    LOGD("VlcJni: nativeCreateMedia() - Successfully created libvlc_media_t via callbacks");
    LOGD("VlcJni: nativeCreateMedia() - Media handle: %p", (void*)media);
    LOGD("VlcJni: nativeCreateMedia() - Callback data: %p", (void*)data);
    LOGD("VlcJni: nativeCreateMedia() - Converting media pointer to jlong");
    auto result = reinterpret_cast<jlong>(media);
    LOGD("VlcJni: nativeCreateMedia() - Returning media handle: %ld", (long)result);
    return result;
}

extern "C"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ble1st_connectias_feature_dvd_media_VlcDvdPlayer_nativeSetMediaOnPlayer(
        JNIEnv *env, 
        jobject thiz, 
        jlong mediaPlayerHandle, 
        jlong mediaHandle) {
    
    LOGD("VlcJni: nativeSetMediaOnPlayer() called - player: %ld, media: %ld", 
         (long)mediaPlayerHandle, (long)mediaHandle);
    
    if (!g_libvlc_media_player_set_media) {
        LOGE("VlcJni: nativeSetMediaOnPlayer() - libvlc_media_player_set_media not loaded");
        return JNI_FALSE;
    }
    
    if (mediaPlayerHandle == 0 || mediaHandle == 0) {
        LOGE("VlcJni: nativeSetMediaOnPlayer() - Invalid handles (player: %ld, media: %ld)", 
             (long)mediaPlayerHandle, (long)mediaHandle);
        return JNI_FALSE;
    }
    
    // Same wrapper issue as with LibVLC instance - dereference the wrapper
    libvlc_media_player_t *player = nullptr;
    if (mediaPlayerHandle != 0) {
        void** ptrs = reinterpret_cast<void**>(mediaPlayerHandle);
        
        __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
            "=== PLAYER HANDLE PROBE: ptr=%p ===", ptrs);
            
        if ((mediaPlayerHandle % sizeof(void*)) == 0) {
            void* p0 = ptrs[0]; // jobject thiz?
            void* p1 = ptrs[1]; // libvlc_media_player_t* ?
            
            __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
                "=== PLAYER HANDLE PROBE: [0]=%p, [1]=%p ===", p0, p1);
                
            if (p1 != nullptr) {
                __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
                    "=== DETECTED PLAYER WRAPPER: Using dereferenced pointer [1] ===");
                player = reinterpret_cast<libvlc_media_player_t*>(p1);
            } else {
                __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
                    "=== PLAYER PROBE FAILED: Using original pointer ===");
                player = reinterpret_cast<libvlc_media_player_t*>(mediaPlayerHandle);
            }
        } else {
            player = reinterpret_cast<libvlc_media_player_t*>(mediaPlayerHandle);
        }
    }
    
    auto *media = reinterpret_cast<libvlc_media_t*>(mediaHandle);
    
    __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
        "=== Setting media on player: player=%p, media=%p ===", 
        (void*)player, (void*)media);
    
    g_libvlc_media_player_set_media(player, media);
    
    __android_log_print(ANDROID_LOG_ERROR, "VlcJni", 
        "=== Media set on player successfully ===");
    
    LOGD("VlcJni: nativeSetMediaOnPlayer() - Success");
    return JNI_TRUE;
}