#ifndef DVD_WRAPPER_H
#define DVD_WRAPPER_H

#include <jni.h>

// DVD Title structure
struct DvdTitleNative {
    int number;
    int chapterCount;
    long duration; // milliseconds
};

// DVD Chapter structure
struct DvdChapterNative {
    int number;
    long startTime; // milliseconds
    long duration; // milliseconds
};

// JNI function declarations
extern "C" {
    JNIEXPORT jlong JNICALL
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdOpen(JNIEnv *env, jclass clazz, jstring path);
    
    JNIEXPORT void JNICALL
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdClose(JNIEnv *env, jclass clazz, jlong handle);
    
    JNIEXPORT jint JNICALL
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdGetTitleCount(JNIEnv *env, jclass clazz, jlong handle);
    
    JNIEXPORT jobject JNICALL
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdReadTitle(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber);
    
    JNIEXPORT jobject JNICALL
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdReadChapter(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber, jint chapterNumber);
    
    #ifdef ENABLE_DVD_CSS
    JNIEXPORT jbyteArray JNICALL
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdDecryptCss(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber);
    #endif
    
    JNIEXPORT jobject JNICALL
    Java_com_ble1st_connectias_feature_usb_native_DvdNative_dvdExtractVideoStream(JNIEnv *env, jclass clazz, jlong handle, jint titleNumber, jint chapterNumber);
}

#endif // DVD_WRAPPER_H
