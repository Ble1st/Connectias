#include "usb_wrapper.h"
#include "libusb_wrapper.h"
#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cstring>
#include <inttypes.h>
#include <map>
#include <mutex>

#define LOG_TAG "UsbNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global libusb context (initialized once)
static libusb_context *g_libusb_context = nullptr;
static std::mutex g_context_mutex;

// Device handle map: Java handle -> libusb_device_handle*
static std::map<jlong, libusb_device_handle*> g_device_handles;
static std::mutex g_handles_mutex;
static jlong g_next_handle = 1;

// Initialize libusb context (thread-safe)
static int ensure_libusb_context() {
#ifdef LIBUSB_NOT_AVAILABLE
    LOGE("libusb is not available - USB functionality disabled");
    return LIBUSB_ERROR_NOT_SUPPORTED;
#endif
    
    std::lock_guard<std::mutex> lock(g_context_mutex);
    if (g_libusb_context == nullptr) {
        int result = libusb_init(&g_libusb_context);
        if (result < 0) {
            LOGE("Failed to initialize libusb: %d", result);
            return result;
        }
        LOGD("libusb context initialized successfully");
    }
    return LIBUSB_SUCCESS;
}

// Helper: Convert libusb error code to Java error code
static jint libusb_error_to_java(int libusb_error) {
    return libusb_error; // libusb error codes match our Java error codes
}

// Helper: Get string descriptor
static std::string get_string_descriptor(libusb_device_handle *handle, uint8_t index) {
#ifdef LIBUSB_NOT_AVAILABLE
    return "";
#else
    if (index == 0) return "";
    
    unsigned char buffer[256];
    int result = libusb_get_string_descriptor_ascii(handle, index, buffer, sizeof(buffer));
    if (result < 0) {
        LOGD("Failed to get string descriptor %d: %d", index, result);
        return "";
    }
    return std::string(reinterpret_cast<char*>(buffer), result);
#endif
}

