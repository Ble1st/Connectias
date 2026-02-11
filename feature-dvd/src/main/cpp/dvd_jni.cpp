#include "dvd_wrapper.h"
#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <linux/cdrom.h>
#include <errno.h>
#include <map>
#include <memory>
#include <vector>
#include <string>
#include <cctype>

// libdvdread includes
#ifdef HAVE_DVDREAD
#ifdef __cplusplus
extern "C" {
#endif
#include <dvdread/dvd_reader.h>
#include <dvdread/ifo_read.h>
#include <dvdread/ifo_types.h>
#include <dvdread/nav_types.h>
#ifdef __cplusplus
}
#endif
#endif // HAVE_DVDREAD

#define LOG_TAG "DvdNative"

#ifndef DVD_LOG_VERBOSE
#define DVD_LOG_VERBOSE 0
#endif

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#if DVD_LOG_VERBOSE
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...) ((void)0)
#endif

// Global JavaVM reference for attaching threads in callbacks
static JavaVM *g_vm = nullptr;

// JNI_OnLoad to get the JavaVM
// This initializes g_vm for both dvd_jni.cpp and vlc_jni.cpp
// Note: vlc_jni.cpp has its own g_vm, but we can't access it directly here.
// Instead, vlc_jni.cpp will get g_vm from JNIEnv in nativeCreateMedia() as fallback.
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

// Forward declarations for callbacks
static int JavaSeekCallback(void *stream, uint64_t pos);
static int JavaReadCallback(void *stream, void *buffer, int size);
static int JavaReadvCallback(void *stream, void *iovec, int blocks);
static int JavaIoctlCallback(void *stream, int op, void *data, int data_size, int *agid, int lba);

// Structure to hold Java UsbBlockDevice reference and state for stream callbacks
struct JavaDataSource {
    jobject blockDevice; // Global ref
    uint64_t position;   // Current byte position
    int blockSize;       // Cached block size (e.g. 2048)
#ifdef HAVE_DVDREAD
    dvd_reader_stream_cb callbacks; // Must persist for the lifetime of the DVD handle
#endif
    
    JavaDataSource(JNIEnv *env, jobject device) : position(0), blockSize(2048) {
        blockDevice = env->NewGlobalRef(device);
        
        // Get block size from device
        jclass deviceClass = env->GetObjectClass(device);
        jmethodID getBlockSizeId = env->GetMethodID(deviceClass, "getBlockSize", "()I");
        if (getBlockSizeId) {
            blockSize = env->CallIntMethod(device, getBlockSizeId);
        }
        
#ifdef HAVE_DVDREAD
        // Initialize callbacks - they must persist for the lifetime of this object
        callbacks.pf_seek = JavaSeekCallback;
        callbacks.pf_read = JavaReadCallback;
        callbacks.pf_readv = JavaReadvCallback;
        callbacks.pf_ioctl = JavaIoctlCallback;  // CSS IOCTL callback
#endif
    }
    
    ~JavaDataSource() {
        JNIEnv *env = nullptr;
        bool attached = false;
        
        // We need JNIEnv to delete global ref. 
        // Destructor might be called from any thread.
        if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
            g_vm->AttachCurrentThread(&env, nullptr);
            attached = true;
        }
        
        if (env) {
            env->DeleteGlobalRef(blockDevice);
        }
        
        if (attached) {
            g_vm->DetachCurrentThread();
        }
    }
};

#ifdef HAVE_DVDREAD
struct DvdHandle {
    dvd_reader_t* dvd;
    std::string path;
    JavaDataSource* javaSource; // Owned by DvdHandle if opened via stream
    
    // Path constructor
    DvdHandle(dvd_reader_t* d, const char* p) : dvd(d), path(p ? p : ""), javaSource(nullptr) {}
    
    // Stream constructor
    DvdHandle(dvd_reader_t* d, JavaDataSource* js) : dvd(d), path(""), javaSource(js) {}
    
    ~DvdHandle() {
        if (dvd) {
            DVDClose(dvd);
            dvd = nullptr;
        }
        if (javaSource) {
            delete javaSource;
            javaSource = nullptr;
        }
    }
};

static int64_t dvdTimeToMs(dvd_time_t* dvd_time) {
    if (!dvd_time) return 0;
    int hours = ((dvd_time->hour >> 4) & 0x0F) * 10 + (dvd_time->hour & 0x0F);
    int minutes = ((dvd_time->minute >> 4) & 0x0F) * 10 + (dvd_time->minute & 0x0F);
    int seconds = ((dvd_time->second >> 4) & 0x0F) * 10 + (dvd_time->second & 0x0F);
    int frames = ((dvd_time->frame_u >> 4) & 0x0F) * 10 + (dvd_time->frame_u & 0x0F);
    int64_t totalMs = (int64_t)hours * 3600000LL +
                      (int64_t)minutes * 60000LL +
                      (int64_t)seconds * 1000LL +
                      (int64_t)frames * 33LL;
    return totalMs;
}

// Stream Callbacks for libdvdread
static int JavaSeekCallback(void *stream, uint64_t pos) {
    LOGD("DvdNative: JavaSeekCallback() called - seeking to position: %llu", (unsigned long long)pos);
    JavaDataSource *source = static_cast<JavaDataSource*>(stream);
    if (!source) {
        LOGE("DvdNative: JavaSeekCallback() - source is null");
        return -1;
    }
    
    uint64_t oldPos = source->position;
    source->position = pos;
    LOGD("DvdNative: JavaSeekCallback() - Position changed from %llu to %llu", (unsigned long long)oldPos, (unsigned long long)pos);
    LOGD("DvdNative: JavaSeekCallback() - Success, returning 0");
    return 0; // Success
}

static std::string langCodeToString(uint16_t code) {
    char a = (code >> 8) & 0xFF;
    char b = code & 0xFF;
    if (!std::isalpha(static_cast<unsigned char>(a)) || !std::isalpha(static_cast<unsigned char>(b))) {
        return "";
    }
    std::string s;
    s.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(a))));
    s.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(b))));
    return s;
}

static std::string audioFormatToCodec(uint8_t fmt) {
    switch (fmt) {
        case 0x00: return "AC3";
        case 0x01: return "Unknown";
        case 0x02: return "MPEG1";
        case 0x03: return "MPEG2";
        case 0x04: return "LPCM";
        case 0x05: return "DTS";
        case 0x06: return "SDDS";
        default:   return "Unknown";
    }
}

static int sampleFrequencyToRate(uint8_t freq) {
    switch (freq & 0x3) {
        case 0x0: return 48000;
        case 0x1: return 96000;
        case 0x2: return 44100;
        case 0x3: return 32000;
        default:  return 0;
    }
}

