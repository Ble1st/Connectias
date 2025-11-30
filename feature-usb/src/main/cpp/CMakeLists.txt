cmake_minimum_required(VERSION 3.18.1)

project("usb")

# Find log library
find_library(log-lib log)

# libusb Integration
find_library(usb_lib usb PATHS ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})
if(NOT usb_lib)
    message(WARNING "libusb not found - USB functionality may be limited")
endif()

# libdvdread/libdvdnav Integration (always active)
find_library(dvdread_lib dvdread PATHS ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})
find_library(dvdnav_lib dvdnav PATHS ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})
find_library(ffmpeg_lib ffmpeg PATHS ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})

# Optional: libdvdcss
# Feature flag for DVD CSS decryption (set via: cmake -DENABLE_DVD_CSS=ON/OFF)
set(ENABLE_DVD_CSS OFF CACHE BOOL "Enable DVD CSS decryption support")

if(ENABLE_DVD_CSS)
    find_library(dvdcss_lib dvdcss PATHS ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})
    message(STATUS "CSS decryption enabled (libdvdcss)")
    add_definitions(-DENABLE_DVD_CSS)
else()
    message(STATUS "CSS decryption disabled")
endif()

# USB JNI Library
add_library(usb_jni SHARED
    usb_jni.cpp
    usb_wrapper.h
)

# 16 KB page size alignment for Android 15+ compatibility
# Android 15+ (API level 35+) uses 16 KB minimal page granularity for BSS segments,
# relocations, and shared library segments. This is a hard compatibility requirement
# for native libraries on Android 15+. Without this alignment, the linker will fail
# or the library may not load correctly on Android 15+ devices.
# On Android < 15, these flags are ignored or have no effect, so it's safe to apply
# them unconditionally. However, we conditionally apply them only for Android API 35+
# to be explicit about the requirement.
if(ANDROID_PLATFORM_LEVEL GREATER_EQUAL 35)
    target_link_options(usb_jni PRIVATE
        -Wl,-z,max-page-size=16384
        -Wl,-z,common-page-size=16384
    )
    message(STATUS "Applied 16 KB page size alignment for Android 15+ compatibility (usb_jni)")
else()
    message(STATUS "Skipping 16 KB page size alignment (Android < 15, usb_jni)")
endif()

target_link_libraries(usb_jni
    ${log-lib}
)

if(usb_lib)
    target_link_libraries(usb_jni ${usb_lib})
endif()

# DVD JNI Library
add_library(dvd_jni SHARED
    dvd_jni.cpp
)

# 16 KB page size alignment for Android 15+ compatibility
# Android 15+ (API level 35+) uses 16 KB minimal page granularity for BSS segments,
# relocations, and shared library segments. This is a hard compatibility requirement
# for native libraries on Android 15+. Without this alignment, the linker will fail
# or the library may not load correctly on Android 15+ devices.
# On Android < 15, these flags are ignored or have no effect, so it's safe to apply
# them unconditionally. However, we conditionally apply them only for Android API 35+
# to be explicit about the requirement.
if(ANDROID_PLATFORM_LEVEL GREATER_EQUAL 35)
    target_link_options(dvd_jni PRIVATE
        -Wl,-z,max-page-size=16384
        -Wl,-z,common-page-size=16384
    )
    message(STATUS "Applied 16 KB page size alignment for Android 15+ compatibility (dvd_jni)")
else()
    message(STATUS "Skipping 16 KB page size alignment (Android < 15, dvd_jni)")
endif()

target_link_libraries(dvd_jni
    ${log-lib}
)

# Optional: libdvdread/libdvdnav/ffmpeg (only link if found)
if(dvdread_lib)
    target_link_libraries(dvd_jni ${dvdread_lib})
else()
    message(WARNING "libdvdread not found - DVD functionality will be limited")
endif()

if(dvdnav_lib)
    target_link_libraries(dvd_jni ${dvdnav_lib})
else()
    message(WARNING "libdvdnav not found - DVD navigation will be limited")
endif()

if(ffmpeg_lib)
    target_link_libraries(dvd_jni ${ffmpeg_lib})
else()
    message(WARNING "FFmpeg not found - Video decoding will be limited")
endif()

# Optional: libdvdcss
if(ENABLE_DVD_CSS)
    if(dvdcss_lib)
        target_link_libraries(dvd_jni ${dvdcss_lib})
    else()
        message(WARNING "libdvdcss not found but ENABLE_DVD_CSS is ON")
    endif()
endif()
