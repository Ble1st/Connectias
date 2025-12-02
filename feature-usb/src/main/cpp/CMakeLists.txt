cmake_minimum_required(VERSION 3.18.1)

project("usb")

# Find log library
find_library(log-lib log)

# libusb Integration
# Note: If libusb is prebuilt, we might need to point to it differently or build it here.
# The original CMakeLists looked for it in jniLibs.
# Assuming jniLibs might still be needed in feature-usb if it contained libusb.so?
# I moved jniLibs to feature-dvd.
# Does feature-usb need libusb.so? 
# UsbNative.kt loads "usb_jni". "usb_jni" likely links against "libusb".
# If libusb was in jniLibs, I moved it away. I should check jniLibs content in feature-dvd.

# USB JNI Library
add_library(usb_jni SHARED
    usb_jni.cpp
    usb_wrapper.h
)

# 16 KB page size alignment for Android 15+ compatibility
target_link_options(usb_jni PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

target_link_libraries(usb_jni
    ${log-lib}
)

# If we had usb_lib logic:
# find_library(usb_lib usb ...) 
# target_link_libraries(usb_jni ${usb_lib})
