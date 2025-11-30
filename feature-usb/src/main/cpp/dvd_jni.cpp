#include "dvd_wrapper.h"
#include <jni.h>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "DvdNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdOpen(JNIEnv *env, jclass clazz, jstring path) {
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    LOGD("Opening DVD at path: %s", pathStr);
    
    // TODO: Implement actual libdvdread/libdvdnav DVD opening
    // For now, return placeholder handle
    jlong handle = 1;
    LOGD("DVD opened successfully, handle: %ld", handle);
    
    env->ReleaseStringUTFChars(path, pathStr);
    return handle;
}

JNIEXPORT void JNICALL
Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdClose(JNIEnv *env, jclass clazz, jlong handle) {
    if (handle <= 0) {
        LOGE("Invalid DVD handle: %ld", handle);
        return;
    }
    
    LOGD("Closing DVD, handle: %ld", handle);
    
    // TODO: Implement actual libdvdread/libdvdnav DVD closing
    LOGD("DVD closed");
}

JNIEXPORT jint JNICALL
Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdGetTitleCount(JNIEnv *env, jclass clazz, jlong handle) {
    if (handle <= 0) {
        LOGE("Invalid DVD handle: %ld", handle);
        jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exceptionClass != nullptr) {
            env->ThrowNew(exceptionClass, "Invalid DVD handle");
        }
        return -1;
    }
    
    LOGD("Getting title count for DVD, handle: %ld", handle);
    
    // TODO: Implement actual libdvdread/libdvdnav title count
    // For now, return placeholder
    jint count = 0;
    LOGD("DVD contains %d titles", count);
    return count;
}

JNIEXPORT jobject JNICALL
Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdReadTitle(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber) {
    if (handle <= 0) {
        LOGE("Invalid DVD handle: %ld", handle);
        jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exceptionClass != nullptr) {
            env->ThrowNew(exceptionClass, "Invalid DVD handle");
        }
        return nullptr;
    }
    
    if (titleNumber < 1) {
        LOGE("Invalid title number: %d", titleNumber);
        jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exceptionClass != nullptr) {
            env->ThrowNew(exceptionClass, "Title number must be >= 1");
        }
        return nullptr;
    }
    
    LOGD("Reading title %d from DVD, handle: %ld", titleNumber, handle);
    
    // TODO: Implement actual libdvdread/libdvdnav title reading
    // For now, throw UnsupportedOperationException
    LOGD("Title %d read not implemented", titleNumber);
    jclass exceptionClass = env->FindClass("java/lang/UnsupportedOperationException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, "DVD title reading not yet implemented");
    }
    return nullptr;
}

JNIEXPORT jobject JNICALL
Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdReadChapter(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber, jint chapterNumber) {
    if (handle <= 0) {
        LOGE("Invalid DVD handle: %ld", handle);
        jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exceptionClass != nullptr) {
            env->ThrowNew(exceptionClass, "Invalid DVD handle");
        }
        return nullptr;
    }
    
    if (titleNumber < 1 || chapterNumber < 1) {
        LOGE("Invalid title or chapter number: title=%d, chapter=%d", titleNumber, chapterNumber);
        jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exceptionClass != nullptr) {
            env->ThrowNew(exceptionClass, "Title and chapter numbers must be >= 1");
        }
        return nullptr;
    }
    
    LOGD("Reading chapter %d from title %d, handle: %ld", chapterNumber, titleNumber, handle);
    
    // TODO: Implement actual libdvdread/libdvdnav chapter reading
    // For now, throw UnsupportedOperationException
    LOGD("Chapter %d from title %d read not implemented", chapterNumber, titleNumber);
    jclass exceptionClass = env->FindClass("java/lang/UnsupportedOperationException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, "DVD chapter reading not yet implemented");
    }
    return nullptr;
}

#ifdef ENABLE_DVD_CSS
JNIEXPORT jbyteArray JNICALL
Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdDecryptCss(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber) {
    LOGD("Decrypting CSS for title %d, handle: %ld", titleNumber, handle);
    
    // TODO: Implement actual libdvdcss CSS decryption
    // For now, return null
    LOGD("CSS decryption for title %d complete", titleNumber);
    return nullptr;
}
#endif

JNIEXPORT jobject JNICALL
Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdExtractVideoStream(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber, jint chapterNumber) {
    if (handle <= 0) {
        LOGE("Invalid DVD handle: %ld", handle);
        jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exceptionClass != nullptr) {
            env->ThrowNew(exceptionClass, "Invalid DVD handle");
        }
        return nullptr;
    }
    
    if (titleNumber < 1 || chapterNumber < 1) {
        LOGE("Invalid title or chapter number: title=%d, chapter=%d", titleNumber, chapterNumber);
        jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exceptionClass != nullptr) {
            env->ThrowNew(exceptionClass, "Title and chapter numbers must be >= 1");
        }
        return nullptr;
    }
    
    LOGD("Video stream extraction not yet implemented for title %d, chapter %d", titleNumber, chapterNumber);
    
    // TODO: Implement actual FFmpeg video stream extraction
    // For now, throw UnsupportedOperationException
    jclass exceptionClass = env->FindClass("java/lang/UnsupportedOperationException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, "Video stream extraction not yet implemented");
    }
    return nullptr;
}

} // extern "C"
