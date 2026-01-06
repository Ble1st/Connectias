# CMakeLists.txt for Rust Root Detector JNI Bridge
cmake_minimum_required(VERSION 3.22.1)

project("connectias_root_detector")

# Find required packages
find_library(log-lib log)

# Rust library path (will be set by Gradle)
set(RUST_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../rust/target/${ANDROID_ABI}/release")
set(RUST_LIB_NAME "libconnectias_root_detector.so")

# Check if Rust library exists
if(EXISTS "${RUST_LIB_DIR}/${RUST_LIB_NAME}")
    message(STATUS "Found Rust library: ${RUST_LIB_DIR}/${RUST_LIB_NAME}")
    
    # Copy Rust library to output directory
    add_custom_command(
        OUTPUT ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/${RUST_LIB_NAME}
        COMMAND ${CMAKE_COMMAND} -E copy
            ${RUST_LIB_DIR}/${RUST_LIB_NAME}
            ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/${RUST_LIB_NAME}
        DEPENDS ${RUST_LIB_DIR}/${RUST_LIB_NAME}
        COMMENT "Copying Rust library to output directory"
    )
    
    add_custom_target(copy_rust_lib ALL
        DEPENDS ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/${RUST_LIB_NAME}
    )
else()
    message(WARNING "Rust library not found at ${RUST_LIB_DIR}/${RUST_LIB_NAME}")
    message(WARNING "Make sure to build Rust library first: cd src/main/rust && cargo ndk build")
endif()

