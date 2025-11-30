#ifndef USB_WRAPPER_H
#define USB_WRAPPER_H

#include <jni.h>
#include <vector>

// USB Device structure
struct UsbDeviceNative {
    int vendorId;
    int productId;
    int deviceClass;
    char* serialNumber;
    char* manufacturer;
    char* product;
};

// JNI function declarations
extern "C" {
    JNIEXPORT jobjectArray JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_enumerateDevices(JNIEnv *env, jclass clazz);
    
    JNIEXPORT jlong JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_openDevice(JNIEnv *env, jclass clazz, jint vendorId, jint productId);
    
    JNIEXPORT void JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_closeDevice(JNIEnv *env, jclass clazz, jlong handle);
    
    JNIEXPORT jint JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_bulkTransfer(JNIEnv *env, jclass clazz, jlong handle, jint endpoint, jbyteArray data);
    
    JNIEXPORT jint JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_interruptTransfer(JNIEnv *env, jclass clazz, jlong handle, jint endpoint, jbyteArray data);
    
    JNIEXPORT jint JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_controlTransfer(JNIEnv *env, jclass clazz, jlong handle, jint requestType, jint request, jint value, jint index, jbyteArray data);
}

#endif // USB_WRAPPER_H