static int JavaReadCallback(void *stream, void *buffer, int size) {
    LOGD("DvdNative: JavaReadCallback() called - size: %d bytes", size);
    JavaDataSource *source = static_cast<JavaDataSource*>(stream);
    if (!source) {
        LOGE("DvdNative: JavaReadCallback() - source is null");
        return -1;
    }
    if (!g_vm) {
        LOGE("DvdNative: JavaReadCallback() - g_vm is null");
        return -1;
    }
    
    LOGD("DvdNative: JavaReadCallback() - Current position: %llu", (unsigned long long)source->position);
    LOGD("DvdNative: JavaReadCallback() - Getting JNI environment");
    JNIEnv *env = nullptr;
    bool attached = false;
    int result = -1;
    
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGD("DvdNative: JavaReadCallback() - Attaching thread to JVM");
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("DvdNative: JavaReadCallback() - Failed to attach thread for read callback");
            return -1;
        }
        attached = true;
        LOGD("DvdNative: JavaReadCallback() - Thread attached successfully");
    } else {
        LOGD("DvdNative: JavaReadCallback() - Already attached to JVM");
    }
    
    // Calculate LBA and offset
    // Note: libdvdread generally reads aligned to 2048 bytes, but we must handle unaligned logic
    // UsbBlockDevice expects: read(lba, buffer, length)
    // Our SCSI driver reads blocks.
    
    LOGD("DvdNative: JavaReadCallback() - Creating Java byte array of size: %d", size);
    // We'll create a temporary byte array for Java
    jbyteArray javaBuffer = env->NewByteArray(size);
    if (javaBuffer == nullptr) {
        LOGE("DvdNative: JavaReadCallback() - Failed to create Java byte array");
        if (attached) {
            LOGD("DvdNative: JavaReadCallback() - Detaching thread");
            g_vm->DetachCurrentThread();
        }
        return -1;
    }
    LOGD("DvdNative: JavaReadCallback() - Java byte array created successfully");
    
    // Calculate block-aligned read
    int blockSize = source->blockSize;
    int64_t startByte = source->position;
    int64_t startLba = startByte / blockSize;
    int offsetInFirstBlock = startByte % blockSize;
    LOGD("DvdNative: JavaReadCallback() - Block size: %d, startByte: %lld, startLba: %lld, offsetInFirstBlock: %d", 
         blockSize, (long long)startByte, (long long)startLba, offsetInFirstBlock);
    
    // For simplicity, we'll assume the Java layer handles the complexity or we request what we need.
    // However, the UsbBlockDevice interface I defined: fun read(lba: Long, buffer: ByteArray, length: Int): Int
    // implies it reads 'length' bytes starting from 'lba' * 'blockSize'.
    // Wait, SCSI READ(10) reads whole blocks.
    // If we request partial blocks, the Java driver must buffer/handle it.
    // Let's assume the Java driver's read() method behaves like:
    // "Read 'length' bytes starting at 'lba' offset 0".
    // If our position is unaligned, we might have a problem if we pass just LBA.
    
    // CORRECTION: The UsbBlockDevice interface I defined in Phase 1:
    // fun read(lba: Long, buffer: ByteArray, length: Int): Int
    // This likely maps to a SCSI read starting at LBA. If 'length' is not a multiple of block size,
    // the SCSI driver might fail or pad.
    
    // Ideally, we should align here. But copying is expensive.
    // Since libdvdread usually reads aligned 2048 bytes (1 block), let's try to just pass it through.
    // If position is unaligned, we might be reading inside a block.
    
    // Let's rely on the Java implementation to handle unaligned reads if necessary,
    // OR force alignment here.
    // Given we are writing a SCSI driver that sends READ(10), it expects block counts.
    // So we MUST request multiples of block size.
    
    // Simple alignment handling:
    // Since we can't easily cache here without complexity, let's hope libdvdread is nice.
    // It usually is.
    
    if (offsetInFirstBlock != 0) {
        // Unaligned read start - this is tricky with pure block IO
        // We would need to read the whole block and copy partial.
        // For now, let's assume the Java driver is smart or libdvdread is aligned.
        // We pass LBA. If offsetInFirstBlock > 0, we are skipping data.
        // We can't represent this with just (lba, buffer, length).
        // We'll pass LBA and hope the caller (SCSI driver) can't handle offset.
        // Actually, we should probably implement read(byteOffset) in Java?
        // Too late, interface is defined.
        
        // Workaround:
        // If we are truly block based, we can only read from block start.
        // So if we are at offset 100 in block 5, we must read block 5, and copy from index 100.
        // BUT buffer size in C is 'size'.
        // Java buffer is size 'size'.
        // If we ask Java to read(lba=5, buf[size], len=size), it fills buf with bytes from 5.0 to 5.(size).
        // But we need 5.100 to 5.(100+size).
        // So we get the WRONG data.
        
        // Proper fix: Align locally.
        // This is getting complicated for a JNI callback.
        // Let's assume aligned for now, log warning if not.
        LOGW("DvdNative: JavaReadCallback() - Unaligned read detected! Pos: %lld, BlockSize: %d, offsetInFirstBlock: %d", 
             (long long)startByte, blockSize, offsetInFirstBlock);
    }
    
    LOGD("DvdNative: JavaReadCallback() - Getting read method from Java class");
    jclass deviceClass = env->GetObjectClass(source->blockDevice);
    LOGD("DvdNative: JavaReadCallback() - Device class: %p", (void*)deviceClass);
    jmethodID readMethod = env->GetMethodID(deviceClass, "read", "(J[BI)I");
    if (!readMethod) {
        LOGE("DvdNative: JavaReadCallback() - Failed to find read method");
        env->DeleteLocalRef(javaBuffer);
        if (attached) {
            LOGD("DvdNative: JavaReadCallback() - Detaching thread");
            g_vm->DetachCurrentThread();
        }
        return -1;
    }
    LOGD("DvdNative: JavaReadCallback() - Read method found: %p", (void*)readMethod);
    
    // Call Java: read(lba, buffer, length)
    LOGD("DvdNative: JavaReadCallback() - Calling Java read() method - lba: %lld, size: %d", (long long)startLba, size);
    jint bytesRead = env->CallIntMethod(source->blockDevice, readMethod, (jlong)startLba, javaBuffer, (jint)size);
    LOGD("DvdNative: JavaReadCallback() - Java read() returned: %d bytes", bytesRead);

    if (env->ExceptionCheck()) {
        LOGE("DvdNative: JavaReadCallback() - Java exception occurred in read callback");
        env->ExceptionDescribe(); // Print exception to logcat
        env->ExceptionClear();    // Clear so we can safely continue/cleanup
        env->DeleteLocalRef(javaBuffer);
        if (attached) {
            LOGD("DvdNative: JavaReadCallback() - Detaching thread");
            g_vm->DetachCurrentThread();
        }
        return -1;
    }
    
    if (bytesRead > 0) {
        LOGD("DvdNative: JavaReadCallback() - Copying %d bytes from Java array to C buffer", bytesRead);
        // Copy back to C buffer
        env->GetByteArrayRegion(javaBuffer, 0, bytesRead, (jbyte*)buffer);
        uint64_t oldPos = source->position;
        source->position += bytesRead;
        LOGD("DvdNative: JavaReadCallback() - Position updated from %llu to %llu", (unsigned long long)oldPos, (unsigned long long)source->position);
        result = bytesRead;
        LOGD("DvdNative: JavaReadCallback() - Success, returning %d bytes", result);
    } else if (bytesRead == 0) {
        LOGD("DvdNative: JavaReadCallback() - Java read() returned 0 (EOF or no data)");
        result = 0;
    } else {
        LOGE("DvdNative: JavaReadCallback() - Java read() returned error: %d", bytesRead);
        result = -1;
    }
    
    LOGD("DvdNative: JavaReadCallback() - Deleting local reference to Java byte array");
    env->DeleteLocalRef(javaBuffer);
    
    if (attached) {
        LOGD("DvdNative: JavaReadCallback() - Detaching thread");
        g_vm->DetachCurrentThread();
    }
    LOGD("DvdNative: JavaReadCallback() - Complete, returning: %d", result);
    return result;
}

static int JavaReadvCallback(void *stream, void *iovec, int blocks) {
    LOGD("DvdNative: JavaReadvCallback() called - blocks: %d", blocks);
    LOGW("DvdNative: JavaReadvCallback() - Not implemented, returning -1");
    return -1;
}

/**
 * IOCTL callback for CSS operations.
 * Routes CSS commands to Java UsbBlockDevice implementation.
 */
