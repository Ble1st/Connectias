#include "usb_wrapper.h"
#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cstring>
#include <inttypes.h>

#define LOG_TAG "UsbNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)



extern "C" {

JNIEXPORT jobjectArray JNICALL
Java_com_ble1st_connectias_feature_usb_native_UsbNative_enumerateDevicesNative(JNIEnv *env, jobject clazz) {
    LOGD("Enumerating USB devices...");
    
    // Find the device class
    jclass deviceClass = env->FindClass("com/ble1st/connectias/feature/usb/native/UsbDeviceNative");
    if (deviceClass == nullptr) {
        LOGE("Failed to find UsbDeviceNative class");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return nullptr;
    }
    
    // Check for exceptions after FindClass
    if (env->ExceptionCheck()) {
        LOGE("Exception occurred during FindClass");
        env->ExceptionDescribe();
        env->ExceptionClear();
        return nullptr;
    }
    
    // TODO: Implement actual libusb enumeration
    // For now, return empty array
    jobjectArray result = env->NewObjectArray(0, deviceClass, nullptr);
    if (result == nullptr) {
        LOGE("Failed to create empty object array");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return nullptr;
    }
    
    LOGD("USB device enumeration complete: %d devices", 0);
    return result;
}

JNIEXPORT jlong JNICALL
Java_com_ble1st_connectias_feature_usb_native_UsbNative_openDevice(JNIEnv *env, jobject clazz, jint vendorId, jint productId) {
    LOGD("Opening USB device: Vendor=0x%04X, Product=0x%04X", vendorId, productId);
    
    // TODO: Implement actual libusb device opening
    // For now, return placeholder handle
    jlong handle = 1;
    LOGD("USB device opened, handle: %" PRId64, static_cast<int64_t>(handle));
    return handle;
}

JNIEXPORT jint JNICALL
Java_com_ble1st_connectias_feature_usb_native_UsbNative_closeDevice(JNIEnv *env, jobject clazz, jlong handle) {
    LOGD("Closing USB device, handle: %" PRId64, static_cast<int64_t>(handle));
    
    // TODO: Implement actual libusb device closing
    // For now, return success (0)
    // On error, return negative error code (e.g., -1 for general error, -4 for no device, etc.)
    LOGD("USB device closed");
    return 0; // Success
}

JNIEXPORT jint JNICALL
Java_com_ble1st_connectias_feature_usb_native_UsbNative_bulkTransfer(JNIEnv *env, jobject clazz, jlong handle, jint endpoint, jbyteArray data, jint length, jint timeout) {
    LOGD("Bulk transfer: handle=%" PRId64 ", endpoint=0x%02X, length=%d, timeout=%d",
         static_cast<int64_t>(handle), endpoint, length, timeout);
    
    // Null check for data parameter
    if (data == nullptr) {
        LOGE("bulkTransfer: data parameter is null");
        jclass npeClass = env->FindClass("java/lang/NullPointerException");
        if (npeClass != nullptr) {
            env->ThrowNew(npeClass, "data must not be null");
        }
        return -2; // ERROR_INVALID_PARAM
    }
    
    // Validate length parameter
    jsize arrayLength = env->GetArrayLength(data);
    if (length < 0 || length > arrayLength) {
        LOGE("bulkTransfer: invalid length parameter (%d), array length is %d", length, arrayLength);
        jclass iaeClass = env->FindClass("java/lang/IllegalArgumentException");
        if (iaeClass != nullptr) {
            env->ThrowNew(iaeClass, "length must be between 0 and data array length");
        }
        return -2; // ERROR_INVALID_PARAM
    }
    
    // TODO: Implement actual libusb bulk transfer
    // For now, return the requested length as if transfer succeeded
    LOGD("Bulk transfer complete: %d bytes", length);
    return length;
}

JNIEXPORT jint JNICALL
Java_com_ble1st_connectias_feature_usb_native_UsbNative_interruptTransfer(JNIEnv *env, jobject clazz, jlong handle, jint endpoint, jbyteArray data, jint length, jint timeout) {
    LOGD("Interrupt transfer: handle=%" PRId64 ", endpoint=0x%02X, length=%d, timeout=%d",
         static_cast<int64_t>(handle), endpoint, length, timeout);
    
    // Null check for data parameter
    if (data == nullptr) {
        LOGE("interruptTransfer: data parameter is null");
        jclass npeClass = env->FindClass("java/lang/NullPointerException");
        if (npeClass != nullptr) {
            env->ThrowNew(npeClass, "data must not be null");
        }
        return -2; // ERROR_INVALID_PARAM
    }
    
    // Validate length parameter
    jsize arrayLength = env->GetArrayLength(data);
    if (length < 0 || length > arrayLength) {
        LOGE("interruptTransfer: invalid length parameter (%d), array length is %d", length, arrayLength);
        jclass iaeClass = env->FindClass("java/lang/IllegalArgumentException");
        if (iaeClass != nullptr) {
            env->ThrowNew(iaeClass, "length must be between 0 and data array length");
        }
        return -2; // ERROR_INVALID_PARAM
    }
    
    // TODO: Implement actual libusb interrupt transfer
    // For now, return the requested length as if transfer succeeded
    LOGD("Interrupt transfer complete: %d bytes", length);
    return length;
}

JNIEXPORT jint JNICALL
Java_com_ble1st_connectias_feature_usb_native_UsbNative_controlTransfer(JNIEnv *env, jobject clazz, jlong handle, jint requestType, jint request, jint value, jint index, jbyteArray data, jint length, jint timeout) {
    LOGD("Control transfer: handle=%" PRId64 ", requestType=0x%02X, request=0x%02X, length=%d, timeout=%d",
         static_cast<int64_t>(handle), requestType, request, length, timeout);
    
    // TODO: Implement actual libusb control transfer
    if (data != nullptr) {
        jsize arrayLength = env->GetArrayLength(data);
        if (length > arrayLength) {
            length = arrayLength;
        }
    }
    LOGD("Control transfer complete: %d bytes", length);
    return length;
}

} // extern "C"
