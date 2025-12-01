cmake_minimum_required(VERSION 3.18.1)

project("usb")

# Find log library
find_library(log-lib log)

# libusb Integration
find_library(usb_lib usb PATHS ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})
if(NOT usb_lib)
    message(WARNING "libusb not found - USB functionality may be limited")
endif()

# --- External Libraries (Build from Source) ---
# Includes: libdvdcss (CSS decryption), libdvdread (DVD reading), libdvdnav (DVD navigation)
add_subdirectory(external)
# ----------------------------------------------

# CSS decryption is now always enabled via libdvdcss built from source
message(STATUS "CSS decryption enabled (libdvdcss built from source)")

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

if(usb_lib)
    target_link_libraries(usb_jni ${usb_lib})
endif()

# DVD JNI Library
add_library(dvd_jni SHARED
    dvd_jni.cpp
)

# 16 KB page size alignment for Android 15+ compatibility
target_link_options(dvd_jni PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

# Link against our locally built static libraries
target_link_libraries(dvd_jni
    ${log-lib}
    dvdread     # Linked from external (static)
    dvdnav      # Linked from external (static)
    android     # For system libs
)

# Define HAVE_DVDREAD to enable code in dvd_jni.cpp
# CSS decryption is handled by libdvdcss which is statically linked into libdvdread
target_compile_definitions(dvd_jni PRIVATE HAVE_DVDREAD)
