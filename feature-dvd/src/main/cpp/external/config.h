#ifndef CONFIG_H
#define CONFIG_H

#define HAVE_DLFCN_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_MEMORY_H 1
#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRINGS_H 1
#define HAVE_STRING_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_UNISTD_H 1
#define HAVE_LIMITS_H 1
#define HAVE_FCNTL_H 1

/* Android specifics */
#define HAVE_VSNPRINTF 1
#define HAVE_SNPRINTF 1

/* Package info */
#define PACKAGE "libdvdread"
#define VERSION "6.1.3" 

/* CSS Decryption Support via libdvdcss */
#define HAVE_DVDCSS 1
#define HAVE_DVDCSS_DVDCSS_H 1

/* DVD-Audio CPXM support */
#define HAVE_DVDCSS_DVDCPXM_H 1

#endif
