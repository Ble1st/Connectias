#ifndef USB_WRAPPER_H
#define USB_WRAPPER_H

#include <jni.h>
#include <string>

// USB Device structure
struct UsbDeviceNative {
    int vendorId;
    int productId;
    int deviceClass;
    std::string serialNumber;
    std::string manufacturer;
    std::string product;
};

// JNI function declarations
extern "C" {
    JNIEXPORT jobjectArray JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_enumerateDevices(JNIEnv *env, jclass clazz);
    
    JNIEXPORT jlong JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_openDevice(JNIEnv *env, jclass clazz, jint vendorId, jint productId);
    
    // Returns 0 on success, negative error code on failure (errno-style)
    JNIEXPORT jint JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_closeDevice(JNIEnv *env, jclass clazz, jlong handle);
    
    // endpoint: USB endpoint address (bit 7: 0=OUT, 1=IN)
    // data: Buffer for OUT transfers (written) or IN transfers (read into)
    // length: Number of bytes to transfer (for OUT) or buffer size (for IN)
    // timeout: Timeout in milliseconds
    // Returns number of bytes transferred, or negative error code on failure
    JNIEXPORT jint JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_bulkTransfer(JNIEnv *env, jclass clazz, jlong handle, jint endpoint, jbyteArray data, jint length, jint timeout);
    
    // endpoint: USB endpoint address (bit 7: 0=OUT, 1=IN)
    // data: Buffer for OUT transfers (written) or IN transfers (read into)
    // length: Number of bytes to transfer (for OUT) or buffer size (for IN)
    // timeout: Timeout in milliseconds
    // Returns number of bytes transferred, or negative error code on failure
    JNIEXPORT jint JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_interruptTransfer(JNIEnv *env, jclass clazz, jlong handle, jint endpoint, jbyteArray data, jint length, jint timeout);
    
    JNIEXPORT jint JNICALL
    Java_com_ble1st_connectias_feature_usb_native_UsbNative_controlTransfer(JNIEnv *env, jclass clazz, jlong handle, jint requestType, jint request, jint value, jint index, jbyteArray data, jint length, jint timeout);
}

#endif // USB_WRAPPER_H