extern "C" {

JNIEXPORT jobjectArray JNICALL
Java_com_ble1st_connectias_feature_usb_native_UsbNative_enumerateDevicesNative(JNIEnv *env, jobject clazz) {
    LOGD("Enumerating USB devices...");
    
    // Initialize libusb context
    int init_result = ensure_libusb_context();
    if (init_result < 0) {
        LOGE("Failed to initialize libusb context: %d", init_result);
        // Return empty array on failure
        jclass deviceClass = env->FindClass("com/ble1st/connectias/feature/usb/native/UsbDeviceNative");
        if (deviceClass != nullptr) {
            return env->NewObjectArray(0, deviceClass, nullptr);
        }
        return nullptr;
    }
    
    // Find the device class and constructor
    jclass deviceClass = env->FindClass("com/ble1st/connectias/feature/usb/native/UsbDeviceNative");
    if (deviceClass == nullptr) {
        LOGE("Failed to find UsbDeviceNative class");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return nullptr;
    }
    
    // Get constructor: UsbDeviceNative(int vendorId, int productId, UsbClass deviceClass, String serialNumber, String manufacturer, String product)
    jmethodID constructor = env->GetMethodID(deviceClass, "<init>", "(IILcom/ble1st/connectias/feature/usb/native/UsbClass;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    if (constructor == nullptr) {
        LOGE("Failed to find UsbDeviceNative constructor");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return nullptr;
    }
    
    // Find UsbClass enum
    jclass usbClassEnum = env->FindClass("com/ble1st/connectias/feature/usb/native/UsbClass");
    if (usbClassEnum == nullptr) {
        LOGE("Failed to find UsbClass enum");
        return nullptr;
    }
    
    // Get UsbClass.values() method
    jmethodID valuesMethod = env->GetStaticMethodID(usbClassEnum, "values", "()[Lcom/ble1st/connectias/feature/usb/native/UsbClass;");
    jmethodID fromValueMethod = env->GetStaticMethodID(usbClassEnum, "fromValue", "(I)Lcom/ble1st/connectias/feature/usb/native/UsbClass;");
    if (fromValueMethod == nullptr) {
        LOGE("Failed to find UsbClass.fromValue method");
        return nullptr;
    }
    
#ifdef LIBUSB_NOT_AVAILABLE
    // Return empty array if libusb is not available
    LOGD("libusb not available - returning empty device list");
    return env->NewObjectArray(0, deviceClass, nullptr);
#else
    // Enumerate devices using libusb
    libusb_device **device_list = nullptr;
    ssize_t device_count = libusb_get_device_list(g_libusb_context, &device_list);
    
    if (device_count < 0) {
        LOGE("Failed to get device list: %zd", device_count);
        libusb_free_device_list(device_list, 1);
        return env->NewObjectArray(0, deviceClass, nullptr);
    }
    
    LOGD("Found %zd USB devices", device_count);
    
    // Create array to hold device objects
    jobjectArray result = env->NewObjectArray(device_count, deviceClass, nullptr);
    if (result == nullptr) {
        LOGE("Failed to create device array");
        libusb_free_device_list(device_list, 1);
        return nullptr;
    }
    
    // Process each device
    std::vector<jobject> device_objects;
    for (ssize_t i = 0; i < device_count; i++) {
        libusb_device *device = device_list[i];
        libusb_device_descriptor desc;
        
        int result_code = libusb_get_device_descriptor(device, &desc);
        if (result_code < 0) {
            LOGD("Failed to get device descriptor for device %zd: %d", i, result_code);
            continue;
        }
        
        // Try to open device to get string descriptors
        libusb_device_handle *handle = nullptr;
        int open_result = libusb_open(device, &handle);
        
        std::string manufacturer = "";
        std::string product = "";
        std::string serial = "";
        
        if (open_result == 0 && handle != nullptr) {
            manufacturer = get_string_descriptor(handle, desc.iManufacturer);
            product = get_string_descriptor(handle, desc.iProduct);
            serial = get_string_descriptor(handle, desc.iSerialNumber);
            libusb_close(handle);
        }
        
        // Get UsbClass enum value
        jobject usbClassObj = env->CallStaticObjectMethod(usbClassEnum, fromValueMethod, static_cast<jint>(desc.bDeviceClass));
        if (usbClassObj == nullptr) {
            // Fallback to UNKNOWN
            jobjectArray values = static_cast<jobjectArray>(env->CallStaticObjectMethod(usbClassEnum, valuesMethod));
            if (values != nullptr && env->GetArrayLength(values) > 0) {
                // Find UNKNOWN (last enum value)
                jsize len = env->GetArrayLength(values);
                usbClassObj = env->GetObjectArrayElement(values, len - 1);
            }
        }
        
        // Create Java strings
        jstring jManufacturer = env->NewStringUTF(manufacturer.c_str());
        jstring jProduct = env->NewStringUTF(product.c_str());
        jstring jSerial = env->NewStringUTF(serial.c_str());
        
        // Create UsbDeviceNative object
        jobject deviceObj = env->NewObject(deviceClass, constructor,
            static_cast<jint>(desc.idVendor),
            static_cast<jint>(desc.idProduct),
            usbClassObj,
            jSerial,
            jManufacturer,
            jProduct
        );
        
        if (deviceObj != nullptr) {
            device_objects.push_back(deviceObj);
            env->SetObjectArrayElement(result, device_objects.size() - 1, deviceObj);
        }
        
        // Clean up local references
        env->DeleteLocalRef(jManufacturer);
        env->DeleteLocalRef(jProduct);
        env->DeleteLocalRef(jSerial);
        if (usbClassObj != nullptr) {
            env->DeleteLocalRef(usbClassObj);
        }
    }
    
    libusb_free_device_list(device_list, 1);
    
    LOGD("USB device enumeration complete: %zu devices", device_objects.size());
    return result;
#endif
}

JNIEXPORT jlong JNICALL
Java_com_ble1st_connectias_feature_usb_native_UsbNative_openDevice(JNIEnv *env, jobject clazz, jint vendorId, jint productId) {
    LOGD("Opening USB device: Vendor=0x%04X, Product=0x%04X", vendorId, productId);
    
#ifdef LIBUSB_NOT_AVAILABLE
    LOGE("libusb not available - cannot open device");
    return libusb_error_to_java(LIBUSB_ERROR_NOT_SUPPORTED);
#else
    // Initialize libusb context
    int init_result = ensure_libusb_context();
    if (init_result < 0) {
        LOGE("Failed to initialize libusb context: %d", init_result);
        return -1;
    }
    
    // Enumerate devices to find matching vendor/product ID
    libusb_device **device_list = nullptr;
    ssize_t device_count = libusb_get_device_list(g_libusb_context, &device_list);
    
    if (device_count < 0) {
        LOGE("Failed to get device list: %zd", device_count);
        return libusb_error_to_java(device_count);
    }
    
    libusb_device_handle *handle = nullptr;
    
    // Search for matching device
    for (ssize_t i = 0; i < device_count; i++) {
        libusb_device *device = device_list[i];
        libusb_device_descriptor desc;
        
        int result = libusb_get_device_descriptor(device, &desc);
        if (result < 0) {
            continue;
        }
        
        if (desc.idVendor == static_cast<uint16_t>(vendorId) && 
            desc.idProduct == static_cast<uint16_t>(productId)) {
            // Found matching device, try to open it
            int open_result = libusb_open(device, &handle);
            if (open_result == 0) {
                LOGD("Successfully opened USB device");
                break;
            } else {
                LOGE("Failed to open device: %d", open_result);
            }
        }
    }
    
    libusb_free_device_list(device_list, 1);
    
    if (handle == nullptr) {
        LOGE("Device not found or could not be opened");
        return libusb_error_to_java(LIBUSB_ERROR_NOT_FOUND);
    }
    
    // Store handle in map and return Java handle
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    jlong java_handle = g_next_handle++;
    g_device_handles[java_handle] = handle;
    
    LOGD("USB device opened, handle: %" PRId64, static_cast<int64_t>(java_handle));
    return java_handle;
#endif
}

JNIEXPORT jint JNICALL
Java_com_ble1st_connectias_feature_usb_native_UsbNative_closeDevice(JNIEnv *env, jobject clazz, jlong handle) {
    LOGD("Closing USB device, handle: %" PRId64, static_cast<int64_t>(handle));
    
    if (handle < 0) {
        return 0; // Invalid handle, no-op
    }
    
#ifdef LIBUSB_NOT_AVAILABLE
    return 0; // No-op if libusb not available
#else
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    auto it = g_device_handles.find(handle);
    if (it == g_device_handles.end()) {
        LOGD("Handle not found, already closed or invalid");
        return 0; // Already closed or invalid
    }
    
    libusb_device_handle *dev_handle = it->second;
    libusb_close(dev_handle);
    g_device_handles.erase(it);
    
    LOGD("USB device closed successfully");
    return 0; // Success
#endif
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
        return libusb_error_to_java(LIBUSB_ERROR_INVALID_PARAM);
    }
    
    // Validate length parameter
    jsize arrayLength = env->GetArrayLength(data);
    if (length < 0 || length > arrayLength) {
        LOGE("bulkTransfer: invalid length parameter (%d), array length is %d", length, arrayLength);
        jclass iaeClass = env->FindClass("java/lang/IllegalArgumentException");
        if (iaeClass != nullptr) {
            env->ThrowNew(iaeClass, "length must be between 0 and data array length");
        }
        return libusb_error_to_java(LIBUSB_ERROR_INVALID_PARAM);
    }
    
#ifdef LIBUSB_NOT_AVAILABLE
    LOGE("libusb not available - bulk transfer not supported");
    return libusb_error_to_java(LIBUSB_ERROR_NOT_SUPPORTED);
#else
    // Get device handle from map
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    auto it = g_device_handles.find(handle);
    if (it == g_device_handles.end()) {
        LOGE("bulkTransfer: invalid handle");
        return libusb_error_to_java(LIBUSB_ERROR_NO_DEVICE);
    }
    
    libusb_device_handle *dev_handle = it->second;
    
    // Get byte array elements
    jbyte *buffer = env->GetByteArrayElements(data, nullptr);
    if (buffer == nullptr) {
        LOGE("bulkTransfer: failed to get byte array elements");
        return libusb_error_to_java(LIBUSB_ERROR_NO_MEM);
    }
    
    // Perform bulk transfer
    int actual_length = 0;
    int result = libusb_bulk_transfer(
        dev_handle,
        static_cast<unsigned char>(endpoint),
        reinterpret_cast<unsigned char*>(buffer),
        length,
        &actual_length,
        static_cast<unsigned int>(timeout)
    );
    
    // Release byte array elements
    env->ReleaseByteArrayElements(data, buffer, (result == 0) ? 0 : JNI_ABORT);
    
    if (result < 0) {
        LOGE("Bulk transfer failed: %d", result);
        return libusb_error_to_java(result);
    }
    
    LOGD("Bulk transfer complete: %d bytes transferred", actual_length);
    return actual_length;
#endif
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
        return libusb_error_to_java(LIBUSB_ERROR_INVALID_PARAM);
    }
    
    // Validate length parameter
    jsize arrayLength = env->GetArrayLength(data);
    if (length < 0 || length > arrayLength) {
        LOGE("interruptTransfer: invalid length parameter (%d), array length is %d", length, arrayLength);
        jclass iaeClass = env->FindClass("java/lang/IllegalArgumentException");
        if (iaeClass != nullptr) {
            env->ThrowNew(iaeClass, "length must be between 0 and data array length");
        }
        return libusb_error_to_java(LIBUSB_ERROR_INVALID_PARAM);
    }
    