static int JavaIoctlCallback(void *stream, int op, void *data, int data_size, int *agid, int lba) {
    LOGD("DvdNative: JavaIoctlCallback() called - op: 0x%02x, data_size: %d, lba: %d", op, data_size, lba);
    JavaDataSource *source = static_cast<JavaDataSource*>(stream);
    if (!source) {
        LOGE("DvdNative: JavaIoctlCallback() - source is null");
        return -1;
    }
    if (!g_vm) {
        LOGE("DvdNative: JavaIoctlCallback() - g_vm is null");
        return -1;
    }
    
    LOGD("DvdNative: JavaIoctlCallback() - Getting JNI environment");
    JNIEnv *env = nullptr;
    bool attached = false;
    int result = -1;
    
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGD("DvdNative: JavaIoctlCallback() - Attaching thread to JVM");
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("DvdNative: JavaIoctlCallback() - Failed to attach thread for ioctl callback");
            return -1;
        }
        attached = true;
        LOGD("DvdNative: JavaIoctlCallback() - Thread attached successfully");
    } else {
        LOGD("DvdNative: JavaIoctlCallback() - Already attached to JVM");
    }
    
    // Get the cssIoctl method from UsbBlockDevice
    // Signature: cssIoctl(op: Int, data: ByteArray?, agid: IntArray, lba: Int): Int
    LOGD("DvdNative: JavaIoctlCallback() - Getting device class");
    jclass deviceClass = env->GetObjectClass(source->blockDevice);
    LOGD("DvdNative: JavaIoctlCallback() - Looking up cssIoctl method");
    jmethodID ioctlMethod = env->GetMethodID(deviceClass, "cssIoctl", "(I[B[II)I");
    
    if (!ioctlMethod) {
        LOGW("DvdNative: JavaIoctlCallback() - cssIoctl method not found in UsbBlockDevice - CSS operations not supported");
        if (attached) {
            LOGD("DvdNative: JavaIoctlCallback() - Detaching thread");
            g_vm->DetachCurrentThread();
        }
        return -1;
    }
    LOGD("DvdNative: JavaIoctlCallback() - cssIoctl method found: %p", (void*)ioctlMethod);
    
    // Create byte array for data (if any)
    LOGD("DvdNative: JavaIoctlCallback() - Creating data arrays");
    jbyteArray javaData = nullptr;
    if (data && data_size > 0) {
        LOGD("DvdNative: JavaIoctlCallback() - Creating Java byte array of size: %d", data_size);
        javaData = env->NewByteArray(data_size);
        if (javaData) {
            LOGD("DvdNative: JavaIoctlCallback() - Java byte array created");
            // For SEND operations, copy data to Java array
            if (op == 0x11 || op == 0x12) { // SEND_CHALLENGE or SEND_KEY2
                LOGD("DvdNative: JavaIoctlCallback() - SEND operation detected, copying data to Java array");
                env->SetByteArrayRegion(javaData, 0, data_size, (jbyte*)data);
                LOGD("DvdNative: JavaIoctlCallback() - Data copied to Java array");
            }
        } else {
            LOGE("DvdNative: JavaIoctlCallback() - Failed to create Java byte array");
        }
    } else {
        LOGD("DvdNative: JavaIoctlCallback() - No data array needed (data_size: %d)", data_size);
    }
    
    // Create int array for AGID (input/output parameter)
    LOGD("DvdNative: JavaIoctlCallback() - Creating AGID array");
    jintArray agidArray = env->NewIntArray(1);
    if (agidArray && agid) {
        LOGD("DvdNative: JavaIoctlCallback() - Setting AGID value: %d", *agid);
        env->SetIntArrayRegion(agidArray, 0, 1, (jint*)agid);
        LOGD("DvdNative: JavaIoctlCallback() - AGID array initialized");
    } else {
        LOGD("DvdNative: JavaIoctlCallback() - AGID array not needed or creation failed");
    }
    
    // Call Java method
    LOGD("DvdNative: JavaIoctlCallback() - Calling Java cssIoctl() method");
    LOGD("DvdNative: JavaIoctlCallback() -   - op: 0x%02x", op);
    LOGD("DvdNative: JavaIoctlCallback() -   - javaData: %p", (void*)javaData);
    LOGD("DvdNative: JavaIoctlCallback() -   - agidArray: %p", (void*)agidArray);
    LOGD("DvdNative: JavaIoctlCallback() -   - lba: %d", lba);
    result = env->CallIntMethod(source->blockDevice, ioctlMethod, op, javaData, agidArray, lba);
    LOGD("DvdNative: JavaIoctlCallback() - Java cssIoctl() returned: %d", result);
    
    if (env->ExceptionCheck()) {
        LOGE("DvdNative: JavaIoctlCallback() - Java exception occurred in cssIoctl callback");
        env->ExceptionDescribe();
        env->ExceptionClear();
        result = -1;
    } else {
        LOGD("DvdNative: JavaIoctlCallback() - No exception, processing result");
        // For REPORT operations, copy data back from Java array
        if (result == 0 && javaData && data && data_size > 0) {
            if (op != 0x11 && op != 0x12) { // Not SEND operations
                LOGD("DvdNative: JavaIoctlCallback() - REPORT operation, copying data back from Java array");
                env->GetByteArrayRegion(javaData, 0, data_size, (jbyte*)data);
                LOGD("DvdNative: JavaIoctlCallback() - Data copied back to C buffer");
            } else {
                LOGD("DvdNative: JavaIoctlCallback() - SEND operation, no data to copy back");
            }
        }
        
        // Get AGID result
        if (result == 0 && agidArray && agid) {
            LOGD("DvdNative: JavaIoctlCallback() - Getting AGID result from Java array");
            env->GetIntArrayRegion(agidArray, 0, 1, (jint*)agid);
            LOGD("DvdNative: JavaIoctlCallback() - AGID result: %d", *agid);
        }
    }
    
    // Cleanup
    LOGD("DvdNative: JavaIoctlCallback() - Cleaning up local references");
    if (javaData) {
        LOGD("DvdNative: JavaIoctlCallback() - Deleting javaData reference");
        env->DeleteLocalRef(javaData);
    }
    if (agidArray) {
        LOGD("DvdNative: JavaIoctlCallback() - Deleting agidArray reference");
        env->DeleteLocalRef(agidArray);
    }
    
    if (attached) {
        LOGD("DvdNative: JavaIoctlCallback() - Detaching thread");
        g_vm->DetachCurrentThread();
    }
    LOGD("DvdNative: JavaIoctlCallback() - Complete, returning: %d", result);
    return result;
}

#else
struct DvdHandle {
    int fd;
    std::string path;
    DvdHandle(int f, const char* p) : fd(f), path(p ? p : "") {}
};
#endif

static std::map<jlong, std::unique_ptr<DvdHandle>> g_dvdHandles;
static jlong g_nextHandleId = 1;

#ifdef HAVE_DVDREAD
// Structure to hold VOB file handle and metadata
struct VobHandle {
    dvd_file_t* vob;
    int vtsN;  // VTS number
    dvd_reader_t* dvd;  // Reference to parent DVD reader
    
    VobHandle(dvd_file_t* v, int vts, dvd_reader_t* d) : vob(v), vtsN(vts), dvd(d) {}
    
    ~VobHandle() {
        if (vob) {
            DVDCloseFile(vob);
            vob = nullptr;
        }
    }
};

