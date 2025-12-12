cmake_minimum_required(VERSION 3.18.1)

project("usb")

# Find log library
find_library(log-lib log)

# libusb Integration
# libusb can be integrated in several ways:
# 1. Prebuilt library: Place libusb.so in src/main/jniLibs/<arch>/libusb.so
# 2. External project: Add libusb as submodule and build it
# 3. System library: If available in Android system (rare)
#
# For now, we try to find prebuilt libusb library
# If not found, the code will compile but runtime will fail gracefully

# Try to find libusb library (prebuilt)
find_library(libusb-lib
    NAMES usb usb-1.0 libusb libusb-1.0
    PATHS
        ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}
        ${CMAKE_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}
    NO_DEFAULT_PATH
)

# USB JNI Library
add_library(usb_jni SHARED
    usb_jni.cpp
    usb_wrapper.h
    libusb_wrapper.h
)

# Include directories
target_include_directories(usb_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# 16 KB page size alignment for Android 15+ compatibility
target_link_options(usb_jni PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

# Link libraries
if(libusb-lib)
    message(STATUS "Found libusb library: ${libusb-lib}")
    target_link_libraries(usb_jni
        ${log-lib}
        ${libusb-lib}
    )
else()
    message(WARNING "libusb library not found - USB functionality will be limited")
    message(STATUS "To enable full USB support:")
    message(STATUS "  1. Build libusb for Android (see https://github.com/libusb/libusb)")
    message(STATUS "  2. Place libusb.so in src/main/jniLibs/<arch>/")
    message(STATUS "  3. Rebuild the project")
    target_link_libraries(usb_jni
        ${log-lib}
    )
    # Add compile definition to indicate libusb is not available
    target_compile_definitions(usb_jni PRIVATE LIBUSB_NOT_AVAILABLE)
endif()