#ifdef LIBUSB_NOT_AVAILABLE
    LOGE("libusb not available - interrupt transfer not supported");
    return libusb_error_to_java(LIBUSB_ERROR_NOT_SUPPORTED);
#else
    // Get device handle from map
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    auto it = g_device_handles.find(handle);
    if (it == g_device_handles.end()) {
        LOGE("interruptTransfer: invalid handle");
        return libusb_error_to_java(LIBUSB_ERROR_NO_DEVICE);
    }
    
    libusb_device_handle *dev_handle = it->second;
    
    // Get byte array elements
    jbyte *buffer = env->GetByteArrayElements(data, nullptr);
    if (buffer == nullptr) {
        LOGE("interruptTransfer: failed to get byte array elements");
        return libusb_error_to_java(LIBUSB_ERROR_NO_MEM);
    }
    
    // Perform interrupt transfer
    int actual_length = 0;
    int result = libusb_interrupt_transfer(
        dev_handle,
        static_cast<unsigned char>(endpoint),
        reinterpret_cast<unsigned char*>(buffer),
        length,
        &actual_length,
        static_cast<unsigned int>(timeout)
    );
    
    // Release byte array elements
    env->ReleaseByteArrayElements(data, buffer, (result == 0) ? 0 : JNI_ABORT);
    
    if (result < 0) {
        LOGE("Interrupt transfer failed: %d", result);
        return libusb_error_to_java(result);
    }
    
    LOGD("Interrupt transfer complete: %d bytes transferred", actual_length);
    return actual_length;
