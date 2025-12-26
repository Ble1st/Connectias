cmake_minimum_required(VERSION 3.18.1)

# --- libdvdcss (CSS Decryption) ---

set(DVDCSS_VERSION_MAJOR 1)
set(DVDCSS_VERSION_MINOR 5)
set(DVDCSS_VERSION_MICRO 0)

set(DVDCSS_SOURCES
    libdvdcss/src/libdvdcss.c
    libdvdcss/src/libdvdcpxm.c
    libdvdcss/src/css.c
    libdvdcss/src/cpxm.c
    libdvdcss/src/device.c
    libdvdcss/src/error.c
    libdvdcss/src/ioctl.c
)

add_library(dvdcss STATIC ${DVDCSS_SOURCES})

# 16 KB page size alignment for Android 15+ compatibility
target_link_options(dvdcss PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

target_include_directories(dvdcss PUBLIC
    libdvdcss
    libdvdcss/src
    libdvdcss/src/dvdcss
    libdvdcss/compat      # For scsi/sg.h compatibility header
)

target_compile_definitions(dvdcss PRIVATE
    -DHAVE_CONFIG_H
    -D_FILE_OFFSET_BITS=64
    -DANDROID
    -D_DEFAULT_SOURCE
    -DHAVE_SCSI_SG_H=1            # Enable SCSI Generic support
    -DDVD_STRUCT_IN_LINUX_CDROM_H=1
    -DHAVE_LINUX_DVD_STRUCT=1
)

# --- libdvdread ---

set(DVDREAD_VERSION_MAJOR 6)
set(DVDREAD_VERSION_MINOR 1)
set(DVDREAD_VERSION_MICRO 3)

configure_file(
    libdvdread/src/dvdread/version.h.in
    ${CMAKE_CURRENT_BINARY_DIR}/dvdread/version.h
)

set(DVDREAD_SOURCES
    libdvdread/src/bitreader.c
    libdvdread/src/dvd_input.c
    libdvdread/src/dvd_reader.c
    libdvdread/src/dvd_udf.c
    libdvdread/src/ifo_print.c
    libdvdread/src/ifo_read.c
    libdvdread/src/logger.c
    libdvdread/src/md5.c
    libdvdread/src/nav_print.c
    libdvdread/src/nav_read.c
)

add_library(dvdread STATIC ${DVDREAD_SOURCES})

# 16 KB page size alignment for Android 15+ compatibility
target_link_options(dvdread PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

# Link libdvdread with libdvdcss for CSS decryption
target_link_libraries(dvdread PUBLIC dvdcss)

target_include_directories(dvdread PUBLIC
    libdvdread/src
    libdvdcss/src        # For dvdcss/dvdcss.h
    libdvdcss/src/dvdcss # For direct header access
    ${CMAKE_CURRENT_BINARY_DIR}
    .
)

target_compile_definitions(dvdread PRIVATE
    -DHAVE_CONFIG_H
    -D_FILE_OFFSET_BITS=64
    -DANDROID
    -DHAVE_DVDCSS_DVDCSS_H=1  # Enable CSS support via libdvdcss
)

# --- libdvdnav ---

set(DVDNAV_MAJOR 6)
set(DVDNAV_MINOR 1)
set(DVDNAV_SUB 1)

configure_file(
    libdvdnav/src/dvdnav/version.h.in
    ${CMAKE_CURRENT_BINARY_DIR}/dvdnav/version.h
)

set(DVDNAV_SOURCES
    libdvdnav/src/dvdnav.c
    libdvdnav/src/highlight.c
    libdvdnav/src/logger.c
    libdvdnav/src/navigation.c
    libdvdnav/src/read_cache.c
    libdvdnav/src/searching.c
    libdvdnav/src/settings.c
    libdvdnav/src/vm/decoder.c
    libdvdnav/src/vm/getset.c
    libdvdnav/src/vm/play.c
    libdvdnav/src/vm/vm.c
    libdvdnav/src/vm/vmcmd.c
    libdvdnav/src/vm/vmget.c
)

add_library(dvdnav STATIC ${DVDNAV_SOURCES})

# 16 KB page size alignment for Android 15+ compatibility
target_link_options(dvdnav PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

target_link_libraries(dvdnav PUBLIC dvdread)

target_include_directories(dvdnav PUBLIC
    libdvdnav/src
    libdvdnav/src/vm
    ${CMAKE_CURRENT_BINARY_DIR}
    .
)

target_compile_definitions(dvdnav PRIVATE
    -DHAVE_CONFIG_H
    -D_FILE_OFFSET_BITS=64
    -DANDROID
)
