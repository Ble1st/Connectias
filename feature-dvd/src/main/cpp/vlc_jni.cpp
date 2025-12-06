#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string>
#include <vector>
#include <cstring>
#include "include/vlc/vlc_stub.h"

#define LOG_TAG "VlcJni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Global JavaVM reference
static JavaVM *g_vm = nullptr;

// Function pointers for LibVLC
static void *g_libvlc_handle = nullptr;
static libvlc_media_new_callbacks_t g_libvlc_media_new_callbacks = nullptr;
static libvlc_media_release_t g_libvlc_media_release = nullptr;

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
    JavaCallbackData *data = static_cast<JavaCallbackData*>(opaque);
    if (!data || !g_vm) return -1;

    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return -1;
        attached = true;
    }

    // Call open()
    bool openSuccess = env->CallBooleanMethod(data->callbackObject, data->openMethod);
    if (!openSuccess) {
        if (attached) g_vm->DetachCurrentThread();
        return -1;
    }

    // Call getSize()
    jlong size = env->CallLongMethod(data->callbackObject, data->getSizeMethod);
    data->size = (uint64_t)size;
    *sizep = data->size;
    *datap = data; // Pass our data struct to other callbacks

    if (attached) g_vm->DetachCurrentThread();
    return 0;
}

static ssize_t media_read_cb(void *opaque, unsigned char *buf, size_t len) {
    JavaCallbackData *data = static_cast<JavaCallbackData*>(opaque);
    if (!data || !g_vm) return -1;

    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return -1;
        attached = true;
    }

    // Create a ByteArray to receive data
    // Note: Creating a new array every read is inefficient. 
    // Optimization: Pass a direct ByteBuffer or reuse a buffer if possible.
    // For now, we stick to the simple JNI contract.
    jbyteArray javaBuf = env->NewByteArray(len);
    
    jint bytesRead = env->CallIntMethod(data->callbackObject, data->readMethod, javaBuf, (jint)len);

    if (bytesRead > 0) {
        env->GetByteArrayRegion(javaBuf, 0, bytesRead, (jbyte*)buf);
    }

    env->DeleteLocalRef(javaBuf);
    if (attached) g_vm->DetachCurrentThread();
    
    return (bytesRead < 0) ? -1 : bytesRead;
}

static int media_seek_cb(void *opaque, uint64_t offset) {
    JavaCallbackData *data = static_cast<JavaCallbackData*>(opaque);
    if (!data || !g_vm) return -1;

    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return -1;
        attached = true;
    }

    jboolean result = env->CallBooleanMethod(data->callbackObject, data->seekMethod, (jlong)offset);

    if (attached) g_vm->DetachCurrentThread();
    return (result == JNI_TRUE) ? 0 : -1;
}

static void media_close_cb(void *opaque) {
    JavaCallbackData *data = static_cast<JavaCallbackData*>(opaque);
    if (!data || !g_vm) return;

    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }

    env->CallVoidMethod(data->callbackObject, data->closeMethod);
    env->DeleteGlobalRef(data->callbackObject);
    
    delete data;

    if (attached) g_vm->DetachCurrentThread();
}

// --- JNI Implementations ---


/**
 * Initialize LibVLC function pointers by loading libvlc.so dynamically.
 * This avoids link-time dependencies.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ble1st_connectias_feature_dvd_media_VlcDvdPlayer_nativeInit(JNIEnv *env, jobject clazz) {
    if (g_libvlc_handle) return JNI_TRUE;

    // Try to load libvlc.so
    // On Android, it should already be loaded by Java, so we can just get the handle
    g_libvlc_handle = dlopen("libvlc.so", RTLD_LAZY);
    if (!g_libvlc_handle) {
        LOGE("Failed to load libvlc.so: %s", dlerror());
        return JNI_FALSE;
    }

    g_libvlc_media_new_callbacks = (libvlc_media_new_callbacks_t)dlsym(g_libvlc_handle, "libvlc_media_new_callbacks");
    g_libvlc_media_release = (libvlc_media_release_t)dlsym(g_libvlc_handle, "libvlc_media_release");

    if (!g_libvlc_media_new_callbacks || !g_libvlc_media_release) {
        LOGE("Failed to load LibVLC symbols");
        dlclose(g_libvlc_handle);
        g_libvlc_handle = nullptr;
        return JNI_FALSE;
    }

    LOGD("Successfully loaded LibVLC symbols");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ble1st_connectias_feature_dvd_media_VlcDvdPlayer_nativeCreateMedia(
        JNIEnv *env, 
        jobject thiz, 
        jlong libVlcInstance) {

    if (!g_libvlc_media_new_callbacks) {
        LOGE("LibVLC not initialized");
        return 0;
    }

    // 1. Prepare Callback Data
    JavaCallbackData *data = new JavaCallbackData();
    data->callbackObject = env->NewGlobalRef(thiz);
    
    jclass clazz = env->GetObjectClass(thiz);
    data->readMethod = env->GetMethodID(clazz, "ioRead", "([BI)I");
    data->seekMethod = env->GetMethodID(clazz, "ioSeek", "(J)Z");
    data->closeMethod = env->GetMethodID(clazz, "ioClose", "()V");
    data->getSizeMethod = env->GetMethodID(clazz, "ioGetSize", "()J");
    data->openMethod = env->GetMethodID(clazz, "ioOpen", "()Z");

    if (!data->readMethod || !data->seekMethod || !data->closeMethod || !data->getSizeMethod || !data->openMethod) {
        LOGE("Failed to find callback methods in Java class");
        env->DeleteGlobalRef(data->callbackObject);
        delete data;
        return 0;
    }

    // 2. Create Media with Callbacks
    // Note: 'libVlcInstance' passed from Java is the pointer to libvlc_instance_t
    libvlc_instance_t *inst = reinterpret_cast<libvlc_instance_t*>(libVlcInstance);
    
    libvlc_media_t *media = g_libvlc_media_new_callbacks(
        inst,
        media_open_cb,
        media_read_cb,
        media_seek_cb,
        media_close_cb,
        data
    );

    if (!media) {
        LOGE("libvlc_media_new_callbacks failed");
        env->DeleteGlobalRef(data->callbackObject);
        delete data;
        return 0;
    }

    return reinterpret_cast<jlong>(media);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ble1st_connectias_feature_dvd_media_VlcDvdPlayer_nativeReleaseMedia(JNIEnv *env, jobject clazz, jlong mediaHandle) {
    if (mediaHandle != 0 && g_libvlc_media_release) {
        g_libvlc_media_release(reinterpret_cast<libvlc_media_t*>(mediaHandle));
    }
}