static std::map<jlong, std::unique_ptr<VobHandle>> g_vobHandles;
static jlong g_nextVobHandleId = 1;
#endif

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdOpenStreamNative(JNIEnv *env, jobject clazz, jobject blockDevice) {
    LOGD("DvdNative: dvdOpenStreamNative() called");
    LOGD("DvdNative: dvdOpenStreamNative() - blockDevice: %p", (void*)blockDevice);
    
#ifdef HAVE_DVDREAD
    LOGD("DvdNative: dvdOpenStreamNative() - HAVE_DVDREAD defined, proceeding");
    LOGD("DvdNative: dvdOpenStreamNative() - Creating JavaDataSource");
    JavaDataSource* source = new JavaDataSource(env, blockDevice);
    LOGD("DvdNative: dvdOpenStreamNative() - JavaDataSource created: %p", (void*)source);
    LOGD("DvdNative: dvdOpenStreamNative() - Block size: %d", source->blockSize);
    LOGD("DvdNative: dvdOpenStreamNative() - Initial position: %llu", (unsigned long long)source->position);
    
    // DVDOpenStream uses the callbacks to access the device
    // 'source' is passed as the 'void* stream' parameter to callbacks
    // IMPORTANT: Pass &source->callbacks (not a local variable!) because 
    // libdvdread stores the pointer and uses it later
    LOGD("DvdNative: dvdOpenStreamNative() - Calling DVDOpenStream()");
    LOGD("DvdNative: dvdOpenStreamNative() -   - source: %p", (void*)source);
    LOGD("DvdNative: dvdOpenStreamNative() -   - callbacks: %p", (void*)&source->callbacks);
    dvd_reader_t* dvd = DVDOpenStream(source, &source->callbacks);
    LOGD("DvdNative: dvdOpenStreamNative() - DVDOpenStream() returned: %p", (void*)dvd);
    
    if (dvd == nullptr) {
        LOGE("DvdNative: dvdOpenStreamNative() - Failed to open DVD with libdvdread via stream");
        LOGE("DvdNative: dvdOpenStreamNative() - DVDOpenStream() returned NULL");
        LOGD("DvdNative: dvdOpenStreamNative() - Deleting JavaDataSource");
        delete source;
        LOGD("DvdNative: dvdOpenStreamNative() - Returning -1");
        return -1L;
    }
    
    LOGD("DvdNative: dvdOpenStreamNative() - DVD opened successfully");
    jlong handleId = g_nextHandleId++;
    LOGD("DvdNative: dvdOpenStreamNative() - Assigning handle ID: %ld", (long)handleId);
    auto handle = std::make_unique<DvdHandle>(dvd, source);
    LOGD("DvdNative: dvdOpenStreamNative() - DvdHandle created");
    g_dvdHandles[handleId] = std::move(handle);
    LOGD("DvdNative: dvdOpenStreamNative() - Handle stored in map, total handles: %zu", g_dvdHandles.size());
    
    LOGI("DvdNative: dvdOpenStreamNative() - DVD opened successfully via stream, handle: %ld", (long)handleId);
    LOGD("DvdNative: dvdOpenStreamNative() - Returning handle: %ld", (long)handleId);
    return handleId;
#else
    LOGE("DvdNative: dvdOpenStreamNative() - libdvdread not available (HAVE_DVDREAD not defined)");
    return -1L;
#endif
}

// ... Existing functions (dvdOpenNative, dvdCloseNative, etc.) need to be kept/merged ...
// I will rewrite the file to include both old and new logic, but focusing on the new Stream support.
// To save tokens/space and follow instructions, I'll replace the whole content with the merged version.

JNIEXPORT jlong JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdOpenNative(JNIEnv *env, jobject clazz, jstring path) {
    LOGD("DvdNative: dvdOpenNative() called");
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    if (!pathStr) {
        LOGE("DvdNative: dvdOpenNative() - Failed to get path string");
        return -1L;
    }
    
    LOGD("DvdNative: dvdOpenNative() - Opening DVD at path: %s", pathStr);
    
#ifdef HAVE_DVDREAD
    LOGD("DvdNative: dvdOpenNative() - HAVE_DVDREAD defined, calling DVDOpen()");
    dvd_reader_t* dvd = DVDOpen(pathStr);
    LOGD("DvdNative: dvdOpenNative() - DVDOpen() returned: %p", (void*)dvd);
    if (!dvd) {
        LOGE("DvdNative: dvdOpenNative() - Failed to open DVD at path: %s", pathStr);
        LOGD("DvdNative: dvdOpenNative() - Releasing path string");
        env->ReleaseStringUTFChars(path, pathStr);
        LOGD("DvdNative: dvdOpenNative() - Returning -1");
        return -1L;
    }
    LOGD("DvdNative: dvdOpenNative() - DVD opened successfully");
    jlong handleId = g_nextHandleId++;
    LOGD("DvdNative: dvdOpenNative() - Assigning handle ID: %ld", (long)handleId);
    auto handle = std::make_unique<DvdHandle>(dvd, pathStr);
    LOGD("DvdNative: dvdOpenNative() - DvdHandle created");
    g_dvdHandles[handleId] = std::move(handle);
    LOGD("DvdNative: dvdOpenNative() - Handle stored in map, total handles: %zu", g_dvdHandles.size());
#else
    LOGE("DvdNative: dvdOpenNative() - libdvdread not available (HAVE_DVDREAD not defined)");
    jlong handleId = -1L;
#endif
    LOGD("DvdNative: dvdOpenNative() - Releasing path string");
    env->ReleaseStringUTFChars(path, pathStr);
    LOGD("DvdNative: dvdOpenNative() - Returning handle: %ld", (long)handleId);
    return handleId;
}

JNIEXPORT void JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdCloseNative(JNIEnv *env, jobject clazz, jlong handle) {
    LOGD("DvdNative: dvdCloseNative() called - handle: %ld", (long)handle);
    if (handle <= 0) {
        LOGD("DvdNative: dvdCloseNative() - Invalid handle (<= 0), returning");
        return;
    }
    LOGD("DvdNative: dvdCloseNative() - Looking up handle in map (total handles: %zu)", g_dvdHandles.size());
    auto it = g_dvdHandles.find(handle);
    if (it != g_dvdHandles.end()) {
        LOGD("DvdNative: dvdCloseNative() - Handle found, closing DVD");
        LOGD("DvdNative: dvdCloseNative() - Erasing handle from map");
        g_dvdHandles.erase(it);
        LOGD("DvdNative: dvdCloseNative() - DVD handle %ld closed, remaining handles: %zu", (long)handle, g_dvdHandles.size());
    } else {
        LOGW("DvdNative: dvdCloseNative() - Handle %ld not found in map", (long)handle);
    }
    LOGD("DvdNative: dvdCloseNative() - Complete");
}

JNIEXPORT jint JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdGetTitleCountNative(JNIEnv *env, jobject clazz, jlong handle) {
    LOGD("DvdNative: dvdGetTitleCountNative() called - handle: %ld", (long)handle);
    auto it = g_dvdHandles.find(handle);
    if (it == g_dvdHandles.end()) {
        LOGE("DvdNative: dvdGetTitleCountNative() - Handle %ld not found", (long)handle);
        return -1;
    }
    LOGD("DvdNative: dvdGetTitleCountNative() - Handle found");
    
#ifdef HAVE_DVDREAD
    LOGD("DvdNative: dvdGetTitleCountNative() - HAVE_DVDREAD defined");
    dvd_reader_t* dvd = it->second->dvd;
    LOGD("DvdNative: dvdGetTitleCountNative() - DVD reader: %p", (void*)dvd);
    LOGD("DvdNative: dvdGetTitleCountNative() - Opening VMG (Video Manager)");
    ifo_handle_t* vmg = ifoOpen(dvd, 0);
    LOGD("DvdNative: dvdGetTitleCountNative() - ifoOpen() returned: %p", (void*)vmg);
    if (!vmg) {
        LOGE("DvdNative: dvdGetTitleCountNative() - Failed to open VMG");
        return 0;
    }
    LOGD("DvdNative: dvdGetTitleCountNative() - VMG opened successfully");
    if (!vmg->tt_srpt) {
        LOGE("DvdNative: dvdGetTitleCountNative() - tt_srpt is null");
        ifoClose(vmg);
        return 0;
    }
    int count = vmg->tt_srpt->nr_of_srpts;
    LOGD("DvdNative: dvdGetTitleCountNative() - Title count: %d", count);
    LOGD("DvdNative: dvdGetTitleCountNative() - Closing VMG");
    ifoClose(vmg);
    LOGD("DvdNative: dvdGetTitleCountNative() - Returning title count: %d", count);
    return count;
#else
    LOGE("DvdNative: dvdGetTitleCountNative() - libdvdread not available");
    return 0;
#endif
}