#endif
}

JNIEXPORT jint JNICALL
Java_com_ble1st_connectias_feature_usb_native_UsbNative_controlTransfer(JNIEnv *env, jobject clazz, jlong handle, jint requestType, jint request, jint value, jint index, jbyteArray data, jint length, jint timeout) {
    LOGD("Control transfer: handle=%" PRId64 ", requestType=0x%02X, request=0x%02X, value=0x%04X, index=0x%04X, length=%d, timeout=%d",
         static_cast<int64_t>(handle), requestType, request, value, index, length, timeout);
    
#ifdef LIBUSB_NOT_AVAILABLE
    LOGE("libusb not available - control transfer not supported");
    return libusb_error_to_java(LIBUSB_ERROR_NOT_SUPPORTED);
#else
    // Get device handle from map
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    auto it = g_device_handles.find(handle);
    if (it == g_device_handles.end()) {
        LOGE("controlTransfer: invalid handle");
        return libusb_error_to_java(LIBUSB_ERROR_NO_DEVICE);
    }
    
    libusb_device_handle *dev_handle = it->second;
    
    // Prepare data buffer
    unsigned char *buffer = nullptr;
    jbyte *jni_buffer = nullptr;
    
    if (data != nullptr && length > 0) {
        jsize arrayLength = env->GetArrayLength(data);
        if (length > arrayLength) {
            length = arrayLength;
        }
        
        jni_buffer = env->GetByteArrayElements(data, nullptr);
        if (jni_buffer == nullptr) {
            LOGE("controlTransfer: failed to get byte array elements");
            return libusb_error_to_java(LIBUSB_ERROR_NO_MEM);
        }
        buffer = reinterpret_cast<unsigned char*>(jni_buffer);
    }
    
    // Perform control transfer
    int result = libusb_control_transfer(
        dev_handle,
        static_cast<uint8_t>(requestType),
        static_cast<uint8_t>(request),
        static_cast<uint16_t>(value),
        static_cast<uint16_t>(index),
        buffer,
        static_cast<uint16_t>(length),
        static_cast<unsigned int>(timeout)
    );
    
    // Release byte array elements
    if (jni_buffer != nullptr) {
        env->ReleaseByteArrayElements(data, jni_buffer, (result >= 0) ? 0 : JNI_ABORT);
    }
    
    if (result < 0) {
        LOGE("Control transfer failed: %d", result);
        return libusb_error_to_java(result);
    }
    
    LOGD("Control transfer complete: %d bytes transferred", result);
    return result;
#endif
}

// Cleanup function (called when library is unloaded)
JNIEXPORT void JNICALL
Java_com_ble1st_connectias_feature_usb_native_UsbNative_cleanup(JNIEnv *env, jclass clazz) {
    LOGD("Cleaning up USB native resources");
    
#ifndef LIBUSB_NOT_AVAILABLE
    // Close all open device handles
    std::lock_guard<std::mutex> lock(g_handles_mutex);
    for (auto &pair : g_device_handles) {
        libusb_close(pair.second);
    }
    g_device_handles.clear();
    
    // Exit libusb context
    std::lock_guard<std::mutex> ctx_lock(g_context_mutex);
    if (g_libusb_context != nullptr) {
        libusb_exit(g_libusb_context);
        g_libusb_context = nullptr;
    }
#endif
    
    LOGD("USB native cleanup complete");
}

} // extern "C"