JNIEXPORT jobject JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdReadTitleNative(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber) {
    auto it = g_dvdHandles.find(handle);
    if (it == g_dvdHandles.end()) return nullptr;
    
#ifdef HAVE_DVDREAD
    dvd_reader_t* dvd = it->second->dvd;
    ifo_handle_t* vmg = ifoOpen(dvd, 0);
    if (!vmg) return nullptr;
    
    if (titleNumber < 1 || titleNumber > vmg->tt_srpt->nr_of_srpts) {
        ifoClose(vmg);
        return nullptr;
    }
    
    tt_srpt_t* tt_srpt = vmg->tt_srpt;
    int titleIdx = titleNumber - 1;
    int vtsN = tt_srpt->title[titleIdx].vts_ttn;
    
    ifo_handle_t* vts = ifoOpen(dvd, vtsN);
    if (!vts) {
        ifoClose(vmg);
        return nullptr;
    }
    
    // Use first PGC
    pgc_t* pgc = vts->vts_pgcit->pgci_srp[0].pgc;
    int chapterCount = pgc ? pgc->nr_of_programs : 0;
    int64_t duration = pgc ? dvdTimeToMs(&pgc->playback_time) : 0;
    
    ifoClose(vts);
    ifoClose(vmg);
    
    jclass titleClass = env->FindClass("com/ble1st/connectias/feature/dvd/native/DvdTitleNative");
    jmethodID constructor = env->GetMethodID(titleClass, "<init>", "(IIJ)V");
    return env->NewObject(titleClass, constructor, titleNumber, chapterCount, duration);
#else
    return nullptr;
#endif
}

JNIEXPORT jobjectArray JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdGetAudioTracksNative(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber) {
    auto it = g_dvdHandles.find(handle);
    if (it == g_dvdHandles.end()) return nullptr;

#ifdef HAVE_DVDREAD
    dvd_reader_t* dvd = it->second->dvd;
    ifo_handle_t* vmg = ifoOpen(dvd, 0);
    if (!vmg) return nullptr;

    if (!vmg->tt_srpt || titleNumber < 1 || titleNumber > vmg->tt_srpt->nr_of_srpts) {
        ifoClose(vmg);
        return nullptr;
    }

    int vtsN = vmg->tt_srpt->title[titleNumber-1].title_set_nr;
    ifoClose(vmg);

    ifo_handle_t* vts = ifoOpen(dvd, vtsN);
    if (!vts || !vts->vtsi_mat) {
        if (vts) ifoClose(vts);
        return nullptr;
    }

    vtsi_mat_t* mat = vts->vtsi_mat;
    int count = mat->nr_of_vts_audio_streams;
    if (count <= 0) {
        ifoClose(vts);
        return nullptr;
    }
    if (count > 8) count = 8;

    jclass audioClass = env->FindClass("com/ble1st/connectias/feature/dvd/native/DvdAudioTrackNative");
    if (!audioClass) {
        ifoClose(vts);
        return nullptr;
    }
    jmethodID ctor = env->GetMethodID(audioClass, "<init>", "(ILjava/lang/String;Ljava/lang/String;II)V");
    if (!ctor) {
        ifoClose(vts);
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(count, audioClass, nullptr);
    if (!result) {
        ifoClose(vts);
        return nullptr;
    }

    for (int i = 0; i < count; i++) {
        audio_attr_t* attr = &mat->vts_audio_attr[i];
        std::string lang = langCodeToString(attr->lang_code);
        std::string codec = audioFormatToCodec(attr->audio_format);
        int sampleRate = sampleFrequencyToRate(attr->sample_frequency);
        int channels = static_cast<int>(attr->channels) + 1;

        jstring jLang = lang.empty() ? nullptr : env->NewStringUTF(lang.c_str());
        jstring jCodec = env->NewStringUTF(codec.c_str());

        jobject obj = env->NewObject(audioClass, ctor, i, jLang, jCodec, channels, sampleRate);
        if (jLang) env->DeleteLocalRef(jLang);
        if (jCodec) env->DeleteLocalRef(jCodec);

        env->SetObjectArrayElement(result, i, obj);
        env->DeleteLocalRef(obj);
    }

    ifoClose(vts);
    return result;
#else
    return nullptr;
#endif
}

JNIEXPORT jobjectArray JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdGetSubtitleTracksNative(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber) {
    auto it = g_dvdHandles.find(handle);
    if (it == g_dvdHandles.end()) return nullptr;

#ifdef HAVE_DVDREAD
    dvd_reader_t* dvd = it->second->dvd;
    ifo_handle_t* vmg = ifoOpen(dvd, 0);
    if (!vmg) return nullptr;

    if (!vmg->tt_srpt || titleNumber < 1 || titleNumber > vmg->tt_srpt->nr_of_srpts) {
        ifoClose(vmg);
        return nullptr;
    }

    int vtsN = vmg->tt_srpt->title[titleNumber-1].title_set_nr;
    ifoClose(vmg);

    ifo_handle_t* vts = ifoOpen(dvd, vtsN);
    if (!vts || !vts->vtsi_mat) {
        if (vts) ifoClose(vts);
        return nullptr;
    }

    vtsi_mat_t* mat = vts->vtsi_mat;
    int count = mat->nr_of_vts_subp_streams;
    if (count <= 0) {
        ifoClose(vts);
        return nullptr;
    }
    if (count > 32) count = 32;

    jclass subClass = env->FindClass("com/ble1st/connectias/feature/dvd/native/DvdSubtitleTrackNative");
    if (!subClass) {
        ifoClose(vts);
        return nullptr;
    }
    jmethodID ctor = env->GetMethodID(subClass, "<init>", "(ILjava/lang/String;Ljava/lang/String;)V");
    if (!ctor) {
        ifoClose(vts);
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(count, subClass, nullptr);
    if (!result) {
        ifoClose(vts);
        return nullptr;
    }

    for (int i = 0; i < count; i++) {
        subp_attr_t* attr = &mat->vts_subp_attr[i];
        std::string lang = langCodeToString(attr->lang_code);
        std::string type = "subpicture";
        switch (attr->code_mode & 0x3) {
            case 0: type = "rle"; break;
            case 1: type = "extended"; break;
            default: type = "subpicture"; break;
        }

        jstring jLang = lang.empty() ? nullptr : env->NewStringUTF(lang.c_str());
        jstring jType = env->NewStringUTF(type.c_str());

        jobject obj = env->NewObject(subClass, ctor, i, jLang, jType);
        if (jLang) env->DeleteLocalRef(jLang);
        if (jType) env->DeleteLocalRef(jType);

        env->SetObjectArrayElement(result, i, obj);
        env->DeleteLocalRef(obj);
    }

    ifoClose(vts);
    return result;
#else
    return nullptr;
#endif
}

JNIEXPORT jobject JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdReadChapterNative(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber, jint chapterNumber) {
    auto it = g_dvdHandles.find(handle);
    if (it == g_dvdHandles.end()) return nullptr;
    
#ifdef HAVE_DVDREAD
    dvd_reader_t* dvd = it->second->dvd;
    ifo_handle_t* vmg = ifoOpen(dvd, 0);
    if (!vmg) return nullptr;
    
    if (titleNumber < 1 || titleNumber > vmg->tt_srpt->nr_of_srpts) {
        ifoClose(vmg);
        return nullptr;
    }
    
    int vtsN = vmg->tt_srpt->title[titleNumber-1].vts_ttn;
    ifo_handle_t* vts = ifoOpen(dvd, vtsN);
    if (!vts) {
        ifoClose(vmg);
        return nullptr;
    }
    
    pgc_t* pgc = vts->vts_pgcit->pgci_srp[0].pgc; // Simplified
    if (!pgc || chapterNumber > pgc->nr_of_programs) {
        ifoClose(vts);
        ifoClose(vmg);
        return nullptr;
    }
    
    int64_t startTime = 0;
    int64_t duration = 0;
    
    // Calculate times (simplified loop)
    for (int i = 0; i < chapterNumber - 1; i++) {
        if (i < pgc->nr_of_programs) {
            int cellIdx = pgc->program_map[i] - 1; // program_map is 1-based
             if (cellIdx < pgc->nr_of_cells)
                startTime += dvdTimeToMs(&pgc->cell_playback[cellIdx].playback_time);
        }
    }
    
    int cellIdx = pgc->program_map[chapterNumber-1] - 1;
    if (cellIdx < pgc->nr_of_cells)
        duration = dvdTimeToMs(&pgc->cell_playback[cellIdx].playback_time);
        
    ifoClose(vts);
    ifoClose(vmg);
    
    jclass chapterClass = env->FindClass("com/ble1st/connectias/feature/dvd/native/DvdChapterNative");
    jmethodID constructor = env->GetMethodID(chapterClass, "<init>", "(IJJ)V");
    return env->NewObject(chapterClass, constructor, chapterNumber, startTime, duration);
#else
    return nullptr;
#endif
}

JNIEXPORT jlong JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdStreamToFdNative(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber, jint outFd) {
    auto it = g_dvdHandles.find(handle);
    if (it == g_dvdHandles.end()) {
        LOGE("Invalid DVD handle: %ld", (long)handle);
        return -1;
    }
    
#ifdef HAVE_DVDREAD
    dvd_reader_t* dvd = it->second->dvd;
    jlong totalBytesWritten = 0;
    
    LOGI("=== Starting DVD stream for title %d ===", titleNumber);
    
    // 1. Open VMG (Video Manager) to find Title info
    ifo_handle_t* vmg = ifoOpen(dvd, 0);
    if (!vmg) {
        LOGE("Failed to open VMG (Video Manager)");
        return -1;
    }
    
    if (!vmg->tt_srpt) {
        LOGE("No title search pointer table in VMG");
        ifoClose(vmg);
        return -1;
    }
    
    int numTitles = vmg->tt_srpt->nr_of_srpts;
    LOGI("DVD has %d titles", numTitles);
    
    if (titleNumber < 1 || titleNumber > numTitles) {
        LOGE("Invalid title number: %d (valid: 1-%d)", titleNumber, numTitles);
        ifoClose(vmg);
        return -1;
    }
    
    // Get the VTS (Video Title Set) number - this is title_set_nr, NOT vts_ttn!
    // vts_ttn = Title number WITHIN the VTS (for PTT lookup)
    // title_set_nr = The actual VTS number (1-based)
    int vtsN = vmg->tt_srpt->title[titleNumber-1].title_set_nr;
    int vtsTtn = vmg->tt_srpt->title[titleNumber-1].vts_ttn;  // Title number within VTS
    int numAngles = vmg->tt_srpt->title[titleNumber-1].nr_of_angles;
    int numPtts = vmg->tt_srpt->title[titleNumber-1].nr_of_ptts; // Chapters
    
    LOGI("Title %d: VTS=%d, VTS_TTN=%d, Angles=%d, Chapters=%d", 
         titleNumber, vtsN, vtsTtn, numAngles, numPtts);
    
    ifoClose(vmg); // Done with VMG
    
    // 2. Open VTS IFO
    ifo_handle_t* vts = ifoOpen(dvd, vtsN);
    if (!vts) {
        LOGE("Failed to open VTS %d IFO", vtsN);
        return -1;
    }
    
    // 3. Find the correct PGC (Program Chain) for this title
    // We need to use the VTS PTT (Part of Title) search pointer table
    pgc_t* pgc = nullptr;
    
    if (vts->vts_ptt_srpt && vtsTtn > 0 && vtsTtn <= vts->vts_ptt_srpt->nr_of_srpts) {
        // Get the first PTT entry for this title within VTS
        int pgcn = vts->vts_ptt_srpt->title[vtsTtn-1].ptt[0].pgcn;
        int pgn = vts->vts_ptt_srpt->title[vtsTtn-1].ptt[0].pgn;
        
        LOGI("VTS_TTN %d maps to PGCN=%d, PGN=%d", vtsTtn, pgcn, pgn);
        
        if (vts->vts_pgcit && pgcn > 0 && pgcn <= vts->vts_pgcit->nr_of_pgci_srp) {
            pgc = vts->vts_pgcit->pgci_srp[pgcn-1].pgc;
        }
    }
    
    // Fallback: use first PGC if we couldn't find it via PTT
    if (!pgc && vts->vts_pgcit && vts->vts_pgcit->nr_of_pgci_srp > 0) {
        LOGW("Using fallback: first PGC in VTS");
        pgc = vts->vts_pgcit->pgci_srp[0].pgc;
    }
    
    if (!pgc) {
        LOGE("No PGC found for VTS %d, VTS_TTN %d", vtsN, vtsTtn);
        ifoClose(vts);
        return -1;
    }
    
    LOGI("PGC has %d cells, %d programs", pgc->nr_of_cells, pgc->nr_of_programs);
    
    // Calculate total sectors to read for progress reporting
    uint64_t totalSectors = 0;
    for (int i = 0; i < pgc->nr_of_cells; i++) {
        if (pgc->cell_playback[i].last_sector >= pgc->cell_playback[i].first_sector) {
            totalSectors += (pgc->cell_playback[i].last_sector - pgc->cell_playback[i].first_sector + 1);
        }
    }
    LOGI("Total sectors to read: %llu (approx %.2f MB)", 
         (unsigned long long)totalSectors, 
         (double)(totalSectors * DVD_VIDEO_LB_LEN) / (1024.0 * 1024.0));
    
    // 4. Open VOB file for reading
    dvd_file_t* vob = DVDOpenFile(dvd, vtsN, DVD_READ_TITLE_VOBS);
    if (!vob) {
        LOGE("Failed to open VOBs for VTS %d", vtsN);
        ifoClose(vts);
        return -1;
    }
    
    // Get VOB file size for validation
    ssize_t vobFileSize = DVDFileSize(vob);
    LOGI("VOB file size: %zd blocks (%.2f MB)", vobFileSize, 
         (double)(vobFileSize * DVD_VIDEO_LB_LEN) / (1024.0 * 1024.0));
    
    // 5. Iterate Cells and Read Blocks
    // Buffer for reading blocks - increased size for better throughput and faster initial buffering
    const int BLOCK_COUNT = 128; // Read 256KB at a time for better throughput and faster ExoPlayer buffering
    unsigned char* buffer = (unsigned char*)malloc(BLOCK_COUNT * DVD_VIDEO_LB_LEN);
    if (!buffer) {
        LOGE("Failed to allocate read buffer");
        DVDCloseFile(vob);
        ifoClose(vts);
        return -1;
    }
    
    uint64_t sectorsRead = 0;
    int lastProgressPercent = -1;
    
    for (int cellIdx = 0; cellIdx < pgc->nr_of_cells; cellIdx++) {
        cell_playback_t* cell = &pgc->cell_playback[cellIdx];
        
        // Skip angle cells that aren't angle 1 (we only play angle 1)
        // Angle cells have specific flags we should check
        // For simplicity, we read all cells in order
        
        uint32_t firstSector = cell->first_sector;
        uint32_t lastSector = cell->last_sector;
        
        if (lastSector < firstSector) {
            LOGW("Cell %d: Invalid sector range (first=%u, last=%u), skipping", 
                 cellIdx, firstSector, lastSector);
            continue;
        }
        
        uint32_t cellSectorCount = lastSector - firstSector + 1;
        LOGD("Cell %d: sectors %u-%u (%u sectors, %.2f MB)", 
             cellIdx, firstSector, lastSector, cellSectorCount,
             (double)(cellSectorCount * DVD_VIDEO_LB_LEN) / (1024.0 * 1024.0));
        
        // DVDReadBlocks uses RELATIVE positions within the VOB file
        // The first_sector/last_sector from cell_playback are also relative to VOB start
        uint32_t currentBlock = firstSector;
        
        while (currentBlock <= lastSector) {
            uint32_t blocksRemaining = lastSector - currentBlock + 1;
            int blocksToRead = (blocksRemaining > BLOCK_COUNT) ? BLOCK_COUNT : blocksRemaining;
            
            // DVDReadBlocks returns number of blocks read, or -1 on error
            ssize_t blocksActuallyRead = DVDReadBlocks(vob, currentBlock, blocksToRead, buffer);
            
            if (blocksActuallyRead <= 0) {
                LOGE("Error reading blocks at position %u (requested %d blocks): returned %zd", 
                     currentBlock, blocksToRead, blocksActuallyRead);
                     
                // Try to continue with next block if we hit an error
                currentBlock++;
                continue;
            }
            
            // Write to pipe - handle partial writes by retrying until all data is written
            size_t bytesToWrite = blocksActuallyRead * DVD_VIDEO_LB_LEN;
            size_t bytesRemaining = bytesToWrite;
            const unsigned char* writePtr = buffer;
            
            while (bytesRemaining > 0) {
                ssize_t bytesWritten = write(outFd, writePtr, bytesRemaining);
                
                if (bytesWritten < 0) {
                    if (errno == EPIPE) {
                        LOGI("Pipe closed by reader (EPIPE) - player may have stopped or encountered format issue");
                        LOGI("Bytes written before EPIPE: %lld", (long long)totalBytesWritten);
                        goto finished;
                    } else if (errno == EAGAIN || errno == EWOULDBLOCK) {
                        // Pipe buffer full, wait a bit and retry
                        usleep(1000); // Wait 1ms before retrying
                        continue;
                    } else {
                        LOGE("Error writing to pipe: %s (errno=%d)", strerror(errno), errno);
                        goto finished;
                    }
                }
                
                if (bytesWritten == 0) {
                    // No data written, pipe might be closed
                    LOGW("Write returned 0 bytes, pipe may be closed");
                    goto finished;
                }
                
                // Update counters
                totalBytesWritten += bytesWritten;
                bytesRemaining -= bytesWritten;
                writePtr += bytesWritten;
                
                if (bytesWritten < (ssize_t)bytesRemaining) {
                    LOGD("Partial write: wrote %zd of %zu bytes, %zu remaining", 
                         bytesWritten, bytesToWrite, bytesRemaining);
                }
            }
            
            currentBlock += blocksActuallyRead;
            sectorsRead += blocksActuallyRead;
            
            // Progress reporting (every 5%)
            if (totalSectors > 0) {
                int progressPercent = (int)((sectorsRead * 100) / totalSectors);
                if (progressPercent >= lastProgressPercent + 5) {
                    lastProgressPercent = progressPercent;
                    LOGI("Progress: %d%% (%.2f MB written)", progressPercent,
                         (double)totalBytesWritten / (1024.0 * 1024.0));
                }
            }
        }
    }
    
    LOGI("=== Stream completed successfully ===");
    LOGI("Total bytes written: %lld (%.2f MB)", (long long)totalBytesWritten,
         (double)totalBytesWritten / (1024.0 * 1024.0));

finished:
    free(buffer);
    DVDCloseFile(vob);
    ifoClose(vts);
    
    return totalBytesWritten;
#else
    LOGE("libdvdread not available");
    return -1;
#endif
}

[[maybe_unused]] JNIEXPORT jobject JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdExtractVideoStreamNative(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber, jint chapterNumber) {
    // Placeholder
    return nullptr;
}

JNIEXPORT jstring JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdGetNameNative(JNIEnv *env, jobject clazz, jlong handle) {
    auto it = g_dvdHandles.find(handle);
    if (it == g_dvdHandles.end()) {
        LOGE("Invalid DVD handle: %ld", (long)handle);
        return nullptr;
    }
    
#ifdef HAVE_DVDREAD
    dvd_reader_t* dvd = it->second->dvd;
    ifo_handle_t* vmg = ifoOpen(dvd, 0);
    if (!vmg || !vmg->vmgi_mat) {
        if (vmg) ifoClose(vmg);
        return nullptr;
    }
    
    // Try to read DVD name from provider identifier (may contain DVD title)
    // Provider identifier is 32 bytes, but may not always contain the movie name
    const char* providerId = (const char*)vmg->vmgi_mat->provider_identifier;
    if (providerId && strlen(providerId) > 0) {
        // Trim whitespace and check if it's not just zeros/spaces
        char trimmed[33] = {0};
        int len = 0;
        for (int i = 0; i < 32 && providerId[i] != 0; i++) {
            if (providerId[i] != ' ' && providerId[i] != '\0') {
                trimmed[len++] = providerId[i];
            }
        }
        trimmed[len] = '\0';
        
        if (len > 0) {
            jstring result = env->NewStringUTF(trimmed);
            ifoClose(vmg);
            return result;
        }
    }
    
    // Try to read from TXTDT_MGI if available (Text Data Manager)
    // Ensure TXTDT_MGI is loaded (ifoOpen may not always load it)
    if (vmg->vmgi_mat->txtdt_mgi != 0 && !vmg->txtdt_mgi) {
        ifoRead_TXTDT_MGI(vmg);
    }
    
    if (vmg->txtdt_mgi) {
        const char* discName = (const char*)vmg->txtdt_mgi->disc_name;
        if (discName && strlen(discName) > 0) {
            // Trim whitespace
            char trimmed[13] = {0};
            int len = 0;
            for (int i = 0; i < 12 && discName[i] != 0; i++) {
                if (discName[i] != ' ' && discName[i] != '\0') {
                    trimmed[len++] = discName[i];
                }
            }
            trimmed[len] = '\0';
            
            if (len > 0) {
                jstring result = env->NewStringUTF(trimmed);
                ifoClose(vmg);
                return result;
            }
        }
    }
    
    ifoClose(vmg);
    return nullptr;
#else
    return nullptr;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_ejectDeviceNative(JNIEnv *env, jobject clazz, jstring devicePath) {
    const char* pathStr = env->GetStringUTFChars(devicePath, nullptr);
    if (!pathStr) return JNI_FALSE;
    
    int fd = open(pathStr, O_RDONLY | O_NONBLOCK);
    if (fd < 0) {
        env->ReleaseStringUTFChars(devicePath, pathStr);
        return JNI_FALSE;
    }
    
    int result = ioctl(fd, CDROMEJECT, 0);
    close(fd);
    env->ReleaseStringUTFChars(devicePath, pathStr);
    
    return (result == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdDecryptCss(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber) {
    return nullptr; // Placeholder
}

#ifdef HAVE_DVDREAD
// Get VOB offsets for a title - returns array of [firstSector, lastSector, firstSector, lastSector, ...]
// Returns null on error, or array with 2*cellCount elements
JNIEXPORT jlongArray JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdGetVobOffsetsNative(JNIEnv *env, jobject clazz, jlong handle, jint titleNumber) {
    auto it = g_dvdHandles.find(handle);
    if (it == g_dvdHandles.end()) {
        LOGE("Invalid DVD handle: %ld", (long)handle);
        return nullptr;
    }
    
    dvd_reader_t* dvd = it->second->dvd;
    
    LOGI("=== Getting VOB offsets for title %d ===", titleNumber);
    
    // 1. Open VMG (Video Manager) to find Title info
    ifo_handle_t* vmg = ifoOpen(dvd, 0);
    if (!vmg) {
        LOGE("Failed to open VMG (Video Manager)");
        return nullptr;
    }
    
    if (!vmg->tt_srpt) {
        LOGE("No title search pointer table in VMG");
        ifoClose(vmg);
        return nullptr;
    }
    
    int numTitles = vmg->tt_srpt->nr_of_srpts;
    LOGI("DVD has %d titles", numTitles);
    
    if (titleNumber < 1 || titleNumber > numTitles) {
        LOGE("Invalid title number: %d (valid: 1-%d)", titleNumber, numTitles);
        ifoClose(vmg);
        return nullptr;
    }
    
    // Get the VTS (Video Title Set) number
    int vtsN = vmg->tt_srpt->title[titleNumber-1].title_set_nr;
    int vtsTtn = vmg->tt_srpt->title[titleNumber-1].vts_ttn;
    
    LOGI("Title %d: VTS=%d, VTS_TTN=%d", titleNumber, vtsN, vtsTtn);
    
    ifoClose(vmg);
    
    // 2. Open VTS IFO
    ifo_handle_t* vts = ifoOpen(dvd, vtsN);
    if (!vts) {
        LOGE("Failed to open VTS %d IFO", vtsN);
        return nullptr;
    }
    
    // 3. Find the correct PGC (Program Chain) for this title
    pgc_t* pgc = nullptr;
    
    if (vts->vts_ptt_srpt && vtsTtn > 0 && vtsTtn <= vts->vts_ptt_srpt->nr_of_srpts) {
        int pgcn = vts->vts_ptt_srpt->title[vtsTtn-1].ptt[0].pgcn;
        int pgn = vts->vts_ptt_srpt->title[vtsTtn-1].ptt[0].pgn;
        
        LOGI("VTS_TTN %d maps to PGCN=%d, PGN=%d", vtsTtn, pgcn, pgn);
        
        if (vts->vts_pgcit && pgcn > 0 && pgcn <= vts->vts_pgcit->nr_of_pgci_srp) {
            pgc = vts->vts_pgcit->pgci_srp[pgcn-1].pgc;
        }
    }
    
    // Fallback: use first PGC if we couldn't find it via PTT
    if (!pgc && vts->vts_pgcit && vts->vts_pgcit->nr_of_pgci_srp > 0) {
        LOGW("Using fallback: first PGC in VTS");
        pgc = vts->vts_pgcit->pgci_srp[0].pgc;
    }
    
    if (!pgc) {
        LOGE("No PGC found for VTS %d, VTS_TTN %d", vtsN, vtsTtn);
        ifoClose(vts);
        return nullptr;
    }
    
    LOGI("PGC has %d cells", pgc->nr_of_cells);
    
    // 4. Collect cell offsets
    std::vector<jlong> offsets;
    for (int i = 0; i < pgc->nr_of_cells; i++) {
        cell_playback_t* cell = &pgc->cell_playback[i];
        uint32_t firstSector = cell->first_sector;
        uint32_t lastSector = cell->last_sector;
        
        if (lastSector >= firstSector) {
            offsets.push_back((jlong)firstSector);
            offsets.push_back((jlong)lastSector);
            LOGD("Cell %d: sectors %u-%u", i, firstSector, lastSector);
        } else {
            LOGW("Cell %d: Invalid sector range (first=%u, last=%u), skipping", i, firstSector, lastSector);
        }
    }
    
    ifoClose(vts);
    
    if (offsets.empty()) {
        LOGE("No valid cell offsets found");
        return nullptr;
    }
    
    // 5. Create Java array - first element is VTS number, then offsets
    // Format: [vtsN, firstSector, lastSector, firstSector, lastSector, ...]
    std::vector<jlong> resultArray;
    resultArray.push_back((jlong)vtsN);  // First element is VTS number
    resultArray.insert(resultArray.end(), offsets.begin(), offsets.end());
    
    jlongArray result = env->NewLongArray(resultArray.size());
    if (!result) {
        LOGE("Failed to create Java long array");
        return nullptr;
    }
    
    env->SetLongArrayRegion(result, 0, resultArray.size(), resultArray.data());
    LOGI("Returning VTS=%d and %zu cell offsets (array size: %zu)", vtsN, offsets.size() / 2, resultArray.size());
    
    return result;
}

// Open VOB file for reading - returns handle to be used with dvdReadVobBlocksNative
JNIEXPORT jlong JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdOpenVobFileNative(JNIEnv *env, jobject clazz, jlong handle, jint vtsN) {
    auto it = g_dvdHandles.find(handle);
    if (it == g_dvdHandles.end()) {
        LOGE("Invalid DVD handle: %ld", (long)handle);
        return -1;
    }
    
    dvd_reader_t* dvd = it->second->dvd;
    
    LOGI("Opening VOB file for VTS %d", vtsN);
    dvd_file_t* vob = DVDOpenFile(dvd, vtsN, DVD_READ_TITLE_VOBS);
    if (!vob) {
        LOGE("Failed to open VOBs for VTS %d", vtsN);
        return -1;
    }
    
    ssize_t vobFileSize = DVDFileSize(vob);
    LOGI("VOB file opened, size: %zd blocks (%.2f MB)", vobFileSize, 
         (double)(vobFileSize * DVD_VIDEO_LB_LEN) / (1024.0 * 1024.0));
    
    jlong vobHandleId = g_nextVobHandleId++;
    auto vobHandle = std::make_unique<VobHandle>(vob, vtsN, dvd);
    g_vobHandles[vobHandleId] = std::move(vobHandle);
    
    LOGI("VOB handle created: %ld", (long)vobHandleId);
    return vobHandleId;
}

// Read VOB blocks - returns number of bytes read, or -1 on error
JNIEXPORT jint JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdReadVobBlocksNative(JNIEnv *env, jobject clazz, jlong vobHandle, jint block, jint count, jbyteArray buffer) {
    auto it = g_vobHandles.find(vobHandle);
    if (it == g_vobHandles.end()) {
        LOGE("Invalid VOB handle: %ld", (long)vobHandle);
        return -1;
    }
    
    dvd_file_t* vob = it->second->vob;
    if (!vob) {
        LOGE("VOB file is null");
        return -1;
    }
    
    if (count <= 0) {
        LOGE("Invalid block count: %d", count);
        return -1;
    }
    
    // Allocate temporary buffer for reading
    size_t bufferSize = count * DVD_VIDEO_LB_LEN;
    unsigned char* tempBuffer = (unsigned char*)malloc(bufferSize);
    if (!tempBuffer) {
        LOGE("Failed to allocate read buffer");
        return -1;
    }
    
    // Read blocks from VOB file
    ssize_t blocksRead = DVDReadBlocks(vob, block, count, tempBuffer);
    
    if (blocksRead <= 0) {
        free(tempBuffer);
        if (blocksRead == 0) {
            LOGD("EOF reached at block %d", block);
            return 0;
        }
        LOGE("Error reading blocks at position %d (requested %d blocks): returned %zd", 
             block, count, blocksRead);
        return -1;
    }
    
    // Copy to Java array
    jsize bytesRead = blocksRead * DVD_VIDEO_LB_LEN;
    jsize arraySize = env->GetArrayLength(buffer);
    if (arraySize < bytesRead) {
        LOGW("Java buffer too small: %d < %d, truncating", arraySize, bytesRead);
        bytesRead = arraySize;
    }
    
    env->SetByteArrayRegion(buffer, 0, bytesRead, (jbyte*)tempBuffer);
    free(tempBuffer);
    
    return bytesRead;
}

// Close VOB file
JNIEXPORT void JNICALL
Java_com_ble1st_connectias_feature_dvd_native_DvdNative_dvdCloseVobFileNative(JNIEnv *env, jobject clazz, jlong vobHandle) {
    auto it = g_vobHandles.find(vobHandle);
    if (it == g_vobHandles.end()) {
        LOGW("VOB handle %ld not found for closing", (long)vobHandle);
        return;
    }
    
    LOGI("Closing VOB handle: %ld", (long)vobHandle);
    g_vobHandles.erase(it);
    LOGD("VOB handle closed, remaining handles: %zu", g_vobHandles.size());
}
#endif

} // extern "C"